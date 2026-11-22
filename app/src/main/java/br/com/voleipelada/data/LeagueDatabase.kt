package br.com.voleipelada.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import br.com.voleipelada.data.local.entity.*
import br.com.voleipelada.domain.*
import br.com.voleipelada.domain.usecase.rotation.ProcessMatchEndUseCase
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Database(entities = [TeamEntity::class, PlayerEntity::class, RotationRulesEntity::class,
    MatchHistoryEntity::class, ActiveMatchStateEntity::class], version = 4, exportSchema = true)
abstract class LeagueDatabase : RoomDatabase() {
    abstract fun dao(): LeagueDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE rotation_rules SET championReturnPolicy = 'FIRST_IN_QUEUE' " +
                        "WHERE id = 1 AND championReturnPolicy = 'AHEAD_OF_LOSER' " +
                        "AND maxConsecutiveWins = 2 AND fixedReturnPosition = 2 AND bothLeave = 0"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE players ADD COLUMN sex TEXT NOT NULL DEFAULT 'UNSPECIFIED'")
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE match_history ADD COLUMN visibleInHistory INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE match_history ADD COLUMN countsForStandings INTEGER NOT NULL DEFAULT 1")
            }
        }

        val seed = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()
                val names = listOf("Time Azul", "Time Vermelho", "Time Amarelo", "Time Verde")
                val colors = listOf("FF2563EB", "FFDC2626", "FFA16207", "FF047857")
                val statuses = listOf("ON_COURT_A", "ON_COURT_B", "IN_QUEUE", "IN_QUEUE")
                val rosters = listOf("Lucas,Mariana,Pedro,Bia", "Rafael,Camila,Thiago,Juliana",
                    "Bruno,Fernanda,Gustavo,Larissa", "Diego,Amanda,Felipe,Carolina")
                names.indices.forEach { index ->
                    val id = index + 1
                    db.execSQL("INSERT INTO teams (id,name,colorHex,consecutiveWins,status,queuePosition,createdAt) VALUES (?, ?, ?, 0, ?, ?, ?)",
                        arrayOf(id, names[index], colors[index], statuses[index], if (index < 2) -1 else index - 2, now))
                    rosters[index].split(",").forEach { player ->
                        db.execSQL("INSERT INTO players (name,skillLevel,isPresent,assignedTeamId) VALUES (?,3,1,?)",
                            arrayOf(player, id))
                    }
                }
                db.execSQL(
                    "INSERT INTO rotation_rules (id,targetPoints,winByTwo,maxPointsCap,maxConsecutiveWins," +
                        "championReturnPolicy,fixedReturnPosition,keepScreenOn,enableSoundAndHaptics,bothLeave,tieRule) " +
                        "VALUES (1,15,1,NULL,2,'FIRST_IN_QUEUE',2,1,1,0,'BOTH_LEAVE')"
                )
                db.execSQL(
                    "INSERT INTO active_match_state (id,teamAId,teamBId,scoreA,scoreB,servingTeam,sidesSwapped," +
                        "elapsedSeconds,elapsedRemainderMs,startedAt,pointHistoryStackJson,round) " +
                        "VALUES (1,1,2,0,0,'TEAM_A',0,0,0,NULL,'[]',0)"
                )
            }
        }
    }
}

@Serializable
private data class PointRow(val a: Int, val b: Int, val serve: Int)

private fun TeamEntity.asTeam() =
    Team(id, name, colorHex.toLong(16).toInt(), consecutiveWins, createdAt)

private fun PlayerEntity.asPlayer() = Player(id, name, skillLevel, isPresent, assignedTeamId,
    PlayerSex.entries.firstOrNull { it.name == sex } ?: PlayerSex.UNSPECIFIED)

private fun RotationRulesEntity.asRules() = Rules(targetPoints, winByTwo, maxPointsCap ?: 0,
    maxConsecutiveWins ?: 0, ReturnRule.valueOf(championReturnPolicy), fixedReturnPosition,
    bothLeave, TieRule.valueOf(tieRule), keepScreenOn, enableSoundAndHaptics)

private fun Rules.asRow() = RotationRulesEntity(targetPoints = target, winByTwo = winByTwo,
    maxPointsCap = cap.takeIf { it > 0 }, maxConsecutiveWins = winLimit.takeIf { it > 0 },
    championReturnPolicy = returnRule.name, fixedReturnPosition = returnPosition, bothLeave = bothLeave,
    tieRule = tieRule.name, keepScreenOn = keepScreenOn, enableSoundAndHaptics = feedback)

private fun MatchHistoryEntity.asResult(teams: Map<Long, Team>) = Result(
    teams[teamAId]?.copy(name = teamAName) ?: Team(teamAId, teamAName, 0xFF2563EB.toInt()),
    teams[teamBId]?.copy(name = teamBName) ?: Team(teamBId, teamBName, 0xFFDC2626.toInt()),
    teamAScore, teamBScore, durationSeconds * 1000, finishedAt,
    when (winnerTeamId) { teamAId -> 0; teamBId -> 1; else -> null }, teamAReason, teamBReason,
)

private fun Result.asRow(next: Session) = MatchHistoryEntity(teamAId = left.id, teamAName = left.name, teamAScore = leftScore,
    teamBId = right.id, teamBName = right.name, teamBScore = rightScore,
    winnerTeamId = winner?.let { if (it == 0) left.id else right.id },
    loserTeamId = winner?.let { if (it == 0) right.id else left.id },
    championExitedByStreakLimit = "Limite de vitórias" in listOf(leftReason, rightReason),
    exitReasonSummary = listOf(left to leftReason, right to rightReason).joinToString(". ") { (team, reason) ->
        val position = next.queue.indexOf(team.id) + 1
        "${team.name} · $reason" + if (position > 0) " · ${position}º na fila" else ""
    },
    durationSeconds = durationMs / 1000, finishedAt = finishedAt, teamAReason = leftReason, teamBReason = rightReason)

@Dao
abstract class LeagueDao {
    @Query("SELECT * FROM teams ORDER BY id") abstract suspend fun teams(): List<TeamEntity>
    @Query("SELECT * FROM players ORDER BY id") abstract suspend fun players(): List<PlayerEntity>
    @Query("SELECT * FROM active_match_state WHERE id = 1") abstract suspend fun active(): ActiveMatchStateEntity?
    @Query("SELECT * FROM rotation_rules WHERE id = 1") abstract suspend fun rules(): RotationRulesEntity?
    @Query("SELECT * FROM match_history ORDER BY id DESC") abstract suspend fun history(): List<MatchHistoryEntity>
    @Query("UPDATE match_history SET visibleInHistory = 0 WHERE visibleInHistory = 1")
    abstract suspend fun hideHistory()
    @Query("UPDATE match_history SET countsForStandings = 0 WHERE countsForStandings = 1")
    abstract suspend fun uncountStandings()
    @Query("DELETE FROM match_history WHERE visibleInHistory = 0 AND countsForStandings = 0")
    abstract suspend fun removeUnusedResults()
    @Query("DELETE FROM teams WHERE id = :id") abstract suspend fun deleteTeam(id: Long)
    @Query("DELETE FROM players WHERE id = :id") abstract suspend fun deletePlayer(id: Long)
    @Upsert abstract suspend fun saveTeam(team: TeamEntity): Long
    @Upsert abstract suspend fun savePlayers(players: List<PlayerEntity>)
    @Upsert abstract suspend fun saveActive(active: ActiveMatchStateEntity)
    @Upsert abstract suspend fun saveRules(rules: RotationRulesEntity)
    @Insert abstract suspend fun saveHistory(result: MatchHistoryEntity)

    private suspend fun readSession(): Session {
        val rows = teams()
        val active = active() ?: ActiveMatchStateEntity()
        return Session(
            rows.map { it.asTeam() },
            listOfNotNull(active.teamAId, active.teamBId),
            rows.filter { it.status == "IN_QUEUE" }.sortedBy { it.queuePosition }.map { it.id },
            Match(listOf(active.scoreA, active.scoreB), if (active.servingTeam == "TEAM_B") 1 else 0,
                active.elapsedSeconds * 1000 + active.elapsedRemainderMs, active.startedAt, active.sidesSwapped,
                Json.decodeFromString<List<PointRow>>(active.pointHistoryStackJson).map {
                    PointSnapshot(listOf(it.a, it.b), it.serve)
                }),
            (rules() ?: RotationRulesEntity()).asRules(),
            active.round,
            players().map { it.asPlayer() },
        )
    }

    private suspend fun persist(previous: Session, next: Session) {
        val old = previous.teams.associateBy { it.id }
        val remapped = mutableMapOf<Long, Long>()
        val nextIds = next.teams.map { it.id }.toSet()
        for (removed in previous.teams.filter { it.id !in nextIds }) {
            deleteTeam(removed.id)
        }
        for (team in next.teams) {
            val position = next.queue.indexOf(team.id)
            val status = when (next.court.indexOf(team.id)) {
                0 -> "ON_COURT_A"
                1 -> "ON_COURT_B"
                else -> if (position >= 0) "IN_QUEUE" else "BENCHED"
            }
            if (old[team.id] == team && previous.court.indexOf(team.id) == next.court.indexOf(team.id) &&
                previous.queue.indexOf(team.id) == position) continue
            val inserted = saveTeam(TeamEntity(team.id.coerceAtLeast(0), team.name,
                team.color.toUInt().toString(16), team.wins, status, position, team.createdAt))
            val id = if (team.id < 0) inserted else team.id
            if (team.id < 0) remapped[team.id] = id
        }
        if (previous.players != next.players) {
            val previousPlayers = previous.players.associateBy { it.id }
            val nextPlayerIds = next.players.map { it.id }.toSet()
            for (id in previousPlayers.keys - nextPlayerIds) deletePlayer(id)
            val changed = next.players.filter { previousPlayers[it.id] != it }.map { player ->
                val teamId = player.assignedTeamId
                PlayerEntity(player.id.coerceAtLeast(0), player.name, player.skillLevel, player.isPresent,
                    remapped[teamId] ?: teamId, player.sex.name)
            }
            if (changed.isNotEmpty()) savePlayers(changed)
        }
        if (previous.match != next.match || previous.court != next.court || previous.round != next.round) {
            saveMatch(next, remapped)
        }
        if (previous.rules != next.rules) saveRules(next.rules.asRow())
    }

    private suspend fun saveMatch(next: Session, remapped: Map<Long, Long>) {
        val match = next.match
        val court = next.court.map { remapped[it] ?: it }
        saveActive(ActiveMatchStateEntity(teamAId = court.getOrNull(0), teamBId = court.getOrNull(1),
            scoreA = match.scores[0], scoreB = match.scores[1],
            servingTeam = if (court.size < 2) "NONE" else if (match.serve == 0) "TEAM_A" else "TEAM_B",
            sidesSwapped = match.swapped, elapsedSeconds = match.elapsedMs / 1000,
            elapsedRemainderMs = match.elapsedMs % 1000, startedAt = match.startedAt,
            pointHistoryStackJson = Json.encodeToString(match.points.map { PointRow(it.scores[0], it.scores[1], it.serve) }),
            round = next.round))
    }

    @Transaction
    open suspend fun readLeague(): League {
        val session = readSession()
        val teams = session.teams.associateBy { it.id }
        val rows = history()
        val results = rows.associate { it.id to it.asResult(teams) }
        return League(
            session = session,
            history = rows.filter { it.visibleInHistory }.map { results.getValue(it.id) },
            standingsMatches = rows.filter { it.countsForStandings }.map { results.getValue(it.id) },
        )
    }

    @Transaction
    open suspend fun update(transform: (Session) -> Session) {
        val current = readSession()
        val next = transform(current)
        if (current != next) persist(current, next)
    }

    @Transaction
    open suspend fun drawTeams(playerIds: Set<Long>, size: Int, replace: Boolean, minWomen: Int) {
        val current = readSession()
        persist(current, current.draw(playerIds, size, replace, minWomen))
        if (replace) {
            uncountStandings()
            removeUnusedResults()
        }
    }

    @Transaction
    open suspend fun clearHistory() {
        hideHistory()
        removeUnusedResults()
    }

    @Transaction
    open suspend fun resetStandings() {
        uncountStandings()
        removeUnusedResults()
    }

    @Transaction
    open suspend fun finishMatchAndRotateTeams(expected: Session, now: Long) {
        val current = readSession()
        if (current != expected) return
        val rotation = ProcessMatchEndUseCase()(current, now) ?: return
        saveHistory(rotation.result.asRow(rotation.next))
        persist(current, rotation.next)
    }
}

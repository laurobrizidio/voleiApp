package br.com.voleipro.data

import androidx.room.*
import br.com.voleipro.data.local.entity.*
import br.com.voleipro.domain.*
import br.com.voleipro.domain.usecase.rotation.ProcessMatchEndUseCase
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Database(entities = [TeamEntity::class, PlayerEntity::class, RotationRulesEntity::class,
    MatchHistoryEntity::class, ActiveMatchStateEntity::class, PeladaEntity::class, PlayerPeladaEntity::class],
    version = 8, exportSchema = true)
abstract class LeagueDatabase : RoomDatabase() {
    abstract fun dao(): LeagueDao
}

@Serializable
private data class PointRow(val a: Int, val b: Int, val serve: Int)

private fun TeamEntity.asTeam() =
    Team(id, name, colorHex.toLong(16).toInt(), consecutiveWins, createdAt)

private fun PlayerEntity.asPlayer(membership: PlayerPeladaEntity?) =
    Player(id, name, skillLevel, membership?.isPresent == true, membership?.assignedTeamId,
    PlayerSex.entries.firstOrNull { it.name == sex } ?: PlayerSex.UNSPECIFIED,
    PlayerPosition.entries.firstOrNull { it.name == membership?.position } ?: PlayerPosition.UNSPECIFIED)

private fun PeladaEntity.asPelada() =
    Pelada(id, name, teamSize, Composition.valueOf(composition), minWomen, minMen)

private fun RotationRulesEntity.asRules() = Rules(targetPoints, winByTwo, maxPointsCap ?: 0,
    maxConsecutiveWins ?: 0, ReturnRule.valueOf(championReturnPolicy), fixedReturnPosition,
    bothLeave, TieRule.valueOf(tieRule), keepScreenOn, enableSoundAndHaptics,
    SetPointTieBreak.entries.firstOrNull { it.name == setPointTieBreak } ?: SetPointTieBreak.STANDARD)

private fun Rules.asRow(leagueId: Long) = RotationRulesEntity(id = leagueId, targetPoints = target, winByTwo = winByTwo,
    maxPointsCap = cap.takeIf { it > 0 }, maxConsecutiveWins = winLimit.takeIf { it > 0 },
    championReturnPolicy = returnRule.name, fixedReturnPosition = returnPosition, bothLeave = bothLeave,
    tieRule = tieRule.name, keepScreenOn = keepScreenOn, enableSoundAndHaptics = feedback,
    setPointTieBreak = setPointTieBreak.name)

private fun MatchHistoryEntity.asResult(teams: Map<Long, Team>) = Result(
    teams[teamAId]?.copy(name = teamAName) ?: Team(teamAId, teamAName, 0xFF2563EB.toInt()),
    teams[teamBId]?.copy(name = teamBName) ?: Team(teamBId, teamBName, 0xFFDC2626.toInt()),
    teamAScore, teamBScore, durationSeconds * 1000, finishedAt,
    when (winnerTeamId) { teamAId -> 0; teamBId -> 1; else -> null }, teamAReason, teamBReason,
)

private fun Result.asRow(next: Session) = MatchHistoryEntity(leagueId = next.pelada.id,
    teamAId = left.id, teamAName = left.name, teamAScore = leftScore,
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
    @Query("SELECT * FROM peladas ORDER BY id") abstract suspend fun profiles(): List<PeladaEntity>
    @Query("SELECT * FROM peladas WHERE id = :leagueId") abstract suspend fun profile(leagueId: Long): PeladaEntity?
    @Query("SELECT * FROM teams WHERE leagueId = :leagueId ORDER BY id")
    abstract suspend fun teams(leagueId: Long = 1): List<TeamEntity>
    @Query("SELECT * FROM players ORDER BY id") abstract suspend fun players(): List<PlayerEntity>
    @Query("SELECT * FROM player_peladas WHERE leagueId = :leagueId")
    abstract suspend fun memberships(leagueId: Long): List<PlayerPeladaEntity>
    @Query("SELECT * FROM player_peladas WHERE playerId = :playerId AND assignedTeamId IS NOT NULL")
    abstract suspend fun assignments(playerId: Long): List<PlayerPeladaEntity>
    @Query("SELECT * FROM active_match_state WHERE id = :leagueId")
    abstract suspend fun active(leagueId: Long): ActiveMatchStateEntity?
    @Query("SELECT * FROM rotation_rules WHERE id = :leagueId")
    abstract suspend fun rules(leagueId: Long): RotationRulesEntity?
    @Query("SELECT * FROM match_history WHERE leagueId = :leagueId ORDER BY id DESC")
    abstract suspend fun history(leagueId: Long = 1): List<MatchHistoryEntity>
    @Query("UPDATE match_history SET visibleInHistory = 0 WHERE leagueId = :leagueId AND visibleInHistory = 1")
    abstract suspend fun hideHistory(leagueId: Long)
    @Query("UPDATE match_history SET countsForStandings = 0 WHERE leagueId = :leagueId AND countsForStandings = 1")
    abstract suspend fun uncountStandings(leagueId: Long)
    @Query("DELETE FROM match_history WHERE leagueId = :leagueId AND visibleInHistory = 0 AND countsForStandings = 0")
    abstract suspend fun removeUnusedResults(leagueId: Long)
    @Query("DELETE FROM teams WHERE id = :id") abstract suspend fun deleteTeam(id: Long)
    @Query("DELETE FROM players WHERE id = :id") abstract suspend fun deletePlayer(id: Long)
    @Query("DELETE FROM peladas WHERE id = :leagueId") abstract suspend fun deleteLeagueProfile(leagueId: Long)
    @Query("DELETE FROM teams WHERE leagueId = :leagueId") abstract suspend fun deleteLeagueTeams(leagueId: Long)
    @Query("DELETE FROM rotation_rules WHERE id = :leagueId") abstract suspend fun deleteLeagueRules(leagueId: Long)
    @Query("DELETE FROM active_match_state WHERE id = :leagueId") abstract suspend fun deleteLeagueMatch(leagueId: Long)
    @Query("DELETE FROM match_history WHERE leagueId = :leagueId") abstract suspend fun deleteLeagueHistory(leagueId: Long)
    @Upsert abstract suspend fun saveTeam(team: TeamEntity): Long
    @Upsert abstract suspend fun savePlayer(player: PlayerEntity): Long
    @Upsert abstract suspend fun saveMembership(membership: PlayerPeladaEntity)
    @Upsert abstract suspend fun upsertProfile(profile: PeladaEntity): Long
    @Upsert abstract suspend fun saveActive(active: ActiveMatchStateEntity)
    @Upsert abstract suspend fun saveRules(rules: RotationRulesEntity)
    @Insert abstract suspend fun saveHistory(result: MatchHistoryEntity)

    private suspend fun readSession(leagueId: Long): Session {
        val pelada = checkNotNull(profile(leagueId)).asPelada()
        val rows = teams(leagueId)
        val active = active(leagueId) ?: ActiveMatchStateEntity()
        val memberships = memberships(leagueId).associateBy { it.playerId }
        return Session(
            rows.map { it.asTeam() },
            listOfNotNull(active.teamAId, active.teamBId),
            rows.filter { it.status == "IN_QUEUE" }.sortedBy { it.queuePosition }.map { it.id },
            Match(listOf(active.scoreA, active.scoreB), if (active.servingTeam == "TEAM_B") 1 else 0,
                active.elapsedSeconds * 1000 + active.elapsedRemainderMs, active.startedAt, active.sidesSwapped,
                Json.decodeFromString<List<PointRow>>(active.pointHistoryStackJson).map {
                    PointSnapshot(listOf(it.a, it.b), it.serve)
                }),
            (rules(leagueId) ?: RotationRulesEntity()).asRules(),
            active.round,
            players().map { it.asPlayer(memberships[it.id]) },
            pelada,
        )
    }

    private suspend fun persist(previous: Session, next: Session) {
        val leagueId = previous.pelada.id
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
                team.color.toUInt().toString(16), team.wins, status, position, team.createdAt, leagueId))
            val id = if (team.id < 0) inserted else team.id
            if (team.id < 0) remapped[team.id] = id
        }
        if (previous.players != next.players) {
            val previousPlayers = previous.players.associateBy { it.id }
            val nextPlayerIds = next.players.map { it.id }.toSet()
            for (id in previousPlayers.keys - nextPlayerIds) deletePlayer(id)
            for (player in next.players) {
                val oldPlayer = previousPlayers[player.id]
                if (oldPlayer == player) continue
                if (oldPlayer != null && oldPlayer.sex != player.sex) validateSexChange(player)
                val row = PlayerEntity(player.id.coerceAtLeast(0), player.name, player.skillLevel, player.sex.name)
                val previousRow = oldPlayer?.let { PlayerEntity(it.id, it.name, it.skillLevel, it.sex.name) }
                val inserted = if (row != previousRow) savePlayer(row) else player.id
                val id = if (oldPlayer == null) inserted else player.id
                val teamId = player.assignedTeamId
                if (oldPlayer == null || oldPlayer.isPresent != player.isPresent ||
                    oldPlayer.assignedTeamId != teamId || oldPlayer.position != player.position) {
                    saveMembership(PlayerPeladaEntity(leagueId, id, player.isPresent, remapped[teamId] ?: teamId,
                        player.position.name))
                }
            }
        }
        if (previous.match != next.match || previous.court != next.court || previous.round != next.round) {
            saveMatch(next, remapped)
        }
        if (previous.rules != next.rules) saveRules(next.rules.asRow(leagueId))
    }

    private suspend fun validateSexChange(player: Player) {
        for (assignment in assignments(player.id)) {
            val session = readSession(assignment.leagueId)
            val roster = session.roster(checkNotNull(assignment.assignedTeamId))
                .map { if (it.id == player.id) player else it }
            if (!session.pelada.acceptsRoster(roster)) {
                throw PeladaException("Remova ${player.name} da escalação em ${session.pelada.displayName} antes de alterar o sexo.")
            }
        }
    }

    private suspend fun saveMatch(next: Session, remapped: Map<Long, Long>) {
        val match = next.match
        val court = next.court.map { remapped[it] ?: it }
        saveActive(ActiveMatchStateEntity(id = next.pelada.id, teamAId = court.getOrNull(0), teamBId = court.getOrNull(1),
            scoreA = match.scores[0], scoreB = match.scores[1],
            servingTeam = if (court.size < 2) "NONE" else if (match.serve == 0) "TEAM_A" else "TEAM_B",
            sidesSwapped = match.swapped, elapsedSeconds = match.elapsedMs / 1000,
            elapsedRemainderMs = match.elapsedMs % 1000, startedAt = match.startedAt,
            pointHistoryStackJson = Json.encodeToString(match.points.map { PointRow(it.scores[0], it.scores[1], it.serve) }),
            round = next.round))
    }

    @Transaction
    open suspend fun readLeague(leagueId: Long = 1): League {
        val session = readSession(leagueId)
        val teams = session.teams.associateBy { it.id }
        val rows = history(leagueId)
        val results = rows.associate { it.id to it.asResult(teams) }
        return League(
            session = session,
            history = rows.filter { it.visibleInHistory }.map { results.getValue(it.id) },
            standingsMatches = rows.filter { it.countsForStandings }.map { results.getValue(it.id) },
        )
    }

    @Transaction
    open suspend fun readSelection(leagueId: Long): LeagueSelection {
        val profiles = profiles().map { it.asPelada() }
        val selected = profiles.find { it.id == leagueId } ?: profiles.firstOrNull()
        return LeagueSelection(selected?.let { readLeague(it.id) }, profiles)
    }

    @Transaction
    open suspend fun deletePeladas(ids: Set<Long>) {
        for (id in ids) {
            deleteLeagueProfile(id)
            deleteLeagueMatch(id)
            deleteLeagueRules(id)
            deleteLeagueHistory(id)
            deleteLeagueTeams(id)
        }
    }

    @Transaction
    open suspend fun savePelada(profile: Pelada, initialTarget: Int): Long {
        require(profile.name.isNotBlank() && profile.name.length <= 60 && profile.teamSize in 2..6)
        require(initialTarget in 1..999)
        require(!profile.mixed || (
            profile.minWomen in 1 until profile.teamSize && profile.minMen in 1 until profile.teamSize &&
                profile.minWomen + profile.minMen <= profile.teamSize
            ))
        if (profile.id != 0L) {
            val session = readSession(profile.id)
            if (profile.mixed && session.teams.any { !profile.acceptsRoster(session.roster(it.id)) }) {
                throw PeladaException("Cada equipe com jogadores precisa ter pelo menos ${profile.minWomen} " +
                    "do feminino e ${profile.minMen} do masculino para salvar a composição mista.")
            }
            val players = session.players.map { player ->
                if (player.assignedTeamId != null && !profile.composition.accepts(player.sex)) {
                    player.copy(assignedTeamId = null)
                } else player
            }
            if (players != session.players) persist(session, session.copy(players = players))
        }
        val inserted = upsertProfile(PeladaEntity(profile.id, profile.name, profile.teamSize,
            profile.composition.name, profile.minWomen, profile.minMen))
        if (profile.id != 0L) return profile.id
        saveRules(Rules(target = initialTarget).asRow(inserted))
        return inserted
    }

    @Transaction
    open suspend fun update(leagueId: Long = 1, transform: (Session) -> Session) {
        val current = readSession(leagueId)
        val next = transform(current)
        if (current != next) persist(current, next)
    }

    @Transaction
    open suspend fun drawTeams(
        playerIds: Set<Long>, size: Int, replace: Boolean, minWomen: Int,
        leagueId: Long = 1, minMen: Int = if (minWomen > 0) 1 else 0,
    ) {
        val current = readSession(leagueId)
        persist(current, current.draw(playerIds, size, replace, minWomen, minMen))
        if (replace) {
            uncountStandings(leagueId)
            removeUnusedResults(leagueId)
        }
    }

    @Transaction
    open suspend fun clearHistory(leagueId: Long = 1) {
        hideHistory(leagueId)
        removeUnusedResults(leagueId)
    }

    @Transaction
    open suspend fun resetStandings(leagueId: Long = 1) {
        uncountStandings(leagueId)
        removeUnusedResults(leagueId)
    }

    @Transaction
    open suspend fun finishMatchAndRotateTeams(expected: Session, now: Long) {
        val current = readSession(expected.pelada.id)
        if (current != expected) return
        val rotation = ProcessMatchEndUseCase()(current, now) ?: return
        saveHistory(rotation.result.asRow(rotation.next))
        persist(current, rotation.next)
    }
}

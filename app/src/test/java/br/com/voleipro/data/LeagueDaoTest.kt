package br.com.voleipro.data

import androidx.room.Room
import br.com.voleipro.di.RepositoryModule
import br.com.voleipro.domain.Composition
import br.com.voleipro.domain.Pelada
import br.com.voleipro.domain.PeladaException
import br.com.voleipro.domain.PlayerPosition
import br.com.voleipro.domain.PlayerSex
import br.com.voleipro.domain.ReturnRule
import br.com.voleipro.domain.ScorePointUseCase
import br.com.voleipro.domain.SetPointTieBreak
import br.com.voleipro.domain.addPlayers
import br.com.voleipro.domain.drawCandidates
import br.com.voleipro.domain.editPlayer
import br.com.voleipro.domain.eligiblePlayers
import br.com.voleipro.domain.removeTeam
import br.com.voleipro.domain.saveTeam
import br.com.voleipro.domain.standings
import br.com.voleipro.domain.togglePresence
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LeagueDaoTest {
    private lateinit var db: LeagueDatabase

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), LeagueDatabase::class.java)
            .build()
    }

    @After fun close() { db.close() }

    private suspend fun createLeague(): LeagueDao = db.dao().also { dao ->
        dao.savePelada(Pelada(id = 0, name = "Liga de teste"), 15)
    }

    private suspend fun createLeagueWithTeams(): LeagueDao = createLeague().also { dao ->
        dao.update { it.addPlayers(listOf("Lucas", "Mariana", "Pedro", "Bia", "Rafael"), 3, true) }
        dao.update { session ->
            val captainId = session.players.first().id
            session.saveTeam(null, "Time Azul", setOf(captainId))
                .saveTeam(null, "Time Vermelho", emptySet())
                .saveTeam(null, "Time Amarelo", emptySet())
                .saveTeam(null, "Time Verde", emptySet())
        }
    }

    @Test fun newInstallationHasNoData() = runBlocking {
        val freshDatabase = RepositoryModule.database(RuntimeEnvironment.getApplication())
        try {
            val selection = freshDatabase.dao().readSelection(1)
            assertTrue(selection.profiles.isEmpty())
            assertNull(selection.active)
            val tables = listOf("peladas", "teams", "players", "player_peladas", "rotation_rules",
                "active_match_state", "match_history")
            for (table in tables) {
                freshDatabase.openHelper.readableDatabase.query("SELECT COUNT(*) FROM " + table).use { rows ->
                    assertTrue(rows.moveToFirst())
                    assertEquals(table, 0, rows.getInt(0))
                }
            }
        } finally {
            freshDatabase.close()
        }
    }

    @Test fun persistsFirstRosterWithGeneratedIds() = runBlocking {
        val dao = createLeague()
        dao.update { it.addPlayers(listOf("Ana"), 5, true) }
        val player = dao.readLeague().session.players.single()
        assertTrue(player.id > 0)
        assertEquals(PlayerSex.UNSPECIFIED, player.sex)
        assertEquals(PlayerPosition.UNSPECIFIED, player.position)
        dao.update { it.saveTeam(null, "Time Roxo", setOf(player.id)) }
        val session = dao.readLeague().session
        val id = session.court.single()
        assertTrue(id > 0)
        assertEquals(listOf(player.copy(assignedTeamId = id)), session.roster(id))
        dao.update { it.removeTeam(id) }
        assertEquals(player, dao.readLeague().session.players.single())
    }

    @Test fun batchAndEditingKeepRecordsAndPreventDuplicates() = runBlocking {
        val dao = createLeague()
        dao.update { it.addPlayers(listOf("Lucas"), 3, false) }
        dao.update {
            it.addPlayers(listOf("  Ana  Maria  ", "ana maria", "  lucas ", "João"), 4, false,
                PlayerSex.OTHER, PlayerPosition.SETTER)
        }
        val initial = dao.readLeague().session.players
        assertEquals(3, initial.size)
        val ana = initial.single { it.name == "Ana Maria" }
        assertEquals(4, ana.skillLevel)
        assertFalse(ana.isPresent)
        assertEquals(PlayerSex.OTHER, ana.sex)
        assertEquals(PlayerPosition.SETTER, ana.position)
        assertEquals(PlayerPosition.SETTER, initial.single { it.name == "João" }.position)
        dao.update { it.editPlayer(ana.id, "Ana M.", 5, true, PlayerSex.FEMALE, PlayerPosition.LIBERO) }
        val updated = dao.readLeague().session.players.single { it.id == ana.id }
        assertEquals(ana.copy(name = "Ana M.", skillLevel = 5, isPresent = true,
            sex = PlayerSex.FEMALE, position = PlayerPosition.LIBERO), updated)
        dao.update { it.editPlayer(updated.id, updated.name, 5, true, updated.sex, PlayerPosition.UNSPECIFIED) }
        assertEquals(PlayerPosition.UNSPECIFIED, dao.readLeague().session.players.single { it.id == ana.id }.position)
    }

    @Test fun replacingTeamsRetainsPlayerIdsAndOnlyAssignsSelectedPresentPlayers() = runBlocking {
        val dao = createLeagueWithTeams()
        val initial = dao.readLeague().session
        val absent = initial.players.first()
        dao.update { it.editPlayer(absent.id, absent.name, 5, false, absent.sex, absent.position) }
        val selected = initial.players.take(5).map { it.id }.toSet()
        dao.update { ScorePointUseCase()(it, 0, 1) }
        dao.finishMatchAndRotateTeams(dao.readLeague().session, 1000)
        assertEquals(1, dao.readLeague().standingsMatches.size)
        dao.drawTeams(selected, 2, replace = true, minWomen = 0)
        val drawn = dao.readLeague().session
        assertEquals(listOf("Time 1", "Time 2"), drawn.teams.map { it.name })
        assertEquals(listOf(2, 2), drawn.court.map { drawn.roster(it).size })
        assertTrue(drawn.queue.isEmpty())
        assertEquals(initial.players.map { it.id }, drawn.players.map { it.id })
        assertEquals(selected - absent.id, drawn.players.filter { it.assignedTeamId != null }.map { it.id }.toSet())
        assertEquals(absent.copy(skillLevel = 5, isPresent = false, assignedTeamId = null), drawn.players.first())
        val league = dao.readLeague()
        assertEquals("Time Azul", league.history.single().left.name)
        assertTrue(league.standingsMatches.isEmpty())
        assertEquals(drawn.teams.map { it.id }.toSet(), league.standings().map { it.team.id }.toSet())
        assertTrue(league.standings().all { it.games == 0 })
    }

    @Test fun clearHistoryKeepsStandingsAndFutureMatchesReappear() = runBlocking {
        val dao = createLeagueWithTeams()
        dao.update { ScorePointUseCase()(it, 0, 1) }
        dao.finishMatchAndRotateTeams(dao.readLeague().session, 1000)
        dao.clearHistory()
        val cleared = dao.readLeague()
        assertTrue(cleared.history.isEmpty())
        assertEquals(1, cleared.standings().single { it.team.id == 1L }.wins)

        dao.finishMatchAndRotateTeams(cleared.session, 2000)
        val afterNextMatch = dao.readLeague()
        assertEquals(2000L, afterNextMatch.history.single().finishedAt)
        assertEquals(2, afterNextMatch.standingsMatches.size)
    }

    @Test fun resetStandingsKeepsHistoryAndFutureMatchesCount() = runBlocking {
        val dao = createLeagueWithTeams()
        dao.update { ScorePointUseCase()(it, 0, 1) }
        dao.finishMatchAndRotateTeams(dao.readLeague().session, 1000)
        dao.resetStandings()
        val reset = dao.readLeague()
        assertEquals(1000L, reset.history.single().finishedAt)
        assertTrue(reset.standings().all { it.games == 0 })
        dao.finishMatchAndRotateTeams(reset.session, 2000)
        val afterNextMatch = dao.readLeague()
        assertEquals(2, afterNextMatch.history.size)
        assertEquals(2000L, afterNextMatch.standingsMatches.single().finishedAt)
        dao.clearHistory()
        dao.resetStandings()
        assertTrue(dao.history().isEmpty())
    }

    @Test fun finishingRecordsOnceAndResetsActiveMatchDuringRotation() = runBlocking {
        val dao = createLeagueWithTeams()
        dao.update { ScorePointUseCase()(it, 0, 1) }
        val playing = dao.readLeague().session
        assertEquals(1, playing.match.points.size)
        dao.finishMatchAndRotateTeams(playing, 1000)
        dao.finishMatchAndRotateTeams(playing, 1000)
        val league = dao.readLeague()
        assertEquals(1, league.history.size)
        assertEquals(1, league.history.single().leftScore)
        assertEquals(listOf(1L, 3L), league.session.court)
        assertEquals(listOf(4L, 2L), league.session.queue)
        assertEquals(listOf(0, 0), league.session.match.scores)
        assertTrue(league.session.match.points.isEmpty())
    }

    @Test fun secondWinPersistsChampionAtFirstQueuePosition() = runBlocking {
        val dao = createLeagueWithTeams()
        dao.update { it.saveTeam(null, "Time Roxo", emptySet()) }
        val extraTeam = dao.readLeague().session.queue.last()

        repeat(2) { match ->
            dao.update { it.copy(match = it.match.copy(scores = listOf(15, 12))) }
            dao.finishMatchAndRotateTeams(dao.readLeague().session, (match + 1) * 1000L)
        }

        val league = dao.readLeague()
        assertEquals(ReturnRule.FIRST_IN_QUEUE, league.session.rules.returnRule)
        assertEquals(listOf(4L, extraTeam), league.session.court)
        assertEquals(listOf(1L, 2L, 3L), league.session.queue)
        assertEquals(0, league.session.team(1L)?.wins)
        assertEquals(2, league.history.size)
        val recorded = dao.history().first()
        assertTrue(recorded.championExitedByStreakLimit)
        assertTrue(recorded.exitReasonSummary.contains("Time Azul · Limite de vitórias · 1º na fila"))
    }

    @Test fun samePlayerCanHaveDifferentPositionsInEachPelada() = runBlocking {
        val dao = createLeague()
        val courtId = dao.savePelada(Pelada(id = 0, name = "Quadra", teamSize = 6), 25)
        dao.update(courtId) {
            it.addPlayers(listOf("Sofia"), 3, false, position = PlayerPosition.LIBERO)
        }
        val sofia = dao.readLeague(courtId).session.players.single { it.name == "Sofia" }
        assertEquals(PlayerPosition.UNSPECIFIED, dao.readLeague().session.players.single { it.id == sofia.id }.position)

        dao.update {
            it.editPlayer(sofia.id, sofia.name, sofia.skillLevel, false, sofia.sex, PlayerPosition.PRIMARY_ATTACKER)
        }

        assertEquals(PlayerPosition.LIBERO, dao.readLeague(courtId).session.players.single { it.id == sofia.id }.position)
        assertEquals(PlayerPosition.PRIMARY_ATTACKER,
            dao.readLeague().session.players.single { it.id == sofia.id }.position)
    }

    @Test fun peladasShareCatalogButIsolatePresenceTeamsRulesAndHistory() = runBlocking {
        val dao = createLeagueWithTeams()
        val original = dao.readLeague().session
        val beachId = dao.savePelada(Pelada(id = 0, name = "Praia", teamSize = 4,
            composition = Composition.MIXED, minWomen = 2, minMen = 2), 21)
        val courtId = dao.savePelada(Pelada(id = 0, name = "Quadra", teamSize = 6), 25)
        assertEquals(listOf(1L, beachId, courtId), dao.readSelection(beachId).profiles.map(Pelada::id))
        val beach = dao.readLeague(beachId).session
        assertTrue(beach.teams.isEmpty())
        assertEquals(21, beach.rules.target)
        assertTrue(beach.players.none { it.isPresent || it.assignedTeamId != null })
        assertEquals(original.players.map { it.id }, beach.players.map { it.id })

        dao.update(beachId) {
            it.addPlayers(listOf("Ana", "Lia", "Eva", "Rita"), 5, true, PlayerSex.FEMALE)
                .addPlayers(listOf("Rui", "Ivo", "Dan", "Caio"), 3, true, PlayerSex.MALE)
        }
        val candidateIds = dao.readLeague(beachId).session.drawCandidates(replace = false).map { it.id }.toSet()
        dao.drawTeams(candidateIds, 4, false, 0, beachId)
        val drawn = dao.readLeague(beachId).session
        assertEquals(2, drawn.court.size)
        assertTrue(drawn.teams.all { drawn.pelada.acceptsRoster(drawn.roster(it.id)) })
        dao.update(beachId) { ScorePointUseCase()(it, 0, 1) }
        dao.finishMatchAndRotateTeams(dao.readLeague(beachId).session, 1000)
        assertEquals(1, dao.readLeague(beachId).history.size)

        dao.update { ScorePointUseCase()(it, 1, 1) }
        val firstPelada = dao.readLeague().session
        assertEquals(original.court, firstPelada.court)
        assertEquals(original.queue, firstPelada.queue)
        assertEquals(listOf(0, 1), firstPelada.match.scores)
        assertTrue(firstPelada.players.filter { it.id in candidateIds }.none { it.isPresent })
        assertTrue(dao.readLeague().history.isEmpty())
        val court = dao.readLeague(courtId).session
        assertEquals(25, court.rules.target)
        assertTrue(court.teams.isEmpty())
        assertTrue(court.players.none { it.isPresent })
        dao.clearHistory(courtId)
        dao.resetStandings(courtId)
        assertEquals(1, dao.readLeague(beachId).history.size)
        assertEquals(1, dao.readLeague(beachId).standingsMatches.size)
    }

    @Test fun singleSexPeladasRejectIncompatibleRostersAndFilterDrawCandidates() = runBlocking {
        val dao = createLeague()
        val id = dao.savePelada(Pelada(id = 0, name = "Feminina", composition = Composition.FEMALE), 15)
        dao.update(id) { it.addPlayers(listOf("Ana"), 3, true, PlayerSex.FEMALE)
            .addPlayers(listOf("Rui"), 3, true, PlayerSex.MALE).addPlayers(listOf("Alex"), 3, true) }
        val session = dao.readLeague(id).session
        assertEquals(listOf("Ana"), session.eligiblePlayers.map { it.name })
        assertEquals(listOf("Ana"), session.copy(players = session.players.map { it.copy(isPresent = false) })
            .eligiblePlayers.map { it.name })
        assertEquals(listOf("Ana"), session.drawCandidates(replace = false).map { it.name })
        val rui = session.players.single { it.name == "Rui" }
        val menId = dao.savePelada(Pelada(id = 0, name = "Masculina", composition = Composition.MALE), 15)
        dao.update(menId) { current -> current.copy(players = current.players.map { it.copy(isPresent = true) }) }
        assertEquals(listOf("Rui"), dao.readLeague(menId).session.eligiblePlayers.map { it.name })
        assertEquals(listOf("Rui"), dao.readLeague(menId).session.drawCandidates(replace = false).map { it.name })
        assertTrue(runCatching { dao.update(id) { it.saveTeam(null, "Time", setOf(rui.id)) } }
            .exceptionOrNull() is PeladaException)
        assertTrue(dao.readLeague(id).session.teams.isEmpty())
        val ana = session.players.single { it.name == "Ana" }
        dao.update(id) { it.saveTeam(null, "Time", setOf(ana.id)) }
        assertTrue(runCatching { dao.update { it.editPlayer(ana.id, ana.name, 3, false, PlayerSex.MALE, ana.position) } }
            .exceptionOrNull() is PeladaException)
    }

    @Test fun changingCompositionUnassignsOnlyIncompatiblePlayersFromTheEditedLeague() = runBlocking {
        val dao = db.dao()
        val firstId = dao.savePelada(Pelada(id = 0, name = "Praia"), 15)
        val secondId = dao.savePelada(Pelada(id = 0, name = "Quadra"), 15)
        dao.update(firstId) {
            it.addPlayers(listOf("Ana"), 3, true, PlayerSex.FEMALE)
                .addPlayers(listOf("Rui"), 3, true, PlayerSex.MALE, PlayerPosition.LIBERO)
                .addPlayers(listOf("Alex"), 3, true)
        }
        val added = dao.readLeague(firstId).session.players.filter { it.name in setOf("Ana", "Rui", "Alex") }
        val playerIds = added.map { it.id }.toSet()
        for (id in listOf(firstId, secondId)) {
            dao.update(id) {
                it.saveTeam(null, "Time 1", playerIds).saveTeam(null, "Time 2", emptySet())
            }
            dao.update(id) { ScorePointUseCase()(it, 0, 1) }
        }
        val firstBefore = dao.readLeague(firstId).session
        val secondBefore = dao.readLeague(secondId).session

        dao.savePelada(firstBefore.pelada.copy(composition = Composition.FEMALE), 15)
        val first = dao.readLeague(firstId).session
        assertEquals(Composition.FEMALE, first.pelada.composition)
        assertEquals(listOf("Ana"), first.roster(first.court.first()).map { it.name })
        assertEquals(firstBefore.match, first.match)
        val rui = first.players.single { it.name == "Rui" }
        assertEquals(added.single { it.id == rui.id }.copy(assignedTeamId = null), rui)
        assertEquals(secondBefore, dao.readLeague(secondId).session)

        dao.savePelada(secondBefore.pelada.copy(composition = Composition.MALE), 15)
        val second = dao.readLeague(secondId).session
        assertEquals(listOf("Rui"), second.roster(second.court.first()).map { it.name })
        assertEquals(first, dao.readLeague(firstId).session)

        assertTrue(runCatching { dao.savePelada(first.pelada.copy(composition = Composition.MIXED), 15) }
            .exceptionOrNull() is PeladaException)
        assertEquals(first, dao.readLeague(firstId).session)
    }

    @Test fun presenceToggleOnlyUpdatesTheSelectedGroupInTheEditedLeague() = runBlocking {
        val dao = createLeague()
        val id = dao.savePelada(Pelada(id = 0, name = "Feminina", composition = Composition.FEMALE), 15)
        dao.update(id) {
            it.addPlayers(listOf("Aline"), 3, false, PlayerSex.FEMALE, PlayerPosition.SETTER)
                .addPlayers(listOf("Bianca"), 3, true, PlayerSex.FEMALE, PlayerPosition.SETTER)
                .addPlayers(listOf("Elisa"), 3, false, PlayerSex.FEMALE, PlayerPosition.LIBERO)
                .addPlayers(listOf("Rui"), 3, true, PlayerSex.MALE, PlayerPosition.SETTER)
        }
        val otherLeague = dao.readLeague().session
        val initial = dao.readLeague(id).session

        suspend fun eligiblePresentNames() = dao.readLeague(id).session.eligiblePlayers
            .filter { it.isPresent }.map { it.name }

        dao.update(id) { it.togglePresence(PlayerPosition.SETTER) }
        assertEquals(listOf("Aline", "Bianca"), eligiblePresentNames())
        dao.update(id) { it.togglePresence(PlayerPosition.SETTER) }
        assertTrue(eligiblePresentNames().isEmpty())

        dao.update(id) { it.togglePresence() }
        assertEquals(listOf("Aline", "Bianca", "Elisa"), eligiblePresentNames())
        dao.update(id) { it.togglePresence() }
        assertTrue(eligiblePresentNames().isEmpty())
        val final = dao.readLeague(id).session
        assertEquals(initial.players.single { it.name == "Rui" }, final.players.single { it.name == "Rui" })
        assertEquals(otherLeague, dao.readLeague().session)
    }

    @Test fun deletingLeaguesClearsTheirDataAndKeepsTheSharedCatalog() = runBlocking {
        val dao = createLeagueWithTeams()
        val secondId = dao.savePelada(Pelada(id = 0, name = "Quadra"), 21)
        val thirdId = dao.savePelada(Pelada(id = 0, name = "Praia"), 15)
        val originalPlayers = dao.players()
        val playerId = originalPlayers.first().id
        dao.update(secondId) { it.saveTeam(null, "Time 1", setOf(playerId)).saveTeam(null, "Time 2", emptySet()) }
        for (id in listOf(1L, secondId)) {
            dao.update(id) { ScorePointUseCase()(it, 0, 1) }
            dao.finishMatchAndRotateTeams(dao.readLeague(id).session, 1000)
        }
        val second = dao.readLeague(secondId)
        assertTrue(dao.history(1).isNotEmpty())
        assertTrue(dao.history(secondId).isNotEmpty())

        dao.deletePeladas(setOf(1L))
        assertNull(dao.profile(1))
        assertEquals(second, dao.readSelection(1).active)
        assertEquals(listOf(secondId, thirdId), dao.readSelection(1).profiles.map { it.id })

        dao.deletePeladas(setOf(secondId, thirdId))
        assertNull(dao.readSelection(secondId).active)
        assertTrue(dao.readSelection(secondId).profiles.isEmpty())
        assertEquals(originalPlayers, dao.players())
        for (id in listOf(1L, secondId, thirdId)) {
            assertTrue(dao.teams(id).isEmpty())
            assertTrue(dao.history(id).isEmpty())
            assertTrue(dao.memberships(id).isEmpty())
            assertNull(dao.active(id))
            assertNull(dao.rules(id))
        }

        val newId = dao.savePelada(Pelada(id = 0, name = "Nova liga"), 25)
        val newSession = checkNotNull(dao.readSelection(secondId).active).session
        assertEquals(newId, newSession.pelada.id)
        assertEquals(25, newSession.rules.target)
        assertTrue(newSession.teams.isEmpty())
        assertEquals(originalPlayers.map { it.id }, newSession.players.map { it.id })
    }

    @Test fun persistsSelectedSetPointTieBreak() = runBlocking {
        val dao = createLeague()
        assertEquals(SetPointTieBreak.STANDARD, dao.readLeague().session.rules.setPointTieBreak)
        dao.update { it.copy(rules = it.rules.copy(setPointTieBreak = SetPointTieBreak.THREE_DIRECT)) }
        assertEquals(SetPointTieBreak.THREE_DIRECT, dao.readLeague().session.rules.setPointTieBreak)
    }

    @Test fun anIdenticalScorelessRematchCannotBeConfirmedTwice() = runBlocking {
        val dao = createLeagueWithTeams()
        dao.update { it.copy(teams = it.teams.take(2), queue = emptyList()) }
        val first = dao.readLeague().session
        dao.finishMatchAndRotateTeams(first, 1000)
        dao.finishMatchAndRotateTeams(first, 1000)
        val league = dao.readLeague()
        assertEquals(1, league.history.size)
        assertNull(league.history.single().winner)
        assertEquals(first.round + 1, league.session.round)
    }

    @Test fun failedRotationRollsBackHistoryAndCourt() = runBlocking {
        val dao = createLeagueWithTeams()
        dao.update { ScorePointUseCase()(it, 0, 1) }
        val first = dao.readLeague()
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_rotation BEFORE UPDATE ON active_match_state " +
                "BEGIN SELECT RAISE(ABORT, 'test rollback'); END"
        )
        assertTrue(runCatching { dao.finishMatchAndRotateTeams(first.session, 1000) }.isFailure)
        assertEquals(first, dao.readLeague())
    }
}

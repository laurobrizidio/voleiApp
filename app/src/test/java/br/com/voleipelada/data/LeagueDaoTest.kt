package br.com.voleipelada.data

import androidx.room.Room
import br.com.voleipelada.domain.PlayerSex
import br.com.voleipelada.domain.ScorePointUseCase
import br.com.voleipelada.domain.addPlayers
import br.com.voleipelada.domain.editPlayer
import br.com.voleipelada.domain.removeTeam
import br.com.voleipelada.domain.saveTeam
import br.com.voleipelada.domain.standings
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
            .addCallback(LeagueDatabase.seed).build()
    }

    @After fun close() { db.close() }

    @Test fun seedAndPersistRosterWithGeneratedId() = runBlocking {
        val dao = db.dao()
        val seed = dao.readLeague().session
        assertEquals(listOf("Time Azul", "Time Vermelho"), seed.court.map { seed.team(it)?.name })
        assertEquals(listOf(3L, 4L), seed.queue)
        assertEquals(4, seed.roster(1L).size)
        assertTrue(seed.players.all { it.sex == PlayerSex.UNSPECIFIED })
        dao.update { it.addPlayers(listOf("Ana"), 5, true) }
        val player = dao.readLeague().session.players.single { it.name == "Ana" }
        dao.update { it.saveTeam(null, "Time Roxo", setOf(player.id)) }
        val session = dao.readLeague().session
        val id = session.queue.last()
        assertTrue(id > 4)
        assertEquals(listOf(player.copy(assignedTeamId = id)), session.roster(id))
        dao.update { it.removeTeam(id) }
        assertEquals(player, dao.readLeague().session.players.single { it.id == player.id })
    }

    @Test fun batchAndEditingKeepRecordsAndPreventDuplicates() = runBlocking {
        val dao = db.dao()
        dao.update { it.addPlayers(listOf("  Ana  Maria  ", "ana maria", "  lucas ", "João"), 4, false, PlayerSex.OTHER) }
        val initial = dao.readLeague().session.players
        assertEquals(18, initial.size)
        val ana = initial.single { it.name == "Ana Maria" }
        assertEquals(4, ana.skillLevel)
        assertFalse(ana.isPresent)
        assertEquals(PlayerSex.OTHER, ana.sex)
        dao.update { it.editPlayer(ana.id, "Ana M.", 5, true, PlayerSex.FEMALE) }
        val updated = dao.readLeague().session.players.single { it.id == ana.id }
        assertEquals(ana.copy(name = "Ana M.", skillLevel = 5, isPresent = true, sex = PlayerSex.FEMALE), updated)
    }

    @Test fun replacingTeamsRetainsPlayerIdsAndOnlyAssignsSelectedPresentPlayers() = runBlocking {
        val dao = db.dao()
        val initial = dao.readLeague().session
        val absent = initial.players.first()
        dao.update { it.editPlayer(absent.id, absent.name, 5, false, absent.sex) }
        val selected = initial.players.take(5).map { it.id }.toSet()
        dao.update { ScorePointUseCase()(it, 0, 1) }
        dao.finishMatchAndRotateTeams(dao.readLeague().session, 1000)
        assertEquals(1, dao.readLeague().standingsMatches.size)
        dao.drawTeams(selected, 2, replace = true, minWomen = 0)
        val drawn = dao.readLeague().session
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
        val dao = db.dao()
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
        val dao = db.dao()
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
        val dao = db.dao()
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

    @Test fun anIdenticalScorelessRematchCannotBeConfirmedTwice() = runBlocking {
        val dao = db.dao()
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
        val dao = db.dao()
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

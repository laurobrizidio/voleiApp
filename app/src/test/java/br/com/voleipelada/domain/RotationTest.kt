package br.com.voleipelada.domain

import br.com.voleipelada.domain.usecase.rotation.*
import org.junit.Assert.*
import org.junit.Test

class RotationTest {
    private val rotate = ProcessMatchEndUseCase()

    private fun game(waiting: Int, leftWins: Int = 1, rightWins: Int = 0,
                     score: List<Int> = listOf(15, 12), rules: Rules = Rules()): Session {
        val ids = (1L..(2L + waiting)).toList()
        return Session(ids.map { Team(it, "Time $it", 0,
            when (it) { 1L -> leftWins; 2L -> rightWins; else -> 0 }) },
            ids.take(2), ids.drop(2), Match(scores = score, elapsedMs = 1_000, startedAt = 10_000), rules)
    }

    @Test fun ordinaryWinPreservesWinnerSideAndFifoForEitherWinner() {
        for (winner in 0..1) {
            val session = game(2, leftWins = 0, rightWins = 0,
                score = if (winner == 0) listOf(15, 12) else listOf(12, 15))
            val (next, result) = requireNotNull(rotate(session, 12_000))
            assertEquals(winner, result.winner)
            assertEquals(3000L, result.durationMs)
            assertEquals(session.court[winner], next.court[winner])
            assertEquals(3L, next.court[1 - winner])
            assertEquals(listOf(4L, session.court[1 - winner]), next.queue)
            assertEquals(1, next.team(session.court[winner])?.wins)
            assertEquals(0, next.team(session.court[1 - winner])?.wins)
            assertEquals(Match(), next.match)
        }
    }

    @Test fun limitedWinnerReturnsInConfiguredOrderAfterTwoPromotions() {
        val cases = listOf(
            Triple(ReturnRule.AHEAD_OF_LOSER, 1, listOf(5L, 6L, 1L, 2L)),
            Triple(ReturnRule.END_OF_QUEUE, 1, listOf(5L, 6L, 2L, 1L)),
            Triple(ReturnRule.FIXED_POSITION, 1, listOf(1L, 5L, 6L, 2L)),
            Triple(ReturnRule.FIXED_POSITION, 2, listOf(5L, 1L, 6L, 2L)),
            Triple(ReturnRule.FIXED_POSITION, 99, listOf(5L, 6L, 1L, 2L)),
        )
        for ((rule, position, queue) in cases) {
            val (next, result) = requireNotNull(rotate(game(4, rules = Rules(returnRule = rule, returnPosition = position)), 0))
            assertEquals(listOf(3L, 4L), next.court)
            assertEquals(queue, next.queue)
            assertEquals(0, next.team(1L)?.wins)
            assertEquals("Limite de vitórias", result.leftReason)
            assertEquals("Derrota", result.rightReason)
        }
    }

    @Test fun threeTeamsUseReturnPriorityAndTwoTeamsRematch() {
        for (policy in ReturnRule.entries) {
            for (position in 1..2) {
                val rules = Rules(returnRule = policy, returnPosition = position)
                val prefersWinner = policy == ReturnRule.AHEAD_OF_LOSER ||
                    policy == ReturnRule.FIXED_POSITION && position == 1
                val three = requireNotNull(rotate(game(1, rules = rules), 0)).next
                assertEquals(if (prefersWinner) listOf(1L, 3L) else listOf(3L, 2L), three.court)
                assertEquals(listOf(if (prefersWinner) 2L else 1L), three.queue)
                val two = requireNotNull(rotate(game(0, rules = rules), 0)).next
                assertEquals(listOf(1L, 2L), two.court)
                assertTrue(two.queue.isEmpty())
                assertEquals(0, two.team(1L)?.wins)
            }
        }
    }

    @Test fun unlimitedStreakAndTotalRotation() {
        val unlimited = requireNotNull(rotate(game(0, leftWins = 9, rules = Rules(winLimit = 0)), 0)).next
        assertEquals(10, unlimited.team(1L)?.wins)
        val (all, result) = requireNotNull(rotate(game(2, rules = Rules(bothLeave = true)), 0))
        assertEquals(listOf(3L, 4L), all.court)
        assertEquals(listOf(2L, 1L), all.queue)
        assertEquals(0, all.team(1L)?.wins)
        assertEquals("Rodízio total", result.leftReason)
    }

    @Test fun tiePolicyUsesStreakBeforeReset() {
        val expected = mapOf(TieRule.BOTH_LEAVE to listOf(3L, 4L),
            TieRule.CHALLENGER_STAYS to listOf(3L, 2L), TieRule.CHAMPION_STAYS to listOf(1L, 3L))
        for ((policy, court) in expected) {
            val (next, result) = requireNotNull(rotate(game(2, score = listOf(13, 13), rules = Rules(tieRule = policy)), 0))
            assertNull(result.winner)
            assertEquals(court, next.court)
            assertEquals(0, next.team(1L)?.wins)
            assertEquals(0, next.team(2L)?.wins)
        }
    }

    @Test fun incompleteCourtAndScoringThresholds() {
        assertNull(rotate(Session(), 0))
        val session = game(0, score = listOf(14, 14))
        assertFalse(session.isSetPoint(0))
        assertNull(session.setWinner())
        assertTrue(session.copy(match = Match(listOf(15, 14))).isSetPoint(0))
        assertEquals(0, session.copy(match = Match(listOf(16, 14))).setWinner())
        assertEquals(1, session.copy(match = Match(listOf(16, 17)), rules = Rules(cap = 17)).setWinner())
        assertEquals(0, session.copy(match = Match(listOf(15, 14)), rules = Rules(winByTwo = false)).setWinner())
    }

    @Test fun pointHistoryKeepsPreviousScoreAndServe() {
        val session = game(0, score = listOf(0, 0))
        val score = ScorePointUseCase()
        assertEquals(session, score(session, 0, -1))
        val first = score(session, 1, 1)
        val second = score(first, 1, -1)
        assertEquals(listOf(0, 0), second.match.scores)
        assertEquals(1, second.match.serve)
        assertEquals(listOf(PointSnapshot(listOf(0, 0), 0), PointSnapshot(listOf(0, 1), 1)), second.match.points)
    }

    @Test fun rosterDrawKeepsEveryEntryAndRespectsCapacity() {
        val players = (1..15).map { Player(it.toLong(), "Jogador $it", (it % 5) + 1) }
        for (size in 2..6) {
            val teams = GenerateBalancedTeamsUseCase()(players, size)
            assertTrue(teams.all { it.size <= size })
            assertEquals(players.map { it.name }.sorted(), teams.flatten().map { it.name }.sorted())
        }
    }
    @Test fun previousNumericExperienceMapsToTheThreeCategories() {
        val categories = (1..5).map { level -> Player(level.toLong(), "Jogador $level", level).experience }
        assertEquals(listOf(Experience.BEGINNER, Experience.BEGINNER, Experience.INTERMEDIATE,
            Experience.EXPERIENCED, Experience.EXPERIENCED), categories)
    }
}

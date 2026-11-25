package br.com.voleipro.domain

import org.junit.Assert.*
import org.junit.Test

class MixedDrawTest {
    private fun players(women: Int, men: Int, other: Int = 0): List<Player> =
        (List(women) { PlayerSex.FEMALE } + List(men) { PlayerSex.MALE } +
            List(other) { if (it % 2 == 0) PlayerSex.OTHER else PlayerSex.UNSPECIFIED })
            .mapIndexed { index, sex -> Player(index.toLong(), "Jogador $index", index % 5 + 1, sex = sex) }

    @Test fun allTeamsMeetEitherMinimumAndEachPlayerAppearsOnce() {
        val cases = listOf(
            Triple(players(3, 4, 2), 4, 1),
            Triple(players(7, 3, 1), 4, 2),
            Triple(players(2, 2), 2, 1),
        )
        for ((input, size, minimum) in cases) repeat(15) {
            val teams = GenerateBalancedTeamsUseCase()(input, size, minimum)
            assertEquals(input.map { it.id }.sorted(), teams.flatten().map { it.id }.sorted())
            assertTrue(teams.all { group ->
                group.size <= size && group.count { it.sex == PlayerSex.FEMALE } >= minimum &&
                    group.any { it.sex == PlayerSex.MALE }
            })
            assertTrue(teams.maxOf { it.size } - teams.minOf { it.size } <= 1)
        }
    }

    @Test fun reportsTheConstraintThatBlocksAValidDraw() {
        assertEquals(DrawProblem(DrawConstraint.TEAM_SIZE, 3), drawProblem(players(2, 2), 2, 2))
        assertEquals(DrawProblem(DrawConstraint.WOMEN, 4), drawProblem(players(3, 3), 3, 2))
        assertEquals(DrawProblem(DrawConstraint.MEN, 2), drawProblem(players(4, 1, 1), 3, 2))
        assertNull(drawProblem(players(0, 0, 6), 3, 0))
    }
}

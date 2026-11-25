package br.com.voleipro.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionalDrawTest {
    private val positions = listOf(
        PlayerPosition.SETTER, PlayerPosition.PRIMARY_ATTACKER, PlayerPosition.SECONDARY_ATTACKER,
        PlayerPosition.LIBERO,
    )

    @Test fun fourPlayerTeamsGetExactlyOnePlayerInEachPosition() {
        val players = roster(List(4) { List(3) { PlayerSex.UNSPECIFIED } })

        repeat(20) {
            assertCompleteRosters(players, GenerateBalancedTeamsUseCase()(players, 4))
        }
    }

    @Test fun mixedFourPlayerTeamsMeetBothQuotasAndGetEveryPosition() {
        val woman = PlayerSex.FEMALE
        val man = PlayerSex.MALE
        val players = roster(listOf(
            listOf(woman, woman, man),
            listOf(woman, man, man),
            listOf(woman, man, man),
            listOf(woman, woman, man),
        ))

        repeat(20) {
            val teams = GenerateBalancedTeamsUseCase()(players, 4, minWomen = 2, minMen = 2)

            assertCompleteRosters(players, teams)
            teams.forEach { team ->
                assertEquals(2, team.count { it.sex == woman })
                assertEquals(2, team.count { it.sex == man })
            }
        }
    }

    @Test fun missingPositionKeepsEveryoneAndSpreadsAvailablePositions() {
        val players = roster(List(4) { List(2) { PlayerSex.UNSPECIFIED } }).map { player ->
            if (player.position == PlayerPosition.LIBERO) player.copy(position = PlayerPosition.UNSPECIFIED)
            else player
        }

        repeat(20) {
            val teams = GenerateBalancedTeamsUseCase()(players, 4)

            assertEquals(players.map { it.id }.sorted(), teams.flatten().map { it.id }.sorted())
            assertTrue(teams.all { team ->
                team.size == 4 && positions.dropLast(1).all { position -> team.count { it.position == position } == 1 }
            })
        }
    }

    private fun roster(sexesByPosition: List<List<PlayerSex>>): List<Player> =
        positions.zip(sexesByPosition).flatMap { (position, sexes) -> sexes.map { position to it } }
            .mapIndexed { index, (position, sex) ->
                Player(index.toLong(), "Jogador $index", index % 5 + 1, sex = sex, position = position)
            }

    private fun assertCompleteRosters(players: List<Player>, teams: List<List<Player>>) {
        assertEquals(players.size / positions.size, teams.size)
        assertEquals(players.map { it.id }.sorted(), teams.flatten().map { it.id }.sorted())
        teams.forEach { team ->
            assertEquals(positions.sorted(), team.map { it.position }.sorted())
        }
    }
}

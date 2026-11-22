package br.com.voleipelada.domain

class ScorePointUseCase {
    operator fun invoke(session: Session, side: Int, delta: Int): Session {
        if (session.court.size != 2 || side !in 0..1 || delta !in listOf(-1, 1)) return session
        val match = session.match
        val scores = match.scores.toMutableList()
        val next = (scores[side] + delta).coerceAtLeast(0)
        if (next == scores[side]) return session
        scores[side] = next
        return session.copy(match = match.copy(scores = scores, serve = if (delta > 0) side else match.serve,
            points = match.points + PointSnapshot(match.scores, match.serve)))
    }
}

enum class DrawConstraint { PLAYER_COUNT, TEAM_SIZE, WOMEN, MEN }

data class DrawProblem(val constraint: DrawConstraint, val required: Int)

fun drawProblem(players: List<Player>, size: Int, minWomen: Int): DrawProblem? {
    require(size in 2..6 && minWomen in 0..2)
    if (players.size <= size) return DrawProblem(DrawConstraint.PLAYER_COUNT, size + 1)
    if (minWomen == 0) return null
    val count = teamCount(players.size, size)
    if (players.size / count < minWomen + 1) return DrawProblem(DrawConstraint.TEAM_SIZE, minWomen + 1)
    val women = count * minWomen
    if (players.count { it.sex == PlayerSex.FEMALE } < women) return DrawProblem(DrawConstraint.WOMEN, women)
    if (players.count { it.sex == PlayerSex.MALE } < count) return DrawProblem(DrawConstraint.MEN, count)
    return null
}

private fun teamCount(players: Int, size: Int): Int = (players + size - 1) / size

class GenerateBalancedTeamsUseCase {
    operator fun invoke(players: List<Player>, size: Int, minWomen: Int = 0): List<List<Player>> {
        require(size in 2..6 && minWomen in 0..2)
        if (players.isEmpty() && minWomen == 0) return emptyList()
        if (minWomen > 0) require(drawProblem(players, size, minWomen) == null)
        val count = teamCount(players.size, size)
        val teams = List(count) { mutableListOf<Player>() }
        val capacities = List(count) { index -> players.size / count + if (index < players.size % count) 1 else 0 }
        val levels = IntArray(count)
        val ordered = players.shuffled().sortedByDescending { it.experience.level }

        fun place(player: Player, available: (Int) -> Boolean) {
            val index = teams.indices.filter(available)
                .minWith(compareBy<Int> { levels[it] }.thenBy { teams[it].size })
            levels[index] += player.experience.level
            teams[index].add(player)
        }

        val reservedIds = if (minWomen == 0) emptySet() else {
            val women = ordered.filter { it.sex == PlayerSex.FEMALE }.take(count * minWomen)
            val men = ordered.filter { it.sex == PlayerSex.MALE }.take(count)
            women.forEach { player -> place(player) { teams[it].size < minWomen } }
            men.forEach { player -> place(player) { teams[it].size == minWomen } }
            (women + men).map { it.id }.toSet()
        }
        ordered.filterNot { it.id in reservedIds }.forEach { player ->
            place(player) { teams[it].size < capacities[it] }
        }
        return teams.map { it.toList() }
    }
}

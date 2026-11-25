package br.com.voleipro.domain

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

fun drawProblem(players: List<Player>, size: Int, minWomen: Int, minMen: Int = if (minWomen > 0) 1 else 0): DrawProblem? {
    require(size in 2..6 && minWomen in 0..5 && minMen in 0..5)
    if (players.size <= size) return DrawProblem(DrawConstraint.PLAYER_COUNT, size + 1)
    if (minWomen + minMen == 0) return null
    val count = teamCount(players.size, size)
    if (players.size / count < minWomen + minMen) return DrawProblem(DrawConstraint.TEAM_SIZE, minWomen + minMen)
    val women = count * minWomen
    if (players.count { it.sex == PlayerSex.FEMALE } < women) return DrawProblem(DrawConstraint.WOMEN, women)
    val men = count * minMen
    if (players.count { it.sex == PlayerSex.MALE } < men) return DrawProblem(DrawConstraint.MEN, men)
    return null
}

private fun teamCount(players: Int, size: Int): Int = (players + size - 1) / size

class GenerateBalancedTeamsUseCase {
    operator fun invoke(
        players: List<Player>, size: Int, minWomen: Int = 0, minMen: Int = if (minWomen > 0) 1 else 0,
    ): List<List<Player>> {
        require(size in 2..6 && minWomen in 0..5 && minMen in 0..5)
        if (players.isEmpty() && minWomen + minMen == 0) return emptyList()
        if (minWomen + minMen > 0) require(drawProblem(players, size, minWomen, minMen) == null)
        val count = teamCount(players.size, size)
        val teams = List(count) { mutableListOf<Player>() }
        val capacities = List(count) { index -> players.size / count + if (index < players.size % count) 1 else 0 }
        val levels = IntArray(count)
        val ordered = players.shuffled().sortedByDescending { it.experience.level }
        val positions = if (size == 4) PlayerPosition.forTeamSize(size) - PlayerPosition.UNSPECIFIED else emptyList()

        fun place(player: Player, available: (Int) -> Boolean) {
            val hasPosition = player.position in positions
            val index = teams.indices.filter(available).minWith(compareBy<Int> { team ->
                if (hasPosition) teams[team].count { it.position == player.position } else 0
            }.thenBy { levels[it] }.thenBy { teams[it].size })
            levels[index] += player.experience.level
            teams[index].add(player)
        }

        val byPosition = ordered.groupBy { it.position }
        if (positions.isNotEmpty() && players.size == count * size &&
            positions.all { byPosition[it]?.size == count }
        ) {
            val slots = if (minWomen + minMen > 0) {
                positionalSlots(positions.map { byPosition.getValue(it) }, count, minWomen, minMen)
            } else null
            val positionIndices = positions.withIndex().associate { it.value to it.index }
            ordered.forEach { player ->
                val position = positionIndices.getValue(player.position)
                place(player) { team ->
                    (slots == null || slots[team][position] == player.sex) &&
                        teams[team].none { it.position == player.position }
                }
            }
            return teams.map { it.toList() }
        }

        val women = ordered.filter { it.sex == PlayerSex.FEMALE }.take(count * minWomen)
        val men = ordered.filter { it.sex == PlayerSex.MALE }.take(count * minMen)
        women.forEach { player -> place(player) { teams[it].size < minWomen } }
        men.forEach { player -> place(player) { teams[it].size < minWomen + minMen } }
        val reservedIds = (women + men).map { it.id }.toSet()
        val remaining = ordered.filterNot { it.id in reservedIds }
            .sortedBy { if (it.position in positions) byPosition.getValue(it.position).size else Int.MAX_VALUE }
        remaining.forEach { player ->
            place(player) { teams[it].size < capacities[it] }
        }
        return teams.map { it.toList() }
    }

    private fun positionalSlots(
        byPosition: List<List<Player>>, count: Int, minWomen: Int, minMen: Int,
    ): List<List<PlayerSex>> {
        val remaining = byPosition.map { players -> players.groupingBy { it.sex }.eachCount().toMutableMap() }

        fun select(position: Int, women: IntRange, men: IntRange): List<PlayerSex>? {
            if (position == remaining.size) return if (0 in women && 0 in men) emptyList() else null
            for ((sex, available) in remaining[position]) {
                if (available == 0) continue
                val female = if (sex == PlayerSex.FEMALE) 1 else 0
                val male = if (sex == PlayerSex.MALE) 1 else 0
                val rest = select(position + 1,
                    (women.first - female)..(women.last - female), (men.first - male)..(men.last - male))
                if (rest != null) return listOf(sex) + rest
            }
            return null
        }

        return List(count) { team ->
            val teamsLeft = count - team - 1
            val women = remaining.sumOf { it[PlayerSex.FEMALE] ?: 0 }
            val men = remaining.sumOf { it[PlayerSex.MALE] ?: 0 }
            // Leave enough players of each sex for the remaining teams, each still with every position.
            val slots = checkNotNull(select(0, minWomen..(women - teamsLeft * minWomen),
                minMen..(men - teamsLeft * minMen)))
            slots.forEachIndexed { position, sex ->
                remaining[position][sex] = remaining[position].getValue(sex) - 1
            }
            slots
        }
    }
}

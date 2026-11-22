package br.com.voleipelada.domain

import kotlin.random.Random

private val palette = listOf(0xFF2563EB, 0xFFDC2626, 0xFFA16207, 0xFF047857, 0xFF7E22CE, 0xFF0E7490)

fun Session.addTeams(additions: List<Team>, replace: Boolean = false): Session {
    val base = if (replace) copy(teams = emptyList(), court = emptyList(), queue = emptyList(),
        match = Match(), round = round + 1, players = players.map { it.copy(assignedTeamId = null) }) else this
    val order = base.court + base.queue + additions.map { it.id }
    return base.copy(teams = base.teams + additions, court = order.take(2), queue = order.drop(2))
}

private fun Session.newTeam(name: String, offset: Int = 0) =
    Team(Random.nextLong(Long.MIN_VALUE, 0), name, palette[(teams.size + offset) % palette.size].toInt(),
        createdAt = System.currentTimeMillis())

fun Session.saveTeam(id: Long?, name: String, playerIds: Set<Long>): Session {
    val current = team(id)
    if (id != null && current == null) return this
    val saved = current?.copy(name = name) ?: newTeam(name)
    val updated = if (current == null) addTeams(listOf(saved))
        else copy(teams = teams.map { if (it.id == id) saved else it })
    return updated.copy(players = updated.players.map { player ->
        when {
            player.id in playerIds && (player.assignedTeamId == null || player.assignedTeamId == id) ->
                player.copy(assignedTeamId = saved.id)
            player.assignedTeamId == saved.id -> player.copy(assignedTeamId = null)
            else -> player
        }
    })
}

fun Session.drawCandidates(replace: Boolean): List<Player> =
    players.filter { it.isPresent && (replace || it.assignedTeamId == null) }

fun Session.draw(playerIds: Set<Long>, size: Int, replace: Boolean, minWomen: Int): Session {
    val eligible = drawCandidates(replace).filter { it.id in playerIds }
    require(eligible.size > size)
    val groups = GenerateBalancedTeamsUseCase()(eligible, size, minWomen)
    val count = if (replace) 0 else teams.size
    val base = if (replace) copy(teams = emptyList()) else this
    val additions = groups.indices.map { index -> base.newTeam("Time sorteado ${count + index + 1}", index) }
    val assignments = groups.flatMapIndexed { index, group -> group.map { it.id to additions[index].id } }.toMap()
    val updated = addTeams(additions, replace)
    return updated.copy(players = updated.players.map { player ->
        val teamId = assignments[player.id]
        if (teamId == null) player else player.copy(assignedTeamId = teamId)
    })
}

fun Session.removeTeam(id: Long): Session {
    val order = (court + queue).filterNot { it == id }
    return copy(teams = teams.filterNot { it.id == id }, court = order.take(2), queue = order.drop(2),
        match = if (id in court) Match() else match,
        players = players.map { if (it.assignedTeamId == id) it.copy(assignedTeamId = null) else it })
}

fun Session.substitute(side: Int, id: Long): Session {
    if (side !in court.indices || id !in queue) return this
    val leaving = court[side]
    val sides = court.toMutableList().apply { set(side, id) }
    return copy(court = sides, queue = queue - id + leaving, match = Match(),
        teams = teams.map { if (it.id == leaving) it.copy(wins = 0) else it })
}

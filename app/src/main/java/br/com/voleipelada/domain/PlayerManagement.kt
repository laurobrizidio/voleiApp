package br.com.voleipelada.domain

import kotlin.random.Random

const val PLAYER_NAME_LIMIT = 80

fun normalizePlayerName(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")

fun Session.availablePlayerNames(rawNames: List<String>): List<String> {
    val used = players.map { it.name.lowercase() }.toMutableSet()
    return rawNames.map(::normalizePlayerName).filter { it.length in 1..PLAYER_NAME_LIMIT && used.add(it.lowercase()) }
}

fun Session.addPlayers(
    names: List<String>, skillLevel: Int, present: Boolean, sex: PlayerSex = PlayerSex.UNSPECIFIED,
): Session {
    require(skillLevel in 1..5)
    return copy(players = players + availablePlayerNames(names).map {
        Player(Random.nextLong(Long.MIN_VALUE, 0), it, skillLevel, present, sex = sex)
    })
}

fun Session.editPlayer(id: Long, name: String, skillLevel: Int, present: Boolean, sex: PlayerSex): Session {
    val normalized = normalizePlayerName(name)
    require(normalized.length in 1..PLAYER_NAME_LIMIT && skillLevel in 1..5)
    require(players.none { it.id != id && it.name.equals(normalized, ignoreCase = true) })
    return copy(players = players.map {
        if (it.id == id) it.copy(name = normalized, skillLevel = skillLevel, isPresent = present, sex = sex) else it
    })
}

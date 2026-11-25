package br.com.voleipro.domain

import kotlin.random.Random

const val PLAYER_NAME_LIMIT = 80

val Session.eligiblePlayers: List<Player>
    get() = players.filter { pelada.composition.accepts(it.sex) }

fun Session.togglePresence(position: PlayerPosition? = null): Session {
    val group = eligiblePlayers.filter { position == null || it.position == position }
    if (group.isEmpty()) return this
    val present = group.any { !it.isPresent }
    val ids = group.map(Player::id).toSet()
    return copy(players = players.map { player ->
        if (player.id in ids && player.isPresent != present) player.copy(isPresent = present) else player
    })
}

fun normalizePlayerName(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")

fun Session.availablePlayerNames(rawNames: List<String>): List<String> {
    val used = players.map { it.name.lowercase() }.toMutableSet()
    return rawNames.map(::normalizePlayerName).filter { it.length in 1..PLAYER_NAME_LIMIT && used.add(it.lowercase()) }
}

fun Session.addPlayers(
    names: List<String>, skillLevel: Int, present: Boolean, sex: PlayerSex = PlayerSex.UNSPECIFIED,
    position: PlayerPosition = PlayerPosition.UNSPECIFIED,
): Session {
    require(skillLevel in 1..5)
    return copy(players = players + availablePlayerNames(names).map {
        Player(Random.nextLong(Long.MIN_VALUE, 0), it, skillLevel, present, sex = sex, position = position)
    })
}

fun Session.editPlayer(
    id: Long, name: String, skillLevel: Int, present: Boolean, sex: PlayerSex, position: PlayerPosition,
): Session {
    val normalized = normalizePlayerName(name)
    require(normalized.length in 1..PLAYER_NAME_LIMIT && skillLevel in 1..5)
    require(players.none { it.id != id && it.name.equals(normalized, ignoreCase = true) })
    return copy(players = players.map {
        if (it.id == id) {
            it.copy(name = normalized, skillLevel = skillLevel, isPresent = present, sex = sex, position = position)
        } else it
    })
}

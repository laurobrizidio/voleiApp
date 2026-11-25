package br.com.voleipro.domain

enum class Composition(val label: String) {
    OPEN("Livre"), FEMALE("Feminina"), MALE("Masculina"), MIXED("Mista");

    fun accepts(sex: PlayerSex): Boolean = when (this) {
        FEMALE -> sex == PlayerSex.FEMALE
        MALE -> sex == PlayerSex.MALE
        OPEN, MIXED -> true
    }
}

data class Pelada(
    val id: Long = 1,
    val name: String = "",
    val teamSize: Int = 4,
    val composition: Composition = Composition.OPEN,
    val minWomen: Int = 1,
    val minMen: Int = 1,
) {
    val displayName: String get() = name.ifBlank { "Minha liga" }
    val format: String get() = "${teamSize}×${teamSize}"
    val mixed: Boolean get() = composition == Composition.MIXED

    fun acceptsRoster(players: List<Player>): Boolean =
        players.all { composition.accepts(it.sex) } &&
            (!mixed || players.isEmpty() || (
                players.count { it.sex == PlayerSex.FEMALE } >= minWomen &&
                    players.count { it.sex == PlayerSex.MALE } >= minMen
            ))
}

data class LeagueSelection(val active: League?, val profiles: List<Pelada>)

class PeladaException(message: String) : IllegalArgumentException(message)

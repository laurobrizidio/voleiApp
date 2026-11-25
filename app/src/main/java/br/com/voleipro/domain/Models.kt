package br.com.voleipro.domain

import kotlinx.coroutines.flow.Flow

data class Team(val id: Long, val name: String, val color: Int, val wins: Int = 0, val createdAt: Long = 0)

data class Player(
    val id: Long,
    val name: String,
    val skillLevel: Int = 3,
    val isPresent: Boolean = true,
    val assignedTeamId: Long? = null,
    val sex: PlayerSex = PlayerSex.UNSPECIFIED,
    val position: PlayerPosition = PlayerPosition.UNSPECIFIED,
) {
    val experience: Experience
        get() = when (skillLevel) {
            1, 2 -> Experience.BEGINNER
            4, 5 -> Experience.EXPERIENCED
            else -> Experience.INTERMEDIATE
        }
}

enum class PlayerSex(val label: String) {
    UNSPECIFIED("Não informar"), FEMALE("Feminino"), MALE("Masculino"), OTHER("Outro")
}

enum class PlayerPosition(val label: String) {
    UNSPECIFIED("Sem posição"),
    SETTER("Levantador"),
    OUTSIDE_HITTER("Ponteiro"),
    MIDDLE_BLOCKER("Central"),
    LIBERO("Líbero"),
    OPPOSITE("Oposto"),
    PRIMARY_ATTACKER("Ataque principal"),
    SECONDARY_ATTACKER("Ataque secundário");

    companion object {
        private val fourPlayerPositions = listOf(UNSPECIFIED, SETTER, PRIMARY_ATTACKER, SECONDARY_ATTACKER, LIBERO)
        private val standardPositions = listOf(UNSPECIFIED, SETTER, OUTSIDE_HITTER, MIDDLE_BLOCKER, LIBERO, OPPOSITE)

        fun forTeamSize(teamSize: Int): List<PlayerPosition> =
            if (teamSize == 4) fourPlayerPositions else standardPositions
    }
}

enum class Experience(val label: String, val level: Int) {
    BEGINNER("Iniciante", 1),
    INTERMEDIATE("Intermediário", 3),
    EXPERIENCED("Experiente", 5),
}

enum class ReturnRule(val label: String) {
    FIRST_IN_QUEUE("Primeiro da fila"), AHEAD_OF_LOSER("À frente do perdedor"),
    END_OF_QUEUE("Fim da fila"), FIXED_POSITION("Posição fixa")
}

enum class TieRule(val label: String) {
    BOTH_LEAVE("Ambos saem"), CHALLENGER_STAYS("Desafiante fica"), CHAMPION_STAYS("Campeão fica")
}

enum class SetPointTieBreak(val extraPoints: Int, val label: String) {
    STANDARD(0, "Regra normal do set"),
    TWO_DIRECT(2, "2 pontos diretos"),
    THREE_DIRECT(3, "3 pontos diretos"),
}

data class Rules(
    val target: Int = 15,
    val winByTwo: Boolean = true,
    val cap: Int = 0,
    val winLimit: Int = 2,
    val returnRule: ReturnRule = ReturnRule.FIRST_IN_QUEUE,
    val returnPosition: Int = 2,
    val bothLeave: Boolean = false,
    val tieRule: TieRule = TieRule.BOTH_LEAVE,
    val keepScreenOn: Boolean = true,
    val feedback: Boolean = true,
    val setPointTieBreak: SetPointTieBreak = SetPointTieBreak.STANDARD,
)

data class PointSnapshot(val scores: List<Int>, val serve: Int)

data class Match(
    val scores: List<Int> = listOf(0, 0),
    val serve: Int = 0,
    val elapsedMs: Long = 0,
    val startedAt: Long? = null,
    val swapped: Boolean = false,
    val points: List<PointSnapshot> = emptyList(),
) {
    fun duration(now: Long) = elapsedMs + (startedAt?.let { (now - it).coerceAtLeast(0) } ?: 0)
}

data class Session(
    val teams: List<Team> = emptyList(),
    val court: List<Long> = emptyList(),
    val queue: List<Long> = emptyList(),
    val match: Match = Match(),
    val rules: Rules = Rules(),
    val round: Long = 0,
    val players: List<Player> = emptyList(),
    val pelada: Pelada = Pelada(),
) {
    fun team(id: Long?) = teams.find { it.id == id }
    fun roster(teamId: Long) = players.filter { it.assignedTeamId == teamId }
}

data class Result(
    val left: Team, val right: Team, val leftScore: Int, val rightScore: Int,
    val durationMs: Long, val finishedAt: Long, val winner: Int?,
    val leftReason: String, val rightReason: String,
)

data class League(
    val session: Session = Session(),
    val history: List<Result> = emptyList(),
    val standingsMatches: List<Result> = history,
)

interface LeagueRepository {
    fun observe(leagueId: Long): Flow<LeagueSelection>
    suspend fun savePelada(profile: Pelada, initialTarget: Int): Long
    suspend fun deletePeladas(ids: Set<Long>)
    suspend fun update(leagueId: Long, transform: (Session) -> Session)
    suspend fun finish(expected: Session, now: Long)
    suspend fun draw(leagueId: Long, playerIds: Set<Long>, size: Int, replace: Boolean, minWomen: Int, minMen: Int)
    suspend fun clearHistory(leagueId: Long)
    suspend fun resetStandings(leagueId: Long)
}

enum class Appearance(val label: String) { SYSTEM("Sistema"), LIGHT("Claro"), DARK("Escuro"), CONTRAST("Alto contraste") }

enum class ThemePalette(val label: String) {
    // Enum names are stored in DataStore.
    SHIELD("Padrão"),
    IMPROVISADAS("Rosa Neon"),
    BENACON("Ouro"),
}

data class Preferences(
    val appearance: Appearance = Appearance.SYSTEM,
    val vibration: Boolean = true,
    val sound: Boolean = false,
    val leagueName: String = "",
    val activeLeagueId: Long = 1,
    val palette: ThemePalette = ThemePalette.SHIELD,
)

interface PreferencesRepository {
    val preferences: Flow<Preferences>
    suspend fun update(transform: (Preferences) -> Preferences)
    suspend fun selectPelada(id: Long)
}

data class Standing(val team: Team, val games: Int, val wins: Int, val losses: Int, val scored: Long, val conceded: Long) {
    val balance get() = scored - conceded
    val percent get() = if (games == 0) 0 else (100L * wins / games).toInt()
}

fun League.standings(): List<Standing> {
    val teams = (session.teams + standingsMatches.flatMap { listOf(it.left, it.right) }).distinctBy { it.id }
    return teams.map { team ->
        val games = standingsMatches.filter { it.left.id == team.id || it.right.id == team.id }
        val wins = games.count { (it.winner == 0 && it.left.id == team.id) || (it.winner == 1 && it.right.id == team.id) }
        Standing(team, games.size, wins, games.count { it.winner != null } - wins,
            games.sumOf { if (it.left.id == team.id) it.leftScore.toLong() else it.rightScore.toLong() },
            games.sumOf { if (it.left.id == team.id) it.rightScore.toLong() else it.leftScore.toLong() })
    }.sortedWith(compareByDescending<Standing> { it.wins }.thenByDescending { it.balance }.thenBy { it.team.name })
}

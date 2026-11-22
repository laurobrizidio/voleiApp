package br.com.voleipelada.domain.usecase.rotation

import br.com.voleipelada.domain.*

fun Rules.wins(score: Int, other: Int): Boolean =
    score > other && ((cap > 0 && score >= cap) ||
        (score >= target && (!winByTwo || score - other >= 2)))

fun Session.setWinner(): Int? = if (court.size < 2) null
    else (0..1).firstOrNull { rules.wins(match.scores[it], match.scores[1 - it]) }

fun Session.isSetPoint(side: Int): Boolean =
    court.size == 2 && setWinner() == null && rules.wins(match.scores[side] + 1, match.scores[1 - side])

data class Rotation(val next: Session, val result: Result)

class ProcessMatchEndUseCase {
    operator fun invoke(session: Session, now: Long): Rotation? = session.computeRotation(now)
}

private fun Session.computeRotation(now: Long): Rotation? {
    if (court.size != 2) return null
    val left = team(court[0]) ?: return null
    val right = team(court[1]) ?: return null
    val (a, b) = match.scores
    val winner = when {
        a > b -> 0
        b > a -> 1
        else -> null
    }
    val sides = listOf(left, right)
    val winnerTeam = winner?.let(sides::get)
    val projected = (winnerTeam?.wins ?: 0) + 1
    val limit = winner != null && !rules.bothLeave && rules.winLimit > 0 && projected >= rules.winLimit
    val nextCourt = court.toMutableList()
    val nextQueue = queue.toMutableList()
    val reasons = MutableList(2) { "Permaneceu em quadra" }

    fun replace(side: Int, reason: String) {
        if (nextQueue.isEmpty()) return
        nextQueue.add(nextCourt[side])
        nextCourt[side] = nextQueue.removeAt(0)
        reasons[side] = reason
    }

    fun rotateBoth(first: Int, reason: String) {
        if (queue.size < 2) {
            replace(first, reason)
            return
        }
        nextCourt[0] = nextQueue.removeAt(0)
        nextCourt[1] = nextQueue.removeAt(0)
        nextQueue.add(court[first])
        nextQueue.add(court[1 - first])
        reasons[0] = reason
        reasons[1] = reason
    }

    when {
        winner == null -> when (rules.tieRule) {
            TieRule.BOTH_LEAVE -> rotateBoth(0, "Rodízio por empate")
            TieRule.CHALLENGER_STAYS -> replace(if (left.wins <= right.wins) 1 else 0, "Rodízio por empate")
            TieRule.CHAMPION_STAYS -> replace(if (left.wins >= right.wins) 1 else 0, "Rodízio por empate")
        }
        rules.bothLeave -> rotateBoth(1 - winner, "Rodízio total")
        limit && queue.size >= 2 -> {
            nextCourt[0] = nextQueue.removeAt(0)
            nextCourt[1] = nextQueue.removeAt(0)
            val loser = court[1 - winner]
            val championPosition = when (rules.returnRule) {
                ReturnRule.FIRST_IN_QUEUE -> 0
                ReturnRule.FIXED_POSITION -> (rules.returnPosition - 1).coerceIn(0, nextQueue.size)
                ReturnRule.AHEAD_OF_LOSER -> nextQueue.size
                ReturnRule.END_OF_QUEUE -> nextQueue.size + 1
            }
            nextQueue.add(loser)
            nextQueue.add(championPosition, court[winner])
            reasons[winner] = "Limite de vitórias"
            reasons[1 - winner] = "Derrota"
        }
        limit && (rules.returnRule == ReturnRule.FIRST_IN_QUEUE || rules.returnRule == ReturnRule.END_OF_QUEUE ||
            (rules.returnRule == ReturnRule.FIXED_POSITION && rules.returnPosition > 1)) ->
            replace(winner, "Limite de vitórias")
        else -> replace(1 - winner, "Derrota")
    }

    val updatedTeams = teams.map {
        when (it.id) {
            winnerTeam?.id -> it.copy(wins = if (limit || rules.bothLeave) 0 else projected)
            left.id, right.id -> it.copy(wins = 0)
            else -> it
        }
    }
    return Rotation(copy(teams = updatedTeams, court = nextCourt, queue = nextQueue, match = Match(), round = round + 1),
        Result(left, right, a, b, match.duration(now), now, winner, reasons[0], reasons[1]))
}

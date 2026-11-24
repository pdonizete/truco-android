package com.pdonizete.truco.game

/**
 * O que aconteceu na mesa, para cada aparelho anunciar do seu ponto de vista.
 * O anfitrião cria os eventos com os lugares de verdade e usa [forViewer] antes de mandar.
 */
sealed interface GameEvent {
    data class Shuffle(val newMatch: Boolean) : GameEvent
    data class HandStart(val starter: Int, val mode: HandMode, val elevenSide: Int?) : GameEvent

    /** [card] é null para a carta coberta de outro jogador. */
    data class Played(val player: Int, val card: Card?, val covered: Boolean) : GameEvent
    data class TrickEnd(val index: Int, val winnerPlayer: Int?) : GameEvent
    data class Raised(val player: Int, val value: Int) : GameEvent

    /** Resposta a um pedido: aceitou (a mão passa a valer [value]) ou correu. */
    data class Answered(val player: Int, val accepted: Boolean, val value: Int) : GameEvent
    data class Eleven(val player: Int, val side: Int, val play: Boolean) : GameEvent
    data class HandEnd(val winnerSide: Int, val points: Int, val reason: EndReason, val score: List<Int>) : GameEvent
    data class MatchEnd(val winnerSide: Int) : GameEvent
}

/** O mesmo evento visto do lugar [seat]: lugares e lados contados a partir dele, cartas cobertas escondidas. */
fun GameEvent.forViewer(seat: Int, players: Int): GameEvent {
    val mySide = seat % 2
    fun p(x: Int) = (x - seat + players) % players
    fun s(x: Int) = x xor mySide
    return when (this) {
        is GameEvent.Shuffle -> this
        is GameEvent.HandStart -> copy(starter = p(starter), elevenSide = elevenSide?.let(::s))
        is GameEvent.Played -> copy(player = p(player), card = if (covered && player != seat) null else card)
        is GameEvent.TrickEnd -> copy(winnerPlayer = winnerPlayer?.let(::p))
        is GameEvent.Raised -> copy(player = p(player))
        is GameEvent.Answered -> copy(player = p(player))
        is GameEvent.Eleven -> copy(player = p(player), side = s(side))
        is GameEvent.HandEnd -> copy(winnerSide = s(winnerSide), score = if (mySide == 0) score else score.reversed())
        is GameEvent.MatchEnd -> copy(winnerSide = s(winnerSide))
    }
}

fun GameEvent.encode(): String = when (this) {
    is GameEvent.Shuffle -> "S,${flag(newMatch)}"
    is GameEvent.HandStart -> "H,$starter,${mode.name},${elevenSide ?: -1}"
    is GameEvent.Played -> "P,$player,${card?.id ?: -1},${flag(covered)}"
    is GameEvent.TrickEnd -> "T,$index,${winnerPlayer ?: -1}"
    is GameEvent.Raised -> "R,$player,$value"
    is GameEvent.Answered -> "A,$player,${flag(accepted)},$value"
    is GameEvent.Eleven -> "E,$player,$side,${flag(play)}"
    is GameEvent.HandEnd -> "F,$winnerSide,$points,${reason.name},${score[0]},${score[1]}"
    is GameEvent.MatchEnd -> "M,$winnerSide"
}

fun decodeEvent(s: String): GameEvent {
    val f = s.split(',')
    fun i(k: Int) = f[k].toInt()
    return when (f[0]) {
        "S" -> GameEvent.Shuffle(f[1] == "1")
        "H" -> GameEvent.HandStart(i(1), HandMode.valueOf(f[2]), optInt(f[3]))
        "P" -> GameEvent.Played(i(1), optInt(f[2])?.let(Card::fromId), f[3] == "1")
        "T" -> GameEvent.TrickEnd(i(1), optInt(f[2]))
        "R" -> GameEvent.Raised(i(1), i(2))
        "A" -> GameEvent.Answered(i(1), f[2] == "1", i(3))
        "E" -> GameEvent.Eleven(i(1), i(2), f[3] == "1")
        "F" -> GameEvent.HandEnd(i(1), i(2), EndReason.valueOf(f[3]), listOf(i(4), i(5)))
        "M" -> GameEvent.MatchEnd(i(1))
        else -> throw IllegalArgumentException("Evento desconhecido: ${f[0]}")
    }
}

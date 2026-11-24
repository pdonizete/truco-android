package com.pdonizete.truco.game

import kotlin.random.Random

const val WINNING_SCORE = 12

/**
 * Partida até 12 pontos. O placar é por lado: 0 (você ou a sua dupla) e 1.
 * Fluxo: jogar a [hand] até terminar, chamar [settle] e depois [nextHand].
 */
class Match(
    val players: Int = 2,
    private val random: Random = Random.Default,
    val coveredAllowed: Boolean = false,
    firstStarter: Int = random.nextInt(players),
    initialScore: IntArray = intArrayOf(0, 0),
) {
    val score: IntArray = initialScore.copyOf()
    var starter: Int = firstStarter; private set
    var hand: Hand = deal(); private set
    var winnerSide: Int? = null; private set
    private var settled = false

    val isOver: Boolean get() = winnerSide != null

    /** Soma ao placar os pontos da mão que terminou. Uma vez por mão. */
    fun settle(): HandResult {
        val r = checkNotNull(hand.result) { "A mão ainda não terminou." }
        check(!settled) { "Os pontos desta mão já foram somados." }
        settled = true
        score[r.winnerSide] += r.points
        if (score[r.winnerSide] >= WINNING_SCORE) winnerSide = r.winnerSide
        return r
    }

    /** Dá as cartas da mão seguinte; quem é mão passa para o próximo jogador. */
    fun nextHand(): Hand {
        check(settled && !isOver) { "Não é hora de dar uma nova mão." }
        starter = (starter + 1) % players
        settled = false
        hand = deal()
        return hand
    }

    private fun deal(): Hand {
        val eleven = (0..1).filter { score[it] == WINNING_SCORE - 1 }
        val (mode, side) = when (eleven.size) {
            2 -> HandMode.MAO_DE_FERRO to null
            1 -> HandMode.MAO_DE_ONZE to eleven[0]
            else -> HandMode.NORMAL to null
        }
        return Hand.deal(players, starter, random, mode, side, coveredAllowed)
    }
}

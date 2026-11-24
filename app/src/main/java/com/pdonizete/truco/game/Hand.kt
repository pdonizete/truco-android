package com.pdonizete.truco.game

import kotlin.random.Random

/** Valores que a mão pode ter: 1, truco (3), seis, nove e doze. */
val HAND_VALUES = listOf(1, 3, 6, 9, 12)

/** Valor do pedido seguinte a [value], ou null se a mão já vale 12. */
fun nextRaise(value: Int): Int? {
    val i = HAND_VALUES.indexOf(value)
    require(i >= 0) { "Valor de mão inválido: $value" }
    return HAND_VALUES.getOrNull(i + 1)
}

/** Pontos de quem pediu quando o outro lado corre: o valor anterior ao pedido. */
fun refusalPoints(requested: Int): Int {
    val i = HAND_VALUES.indexOf(requested)
    require(i >= 1) { "Pedido inválido: $requested" }
    return HAND_VALUES[i - 1]
}

/**
 * Lado que ganhou a mão, ou null se ainda não está decidido.
 * [winners] traz o lado que ganhou cada rodada (null quando cangou).
 * Se as três rodadas cangarem, ganha o lado de quem é mão ([starterSide]).
 */
fun handWinner(winners: List<Int?>, starterSide: Int): Int? {
    if (winners.size < 2) return null
    val first = winners[0]
    val second = winners[1]
    return when {
        first != null && (second == first || second == null) -> first
        first == null && second != null -> second
        winners.size < 3 -> null
        first == null -> winners[2] ?: starterSide
        else -> winners[2] ?: first
    }
}

enum class HandMode {
    NORMAL,

    /** Um lado tem 11: vê as cartas e decide se joga (vale 3) ou corre (o outro ganha 1). Sem truco. */
    MAO_DE_ONZE,

    /** Os dois lados têm 11: joga-se sem ver as cartas, valendo 1. Sem truco. */
    MAO_DE_FERRO,
}

enum class EndReason { RODADAS, CORREU, CORREU_MAO_DE_ONZE }

enum class Response { ACEITAR, AUMENTAR, CORRER }

data class HandResult(val winnerSide: Int, val points: Int, val reason: EndReason)

/** Carta jogada na mesa. Coberta (virada para baixo) perde para qualquer carta aberta. */
data class PlayedCard(val player: Int, val card: Card, val covered: Boolean = false)

fun PlayedCard.power(vira: Card): Int = if (covered) -1 else card.strength(vira)

/** Rodada completa. [winnerSide] e [winnerPlayer] são null quando cangou. */
data class Trick(val plays: List<PlayedCard>, val winnerSide: Int?, val winnerPlayer: Int?)

/** Pedido esperando resposta. [value] é quanto a mão passa a valer se o outro lado aceitar. */
data class RaiseRequest(val requesterSide: Int, val value: Int) {
    val responderSide: Int get() = 1 - requesterSide
}

/**
 * Uma mão de truco paulista com até três rodadas, para 2 ou 4 jogadores.
 * Com 4, as duplas são alternadas: jogadores 0 e 2 contra 1 e 3. O lado de um jogador é jogador % 2.
 * As ações devolvem false quando não são permitidas e, nesse caso, não mudam nada.
 */
class Hand(
    val players: Int,
    /** Quem é mão: joga primeiro e leva quando as três rodadas cangam. */
    val starter: Int,
    hands: List<List<Card>>,
    val vira: Card,
    val mode: HandMode = HandMode.NORMAL,
    /** Lado com 11 pontos na mão de onze; null nos outros modos. */
    val elevenSide: Int? = null,
    /** Permite jogar carta coberta a partir da segunda rodada. */
    val coveredAllowed: Boolean = false,
) {
    init {
        require(players == 2 || players == 4) { "O truco tem 2 ou 4 jogadores." }
        require(starter in 0 until players) { "Jogador inicial inválido." }
        require(hands.size == players && hands.all { it.size == 3 }) { "Cada jogador recebe 3 cartas." }
        require((mode == HandMode.MAO_DE_ONZE) == (elevenSide != null)) { "Lado da mão de onze inválido." }
    }

    val hands: List<MutableList<Card>> = hands.map { it.toMutableList() }

    var value: Int = 1
        internal set
    var current: Int = starter
        private set
    var pending: RaiseRequest? = null
        private set
    var elevenDecided: Boolean = mode != HandMode.MAO_DE_ONZE
        private set
    var result: HandResult? = null
        private set

    private val tableCards = mutableListOf<PlayedCard>()
    private val doneTricks = mutableListOf<Trick>()

    /** Cartas da rodada em andamento, na ordem em que foram jogadas. */
    val table: List<PlayedCard> get() = tableCards
    val tricks: List<Trick> get() = doneTricks

    // Quem pediu por último não pode pedir de novo; ninguém pede antes de alguém jogar uma carta.
    private var lastRaiserSide: Int? = null
    private var playsAtLastRaise = -1

    val isOver: Boolean get() = result != null
    val blind: Boolean get() = mode == HandMode.MAO_DE_FERRO
    val awaitingEleven: Boolean get() = !isOver && !elevenDecided
    val starterSide: Int get() = sideOf(starter)
    private val playedCount: Int get() = doneTricks.size * players + tableCards.size

    fun sideOf(player: Int): Int = player % 2

    fun canPlay(player: Int): Boolean =
        !isOver && elevenDecided && pending == null && player == current

    fun canCover(player: Int): Boolean = canPlay(player) && coveredAllowed && doneTricks.isNotEmpty()

    fun canRaise(player: Int): Boolean =
        mode == HandMode.NORMAL && canPlay(player) && nextRaise(value) != null &&
            lastRaiserSide != sideOf(player) && playsAtLastRaise != playedCount

    fun canEscalate(side: Int): Boolean {
        val p = pending ?: return false
        return !isOver && p.responderSide == side && nextRaise(p.value) != null
    }

    fun requestRaise(player: Int): Boolean {
        if (!canRaise(player)) return false
        startRaise(sideOf(player), nextRaise(value)!!)
        return true
    }

    fun respond(side: Int, response: Response): Boolean {
        val p = pending ?: return false
        if (isOver || p.responderSide != side) return false
        when (response) {
            Response.ACEITAR -> {
                value = p.value
                pending = null
            }
            Response.AUMENTAR -> startRaise(side, nextRaise(p.value) ?: return false)
            Response.CORRER -> finish(p.requesterSide, refusalPoints(p.value), EndReason.CORREU)
        }
        return true
    }

    /** Decisão do lado com 11: jogar (a mão vale 3) ou correr (o adversário ganha 1). */
    fun decideEleven(play: Boolean): Boolean {
        if (!awaitingEleven) return false
        elevenDecided = true
        if (play) value = 3 else finish(1 - elevenSide!!, 1, EndReason.CORREU_MAO_DE_ONZE)
        return true
    }

    fun play(player: Int, card: Card, covered: Boolean = false): Boolean {
        if (!canPlay(player) || (covered && !canCover(player))) return false
        if (!hands[player].remove(card)) return false
        tableCards += PlayedCard(player, card, covered)
        if (tableCards.size < players) {
            current = (player + 1) % players
            return true
        }
        closeTrick()
        return true
    }

    private fun startRaise(side: Int, to: Int) {
        pending = RaiseRequest(side, to)
        lastRaiserSide = side
        playsAtLastRaise = playedCount
    }

    private fun closeTrick() {
        val best = tableCards.maxOf { it.power(vira) }
        val top = tableCards.filter { it.power(vira) == best }
        val trick = if (top.map { sideOf(it.player) }.toSet().size > 1) {
            Trick(tableCards.toList(), null, null)
        } else {
            Trick(tableCards.toList(), sideOf(top.first().player), top.first().player)
        }
        doneTricks += trick
        tableCards.clear()
        val winner = handWinner(doneTricks.map { it.winnerSide }, starterSide)
            ?: if (doneTricks.size == 3) starterSide else null
        if (winner != null) {
            finish(winner, value, EndReason.RODADAS)
            return
        }
        current = nextLeader()
    }

    /** Quem ganhou a última rodada começa a próxima; se cangou, começa quem é mão. */
    private fun nextLeader(): Int {
        doneTricks.last().winnerPlayer?.let { return it }
        if (doneTricks.size == 2) doneTricks[0].winnerPlayer?.let { return it }
        return starter
    }

    private fun finish(side: Int, points: Int, reason: EndReason) {
        result = HandResult(side, points, reason)
        pending = null
    }

    companion object {
        fun deal(
            players: Int,
            starter: Int,
            random: Random,
            mode: HandMode = HandMode.NORMAL,
            elevenSide: Int? = null,
            coveredAllowed: Boolean = false,
        ): Hand {
            val deck = Deck(random)
            val hands = List(players) { deck.draw(3) }
            return Hand(players, starter, hands, deck.draw(), mode, elevenSide, coveredAllowed)
        }
    }
}

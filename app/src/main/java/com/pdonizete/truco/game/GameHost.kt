package com.pdonizete.truco.game

import kotlin.random.Random

/** Jogada de uma pessoa. */
sealed interface Move {
    data class Play(val card: Card, val covered: Boolean = false) : Move
    data object Raise : Move
    data class Answer(val response: Response) : Move
    data class Eleven(val play: Boolean) : Move
    data object Next : Move
}

fun Move.encode(): String = when (this) {
    is Move.Play -> "P,${card.id},${flag(covered)}"
    Move.Raise -> "R"
    is Move.Answer -> "A,${response.name}"
    is Move.Eleven -> "E,${flag(play)}"
    Move.Next -> "N"
}

fun decodeMove(s: String): Move {
    val f = s.split(',')
    return when (f[0]) {
        "P" -> Move.Play(Card.fromId(f[1].toInt()), f[2] == "1")
        "R" -> Move.Raise
        "A" -> Move.Answer(Response.valueOf(f[1]))
        "E" -> Move.Eleven(f[1] == "1")
        "N" -> Move.Next
        else -> throw IllegalArgumentException("Jogada desconhecida: ${f[0]}")
    }
}

/**
 * Autoridade da partida: aplica as jogadas, faz o computador jogar nos lugares sem pessoa
 * e devolve os eventos para anunciar. No jogo pelo Bluetooth roda só no anfitrião.
 * [humans] são os lugares de pessoas; os outros são do computador. Sem Android.
 */
class GameHost(
    val players: Int,
    val humans: Set<Int>,
    difficulty: Difficulty,
    coveredAllowed: Boolean,
    random: Random = Random.Default,
) {
    private val ai = Ai(difficulty, random)
    val match = Match(players, random, coveredAllowed)

    /** As cartas estão sendo dadas: ninguém joga até [handStart]. */
    var dealing = false
        private set

    fun view(seat: Int): TableView = TableView.of(match, seat, dealing)

    fun start(): List<GameEvent> {
        dealing = true
        return listOf(GameEvent.Shuffle(true))
    }

    /** Fim da distribuição: a mão começa. */
    fun handStart(): List<GameEvent> {
        if (!dealing) return emptyList()
        dealing = false
        val h = match.hand
        return listOf(GameEvent.HandStart(h.starter, h.mode, h.elevenSide))
    }

    /** Aplica a jogada do lugar [seat]. Devolve null se ela não for permitida agora. */
    fun apply(seat: Int, move: Move): List<GameEvent>? {
        if (dealing || seat !in 0 until players) return null
        val h = match.hand
        val side = h.sideOf(seat)
        return when (move) {
            is Move.Play -> {
                val before = h.tricks.size
                if (!h.play(seat, move.card, move.covered)) return null
                val ev = mutableListOf<GameEvent>(GameEvent.Played(seat, move.card, move.covered))
                if (h.tricks.size > before) ev += GameEvent.TrickEnd(h.tricks.size - 1, h.tricks.last().winnerPlayer)
                settle(ev)
            }
            Move.Raise -> if (h.requestRaise(seat)) listOf(GameEvent.Raised(seat, h.pending!!.value)) else null
            is Move.Answer -> {
                val p = h.pending ?: return null
                if (!h.respond(side, move.response)) return null
                val e = when (move.response) {
                    Response.ACEITAR -> GameEvent.Answered(seat, true, p.value)
                    Response.AUMENTAR -> GameEvent.Raised(seat, h.pending!!.value)
                    Response.CORRER -> GameEvent.Answered(seat, false, p.value)
                }
                settle(listOf(e))
            }
            is Move.Eleven ->
                if (h.elevenSide == side && h.decideEleven(move.play)) settle(listOf(GameEvent.Eleven(seat, side, move.play))) else null
            Move.Next -> {
                if (h.result == null || match.isOver) return null
                match.nextHand()
                dealing = true
                listOf(GameEvent.Shuffle(false))
            }
        }
    }

    /** Lugar do computador que precisa agir agora, ou null se é a vez de uma pessoa. */
    fun cpuToAct(): Int? {
        if (dealing) return null
        val h = match.hand
        if (h.isOver) return null
        val p = h.pending
        return when {
            h.awaitingEleven -> h.elevenSide!!.takeIf { humansOn(it).isEmpty() }?.let(::bestOf)
            p != null -> p.responderSide.takeIf { humansOn(it).isEmpty() }?.let(::bestOf)
            h.current !in humans && h.canPlay(h.current) -> h.current
            else -> null
        }
    }

    /** Uma ação do computador, ou null se não é a vez dele. */
    fun cpuStep(): List<GameEvent>? {
        val who = cpuToAct() ?: return null
        val h = match.hand
        val side = h.sideOf(who)
        val mine = match.score[side]
        val theirs = match.score[1 - side]
        return when {
            h.awaitingEleven -> apply(who, Move.Eleven(ai.playEleven(h, who, mine, theirs)))
            h.pending != null ->
                apply(who, Move.Answer(ai.respond(h, who, mine, theirs))) ?: apply(who, Move.Answer(Response.ACEITAR))
            ai.shouldRaise(h, who, mine, theirs) ->
                apply(who, Move.Raise) ?: apply(who, Move.Play(ai.chooseCard(h, who)))
            else -> apply(who, Move.Play(ai.chooseCard(h, who)))
        }
    }

    private fun humansOn(side: Int) = humans.filter { it % 2 == side }

    /** Quem decide pelo lado (resposta ao truco, mão de onze): o jogador com a mão mais forte. */
    private fun bestOf(side: Int): Int {
        val h = match.hand
        return (0 until players).filter { it % 2 == side }
            .maxBy { ai.confidence(h, it, match.score[side], match.score[1 - side]) }
    }

    /** Se a mão terminou, soma os pontos e acrescenta o fim da mão (e da partida). */
    private fun settle(ev: List<GameEvent>): List<GameEvent> {
        if (match.hand.result == null) return ev
        val r = match.settle()
        val out = ev + GameEvent.HandEnd(r.winnerSide, r.points, r.reason, match.score.toList())
        return if (match.isOver) out + GameEvent.MatchEnd(match.winnerSide!!) else out
    }
}

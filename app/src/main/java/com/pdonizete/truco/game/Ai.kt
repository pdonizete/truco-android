package com.pdonizete.truco.game

import kotlin.random.Random

enum class Difficulty(val label: String) {
    FACIL("Fácil"),
    MEDIO("Médio"),
    DIFICIL("Difícil"),
}

/** Chance de [card] não perder para nenhuma de [opponentCards] cartas sorteadas de [unseen]. */
fun probabilityBeatsRandomHand(card: Card, vira: Card, unseen: List<Card>, opponentCards: Int): Double {
    if (opponentCards <= 0 || unseen.isEmpty()) return 1.0
    val total = unseen.size
    val k = minOf(opponentCards, total)
    val safe = total - unseen.count { compareCards(it, card, vira) > 0 }
    if (safe < k) return 0.0
    return comb(safe, k) / comb(total, k)
}

private fun comb(n: Int, k: Int): Double {
    var r = 1.0
    for (i in 0 until k) r = r * (n - i) / (i + 1)
    return r
}

/**
 * Computador de truco. A lógica segue o ai/bot.py do projeto Python.
 * [me] é o índice do jogador; [myScore] e [oppScore] são os placares dos lados.
 */
class Ai(val difficulty: Difficulty, private val random: Random = Random.Default) {

    fun chooseCard(h: Hand, me: Int): Card {
        val hand = h.hands[me]
        check(hand.isNotEmpty()) { "A mão do computador está vazia." }
        // Na mão de ferro ninguém vê as próprias cartas.
        if (h.blind) return hand[random.nextInt(hand.size)]
        return when (difficulty) {
            Difficulty.FACIL -> easyCard(h, me)
            Difficulty.MEDIO -> mediumCard(h, me)
            Difficulty.DIFICIL -> hardCard(h, me)
        }
    }

    fun shouldRaise(h: Hand, me: Int, myScore: Int, oppScore: Int): Boolean {
        if (!h.canRaise(me)) return false
        val next = nextRaise(h.value) ?: return false
        val confidence = confidence(h, me, myScore, oppScore)
        val threshold = RAISE.getValue(difficulty).getValue(next)
        val pressure = (if (oppScore > myScore) 0.06 else 0.0) +
            (if (myScore + next >= WINNING_SCORE) 0.05 else 0.0)
        var bluff = BLUFF.getValue(difficulty)
        if (h.value > 1) bluff *= if (next == 6) 0.25 else 0.0
        if (confidence >= threshold - pressure) return true
        return confidence >= 0.35 && random.nextDouble() < bluff + pressure / 2
    }

    fun respond(h: Hand, me: Int, myScore: Int, oppScore: Int): Response {
        val p = h.pending ?: return Response.ACEITAR
        val confidence = confidence(h, me, myScore, oppScore)
        val requested = p.value
        val accept = ACCEPT.getValue(difficulty).getValue(requested)
        val escalate = ESCALATE.getValue(difficulty).getValue(requested)
        val pressure = if (oppScore >= 9) 0.06 else 0.0
        if (h.canEscalate(h.sideOf(me)) && confidence >= escalate - pressure) return Response.AUMENTAR
        if (confidence >= accept - pressure) return Response.ACEITAR
        val desperation = if (myScore + requested >= WINNING_SCORE) 0.10 else 0.0
        if (confidence >= accept - pressure - desperation) return Response.ACEITAR
        return Response.CORRER
    }

    /** Na mão de onze: true para jogar, false para correr. */
    fun playEleven(h: Hand, me: Int, myScore: Int, oppScore: Int): Boolean {
        var threshold = ELEVEN.getValue(difficulty)
        // Se perder a mão de onze dá a partida ao adversário, fica mais cauteloso.
        if (oppScore + 3 >= WINNING_SCORE) threshold += 0.08
        return confidence(h, me, myScore, oppScore) >= threshold
    }

    internal fun confidence(h: Hand, me: Int, myScore: Int, oppScore: Int): Double {
        val hand = h.hands[me]
        if (hand.isEmpty()) return 0.0
        val unseen = unseen(h, me)
        val opp = opponentCards(h, me)
        val combined = hand.map {
            probabilityBeatsRandomHand(it, h.vira, unseen, opp) * 0.65 + normalized(it, h.vira) * 0.35
        }.sortedDescending()
        var base = 0.0
        WEIGHTS.zip(combined).forEach { (w, s) -> base += w * s }
        val (mine, theirs) = trickWins(h, me)
        val stateBonus = when {
            mine > theirs -> 0.10
            theirs > mine -> -0.08
            else -> 0.0
        }
        val scoreContext = (myScore - oppScore) / 12.0
        return (base + stateBonus + scoreContext * 0.05).coerceIn(0.0, 1.0)
    }

    private fun easyCard(h: Hand, me: Int): Card {
        val hand = h.hands[me]
        val target = opponentBest(h, me)
        if (target != null) {
            val beating = hand.filter { it.strength(h.vira) > target.power(h.vira) }
            if (beating.isNotEmpty() && random.nextDouble() < 0.45) {
                return beating.minBy { it.strength(h.vira) }
            }
        }
        return hand[random.nextInt(hand.size)]
    }

    private fun mediumCard(h: Hand, me: Int): Card {
        val v = h.vira
        val sorted = h.hands[me].sortedBy { it.strength(v) }
        if (partnerWinning(h, me)) return sorted.first()
        val target = opponentBest(h, me)
        if (target != null) {
            return sorted.firstOrNull { it.strength(v) > target.power(v) } ?: sorted.first()
        }
        val (mine, theirs) = trickWins(h, me)
        return when {
            mine > theirs -> sorted.first()
            theirs > mine -> sorted.last()
            else -> sorted[sorted.size / 2]
        }
    }

    private fun hardCard(h: Hand, me: Int): Card {
        val v = h.vira
        val hand = h.hands[me]
        if (partnerWinning(h, me)) return hand.minBy { it.strength(v) }
        val unseen = unseen(h, me)
        val opp = opponentCards(h, me)
        val (mine, theirs) = trickWins(h, me)
        val target = opponentBest(h, me)
        var best = hand[0]
        var bestScore = Double.NEGATIVE_INFINITY
        for (card in hand) {
            val immediate = if (target != null) {
                val cmp = card.strength(v).compareTo(target.power(v))
                when {
                    cmp > 0 -> 1.0
                    cmp == 0 -> 0.55
                    else -> 0.05
                }
            } else {
                probabilityBeatsRandomHand(card, v, unseen, opp)
            }
            val rest = hand - card
            val preserved = if (rest.isEmpty()) 0.0 else
                rest.map { probabilityBeatsRandomHand(it, v, unseen, maxOf(0, opp - 1)) }.average()
            val s = normalized(card, v)
            var score = immediate * 0.65 + preserved * 0.35
            if (mine > theirs) score -= s * 0.15 else if (theirs > mine) score += s * 0.12
            if (card.isManilha(v) && mine == theirs) score -= 0.05
            if (score > bestScore) {
                bestScore = score
                best = card
            }
        }
        return best
    }

    private fun opponentBest(h: Hand, me: Int): PlayedCard? =
        h.table.filter { h.sideOf(it.player) != h.sideOf(me) }.maxByOrNull { it.power(h.vira) }

    /** Com duplas: a carta do parceiro já está ganhando a rodada. */
    private fun partnerWinning(h: Hand, me: Int): Boolean {
        val partner = h.table.filter { h.sideOf(it.player) == h.sideOf(me) }
            .maxOfOrNull { it.power(h.vira) } ?: return false
        val opp = opponentBest(h, me)?.power(h.vira) ?: return false
        return partner > opp
    }

    private fun trickWins(h: Hand, me: Int): Pair<Int, Int> {
        val side = h.sideOf(me)
        return h.tricks.count { it.winnerSide == side } to
            h.tricks.count { it.winnerSide != null && it.winnerSide != side }
    }

    private fun opponentCards(h: Hand, me: Int): Int =
        (0 until h.players).filter { h.sideOf(it) != h.sideOf(me) }.sumOf { h.hands[it].size }

    /** Cartas que este jogador ainda não viu. Cartas cobertas dos outros continuam desconhecidas. */
    private fun unseen(h: Hand, me: Int): List<Card> {
        val known = HashSet<Card>()
        known += h.vira
        known += h.hands[me]
        (h.tricks.flatMap { it.plays } + h.table).forEach {
            if (!it.covered || it.player == me) known += it.card
        }
        return Card.ALL.filter { it !in known }
    }

    private fun normalized(card: Card, vira: Card): Double =
        if (card.isManilha(vira)) 0.82 + card.suit.ordinal * 0.06 else (card.rank.ordinal + 1) / 12.0

    private companion object {
        val RAISE = mapOf(
            Difficulty.FACIL to mapOf(3 to 0.83, 6 to 0.94, 9 to 0.99, 12 to 1.01),
            Difficulty.MEDIO to mapOf(3 to 0.70, 6 to 0.82, 9 to 0.91, 12 to 0.98),
            Difficulty.DIFICIL to mapOf(3 to 0.60, 6 to 0.74, 9 to 0.84, 12 to 0.93),
        )
        val ACCEPT = mapOf(
            Difficulty.FACIL to mapOf(3 to 0.53, 6 to 0.69, 9 to 0.80, 12 to 0.90),
            Difficulty.MEDIO to mapOf(3 to 0.44, 6 to 0.58, 9 to 0.74, 12 to 0.86),
            Difficulty.DIFICIL to mapOf(3 to 0.35, 6 to 0.48, 9 to 0.64, 12 to 0.77),
        )
        val ESCALATE = mapOf(
            Difficulty.FACIL to mapOf(3 to 0.93, 6 to 0.99, 9 to 9.99, 12 to 9.99),
            Difficulty.MEDIO to mapOf(3 to 0.85, 6 to 0.93, 9 to 9.99, 12 to 9.99),
            Difficulty.DIFICIL to mapOf(3 to 0.76, 6 to 0.84, 9 to 0.92, 12 to 9.99),
        )
        val BLUFF = mapOf(Difficulty.FACIL to 0.03, Difficulty.MEDIO to 0.07, Difficulty.DIFICIL to 0.10)
        val ELEVEN = mapOf(Difficulty.FACIL to 0.35, Difficulty.MEDIO to 0.45, Difficulty.DIFICIL to 0.50)
        val WEIGHTS = listOf(0.5, 0.3, 0.2)
    }
}

package com.pdonizete.truco.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Sorteia sempre quase 1.0: o computador nunca blefa. */
private object NoBluffRandom : Random() {
    override fun nextBits(bitCount: Int): Int = if (bitCount == 0) 0 else -1 ushr (32 - bitCount)
}

class AiTest {
    private fun raiseHand(value: Int) =
        Hand(2, 1, listOf(cards("Kp Ap 2p"), cards("5o 6o 5c")), c("4o")).also { it.value = value }

    @Test fun raiseThresholdGrowsWithHandValue() {
        val ai = Ai(Difficulty.MEDIO, NoBluffRandom)
        assertTrue(ai.shouldRaise(raiseHand(1), 1, 0, 0))
        assertFalse(ai.shouldRaise(raiseHand(3), 1, 0, 0))
    }

    @Test fun mediumBeatsWithCheapestCard() {
        val h = Hand(2, 0, listOf(cards("Kc 4c 5c"), cards("3e 2e 4e")), c("7o"))
        h.play(0, c("Kc"))
        assertEquals(c("2e"), Ai(Difficulty.MEDIO, Random(1)).chooseCard(h, 1))
    }

    @Test fun mediumDumpsWeakestWhenItCannotWin() {
        val h = Hand(2, 0, listOf(cards("3c 4c 5c"), cards("Ke 4e Ae")), c("7o"))
        h.play(0, c("3c"))
        assertEquals(c("4e"), Ai(Difficulty.MEDIO, Random(1)).chooseCard(h, 1))
    }

    @Test fun maoDeOnzeDecisionFollowsHandStrength() {
        val ai = Ai(Difficulty.MEDIO, Random(1))
        val strong = Hand(2, 0, listOf(cards("Ap Ac 3e"), cards("4o 5o 6o")), c("Ko"), HandMode.MAO_DE_ONZE, 0)
        val weak = Hand(2, 0, listOf(cards("4c 5e 6o"), cards("3o 2o Ae")), c("Ko"), HandMode.MAO_DE_ONZE, 0)
        assertTrue(ai.playEleven(strong, 0, 11, 5))
        assertFalse(ai.playEleven(weak, 0, 11, 5))
    }

    @Test fun aiVsAiMatchesFinishConsistently() {
        val levels = Difficulty.entries
        repeat(600) { seed ->
            val m = Match(random = Random(seed), coveredAllowed = seed % 2 == 0)
            val ais = listOf(
                Ai(levels[seed % 3], Random(seed * 7 + 1)),
                Ai(levels[(seed / 3) % 3], Random(seed * 7 + 2)),
            )
            var handsPlayed = 0
            while (true) {
                val h = m.hand
                var steps = 0
                while (!h.isOver) {
                    assertTrue(steps++ < 100)
                    val pend = h.pending
                    when {
                        h.awaitingEleven -> {
                            val s = h.elevenSide!!
                            assertTrue(h.decideEleven(ais[s].playEleven(h, s, m.score[s], m.score[1 - s])))
                        }
                        pend != null -> {
                            val s = pend.responderSide
                            assertTrue(h.respond(s, ais[s].respond(h, s, m.score[s], m.score[1 - s])))
                        }
                        else -> {
                            val p = h.current
                            if (ais[p].shouldRaise(h, p, m.score[p], m.score[1 - p])) {
                                assertTrue(h.requestRaise(p))
                            } else {
                                assertTrue(h.play(p, ais[p].chooseCard(h, p)))
                            }
                        }
                    }
                    assertCardsConsistent(h)
                }
                val before = m.score.copyOf()
                val r = m.settle()
                assertEquals(before[r.winnerSide] + r.points, m.score[r.winnerSide])
                when (h.mode) {
                    HandMode.MAO_DE_FERRO -> assertEquals(1, r.points)
                    HandMode.MAO_DE_ONZE ->
                        assertEquals(if (r.reason == EndReason.CORREU_MAO_DE_ONZE) 1 else 3, r.points)
                    HandMode.NORMAL -> assertTrue(r.points in HAND_VALUES)
                }
                if (m.isOver) break
                assertTrue(handsPlayed++ < 30)
                m.nextHand()
            }
            val w = m.winnerSide!!
            assertTrue(m.score[w] >= WINNING_SCORE)
            assertTrue(m.score[1 - w] < WINNING_SCORE)
        }
    }

    private fun assertCardsConsistent(h: Hand) {
        val all = h.hands.flatten() + h.table.map { it.card } +
            h.tricks.flatMap { t -> t.plays.map { it.card } } + h.vira
        assertEquals(3 * h.players + 1, all.size)
        assertEquals(all.size, all.toSet().size)
    }
}

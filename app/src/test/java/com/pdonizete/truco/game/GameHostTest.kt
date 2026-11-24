package com.pdonizete.truco.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameHostTest {

    /** Confere que a visão e os eventos de cada lugar passam pela codificação sem perder nada. */
    private fun checkRoundTrip(host: GameHost, events: List<GameEvent>) {
        for (seat in 0 until host.players) {
            val v = host.view(seat)
            assertEquals(v, TableView.decode(v.encode()))
            assertEquals(host.match.hand.hands[seat], v.myCards)
            for (e in events) {
                val mine = e.forViewer(seat, host.players)
                assertEquals(mine, decodeEvent(mine.encode()))
            }
        }
    }

    @Test fun computerOnlyMatchesFinishAndEncodeLosslessly() {
        for (players in listOf(2, 4)) repeat(60) { seed ->
            val host = GameHost(players, emptySet(), Difficulty.entries[seed % 3], seed % 2 == 0, Random(seed))
            checkRoundTrip(host, host.start())
            checkRoundTrip(host, host.handStart())
            var guard = 0
            while (!host.match.isOver) {
                assertTrue(guard++ < 2000)
                val ev = host.cpuStep()
                if (ev == null) {
                    assertNotNull(host.match.hand.result)
                    checkRoundTrip(host, host.apply(0, Move.Next)!!)
                    checkRoundTrip(host, host.handStart())
                } else {
                    checkRoundTrip(host, ev)
                }
            }
            assertTrue(host.match.score.max() >= WINNING_SCORE)
        }
    }

    @Test fun viewIsRotatedToTheViewer() {
        val host = GameHost(4, emptySet(), Difficulty.MEDIO, false, Random(3))
        host.start(); host.handStart()
        val h = host.match.hand
        for (seat in 0..3) {
            val v = host.view(seat)
            assertEquals((h.current - seat + 4) % 4, v.current)
            assertEquals((h.starter - seat + 4) % 4, v.starter)
            assertEquals(List(4) { h.hands[(it + seat) % 4].size }, v.counts)
        }
    }

    @Test fun humanSeatWaitsAndInvalidMovesChangeNothing() {
        val host = GameHost(2, setOf(0), Difficulty.MEDIO, false, Random(9))
        host.start()
        assertNull(host.apply(0, Move.Raise)) // ainda dando as cartas
        host.handStart()
        while (host.cpuToAct() != null) host.cpuStep()
        val h = host.match.hand
        if (h.isOver || h.awaitingEleven) return
        val v = host.view(0)
        assertTrue(v.canPlay || v.askedMe)
        val before = host.view(0).encode()
        assertNull(host.apply(1, Move.Play(h.hands[1].first())))
        assertNull(host.apply(0, Move.Next))
        assertEquals(before, host.view(0).encode())
    }

    @Test fun coveredCardIsHiddenFromOthers() {
        var seen = false
        repeat(200) { seed ->
            if (seen) return@repeat
            val host = GameHost(2, setOf(0), Difficulty.FACIL, true, Random(seed))
            host.start(); host.handStart()
            var guard = 0
            while (!host.match.hand.isOver && guard++ < 50) {
                if (host.cpuStep() != null) continue
                val h = host.match.hand
                val v = host.view(0)
                val ev = when {
                    v.elevenMine -> host.apply(0, Move.Eleven(true))
                    v.askedMe -> host.apply(0, Move.Answer(Response.ACEITAR))
                    v.canPlay -> host.apply(0, Move.Play(h.hands[0].first(), covered = v.canCover))
                    else -> null
                } ?: break
                val played = ev.filterIsInstance<GameEvent.Played>().firstOrNull()
                if (played != null && played.covered) {
                    assertNull((played.forViewer(1, 2) as GameEvent.Played).card)
                    assertEquals(played.card, (played.forViewer(0, 2) as GameEvent.Played).card)
                    val other = host.view(1)
                    val mine = host.view(0)
                    if (other.table.isNotEmpty()) {
                        assertNull(other.table.last().card)
                        assertNotNull(mine.table.last().card)
                    }
                    seen = true
                }
            }
        }
        assertTrue(seen)
    }

    @Test fun narratorSpeaksFromTheViewersSide() {
        val host = GameHost(2, emptySet(), Difficulty.MEDIO, false, Random(1))
        host.start(); host.handStart()
        val v = host.view(0)
        val n = Narrator(Seats(2, listOf(null, "Ana")))
        val card = Card(Rank.SEVEN, Suit.COPAS)
        assertEquals("Ana pediu truco!", n.line(GameEvent.Raised(1, 3), v))
        assertEquals("Ana jogou uma carta coberta.", n.line(GameEvent.Played(1, null, true), v))
        assertEquals("Você aceitou. A mão vale 3 pontos.", n.line(GameEvent.Answered(0, true, 3), v))
        assertEquals("A segunda rodada cangou.", n.line(GameEvent.TrickEnd(1, null), v))
        assertEquals(
            "Você ganhou a mão e fez 3 pontos. Placar: você 5, Ana 2.",
            n.line(GameEvent.HandEnd(0, 3, EndReason.RODADAS, listOf(5, 2)), v),
        )
        val ev = GameEvent.HandEnd(0, 1, EndReason.CORREU, listOf(4, 7)).forViewer(1, 2) as GameEvent.HandEnd
        assertEquals(1, ev.winnerSide)
        assertEquals(listOf(7, 4), ev.score)
        assertTrue(n.line(GameEvent.Played(0, card, false), v).startsWith("Você jogou sete de copas"))
    }
}

package com.pdonizete.truco.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MatchTest {
    @Test fun handModeFollowsScore() {
        assertEquals(HandMode.NORMAL, Match(random = Random(1)).hand.mode)
        Match(random = Random(1), initialScore = intArrayOf(11, 4)).hand.let {
            assertEquals(HandMode.MAO_DE_ONZE, it.mode)
            assertEquals(0, it.elevenSide)
        }
        Match(random = Random(1), initialScore = intArrayOf(3, 11)).hand.let {
            assertEquals(HandMode.MAO_DE_ONZE, it.mode)
            assertEquals(1, it.elevenSide)
        }
        Match(random = Random(1), initialScore = intArrayOf(11, 11)).hand.let {
            assertEquals(HandMode.MAO_DE_FERRO, it.mode)
            assertNull(it.elevenSide)
        }
    }

    @Test fun settleAddsPointsAndStarterRotates() {
        val m = Match(random = Random(5), firstStarter = 0)
        assertEquals(0, m.hand.starter)
        assertThrows(IllegalStateException::class.java) { m.settle() }
        assertTrue(m.hand.requestRaise(0))
        assertTrue(m.hand.respond(1, Response.CORRER))
        assertEquals(1, m.settle().points)
        assertEquals(listOf(1, 0), m.score.toList())
        assertThrows(IllegalStateException::class.java) { m.settle() }
        assertEquals(1, m.nextHand().starter)
        assertNull(m.winnerSide)
    }

    @Test fun matchEndsAtTwelve() {
        val m = Match(random = Random(3), initialScore = intArrayOf(9, 9))
        val h = m.hand
        val p = h.current
        assertTrue(h.requestRaise(p))
        assertTrue(h.respond(1 - p, Response.AUMENTAR))
        assertTrue(h.respond(p, Response.CORRER))
        assertEquals(3, m.settle().points)
        assertEquals(12, m.score[1 - p])
        assertEquals(1 - p, m.winnerSide)
        assertTrue(m.isOver)
        assertThrows(IllegalStateException::class.java) { m.nextHand() }
    }

    @Test fun runningFromMaoDeOnzeKeepsNextHandAsMaoDeOnze() {
        val m = Match(random = Random(8), initialScore = intArrayOf(11, 4))
        assertTrue(m.hand.decideEleven(false))
        m.settle()
        assertEquals(listOf(11, 5), m.score.toList())
        assertEquals(HandMode.MAO_DE_ONZE, m.nextHand().mode)
    }
}

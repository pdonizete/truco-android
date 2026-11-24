package com.pdonizete.truco.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandTest {
    // Jogador 0 é você, jogador 1 é o computador. Com vira 7 de ouros, a manilha é a dama.
    private fun hand2(
        p0: String,
        p1: String,
        vira: String = "7o",
        starter: Int = 0,
        mode: HandMode = HandMode.NORMAL,
        eleven: Int? = null,
        covered: Boolean = false,
    ) = Hand(2, starter, listOf(cards(p0), cards(p1)), c(vira), mode, eleven, covered)

    // ---- Quem ganha a mão (mesmas regras de determine_hand_winner)

    @Test fun notDecidedBeforeSecondTrick() {
        assertNull(handWinner(emptyList(), 0))
        assertNull(handWinner(listOf(0), 0))
        assertNull(handWinner(listOf(null), 1))
    }

    @Test fun sameSideWinsFirstTwoTricks() {
        assertEquals(0, handWinner(listOf(0, 0), 1))
        assertEquals(1, handWinner(listOf(1, 1), 0))
    }

    @Test fun firstWinnerTakesHandWhenSecondTies() {
        assertEquals(0, handWinner(listOf(0, null), 1))
        assertEquals(1, handWinner(listOf(1, null), 0))
    }

    @Test fun secondTrickDecidesAfterFirstTie() {
        assertEquals(1, handWinner(listOf(null, 1), 0))
        assertEquals(0, handWinner(listOf(null, 0), 1))
    }

    @Test fun twoTiesThirdDecides() {
        assertNull(handWinner(listOf(null, null), 0))
        assertEquals(0, handWinner(listOf(null, null, 0), 1))
        assertEquals(1, handWinner(listOf(null, null, 1), 0))
    }

    @Test fun threeTiesGoToStarter() {
        assertEquals(1, handWinner(listOf(null, null, null), 1))
        assertEquals(0, handWinner(listOf(null, null, null), 0))
    }

    @Test fun splitTricksThirdDecides() {
        assertNull(handWinner(listOf(0, 1), 0))
        assertEquals(1, handWinner(listOf(0, 1, 1), 0))
        assertEquals(0, handWinner(listOf(1, 0, 0), 1))
    }

    @Test fun splitTricksThirdTieGoesToFirstWinner() {
        assertEquals(0, handWinner(listOf(0, 1, null), 1))
        assertEquals(1, handWinner(listOf(1, 0, null), 0))
    }

    @Test fun raiseSequenceAndRunPoints() {
        assertEquals(listOf(3, 6, 9, 12, null), listOf(1, 3, 6, 9, 12).map(::nextRaise))
        assertEquals(listOf(1, 3, 6, 9), listOf(3, 6, 9, 12).map(::refusalPoints))
    }

    // ---- Jogadas

    @Test fun firstTieThenSecondDecides() {
        val h = hand2("7c 3c 4c", "7e Ke 5e")
        assertTrue(h.play(0, c("7c")))
        assertTrue(h.play(1, c("7e")))
        assertNull(h.tricks[0].winnerSide)
        assertEquals(0, h.current) // cangou: começa quem é mão
        assertTrue(h.play(0, c("3c")))
        assertTrue(h.play(1, c("Ke")))
        assertEquals(HandResult(0, 1, EndReason.RODADAS), h.result)
    }

    @Test fun trickWinnerLeadsNextTrick() {
        val h = hand2("4c 5c 6c", "3e 2e Ae")
        h.play(0, c("4c")); h.play(1, c("3e"))
        assertEquals(1, h.tricks[0].winnerSide)
        assertEquals(1, h.current)
        assertTrue(h.play(1, c("2e")))
        assertTrue(h.play(0, c("5c")))
        assertEquals(HandResult(1, 1, EndReason.RODADAS), h.result)
    }

    @Test fun thirdTrickTieGoesToFirstTrickWinner() {
        val h = hand2("3c 4c 6c", "2e Ke 6e", starter = 0)
        h.play(0, c("3c")); h.play(1, c("2e"))
        h.play(0, c("4c")); h.play(1, c("Ke"))
        assertEquals(1, h.current)
        h.play(1, c("6e")); h.play(0, c("6c"))
        assertEquals(HandResult(0, 1, EndReason.RODADAS), h.result)
    }

    @Test fun threeTiedTricksGoToStarter() {
        val h = hand2("4c 5c 6c", "4e 5e 6e", starter = 1)
        for (r in listOf("4", "5", "6")) {
            assertEquals(1, h.current)
            assertTrue(h.play(1, c(r + "e")))
            assertTrue(h.play(0, c(r + "c")))
        }
        assertEquals(HandResult(1, 1, EndReason.RODADAS), h.result)
    }

    @Test fun turnOrderAndCardOwnershipAreEnforced() {
        val h = hand2("4c 5c 6c", "4e 5e 6e")
        assertFalse(h.play(1, c("4e")))
        assertFalse(h.play(0, c("4e")))
        assertTrue(h.play(0, c("4c")))
        assertFalse(h.play(0, c("5c")))
    }

    // ---- Truco, seis, nove e doze

    @Test fun trucoAcceptedAndNoChainedRaise() {
        val h = hand2("3c 2c Ac", "4e 5e 6e")
        assertTrue(h.canRaise(0))
        assertFalse(h.canRaise(1))
        assertTrue(h.requestRaise(0))
        assertEquals(RaiseRequest(0, 3), h.pending)
        assertFalse(h.play(0, c("3c")))
        assertFalse(h.respond(0, Response.ACEITAR))
        assertTrue(h.respond(1, Response.ACEITAR))
        assertEquals(3, h.value)
        assertNull(h.pending)
        assertFalse(h.canRaise(0))
        assertTrue(h.play(0, c("3c")))
        assertTrue(h.requestRaise(1))
        assertEquals(RaiseRequest(1, 6), h.pending)
        assertTrue(h.respond(0, Response.ACEITAR))
        assertEquals(6, h.value)
        assertFalse(h.canRaise(1))
    }

    @Test fun runningFromTrucoGivesOnePoint() {
        val h = hand2("3c 2c Ac", "4e 5e 6e")
        h.requestRaise(0)
        assertTrue(h.respond(1, Response.CORRER))
        assertEquals(HandResult(0, 1, EndReason.CORREU), h.result)
        assertFalse(h.play(0, c("3c")))
    }

    @Test fun runningFromSixGivesThreePoints() {
        val h = hand2("3c 2c Ac", "4e 5e 6e")
        h.requestRaise(0)
        assertTrue(h.respond(1, Response.AUMENTAR))
        assertEquals(RaiseRequest(1, 6), h.pending)
        assertEquals(1, h.value)
        assertTrue(h.respond(0, Response.CORRER))
        assertEquals(HandResult(1, 3, EndReason.CORREU), h.result)
    }

    @Test fun raisingStopsAtTwelveAndRunningFromTwelveGivesNine() {
        val h = hand2("3c 2c Ac", "4e 5e 6e")
        h.requestRaise(0)
        assertTrue(h.respond(1, Response.AUMENTAR))
        assertTrue(h.respond(0, Response.AUMENTAR))
        assertTrue(h.respond(1, Response.AUMENTAR))
        assertEquals(RaiseRequest(1, 12), h.pending)
        assertFalse(h.canEscalate(0))
        assertFalse(h.respond(0, Response.AUMENTAR))
        assertTrue(h.respond(0, Response.CORRER))
        assertEquals(HandResult(1, 9, EndReason.CORREU), h.result)
    }

    @Test fun acceptingEscalationKeepsTurnAndValueTwelveBlocksRaises() {
        val h = hand2("3c 2c Ac", "4e 5e 6e")
        h.requestRaise(0)
        h.respond(1, Response.AUMENTAR)
        h.respond(0, Response.AUMENTAR)
        h.respond(1, Response.AUMENTAR)
        assertTrue(h.respond(0, Response.ACEITAR))
        assertEquals(12, h.value)
        assertEquals(0, h.current)
        h.play(0, c("3c"))
        assertFalse(h.canRaise(1))
    }

    // ---- Mão de onze e mão de ferro

    @Test fun maoDeOnzePlayedIsWorthThreeWithoutTruco() {
        val h = hand2("Qc Qp 3c", "4e 5e 6e", mode = HandMode.MAO_DE_ONZE, eleven = 0)
        assertTrue(h.awaitingEleven)
        assertFalse(h.canPlay(0))
        assertFalse(h.play(0, c("Qc")))
        assertTrue(h.decideEleven(true))
        assertFalse(h.decideEleven(true))
        assertEquals(3, h.value)
        assertFalse(h.canRaise(0))
        h.play(0, c("Qc")); h.play(1, c("4e"))
        assertFalse(h.canRaise(0))
        h.play(0, c("Qp")); h.play(1, c("5e"))
        assertEquals(HandResult(0, 3, EndReason.RODADAS), h.result)
    }

    @Test fun runningFromMaoDeOnzeGivesOnePointToOpponent() {
        val h = hand2("4c 5c 6c", "4e 5e 6e", mode = HandMode.MAO_DE_ONZE, eleven = 1)
        assertTrue(h.decideEleven(false))
        assertEquals(HandResult(0, 1, EndReason.CORREU_MAO_DE_ONZE), h.result)
    }

    @Test fun maoDeFerroIsBlindWorthOneWithoutTruco() {
        val h = hand2("3c 2c Ac", "4e 5e 6e", mode = HandMode.MAO_DE_FERRO)
        assertTrue(h.blind)
        assertFalse(h.awaitingEleven)
        assertFalse(h.canRaise(0))
        h.play(0, c("3c")); h.play(1, c("4e"))
        h.play(0, c("2c")); h.play(1, c("5e"))
        assertEquals(HandResult(0, 1, EndReason.RODADAS), h.result)
    }

    // ---- Carta coberta

    @Test fun coveredCardOnlyFromSecondTrickAndLosesToAnything() {
        val h = hand2("3c 2c 4c", "4e 5e 6e", covered = true)
        assertFalse(h.play(0, c("3c"), covered = true))
        h.play(0, c("3c")); h.play(1, c("4e"))
        assertTrue(h.canCover(0))
        assertTrue(h.play(0, c("2c"), covered = true))
        h.play(1, c("5e"))
        assertEquals(1, h.tricks[1].winnerSide)
        h.play(1, c("6e")); h.play(0, c("4c"))
        assertEquals(HandResult(1, 1, EndReason.RODADAS), h.result)
    }

    @Test fun coveredCardNotAllowedWhenOptionIsOff() {
        val h = hand2("3c 2c 4c", "4e 5e 6e")
        h.play(0, c("3c")); h.play(1, c("4e"))
        assertFalse(h.canCover(0))
        assertFalse(h.play(0, c("2c"), covered = true))
    }

    // ---- Duplas

    @Test fun fourPlayersTeammatesTieWinsOpponentsTieCangou() {
        val h = Hand(4, 0, listOf(cards("3c 4c 5c"), cards("2e Ke 6e"), cards("3o 5o 6o"), cards("Ap Kp 5p")), c("7o"))
        h.play(0, c("3c")); h.play(1, c("2e")); h.play(2, c("3o")); h.play(3, c("Ap"))
        assertEquals(Trick(h.tricks[0].plays, 0, 0), h.tricks[0])
        assertEquals(0, h.current)
        h.play(0, c("4c")); h.play(1, c("Ke")); h.play(2, c("5o")); h.play(3, c("Kp"))
        assertEquals(1, h.tricks[1].winnerSide)
        assertEquals(1, h.current)
        h.play(1, c("6e")); h.play(2, c("6o")); h.play(3, c("5p")); h.play(0, c("5c"))
        assertNull(h.tricks[2].winnerSide)
        assertEquals(HandResult(0, 1, EndReason.RODADAS), h.result)
    }

    @Test fun fourPlayersRaisesAlternateBetweenPairs() {
        val h = Hand(4, 0, listOf(cards("3c 4c 5c"), cards("2e Ke 6e"), cards("3o 5o 6o"), cards("Ap Kp 5p")), c("7o"))
        assertTrue(h.requestRaise(0))
        assertTrue(h.respond(1, Response.ACEITAR))
        h.play(0, c("3c"))
        assertTrue(h.canRaise(1))
        h.play(1, c("2e"))
        assertFalse(h.canRaise(2)) // a dupla do jogador 2 pediu por último
        h.play(2, c("3o"))
        assertTrue(h.requestRaise(3))
        assertEquals(RaiseRequest(1, 6), h.pending)
        assertFalse(h.respond(1, Response.ACEITAR))
        assertTrue(h.respond(0, Response.CORRER))
        assertEquals(HandResult(1, 3, EndReason.CORREU), h.result)
    }
}

package com.pdonizete.truco.game

import org.junit.Assert.assertEquals
import org.junit.Test

class SeatsTest {
    @Test fun namesInOneOnOne() {
        val s = Seats(2)
        assertEquals(listOf("Você", "O computador"), (0..1).map(s::who))
        assertEquals("Você", s.sideWho(0))
        assertEquals("O computador", s.sideWho(1))
        assertEquals("ganhou", s.verb(1, "ganhou", "ganharam"))
        assertEquals("do computador", s.of(1))
        assertEquals("o computador", s.them)
    }

    @Test fun namesInPairs() {
        val s = Seats(4)
        assertEquals(
            listOf("Você", "O adversário da direita", "O parceiro", "O adversário da esquerda"),
            (0..3).map(s::who),
        )
        assertEquals(listOf("Você", "Direita", "Parceiro", "Esquerda"), (0..3).map(s::shortName))
        assertEquals("Sua dupla", s.sideWho(0))
        assertEquals("Os adversários", s.sideWho(1))
        assertEquals("ganhou", s.verb(0, "ganhou", "ganharam"))
        assertEquals("ganharam", s.verb(1, "ganhou", "ganharam"))
        assertEquals("do parceiro", s.of(2))
        assertEquals("os adversários", s.them)
    }
}

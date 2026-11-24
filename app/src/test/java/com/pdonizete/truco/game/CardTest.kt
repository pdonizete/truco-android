package com.pdonizete.truco.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CardTest {
    @Test fun deckHas40UniqueCardsWithout8_9_10() {
        assertEquals(40, Card.ALL.size)
        assertEquals(40, Card.ALL.toSet().size)
        assertEquals(listOf("4", "5", "6", "7", "Q", "J", "K", "A", "2", "3"), Rank.entries.map { it.symbol })
        Card.ALL.forEach { assertEquals(it, Card.fromId(it.id)) }
    }

    @Test fun deckDealsEveryCardOnceAndRepeatsWithSameSeed() {
        val d = Deck(Random(42))
        val all = d.draw(40)
        assertEquals(40, all.toSet().size)
        assertEquals(0, d.size)
        assertThrows(IllegalStateException::class.java) { d.draw() }
        assertEquals(Deck(Random(7)).draw(40), Deck(Random(7)).draw(40))
    }

    @Test fun commonCardsFollowOrderForEveryVira() {
        for (vira in Card.ALL) {
            val commons = Rank.entries.filter { it != manilhaOf(vira) }.map { Card(it, Suit.COPAS) }
            commons.zipWithNext().forEach { (a, b) ->
                assertEquals("$a < $b com vira $vira", -1, compareCards(a, b, vira))
                assertEquals(1, compareCards(b, a, vira))
            }
        }
    }

    @Test fun manilhaFromEachVira() {
        val expected = mapOf(
            "4" to "5", "5" to "6", "6" to "7", "7" to "Q", "Q" to "J",
            "J" to "K", "K" to "A", "A" to "2", "2" to "3", "3" to "4",
        )
        for ((v, m) in expected) for (s in listOf("o", "e", "c", "p")) {
            assertEquals("vira $v$s", m, manilhaOf(c(v + s)).symbol)
        }
    }

    @Test fun manilhaBeatsEveryCommonCard() {
        for (vira in Card.ALL) {
            val manilhas = Card.ALL.filter { it.isManilha(vira) }
            assertEquals(4, manilhas.size)
            for (m in manilhas) for (x in Card.ALL.filter { !it.isManilha(vira) }) {
                assertEquals("$m > $x com vira $vira", 1, compareCards(m, x, vira))
            }
        }
    }

    @Test fun manilhasOrderedBySuitAndZapIsPaus() {
        for (vira in Card.ALL) {
            val bySuit = Card.ALL.filter { it.isManilha(vira) }.sortedBy { it.strength(vira) }.map { it.suit }
            assertEquals(listOf(Suit.OUROS, Suit.ESPADAS, Suit.COPAS, Suit.PAUS), bySuit)
            assertEquals(listOf(Card(manilhaOf(vira), Suit.PAUS)), Card.ALL.filter { it.isZap(vira) })
        }
        // Vira 3: a manilha é o 4.
        val vira = c("3o")
        assertEquals(1, compareCards(c("4p"), c("4c"), vira))
        assertEquals(1, compareCards(c("4c"), c("4e"), vira))
        assertEquals(1, compareCards(c("4e"), c("4o"), vira))
        assertEquals(1, compareCards(c("4o"), c("3p"), vira))
        assertFalse(c("3p").isManilha(vira))
    }

    @Test fun equalCommonCardsTie() {
        val vira = c("5c")
        assertEquals(0, compareCards(c("7o"), c("7p"), vira))
        assertEquals(0, compareCards(c("3c"), c("3e"), vira))
        for (r in Rank.entries.filter { it != manilhaOf(vira) }) {
            assertEquals(0, compareCards(Card(r, Suit.OUROS), Card(r, Suit.PAUS), vira))
        }
    }

    @Test fun spokenNames() {
        assertEquals("Sete de copas", Words.card(c("7c"), c("5o")))
        assertEquals("Quatro de paus, zap", Words.card(c("4p"), c("3o")))
        assertEquals("Ás de espadas, manilha", Words.card(c("Ae"), c("Ko")))
        assertEquals("sete de copas", Words.cardLower(c("7c"), c("5o")))
        assertEquals("Dama de ouros", Words.card(c("Qo")))
        assertEquals(listOf("truco", "seis", "nove", "doze"), listOf(3, 6, 9, 12).map(Words::raiseName))
        assertTrue(Card.ALL.map { Words.card(it) }.toSet().size == 40)
    }
}

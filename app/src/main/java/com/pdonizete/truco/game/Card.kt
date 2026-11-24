package com.pdonizete.truco.game

import kotlin.math.sign

/** Naipes na ordem de força entre manilhas: ouros < espadas < copas < paus. */
enum class Suit(val label: String) {
    OUROS("ouros"),
    ESPADAS("espadas"),
    COPAS("copas"),
    PAUS("paus"),
}

/** Valores na ordem de força das cartas comuns: 4 < 5 < 6 < 7 < Q < J < K < A < 2 < 3. */
enum class Rank(val symbol: String) {
    FOUR("4"), FIVE("5"), SIX("6"), SEVEN("7"),
    QUEEN("Q"), JACK("J"), KING("K"), ACE("A"),
    TWO("2"), THREE("3");

    /** Valor seguinte na sequência; depois do 3 volta para o 4. */
    val next: Rank get() = entries[(ordinal + 1) % entries.size]
}

data class Card(val rank: Rank, val suit: Suit) {
    /** Número de 0 a 39, usado para codificar a carta (por exemplo no Bluetooth). */
    val id: Int get() = suit.ordinal * Rank.entries.size + rank.ordinal

    override fun toString() = "${rank.symbol}${suit.label.first()}"

    companion object {
        /** As 40 cartas do baralho de truco, sem 8, 9, 10 e coringas. */
        val ALL: List<Card> = Suit.entries.flatMap { s -> Rank.entries.map { r -> Card(r, s) } }

        fun fromId(id: Int): Card = ALL[id]
    }
}

/** Valor da manilha: o seguinte ao da vira. */
fun manilhaOf(vira: Card): Rank = vira.rank.next

fun Card.isManilha(vira: Card): Boolean = rank == manilhaOf(vira)

/** A manilha de paus. */
fun Card.isZap(vira: Card): Boolean = isManilha(vira) && suit == Suit.PAUS

/**
 * Força da carta com esta vira. Cartas comuns vão de 0 (4) a 9 (3).
 * Manilhas valem 100 + naipe, então ganham de qualquer carta comum.
 */
fun Card.strength(vira: Card): Int =
    if (isManilha(vira)) 100 + suit.ordinal else rank.ordinal

/** 1 se [a] ganha, -1 se [b] ganha, 0 se empatam (a rodada cangou). */
fun compareCards(a: Card, b: Card, vira: Card): Int =
    (a.strength(vira) - b.strength(vira)).sign

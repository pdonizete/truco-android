package com.pdonizete.truco.game

/** "7c" é o Sete de copas. O naipe é a primeira letra: o, e, c, p. */
fun c(s: String): Card = Card(
    Rank.entries.first { it.symbol == s.dropLast(1) },
    Suit.entries.first { it.label.first() == s.last() },
)

fun cards(s: String): List<Card> = s.split(" ").map(::c)

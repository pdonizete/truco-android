package com.pdonizete.truco.game

import kotlin.random.Random

/** Baralho de 40 cartas, embaralhado com o [random] dado (semente fixa nos testes). */
class Deck(random: Random = Random.Default) {
    private val cards: ArrayDeque<Card> = ArrayDeque(Card.ALL.shuffled(random))

    val size: Int get() = cards.size

    fun draw(): Card = cards.removeFirstOrNull()
        ?: throw IllegalStateException("Não há cartas suficientes no baralho.")

    fun draw(count: Int): List<Card> {
        require(count >= 1) { "A quantidade de cartas deve ser positiva." }
        check(count <= cards.size) { "Não há cartas suficientes no baralho." }
        return List(count) { draw() }
    }
}

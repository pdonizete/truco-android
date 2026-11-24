package com.pdonizete.truco.game

/**
 * Nomes falados dos lugares da mesa. Você é sempre o jogador 0.
 * Com 4 jogadores a ordem é: você (0), adversário da direita (1), parceiro (2), adversário da esquerda (3).
 * As duplas são 0 e 2 (lado 0) contra 1 e 3 (lado 1).
 */
class Seats(val players: Int) {
    init {
        require(players == 2 || players == 4) { "O truco tem 2 ou 4 jogadores." }
    }

    val duo: Boolean get() = players == 4

    /** Nome sem artigo: "computador", "parceiro", "adversário da direita". */
    fun noun(p: Int): String = when {
        p == 0 -> "você"
        !duo -> "computador"
        p == 1 -> "adversário da direita"
        p == 2 -> "parceiro"
        else -> "adversário da esquerda"
    }

    /** Sujeito no começo da frase: "Você", "O parceiro". */
    fun who(p: Int): String = if (p == 0) "Você" else "O ${noun(p)}"

    /** "do parceiro", para "vez do parceiro". */
    fun of(p: Int): String = if (p == 0) "sua" else "do ${noun(p)}"

    /** Rótulo curto para a mesa desenhada. */
    fun shortName(p: Int): String = when {
        p == 0 -> "Você"
        !duo -> "Computador"
        p == 1 -> "Direita"
        p == 2 -> "Parceiro"
        else -> "Esquerda"
    }

    /** Sujeito de um lado: "Você", "O computador", "Sua dupla", "Os adversários". */
    fun sideWho(side: Int): String = when {
        !duo -> who(if (side == 0) 0 else 1)
        side == 0 -> "Sua dupla"
        else -> "Os adversários"
    }

    /** Verbo concordando com [sideWho]: só "Os adversários" pede plural. */
    fun verb(side: Int, singular: String, plural: String): String = if (duo && side != 0) plural else singular

    /** O outro lado como objeto: "o computador" ou "os adversários". */
    val them: String get() = if (duo) "os adversários" else "o computador"
    val ofThem: String get() = if (duo) "dos adversários" else "do computador"
    fun themVerb(singular: String, plural: String): String = if (duo) plural else singular

    /** Nome do lado no placar desenhado e no placar falado. */
    fun sideShort(side: Int): String = if (!duo) (if (side == 0) "Você" else "Computador") else (if (side == 0) "Nós" else "Eles")
    fun sideSpoken(side: Int): String = if (!duo) (if (side == 0) "você" else "computador") else (if (side == 0) "sua dupla" else "adversários")
}

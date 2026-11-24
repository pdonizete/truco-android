package com.pdonizete.truco.game

/**
 * Nomes falados dos lugares da mesa, do ponto de vista de quem ouve (o jogador 0).
 * Com 4 jogadores a ordem é: você (0), adversário da direita (1), parceiro (2), adversário da esquerda (3).
 * As duplas são 0 e 2 (lado 0) contra 1 e 3 (lado 1).
 * [names] traz o nome de cada lugar no jogo pelo Bluetooth; null é o computador.
 */
class Seats(val players: Int, names: List<String?> = emptyList()) {
    init {
        require(players == 2 || players == 4) { "O truco tem 2 ou 4 jogadores." }
    }

    private val names: List<String?> =
        List(players) { p -> names.getOrNull(p)?.trim()?.takeIf { p != 0 && it.isNotEmpty() } }

    val duo: Boolean get() = players == 4

    private fun role(p: Int): String = when {
        !duo -> "computador"
        p == 1 -> "adversário da direita"
        p == 2 -> "parceiro"
        else -> "adversário da esquerda"
    }

    /** Nome sem artigo: "computador", "parceiro", "Ana". */
    fun noun(p: Int): String = if (p == 0) "você" else names[p] ?: role(p)

    /** Sujeito no começo da frase: "Você", "O parceiro", "Ana". */
    fun who(p: Int): String = if (p == 0) "Você" else names[p] ?: "O ${role(p)}"

    /** "do parceiro" ou "de Ana", para "vez do parceiro". */
    fun of(p: Int): String = if (p == 0) "sua" else names[p]?.let { "de $it" } ?: "do ${role(p)}"

    /** Rótulo curto para a mesa desenhada. */
    fun shortName(p: Int): String = when {
        p == 0 -> "Você"
        names[p] != null -> names[p]!!
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

    /** O outro lado como objeto: "o computador", "Ana" ou "os adversários". */
    val them: String get() = if (duo) "os adversários" else names[1] ?: "o computador"
    val ofThem: String get() = if (duo) "dos adversários" else names[1]?.let { "de $it" } ?: "do computador"
    fun themVerb(singular: String, plural: String): String = if (duo) plural else singular

    /** Nome do lado no placar desenhado e no placar falado. */
    fun sideShort(side: Int): String =
        if (!duo) (if (side == 0) "Você" else shortName(1)) else (if (side == 0) "Nós" else "Eles")

    fun sideSpoken(side: Int): String =
        if (!duo) (if (side == 0) "você" else names[1] ?: "computador") else (if (side == 0) "sua dupla" else "adversários")
}

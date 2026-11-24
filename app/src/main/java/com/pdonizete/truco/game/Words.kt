package com.pdonizete.truco.game

/** Textos falados em português do Brasil. Sem dependência do Android. */
object Words {
    fun rankName(rank: Rank): String = when (rank) {
        Rank.FOUR -> "Quatro"
        Rank.FIVE -> "Cinco"
        Rank.SIX -> "Seis"
        Rank.SEVEN -> "Sete"
        Rank.QUEEN -> "Dama"
        Rank.JACK -> "Valete"
        Rank.KING -> "Rei"
        Rank.ACE -> "Ás"
        Rank.TWO -> "Dois"
        Rank.THREE -> "Três"
    }

    /** "Sete de copas". */
    fun card(card: Card): String = "${rankName(card.rank)} de ${card.suit.label}"

    /** "Quatro de paus, zap", "Ás de espadas, manilha" ou só "Sete de copas". */
    fun card(card: Card, vira: Card?): String = when {
        vira == null -> card(card)
        card.isZap(vira) -> "${card(card)}, zap"
        card.isManilha(vira) -> "${card(card)}, manilha"
        else -> card(card)
    }

    /** Mesma carta no meio da frase: "sete de copas". */
    fun cardLower(card: Card, vira: Card?): String = card(card, vira).replaceFirstChar { it.lowercase() }

    /** Nome do pedido que leva a mão a [value]: truco, seis, nove ou doze. */
    fun raiseName(value: Int): String = when (value) {
        3 -> "truco"
        6 -> "seis"
        9 -> "nove"
        12 -> "doze"
        else -> value.toString()
    }

    fun trickOrdinal(index: Int): String = when (index) {
        0 -> "primeira"
        1 -> "segunda"
        else -> "terceira"
    }

    fun points(n: Int): String = if (n == 1) "1 ponto" else "$n pontos"

    /** Nome do valor da manilha, por exemplo "Ás". */
    fun manilha(vira: Card): String = rankName(manilhaOf(vira))
}

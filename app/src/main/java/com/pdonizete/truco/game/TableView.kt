package com.pdonizete.truco.game

/** Carta na mesa vista por um jogador. [card] é null quando é a carta coberta de outro jogador. */
data class PlayView(val player: Int, val card: Card?, val covered: Boolean)

/** Rodada completa vista por um jogador. [winnerPlayer] é null quando cangou. */
data class TrickView(val plays: List<PlayView>, val winnerPlayer: Int?) {
    val winnerSide: Int? get() = winnerPlayer?.rem(2)
}

/**
 * O que um jogador pode ver da partida. Os lugares são contados a partir dele: ele é o jogador 0
 * e o lado dele é o lado 0. Traz só as cartas dele (e as do parceiro na mão de onze da dupla).
 * É o que o anfitrião manda pelo Bluetooth e o que a tela desenha.
 */
data class TableView(
    val players: Int,
    /** Placar: [0] é o seu lado, [1] o outro. */
    val score: List<Int>,
    val value: Int,
    val mode: HandMode,
    val elevenSide: Int?,
    val awaitingEleven: Boolean,
    val vira: Card,
    val starter: Int,
    val current: Int,
    val pending: RaiseRequest?,
    val myCards: List<Card>,
    /** Quantas cartas cada jogador tem na mão. */
    val counts: List<Int>,
    val table: List<PlayView>,
    val tricks: List<TrickView>,
    val result: HandResult?,
    val canPlay: Boolean,
    val canCover: Boolean,
    val canRaise: Boolean,
    val canEscalate: Boolean,
    val matchOver: Boolean,
    val winnerSide: Int?,
    val partnerCards: List<Card>,
    val dealing: Boolean,
) {
    val blind: Boolean get() = mode == HandMode.MAO_DE_FERRO
    val handOver: Boolean get() = result != null
    val duo: Boolean get() = players == 4

    /** Há um pedido esperando a resposta do seu lado. */
    val askedMe: Boolean get() = !handOver && pending?.responderSide == 0

    /** O seu lado está com 11 e precisa decidir a mão de onze. */
    val elevenMine: Boolean get() = awaitingEleven && elevenSide == 0

    fun encode(): String = listOf<Any>(
        players, score.joinToString(","), value, mode.name, elevenSide ?: -1, flag(awaitingEleven),
        vira.id, starter, current, pending?.let { "${it.requesterSide},${it.value}" } ?: "",
        encodeCards(myCards), counts.joinToString(","), encodePlays(table),
        tricks.joinToString("/") { "${it.winnerPlayer ?: -1}:${encodePlays(it.plays)}" },
        result?.let { "${it.winnerSide},${it.points},${it.reason.name}" } ?: "",
        flag(canPlay), flag(canCover), flag(canRaise), flag(canEscalate), flag(matchOver), winnerSide ?: -1,
        encodeCards(partnerCards), flag(dealing),
    ).joinToString(";")

    companion object {
        private const val FIELDS = 23

        /** Visão da partida [m] a partir do lugar [seat]. */
        fun of(m: Match, seat: Int, dealing: Boolean): TableView {
            val h = m.hand
            val n = h.players
            val mySide = h.sideOf(seat)
            fun rp(p: Int) = (p - seat + n) % n
            fun rs(s: Int) = s xor mySide
            fun play(p: PlayedCard) = PlayView(rp(p.player), if (p.covered && p.player != seat) null else p.card, p.covered)
            return TableView(
                players = n,
                score = listOf(m.score[mySide], m.score[1 - mySide]),
                value = h.value,
                mode = h.mode,
                elevenSide = h.elevenSide?.let(::rs),
                awaitingEleven = h.awaitingEleven,
                vira = h.vira,
                starter = rp(h.starter),
                current = rp(h.current),
                pending = h.pending?.let { RaiseRequest(rs(it.requesterSide), it.value) },
                myCards = h.hands[seat].toList(),
                counts = List(n) { h.hands[(it + seat) % n].size },
                table = h.table.map(::play),
                tricks = h.tricks.map { t -> TrickView(t.plays.map(::play), t.winnerPlayer?.let(::rp)) },
                result = h.result?.let { it.copy(winnerSide = rs(it.winnerSide)) },
                canPlay = !dealing && h.canPlay(seat),
                canCover = !dealing && h.canCover(seat) && !h.blind,
                canRaise = !dealing && h.canRaise(seat),
                canEscalate = !dealing && h.canEscalate(mySide),
                matchOver = m.isOver,
                winnerSide = m.winnerSide?.let(::rs),
                partnerCards = if (n == 4 && h.awaitingEleven && h.elevenSide == mySide) h.hands[(seat + 2) % 4].toList() else emptyList(),
                dealing = dealing,
            )
        }

        fun decode(s: String): TableView {
            val f = s.split(';')
            require(f.size == FIELDS) { "Visão inválida." }
            val pend = f[9].takeIf { it.isNotEmpty() }?.split(',')?.let { RaiseRequest(it[0].toInt(), it[1].toInt()) }
            val res = f[14].takeIf { it.isNotEmpty() }?.split(',')
                ?.let { HandResult(it[0].toInt(), it[1].toInt(), EndReason.valueOf(it[2])) }
            return TableView(
                players = f[0].toInt(),
                score = items(f[1], ',').map(String::toInt),
                value = f[2].toInt(),
                mode = HandMode.valueOf(f[3]),
                elevenSide = optInt(f[4]),
                awaitingEleven = f[5] == "1",
                vira = Card.fromId(f[6].toInt()),
                starter = f[7].toInt(),
                current = f[8].toInt(),
                pending = pend,
                myCards = decodeCards(f[10]),
                counts = items(f[11], ',').map(String::toInt),
                table = decodePlays(f[12]),
                tricks = items(f[13], '/').map { t ->
                    val i = t.indexOf(':')
                    TrickView(decodePlays(t.substring(i + 1)), optInt(t.substring(0, i)))
                },
                result = res,
                canPlay = f[15] == "1",
                canCover = f[16] == "1",
                canRaise = f[17] == "1",
                canEscalate = f[18] == "1",
                matchOver = f[19] == "1",
                winnerSide = optInt(f[20]),
                partnerCards = decodeCards(f[21]),
                dealing = f[22] == "1",
            )
        }
    }
}

private fun encodeCards(c: List<Card>) = c.joinToString(",") { it.id.toString() }
private fun decodeCards(s: String) = items(s, ',').map { Card.fromId(it.toInt()) }

private fun encodePlays(p: List<PlayView>) =
    p.joinToString(",") { "${it.player}.${it.card?.id ?: -1}.${flag(it.covered)}" }

private fun decodePlays(s: String) = items(s, ',').map {
    val p = it.split('.')
    PlayView(p[0].toInt(), optInt(p[1])?.let(Card::fromId), p[2] == "1")
}

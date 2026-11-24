package com.pdonizete.truco.game

/**
 * Transforma os eventos (já vistos do lugar de quem ouve) em frases faladas.
 * Sem Android: o mesmo texto serve para o jogo local e para cada aparelho do Bluetooth.
 */
class Narrator(private val s: Seats) {

    fun line(e: GameEvent, v: TableView): String = when (e) {
        is GameEvent.Shuffle -> "Embaralhando."
        is GameEvent.HandStart -> handStart(e, v)
        is GameEvent.Played -> "${s.who(e.player)} jogou " + when {
            e.card == null -> "uma carta coberta."
            e.covered -> "${Words.cardLower(e.card, v.vira)}, coberta."
            else -> "${Words.cardLower(e.card, v.vira)}."
        }
        is GameEvent.TrickEnd -> {
            val ord = Words.trickOrdinal(e.index)
            val w = e.winnerPlayer
            if (w == null) "A $ord rodada cangou." else "${s.who(w)} ganhou a $ord rodada."
        }
        is GameEvent.Raised -> "${s.who(e.player)} pediu ${Words.raiseName(e.value)}!"
        is GameEvent.Answered ->
            if (e.accepted) "${s.who(e.player)} aceitou. A mão vale ${Words.points(e.value)}."
            else "${s.who(e.player)} correu."
        is GameEvent.Eleven ->
            if (e.play) "${s.sideWho(e.side)} ${s.verb(e.side, "vai", "vão")} jogar a mão de onze. A mão vale 3."
            else "${s.sideWho(e.side)} ${s.verb(e.side, "correu", "correram")} da mão de onze."
        is GameEvent.HandEnd -> {
            val side = e.winnerSide
            val pts = Words.points(e.points)
            val what = if (e.reason == EndReason.RODADAS) {
                "${s.sideWho(side)} ${s.verb(side, "ganhou", "ganharam")} a mão e ${s.verb(side, "fez", "fizeram")} $pts."
            } else {
                "${s.sideWho(side)} ${s.verb(side, "fez", "fizeram")} $pts."
            }
            "$what Placar: ${score(e.score)}."
        }
        is GameEvent.MatchEnd -> {
            val w = e.winnerSide
            "${s.sideWho(w)} ${s.verb(w, "venceu", "venceram")} a partida" + if (w == 0) "!" else "."
        }
    }

    /** Pergunta ou aviso de vez depois dos eventos: "Sua vez.", "Aceita ou corre?". Vazio se não é com você. */
    fun prompt(v: TableView): String = when {
        v.dealing || v.handOver -> ""
        v.askedMe -> if (v.canEscalate) {
            "Aceita, aumenta para ${Words.raiseName(nextRaise(v.pending!!.value)!!)} ou corre?"
        } else {
            "Aceita ou corre?"
        }
        v.canPlay -> "Sua vez."
        else -> ""
    }

    fun score(score: List<Int>): String =
        "${s.sideSpoken(0)} ${score[0]}, ${s.sideSpoken(1)} ${score[1]}"

    fun vira(vira: Card): String =
        "Vira: ${Words.cardLower(vira, null)}. Manilha: ${Words.manilha(vira).lowercase()}."

    private fun handStart(e: GameEvent.HandStart, v: TableView): String {
        val sb = StringBuilder(vira(v.vira)).append(' ')
        sb.append(if (e.starter == 0) "Você é mão." else "${s.who(e.starter)} é mão.")
        when (e.mode) {
            HandMode.MAO_DE_ONZE -> if (e.elevenSide == 0) {
                sb.append(
                    if (s.duo) " Mão de onze! Sua dupla tem 11 pontos. Veja as suas cartas e as do parceiro e decida se joga valendo 3 ou corre."
                    else " Mão de onze! Você tem 11 pontos. Veja suas cartas e decida se joga valendo 3 ou corre.",
                )
                if (v.partnerCards.isNotEmpty()) {
                    sb.append(" Cartas do parceiro: ")
                        .append(v.partnerCards.joinToString(", ") { Words.cardLower(it, v.vira) }).append('.')
                }
            } else {
                sb.append(
                    if (s.duo) " Mão de onze! Os adversários têm 11 pontos e vão decidir se jogam."
                    else " Mão de onze! ${s.who(1)} tem 11 pontos e vai decidir se joga.",
                )
            }
            HandMode.MAO_DE_FERRO -> sb.append(" Mão de ferro! Os dois lados têm 11 pontos. As cartas estão cobertas e a mão vale 1.")
            HandMode.NORMAL -> {}
        }
        return sb.toString()
    }
}

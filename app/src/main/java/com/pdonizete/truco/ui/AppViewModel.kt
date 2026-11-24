package com.pdonizete.truco.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pdonizete.truco.audio.SoundFx
import com.pdonizete.truco.game.Ai
import com.pdonizete.truco.game.Card
import com.pdonizete.truco.game.Difficulty
import com.pdonizete.truco.game.EndReason
import com.pdonizete.truco.game.Hand
import com.pdonizete.truco.game.HandMode
import com.pdonizete.truco.game.Match
import com.pdonizete.truco.game.PlayedCard
import com.pdonizete.truco.game.Response
import com.pdonizete.truco.game.Seats
import com.pdonizete.truco.game.WINNING_SCORE
import com.pdonizete.truco.game.Words
import com.pdonizete.truco.game.nextRaise
import com.pdonizete.truco.speech.Voice
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Screen { MENU, GAME, HELP, SETTINGS }

/**
 * Você é sempre o jogador 0, e o seu lado (você ou a sua dupla) é o lado 0.
 * Com 4 jogadores a ordem é: você, adversário da direita, parceiro, adversário da esquerda.
 */
const val ME = 0
const val MY_SIDE = 0

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("truco", Context.MODE_PRIVATE)
    private val voice = Voice(app)
    private val sfx = SoundFx()
    private val gameJobs = SupervisorJob()

    /** Muda a cada partida e ao sair: ações atrasadas de uma partida antiga são ignoradas. */
    private var generation = 0
    private var settledHand: Hand? = null
    private var ai = Ai(Difficulty.MEDIO)

    var screen by mutableStateOf(Screen.MENU); private set
    var difficulty by mutableStateOf(savedDifficulty()); private set
    var voiceOn by mutableStateOf(prefs.getBoolean(KEY_VOICE, true)); private set
    var soundsMuted by mutableStateOf(prefs.getBoolean(KEY_MUTED, false)); private set
    var coveredOption by mutableStateOf(prefs.getBoolean(KEY_COVERED, false)); private set
    var screenReaderOn by mutableStateOf(false); private set

    /** A voz do app só fala com o leitor de tela ligado, para não falar por cima de quem enxerga. */
    val voiceActive: Boolean get() = screenReaderOn && voiceOn

    var match by mutableStateOf<Match?>(null); private set

    /** Nomes dos lugares da mesa na partida atual (1 contra 1 ou duplas). */
    var seats by mutableStateOf(Seats(2)); private set

    /** A mão é um objeto mutável: [version] muda a cada jogada para a tela se redesenhar. */
    var version by mutableIntStateOf(0); private set
    var status by mutableStateOf(""); private set
    var dealing by mutableStateOf(false); private set
    var handFocus by mutableIntStateOf(0); private set
    var headingFocus by mutableIntStateOf(0); private set
    var actionFocus by mutableIntStateOf(0); private set

    init {
        sfx.muted = soundsMuted
        voice.enabled = false
    }

    // ---------------- Configurações

    private fun savedDifficulty(): Difficulty =
        runCatching { Difficulty.valueOf(prefs.getString(KEY_LEVEL, null) ?: "MEDIO") }.getOrDefault(Difficulty.MEDIO)

    fun updateScreenReader(on: Boolean) {
        screenReaderOn = on
        voice.enabled = voiceActive
    }

    fun changeDifficulty(d: Difficulty) {
        difficulty = d
        prefs.edit().putString(KEY_LEVEL, d.name).apply()
    }

    fun changeVoice(on: Boolean) {
        voiceOn = on
        prefs.edit().putBoolean(KEY_VOICE, on).apply()
        voice.enabled = voiceActive
    }

    fun changeSoundsMuted(muted: Boolean) {
        soundsMuted = muted
        sfx.muted = muted
        prefs.edit().putBoolean(KEY_MUTED, muted).apply()
    }

    fun changeCovered(on: Boolean) {
        coveredOption = on
        prefs.edit().putBoolean(KEY_COVERED, on).apply()
    }

    // ---------------- Navegação

    fun openHelp() { screen = Screen.HELP }
    fun openSettings() { screen = Screen.SETTINGS }

    fun backToMenu() {
        stopGame()
        match = null
        status = ""
        screen = Screen.MENU
    }

    private fun stopGame() {
        generation++
        gameJobs.cancelChildren()
        voice.stop()
        dealing = false
    }

    // ---------------- Partida

    /** [players] = 2 para 1 contra 1, 4 para duplas. Sem argumento, repete o tipo da última partida. */
    fun startMatch(players: Int = seats.players) {
        stopGame()
        ai = Ai(difficulty)
        settledHand = null
        seats = Seats(players)
        match = Match(players = players, coveredAllowed = coveredOption)
        screen = Screen.GAME
        val kind = if (players == 4) "em duplas" else "contra o computador"
        deal("Nova partida $kind, nível ${difficulty.label}. ")
    }

    fun nextHand() {
        val m = match ?: return
        if (dealing || m.isOver || settledHand !== m.hand) return
        m.nextHand()
        deal("")
    }

    private fun deal(intro: String) {
        dealing = true
        bump()
        say(intro + "Embaralhando.", flush = true)
        sfx.playShuffle()
        val gen = generation
        val cards = seats.players * 3
        val gap = if (seats.duo) 60L else 90L
        viewModelScope.launch(gameJobs) {
            delay(1100)
            repeat(cards) { sfx.playDeal(); delay(gap) }
            delay(60)
            sfx.playVira()
            delay(200)
            val m = match ?: return@launch
            if (gen != generation) return@launch
            dealing = false
            advance(handIntro(m.hand))
        }
    }

    private fun handIntro(h: Hand): String {
        val s = seats
        val sb = StringBuilder(viraText(h)).append(' ')
        sb.append(if (h.starter == ME) "Você é mão. " else "${s.who(h.starter)} é mão. ")
        when (h.mode) {
            HandMode.MAO_DE_ONZE -> if (h.elevenSide == MY_SIDE) {
                sb.append(
                    if (s.duo) "Mão de onze! Sua dupla tem 11 pontos. Veja as suas cartas e as do parceiro e decida se joga valendo 3 ou corre. "
                    else "Mão de onze! Você tem 11 pontos. Veja suas cartas e decida se joga valendo 3 ou corre. ",
                )
                partnerCardsText(h)?.let { sb.append(it).append(' ') }
            } else {
                sb.append(
                    if (s.duo) "Mão de onze! Os adversários têm 11 pontos e vão decidir se jogam. "
                    else "Mão de onze! O computador tem 11 pontos e vai decidir se joga. ",
                )
            }
            HandMode.MAO_DE_FERRO -> sb.append("Mão de ferro! Os dois lados têm 11 pontos. As cartas estão cobertas e a mão vale 1. ")
            HandMode.NORMAL -> {}
        }
        return sb.toString()
    }

    // ---------------- Suas ações

    /** Toque numa carta. Devolve true quando a tela deve perguntar se a carta vai aberta ou coberta. */
    fun tapCard(card: Card): Boolean {
        val h = match?.hand ?: return false
        if (dealing || !h.canPlay(ME)) {
            say(whyNot(h), flush = true)
            return false
        }
        if (h.canCover(ME) && !h.blind) return true
        playCard(card, false)
        return false
    }

    fun playCard(card: Card, covered: Boolean) {
        if (dealing) return
        val text = playMove(ME, card, covered) ?: return
        // A carta saiu da mão: o foco iria para outro lugar. Leva ao título "Suas cartas".
        if (screenReaderOn) headingFocus++
        advance(text)
    }

    fun raise() {
        val h = match?.hand ?: return
        if (dealing || !h.requestRaise(ME)) return
        trucoThen("Você pediu ${Words.raiseName(h.pending!!.value)}! ")
    }

    fun respond(r: Response) {
        val h = match?.hand ?: return
        val p = h.pending ?: return
        if (p.responderSide != MY_SIDE || !h.respond(MY_SIDE, r)) return
        when (r) {
            Response.ACEITAR -> advance("Você aceitou. A mão vale ${Words.points(p.value)}. ")
            Response.AUMENTAR -> trucoThen("Você pediu ${Words.raiseName(h.pending!!.value)}! ")
            Response.CORRER -> advance("Você correu. ")
        }
    }

    fun decideEleven(play: Boolean) {
        val h = match?.hand ?: return
        if (h.elevenSide != MY_SIDE || !h.decideEleven(play)) return
        advance(if (play) "Você vai jogar a mão de onze. A mão vale 3. " else "Você correu da mão de onze. ")
    }

    private fun whyNot(h: Hand): String {
        val s = seats
        val p = h.pending
        return when {
            dealing -> "Espere, as cartas estão sendo dadas."
            h.isOver -> "A mão terminou."
            h.awaitingEleven && h.elevenSide == MY_SIDE -> "Primeiro decida se joga a mão de onze."
            h.awaitingEleven -> "Espere ${s.them} ${s.themVerb("decidir", "decidirem")} a mão de onze."
            p != null && p.responderSide == MY_SIDE -> "Responda ao pedido primeiro."
            p != null -> "Espere a resposta ${s.ofThem}."
            else -> "Espere, é a vez ${s.of(h.current)}."
        }
    }

    // ---------------- Computador (adversários e parceiro)

    /** Só a última jogada agendada do computador vale; agendamentos antigos são ignorados. */
    private var cpuTicket = 0

    private fun cpuLater() {
        val ticket = ++cpuTicket
        afterSpeech { later(if (voiceActive) 350 else 900) { if (ticket == cpuTicket) cpuAct() } }
    }

    private fun cpuAct() {
        val m = match ?: return
        val h = m.hand
        if (h.isOver || dealing) return
        val s = seats
        val p = h.pending
        when {
            h.awaitingEleven -> {
                val side = h.elevenSide ?: return
                if (side == MY_SIDE) return
                val who = bestOf(m, side)
                val play = ai.playEleven(h, who, m.score[side], m.score[1 - side])
                h.decideEleven(play)
                advance(
                    if (play) "${s.sideWho(side)} ${s.verb(side, "vai", "vão")} jogar a mão de onze. A mão vale 3. "
                    else "${s.sideWho(side)} ${s.verb(side, "correu", "correram")} da mão de onze. ",
                )
            }
            p != null -> {
                val side = p.responderSide
                if (side == MY_SIDE) return
                val who = bestOf(m, side)
                var r = ai.respond(h, who, m.score[side], m.score[1 - side])
                if (!h.respond(side, r)) { r = Response.ACEITAR; h.respond(side, r) }
                when (r) {
                    Response.ACEITAR -> advance("${s.who(who)} aceitou. A mão vale ${Words.points(p.value)}. ")
                    Response.AUMENTAR -> trucoThen("${s.who(who)} pediu ${Words.raiseName(h.pending!!.value)}! ")
                    Response.CORRER -> advance("${s.who(who)} correu. ")
                }
            }
            h.current != ME && h.canPlay(h.current) -> {
                val who = h.current
                val side = h.sideOf(who)
                if (ai.shouldRaise(h, who, m.score[side], m.score[1 - side]) && h.requestRaise(who)) {
                    trucoThen("${s.who(who)} pediu ${Words.raiseName(h.pending!!.value)}! ")
                } else {
                    playMove(who, ai.chooseCard(h, who), false)?.let { advance(it) }
                }
            }
        }
    }

    /** Quem decide pelo lado [side] (resposta ao truco, mão de onze): o jogador com a mão mais forte. */
    private fun bestOf(m: Match, side: Int): Int {
        val h = m.hand
        return (0 until h.players).filter { h.sideOf(it) == side }
            .maxBy { ai.confidence(h, it, m.score[side], m.score[1 - side]) }
    }

    // ---------------- Andamento e anúncios

    private fun playMove(player: Int, card: Card, covered: Boolean): String? {
        val h = match?.hand ?: return null
        val before = h.tricks.size
        if (!h.play(player, card, covered)) return null
        sfx.playCard()
        val s = seats
        val sb = StringBuilder("${s.who(player)} jogou ")
        sb.append(Words.cardLower(card, h.vira)).append(if (covered) ", coberta. " else ". ")
        if (h.tricks.size > before) {
            val ord = Words.trickOrdinal(h.tricks.size - 1)
            val wp = h.tricks.last().winnerPlayer
            sb.append(if (wp == null) "A $ord rodada cangou. " else "${s.who(wp)} ganhou a $ord rodada. ")
        }
        return sb.toString()
    }

    /** Batida na mesa e, logo depois, a fala do pedido. */
    private fun trucoThen(text: String) {
        sfx.playTruco()
        bump()
        later(350) { advance(text) }
    }

    /** Fala o que aconteceu e decide quem age em seguida. */
    private fun advance(prefix: String) {
        val m = match ?: return
        val h = m.hand
        bump()
        val text = StringBuilder(prefix)
        if (h.isOver) {
            endHand(m, text)
            return
        }
        val p = h.pending
        when {
            h.awaitingEleven -> {
                say(text)
                if (h.elevenSide == MY_SIDE) focusActions() else cpuLater()
            }
            p != null && p.responderSide == MY_SIDE -> {
                val up = nextRaise(p.value)
                text.append(if (up != null && h.canEscalate(MY_SIDE)) "Aceita, aumenta para ${Words.raiseName(up)} ou corre?" else "Aceita ou corre?")
                say(text)
                focusActions()
            }
            p != null -> { say(text); cpuLater() }
            h.current == ME -> {
                text.append("Sua vez.")
                say(text)
                afterSpeech {
                    sfx.playTurn()
                    if (screenReaderOn) handFocus++
                }
            }
            else -> { say(text); cpuLater() }
        }
    }

    private fun endHand(m: Match, text: StringBuilder) {
        if (settledHand === m.hand) return
        settledHand = m.hand
        val r = m.settle()
        val s = seats
        val side = r.winnerSide
        val pts = Words.points(r.points)
        text.append(
            if (r.reason == EndReason.RODADAS) {
                "${s.sideWho(side)} ${s.verb(side, "ganhou", "ganharam")} a mão e ${s.verb(side, "fez", "fizeram")} $pts. "
            } else {
                "${s.sideWho(side)} ${s.verb(side, "fez", "fizeram")} $pts. "
            },
        )
        text.append("Placar: ${scoreSpoken()}. ")
        if (m.isOver) {
            val w = m.winnerSide!!
            text.append("${s.sideWho(w)} ${s.verb(w, "venceu", "venceram")} a partida")
            text.append(if (w == MY_SIDE) "!" else ".")
            if (w == MY_SIDE) sfx.playApplause()
        }
        bump()
        say(text)
        focusActions()
    }

    /** Botão Repetir: fala de novo a situação atual. */
    fun describe() {
        val m = match ?: return
        val h = m.hand
        val sb = StringBuilder("Placar: ${scoreSpoken()}. ${handValueText(h)}. ")
        if (dealing) {
            say(sb.append("As cartas estão sendo dadas."), flush = true)
            return
        }
        sb.append(viraText(h)).append(' ')
        sb.append(tableText(h)).append(' ')
        sb.append(myCardsText(h)).append(' ')
        partnerCardsText(h)?.let { sb.append(it).append(' ') }
        sb.append(situation(m, h))
        say(sb, flush = true)
    }

    private fun situation(m: Match, h: Hand): String {
        val s = seats
        val p = h.pending
        return when {
            m.isOver -> "A partida terminou."
            h.isOver -> "A mão terminou. Use o botão Próxima mão."
            h.awaitingEleven && h.elevenSide == MY_SIDE -> "Decida se joga a mão de onze."
            h.awaitingEleven -> "${s.sideWho(1)} ${s.verb(1, "está", "estão")} decidindo a mão de onze."
            p != null && p.responderSide == MY_SIDE -> {
                val ask = if (h.canEscalate(MY_SIDE)) "Aceita, aumenta ou corre?" else "Aceita ou corre?"
                "${s.sideWho(p.requesterSide)} ${s.verb(p.requesterSide, "pediu", "pediram")} ${Words.raiseName(p.value)}. $ask"
            }
            p != null -> "${s.sideWho(p.requesterSide)} ${s.verb(p.requesterSide, "pediu", "pediram")} ${Words.raiseName(p.value)}. Esperando ${s.them}."
            h.current == ME -> "Sua vez."
            else -> "Vez ${s.of(h.current)}."
        }
    }

    // ---------------- Textos da tela

    fun scoreDisplay(): String =
        match?.score?.let { "${seats.sideShort(0)} ${it[0]}  ×  ${it[1]} ${seats.sideShort(1)}" } ?: ""

    fun scoreSpoken(): String =
        match?.score?.let { "${seats.sideSpoken(0)} ${it[0]}, ${seats.sideSpoken(1)} ${it[1]}" } ?: ""

    fun scoreDescription(): String = scoreSpoken().replaceFirstChar { it.uppercase() } + ". Partida até $WINNING_SCORE."

    fun runElevenQuestion(): String =
        "Deseja correr da mão de onze? ${seats.them.replaceFirstChar { it.uppercase() }} ${seats.themVerb("ganha", "ganham")} 1 ponto."

    fun handValueText(h: Hand): String {
        if (h.awaitingEleven) return "Mão de onze: vale 3 se for jogada"
        val mode = when (h.mode) {
            HandMode.MAO_DE_ONZE -> ", mão de onze"
            HandMode.MAO_DE_FERRO -> ", mão de ferro"
            HandMode.NORMAL -> ""
        }
        val p = h.pending
        val ask = if (p != null && !h.isOver) ", pedido de ${Words.raiseName(p.value)} esperando resposta" else ""
        return "Mão valendo ${Words.points(h.value)}$mode$ask"
    }

    fun viraText(h: Hand): String =
        "Vira: ${Words.cardLower(h.vira, null)}. Manilha: ${Words.manilha(h.vira).lowercase()}."

    fun cardLabel(h: Hand, card: Card, index: Int): String =
        if (h.blind) "Carta ${index + 1}, coberta" else Words.card(card, h.vira)

    fun cardsCount(n: Int): String = when (n) {
        0 -> "nenhuma carta"
        1 -> "1 carta"
        else -> "$n cartas"
    }

    private fun playLabel(h: Hand, p: PlayedCard): String {
        val who = seats.noun(p.player)
        val what = when {
            p.covered && p.player == ME -> "${Words.cardLower(p.card, h.vira)}, coberta"
            p.covered -> "carta coberta"
            else -> Words.cardLower(p.card, h.vira)
        }
        return "$who: $what"
    }

    fun tableText(h: Hand): String {
        val sb = StringBuilder()
        h.tricks.forEachIndexed { i, t ->
            sb.append(Words.trickOrdinal(i).replaceFirstChar { it.uppercase() }).append(" rodada, ")
            sb.append(t.plays.joinToString(", ") { playLabel(h, it) })
            val wp = t.winnerPlayer
            sb.append(if (wp == null) ". Cangou. " else ". ${seats.who(wp)} ganhou. ")
        }
        if (h.table.isNotEmpty()) sb.append("Na mesa: ").append(h.table.joinToString(", ") { playLabel(h, it) }).append(". ")
        else if (!h.isOver) sb.append("Mesa vazia. ")
        sb.append(othersCardsText(h))
        return sb.toString()
    }

    private fun othersCardsText(h: Hand): String =
        if (!seats.duo) "O computador tem ${cardsCount(h.hands[1].size)}."
        else "Cartas na mão: " + (1 until h.players).joinToString(", ") { "${seats.noun(it)} com ${cardsCount(h.hands[it].size)}" } + "."

    fun othersCardsShort(h: Hand): String =
        if (!seats.duo) "Computador: ${cardsCount(h.hands[1].size)}"
        else (1 until h.players).joinToString("  ·  ") { "${seats.shortName(it)} ${h.hands[it].size}" }

    fun tricksLine(h: Hand): String = h.tricks.mapIndexed { i, t ->
        "${i + 1}ª: " + (t.winnerSide?.let { seats.sideShort(it).lowercase() } ?: "cangou")
    }.joinToString("  ·  ")

    private fun myCardsText(h: Hand): String {
        val cards = h.hands[ME]
        return when {
            cards.isEmpty() -> "Você não tem mais cartas."
            h.blind -> "Suas cartas: ${cardsCount(cards.size)} cobertas."
            else -> "Suas cartas: " + cards.joinToString(", ") { Words.cardLower(it, h.vira) } + "."
        }
    }

    /** Na mão de onze da sua dupla, você vê as cartas do parceiro antes de decidir. */
    fun partnerCardsText(h: Hand): String? {
        if (!seats.duo || !h.awaitingEleven || h.elevenSide != MY_SIDE) return null
        return "Cartas do parceiro: " + h.hands[2].joinToString(", ") { Words.cardLower(it, h.vira) } + "."
    }

    // ---------------- Utilidades

    private fun focusActions() = afterSpeech { if (screenReaderOn) actionFocus++ }

    private fun bump() { version++ }

    private fun say(text: CharSequence, flush: Boolean = false) {
        val s = text.toString().trim()
        if (s.isEmpty()) return
        status = s
        if (voiceActive) voice.speak(s, flush)
    }

    private fun afterSpeech(block: () -> Unit) {
        val gen = generation
        voice.afterQueue { if (gen == generation) block() }
    }

    private fun later(ms: Long, block: () -> Unit) {
        val gen = generation
        viewModelScope.launch(gameJobs) {
            delay(ms)
            if (gen == generation) block()
        }
    }

    override fun onCleared() {
        gameJobs.cancel()
        voice.shutdown()
        sfx.release()
    }

    private companion object {
        const val KEY_LEVEL = "nivel"
        const val KEY_VOICE = "voz"
        const val KEY_MUTED = "sons_mudos"
        const val KEY_COVERED = "carta_coberta"
    }
}

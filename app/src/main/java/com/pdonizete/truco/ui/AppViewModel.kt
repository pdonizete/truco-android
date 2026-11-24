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

/** Você é o jogador 0 (lado 0). O computador é o jogador 1 (lado 1). */
const val ME = 0
const val CPU = 1

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

    fun startMatch() {
        stopGame()
        ai = Ai(difficulty)
        settledHand = null
        match = Match(players = 2, coveredAllowed = coveredOption)
        screen = Screen.GAME
        deal("Nova partida contra o computador, nível ${difficulty.label}. ")
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
        viewModelScope.launch(gameJobs) {
            delay(1600)
            repeat(6) { sfx.playDeal(); delay(140) }
            delay(100)
            sfx.playVira()
            delay(300)
            val m = match ?: return@launch
            if (gen != generation) return@launch
            dealing = false
            advance(handIntro(m.hand))
        }
    }

    private fun handIntro(h: Hand): String {
        val sb = StringBuilder(viraText(h)).append(' ')
        sb.append(if (h.starter == ME) "Você é mão. " else "O computador é mão. ")
        when (h.mode) {
            HandMode.MAO_DE_ONZE -> sb.append(
                if (h.elevenSide == ME) "Mão de onze! Você tem 11 pontos. Veja suas cartas e decida se joga valendo 3 ou corre. "
                else "Mão de onze! O computador tem 11 pontos e vai decidir se joga. ",
            )
            HandMode.MAO_DE_FERRO -> sb.append("Mão de ferro! Os dois têm 11 pontos. As cartas estão cobertas e a mão vale 1. ")
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
        if (p.responderSide != ME || !h.respond(ME, r)) return
        when (r) {
            Response.ACEITAR -> advance("Você aceitou. A mão vale ${Words.points(p.value)}. ")
            Response.AUMENTAR -> trucoThen("Você pediu ${Words.raiseName(h.pending!!.value)}! ")
            Response.CORRER -> advance("Você correu. ")
        }
    }

    fun decideEleven(play: Boolean) {
        val h = match?.hand ?: return
        if (h.elevenSide != ME || !h.decideEleven(play)) return
        advance(if (play) "Você vai jogar a mão de onze. A mão vale 3. " else "Você correu da mão de onze. ")
    }

    private fun whyNot(h: Hand): String {
        val p = h.pending
        return when {
            dealing -> "Espere, as cartas estão sendo dadas."
            h.isOver -> "A mão terminou."
            h.awaitingEleven && h.elevenSide == ME -> "Primeiro decida se joga a mão de onze."
            h.awaitingEleven -> "Espere o computador decidir a mão de onze."
            p != null && p.responderSide == ME -> "Responda ao pedido primeiro."
            p != null -> "Espere a resposta do computador."
            else -> "Espere, é a vez do computador."
        }
    }

    // ---------------- Computador

    private fun cpuLater() {
        afterSpeech { later(if (voiceActive) 600 else 1200) { cpuAct() } }
    }

    private fun cpuAct() {
        val m = match ?: return
        val h = m.hand
        if (h.isOver || dealing) return
        val mine = m.score[CPU]
        val theirs = m.score[ME]
        val p = h.pending
        when {
            h.awaitingEleven && h.elevenSide == CPU -> {
                val play = ai.playEleven(h, CPU, mine, theirs)
                h.decideEleven(play)
                advance(if (play) "O computador vai jogar a mão de onze. A mão vale 3. " else "O computador correu da mão de onze. ")
            }
            p != null && p.responderSide == CPU -> {
                var r = ai.respond(h, CPU, mine, theirs)
                if (!h.respond(CPU, r)) { r = Response.ACEITAR; h.respond(CPU, r) }
                when (r) {
                    Response.ACEITAR -> advance("O computador aceitou. A mão vale ${Words.points(p.value)}. ")
                    Response.AUMENTAR -> trucoThen("O computador pediu ${Words.raiseName(h.pending!!.value)}! ")
                    Response.CORRER -> advance("O computador correu. ")
                }
            }
            p == null && h.canPlay(CPU) -> {
                if (ai.shouldRaise(h, CPU, mine, theirs) && h.requestRaise(CPU)) {
                    trucoThen("O computador pediu ${Words.raiseName(h.pending!!.value)}! ")
                } else {
                    playMove(CPU, ai.chooseCard(h, CPU), false)?.let { advance(it) }
                }
            }
        }
    }

    // ---------------- Andamento e anúncios

    private fun playMove(player: Int, card: Card, covered: Boolean): String? {
        val h = match?.hand ?: return null
        val before = h.tricks.size
        if (!h.play(player, card, covered)) return null
        sfx.playCard()
        val sb = StringBuilder(if (player == ME) "Você jogou " else "O computador jogou ")
        sb.append(Words.cardLower(card, h.vira)).append(if (covered) ", coberta. " else ". ")
        if (h.tricks.size > before) {
            val ord = Words.trickOrdinal(h.tricks.size - 1)
            sb.append(
                when (h.tricks.last().winnerSide) {
                    null -> "A $ord rodada cangou. "
                    ME -> "Você ganhou a $ord rodada. "
                    else -> "O computador ganhou a $ord rodada. "
                },
            )
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
                if (h.elevenSide == ME) focusActions() else cpuLater()
            }
            p != null && p.responderSide == ME -> {
                val up = nextRaise(p.value)
                text.append(if (up != null && h.canEscalate(ME)) "Aceita, aumenta para ${Words.raiseName(up)} ou corre?" else "Aceita ou corre?")
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
        val pts = Words.points(r.points)
        val mine = r.winnerSide == ME
        text.append(
            when {
                r.reason == EndReason.RODADAS && mine -> "Você ganhou a mão e fez $pts. "
                r.reason == EndReason.RODADAS -> "O computador ganhou a mão e fez $pts. "
                mine -> "Você fez $pts. "
                else -> "O computador fez $pts. "
            },
        )
        text.append("Placar: ${scoreSpoken()}. ")
        if (m.isOver) {
            if (m.winnerSide == ME) {
                text.append("Você venceu a partida!")
                sfx.playApplause()
            } else {
                text.append("O computador venceu a partida.")
            }
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
        sb.append(situation(m, h))
        say(sb, flush = true)
    }

    private fun situation(m: Match, h: Hand): String {
        val p = h.pending
        return when {
            m.isOver -> "A partida terminou."
            h.isOver -> "A mão terminou. Use o botão Próxima mão."
            h.awaitingEleven && h.elevenSide == ME -> "Decida se joga a mão de onze."
            h.awaitingEleven -> "O computador está decidindo a mão de onze."
            p != null && p.responderSide == ME -> "O computador pediu ${Words.raiseName(p.value)}. Aceita, aumenta ou corre?"
            p != null -> "Você pediu ${Words.raiseName(p.value)}. Esperando o computador."
            h.current == ME -> "Sua vez."
            else -> "Vez do computador."
        }
    }

    // ---------------- Textos da tela

    fun scoreDisplay(): String = match?.score?.let { "Você ${it[ME]}  ×  ${it[CPU]} Computador" } ?: ""
    fun scoreSpoken(): String = match?.score?.let { "você ${it[ME]}, computador ${it[CPU]}" } ?: ""
    fun scoreDescription(): String = "Você ${match?.score?.get(ME)}, computador ${match?.score?.get(CPU)}. Partida até $WINNING_SCORE."

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
        val who = if (p.player == ME) "você" else "computador"
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
            sb.append(
                when (t.winnerSide) {
                    null -> ". Cangou. "
                    ME -> ". Você ganhou. "
                    else -> ". O computador ganhou. "
                },
            )
        }
        if (h.table.isNotEmpty()) sb.append("Na mesa: ").append(h.table.joinToString(", ") { playLabel(h, it) }).append(". ")
        else if (!h.isOver) sb.append("Mesa vazia. ")
        sb.append("O computador tem ${cardsCount(h.hands[CPU].size)}.")
        return sb.toString()
    }

    fun tricksLine(h: Hand): String = h.tricks.mapIndexed { i, t ->
        "${i + 1}ª: " + when (t.winnerSide) {
            null -> "cangou"
            ME -> "você"
            else -> "computador"
        }
    }.joinToString("  ·  ")

    private fun myCardsText(h: Hand): String {
        val cards = h.hands[ME]
        return when {
            cards.isEmpty() -> "Você não tem mais cartas."
            h.blind -> "Suas cartas: ${cardsCount(cards.size)} cobertas."
            else -> "Suas cartas: " + cards.joinToString(", ") { Words.cardLower(it, h.vira) } + "."
        }
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

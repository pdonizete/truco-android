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
import com.pdonizete.truco.bt.BtDevice
import com.pdonizete.truco.bt.BtLink
import com.pdonizete.truco.bt.BtRoom
import com.pdonizete.truco.bt.LinkEvent
import com.pdonizete.truco.bt.rotate
import com.pdonizete.truco.game.Card
import com.pdonizete.truco.game.Difficulty
import com.pdonizete.truco.game.GameEvent
import com.pdonizete.truco.game.GameHost
import com.pdonizete.truco.game.HandMode
import com.pdonizete.truco.game.Move
import com.pdonizete.truco.game.Narrator
import com.pdonizete.truco.game.PlayView
import com.pdonizete.truco.game.Response
import com.pdonizete.truco.game.Seats
import com.pdonizete.truco.game.TableView
import com.pdonizete.truco.game.WINNING_SCORE
import com.pdonizete.truco.game.Words
import com.pdonizete.truco.game.forViewer
import com.pdonizete.truco.speech.Voice
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Screen { MENU, GAME, HELP, SETTINGS, BLUETOOTH }

/** LOCAL: contra o computador neste celular. HOST: anfitrião do Bluetooth. GUEST: convidado. */
enum class Mode { LOCAL, HOST, GUEST }

/** Você é sempre o jogador 0 da sua visão, e o seu lado é o lado 0. */
const val ME = 0
const val MY_SIDE = 0

class AppViewModel(app: Application) : AndroidViewModel(app), BtRoom.Listener {
    private val prefs = app.getSharedPreferences("truco", Context.MODE_PRIVATE)
    private val voice = Voice(app)
    private val sfx = SoundFx()
    private val gameJobs = SupervisorJob()
    val link = BtLink(app)
    val room = BtRoom(link, this)

    /** Muda a cada partida e ao sair: ações atrasadas de uma partida antiga são ignoradas. */
    private var generation = 0

    /** Autoridade da partida (jogo local e anfitrião). O convidado não tem. */
    private var game: GameHost? = null
    private var guestSeats: List<Int> = emptyList()
    private var lastDuo = false
    private var matchIntro = ""
    private var narrator = Narrator(Seats(2))
    private var lastRoster: List<String> = emptyList()

    /** Convidado: jogada enviada, esperando o anfitrião responder. */
    private var awaitingHost = false

    var screen by mutableStateOf(Screen.MENU); private set
    var mode by mutableStateOf(Mode.LOCAL); private set
    var difficulty by mutableStateOf(savedDifficulty()); private set
    var voiceOn by mutableStateOf(prefs.getBoolean(KEY_VOICE, true)); private set
    var soundsMuted by mutableStateOf(prefs.getBoolean(KEY_MUTED, false)); private set
    var coveredOption by mutableStateOf(prefs.getBoolean(KEY_COVERED, false)); private set
    var playerName by mutableStateOf(prefs.getString(KEY_NAME, null) ?: ""); private set
    var screenReaderOn by mutableStateOf(false); private set

    /** A voz do app só fala com o leitor de tela ligado, para não falar por cima de quem enxerga. */
    val voiceActive: Boolean get() = screenReaderOn && voiceOn

    /** O que você vê da partida. Null antes da primeira jogada (ou esperando o anfitrião). */
    var view by mutableStateOf<TableView?>(null); private set

    /** Nomes dos lugares da mesa na partida atual. */
    var seats by mutableStateOf(Seats(2)); private set

    var version by mutableIntStateOf(0); private set
    var status by mutableStateOf(""); private set
    var handFocus by mutableIntStateOf(0); private set
    var headingFocus by mutableIntStateOf(0); private set
    var actionFocus by mutableIntStateOf(0); private set

    /** Muda quando a sala do Bluetooth muda, para a tela se redesenhar. */
    var roomVersion by mutableIntStateOf(0); private set
    var lobbyMessage by mutableStateOf<String?>(null); private set
    var partnerChoice by mutableStateOf<Int?>(null); private set

    val isGuest: Boolean get() = mode == Mode.GUEST

    init {
        sfx.muted = soundsMuted
        voice.enabled = false
        viewModelScope.launch {
            link.events.collect { e ->
                when (e) {
                    is LinkEvent.Opened -> room.onOpened(e.id, e.accepted)
                    is LinkEvent.Line -> room.onLine(e.id, e.text)
                    is LinkEvent.Closed -> room.onClosed(e.id)
                }
            }
        }
        viewModelScope.launch { link.errors.collect { announce(it) } }
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

    fun changeName(name: String) {
        playerName = name.take(20)
        prefs.edit().putString(KEY_NAME, playerName).apply()
    }

    // ---------------- Navegação

    fun openHelp() { screen = Screen.HELP }
    fun openSettings() { screen = Screen.SETTINGS }

    fun openBluetooth() {
        leaveRoom()
        lobbyMessage = null
        screen = Screen.BLUETOOTH
    }

    fun backToMenu() {
        if (mode != Mode.LOCAL) leaveRoom() else stopGame()
        game = null
        view = null
        status = ""
        screen = Screen.MENU
    }

    private fun stopGame() {
        generation++
        cpuTicket++
        gameJobs.cancelChildren()
        voice.stop()
        awaitingHost = false
    }

    // ---------------- Partida neste celular

    /** [players] = 2 para 1 contra 1, 4 para duplas. */
    fun startMatch(players: Int) {
        if (mode != Mode.LOCAL) leaveRoom()
        stopGame()
        val g = GameHost(players, setOf(ME), difficulty, coveredOption)
        game = g
        guestSeats = emptyList()
        useSeats(Seats(players))
        val kind = if (players == 4) "em duplas" else "contra o computador"
        matchIntro = "Nova partida $kind, nível ${difficulty.label}. "
        view = null
        screen = Screen.GAME
        publish(g.start())
    }

    /** Botão Nova partida no fim da partida. */
    fun newMatch() {
        when (mode) {
            Mode.LOCAL -> startMatch(seats.players)
            Mode.HOST -> hostStart(lastDuo)
            Mode.GUEST -> {}
        }
    }

    private fun useSeats(s: Seats) {
        seats = s
        narrator = Narrator(s)
    }

    /** Autoridade: manda os eventos a cada convidado, anuncia aqui e segue a partida. */
    private fun publish(events: List<GameEvent>) {
        val g = game ?: return
        if (events.isEmpty()) return
        for (seat in guestSeats) {
            room.sendState(seat, events.map { it.forViewer(seat, g.players) }, g.view(seat))
        }
        val dealt = events.any { it is GameEvent.Shuffle }
        present(events, g.view(ME)) { if (!dealt) cpuLater() }
        if (dealt) later(dealMs(g.players)) { publish(g.handStart()) }
    }

    /** Mostra e anuncia os eventos, já vistos deste celular. [then] roda depois da fala ser pedida. */
    private fun present(events: List<GameEvent>, v: TableView, then: () -> Unit = {}) {
        view = v
        bump()
        val text = StringBuilder()
        var flush = false
        var truco = false
        for (e in events) {
            when (e) {
                is GameEvent.Shuffle -> {
                    flush = true
                    if (e.newMatch) text.append(matchIntro)
                    dealSounds(v.players)
                }
                is GameEvent.HandStart -> sfx.playVira()
                is GameEvent.Played -> {
                    sfx.playCard()
                    // A carta saiu da mão: leva o foco ao título "Suas cartas".
                    if (e.player == ME && screenReaderOn) headingFocus++
                }
                is GameEvent.Raised -> truco = true
                is GameEvent.MatchEnd -> if (e.winnerSide == MY_SIDE) sfx.playApplause()
                else -> {}
            }
            text.append(narrator.line(e, v)).append(' ')
        }
        text.append(narrator.prompt(v))
        val speak = {
            say(text, flush)
            focusAfter(v)
            then()
        }
        // Batida na mesa e, logo depois, a fala do pedido.
        if (truco) {
            sfx.playTruco()
            later(350) { speak() }
        } else {
            speak()
        }
    }

    private fun focusAfter(v: TableView) {
        when {
            v.dealing -> {}
            v.handOver || v.askedMe || v.elevenMine -> focusActions()
            v.canPlay -> afterSpeech {
                sfx.playTurn()
                if (screenReaderOn) handFocus++
            }
        }
    }

    private fun dealSounds(players: Int) {
        sfx.playShuffle()
        val gap = dealGap(players)
        viewModelScope.launch(gameJobs) {
            delay(SHUFFLE_MS)
            repeat(players * 3) { sfx.playDeal(); delay(gap) }
        }
    }

    private fun dealGap(players: Int) = if (players == 4) 60L else 90L
    private fun dealMs(players: Int) = SHUFFLE_MS + players * 3 * dealGap(players) + 260L

    // ---------------- Computador

    /** Só a última jogada agendada do computador vale. */
    private var cpuTicket = 0

    private fun cpuLater() {
        val g = game ?: return
        if (g.cpuToAct() == null) return
        val ticket = ++cpuTicket
        afterSpeech {
            later(if (voiceActive) 350 else 900) {
                if (ticket == cpuTicket) game?.cpuStep()?.let(::publish)
            }
        }
    }

    // ---------------- Suas ações

    private fun send(move: Move) {
        if (mode == Mode.GUEST) {
            awaitingHost = true
            room.sendMove(move)
            return
        }
        game?.apply(ME, move)?.let(::publish)
    }

    /** Toque numa carta. Devolve true quando a tela deve perguntar se a carta vai aberta ou coberta. */
    fun tapCard(card: Card): Boolean {
        val v = view ?: return false
        if (awaitingHost) return false
        if (!v.canPlay) {
            say(whyNot(v), flush = true)
            return false
        }
        if (v.canCover) return true
        playCard(card, false)
        return false
    }

    fun playCard(card: Card, covered: Boolean) {
        val v = view ?: return
        if (v.canPlay && !awaitingHost && card in v.myCards) send(Move.Play(card, covered))
    }

    fun raise() { if (view?.canRaise == true && !awaitingHost) send(Move.Raise) }
    fun respond(r: Response) { if (view?.askedMe == true && !awaitingHost) send(Move.Answer(r)) }
    fun decideEleven(play: Boolean) { if (view?.elevenMine == true && !awaitingHost) send(Move.Eleven(play)) }

    fun nextHand() {
        val v = view ?: return
        if (v.handOver && !v.matchOver && !v.dealing && !awaitingHost) send(Move.Next)
    }

    private fun whyNot(v: TableView): String {
        val s = seats
        return when {
            v.dealing -> "Espere, as cartas estão sendo dadas."
            v.handOver -> "A mão terminou."
            v.elevenMine -> "Primeiro decida se joga a mão de onze."
            v.awaitingEleven -> "Espere ${s.them} ${s.themVerb("decidir", "decidirem")} a mão de onze."
            v.askedMe -> "Responda ao pedido primeiro."
            v.pending != null -> "Espere a resposta ${s.ofThem}."
            else -> "Espere, é a vez ${s.of(v.current)}."
        }
    }

    // ---------------- Bluetooth

    fun hostRoom() {
        leaveRoom()
        lobbyMessage = null
        room.myName = playerName.ifBlank { "Anfitrião" }
        room.host()
        mode = Mode.HOST
        lastRoster = room.roster
        link.openRoom()
        roomVersion++
        announce("Sala aberta. Esperando os amigos.")
    }

    fun joinRoom() {
        leaveRoom()
        lobbyMessage = null
        room.myName = playerName.ifBlank { "Convidado" }
        room.guest()
        mode = Mode.GUEST
        link.startScan()
        roomVersion++
    }

    fun scan() = link.startScan()

    fun connectTo(d: BtDevice) {
        lobbyMessage = null
        link.connect(d)
    }

    fun choosePartner(friendLink: Int) { partnerChoice = friendLink }

    fun notice(text: String) = announce(text)

    /** Fecha a sala e as conexões. A partida em andamento termina para todos. */
    fun leaveRoom() {
        stopGame()
        room.reset()
        link.closeAll()
        mode = Mode.LOCAL
        game = null
        view = null
        guestSeats = emptyList()
        partnerChoice = null
        lastRoster = emptyList()
        roomVersion++
    }

    /** Anfitrião: começa a partida com os amigos da sala. Lugares vazios ficam com o computador. */
    fun hostStart(duo: Boolean) {
        if (mode != Mode.HOST) return
        val plan = room.start(duo, partnerChoice) ?: return
        stopGame()
        lastDuo = duo
        val players = plan.seats.size
        val humans = plan.seats.indices.filter { plan.seats[it] != null }.toSet()
        guestSeats = (humans - ME).sorted()
        val g = GameHost(players, humans, difficulty, coveredOption)
        game = g
        useSeats(Seats(players, plan.names))
        matchIntro = btIntro()
        view = null
        screen = Screen.GAME
        publish(g.start())
    }

    private fun btIntro(): String =
        if (seats.duo) "Partida em duplas pelo Bluetooth. Seu parceiro: ${seats.noun(2)}. "
        else "Partida pelo Bluetooth contra ${seats.noun(1)}. "

    override fun onRoster() {
        roomVersion++
        if (mode == Mode.LOCAL) return
        val now = room.roster
        val old = lastRoster
        lastRoster = now
        if (now.isEmpty()) return
        if (old.isEmpty()) {
            if (mode == Mode.GUEST) announce("Você entrou na sala de ${now.first()}.")
            return
        }
        val parts = now.filterNot { it in old }.map { "$it entrou na sala." } +
            old.filterNot { it in now }.map { "$it saiu da sala." }
        if (parts.isNotEmpty()) announce(parts.joinToString(" "))
    }

    override fun onRejected(reason: String) {
        roomVersion++
        announce(reason)
    }

    override fun onStart(players: Int, seat: Int, names: List<String?>) {
        if (mode != Mode.GUEST) return
        stopGame()
        useSeats(Seats(players, rotate(names, seat)))
        matchIntro = btIntro()
        view = null
        screen = Screen.GAME
    }

    override fun onState(events: List<GameEvent>, view: TableView) {
        if (mode != Mode.GUEST) return
        awaitingHost = false
        present(events, view)
    }

    override fun onMove(seat: Int, move: Move) {
        if (mode == Mode.HOST) game?.apply(seat, move)?.let(::publish)
    }

    override fun onEnd(reason: String) {
        if (mode == Mode.LOCAL) return
        stopGame()
        game = null
        view = null
        guestSeats = emptyList()
        status = ""
        screen = Screen.BLUETOOTH
        roomVersion++
        announce(reason)
    }

    // ---------------- Repetir

    fun describe() {
        val v = view ?: run {
            say("Esperando o anfitrião começar.", flush = true)
            return
        }
        val sb = StringBuilder("Placar: ${narrator.score(v.score)}. ${handValueText(v)}. ")
        if (v.dealing) {
            say(sb.append("As cartas estão sendo dadas."), flush = true)
            return
        }
        sb.append(narrator.vira(v.vira)).append(' ')
        sb.append(tableText(v)).append(' ')
        sb.append(myCardsText(v)).append(' ')
        partnerCardsText(v)?.let { sb.append(it).append(' ') }
        sb.append(situation(v))
        say(sb, flush = true)
    }

    private fun situation(v: TableView): String {
        val s = seats
        val p = v.pending
        return when {
            v.matchOver -> if (isGuest) "A partida terminou. O anfitrião pode começar outra." else "A partida terminou."
            v.handOver -> "A mão terminou. Use o botão Próxima mão."
            v.elevenMine -> "Decida se joga a mão de onze."
            v.awaitingEleven -> "${s.sideWho(1)} ${s.verb(1, "está", "estão")} decidindo a mão de onze."
            p != null && v.askedMe -> {
                val ask = if (v.canEscalate) "Aceita, aumenta ou corre?" else "Aceita ou corre?"
                "${s.sideWho(p.requesterSide)} ${s.verb(p.requesterSide, "pediu", "pediram")} ${Words.raiseName(p.value)}. $ask"
            }
            p != null -> "${s.sideWho(p.requesterSide)} ${s.verb(p.requesterSide, "pediu", "pediram")} ${Words.raiseName(p.value)}. Esperando ${s.them}."
            v.canPlay -> "Sua vez."
            else -> "Vez ${s.of(v.current)}."
        }
    }

    // ---------------- Textos da tela

    fun scoreDisplay(v: TableView): String =
        "${seats.sideShort(0)} ${v.score[0]}  ×  ${v.score[1]} ${seats.sideShort(1)}"

    fun scoreDescription(v: TableView): String =
        narrator.score(v.score).replaceFirstChar { it.uppercase() } + ". Partida até $WINNING_SCORE."

    fun runElevenQuestion(): String =
        "Deseja correr da mão de onze? ${seats.them.replaceFirstChar { it.uppercase() }} ${seats.themVerb("ganha", "ganham")} 1 ponto."

    fun exitQuestion(): String =
        if (mode == Mode.LOCAL) "Deseja sair da partida?" else "Deseja sair? A partida termina para todos."

    fun handValueText(v: TableView): String {
        if (v.awaitingEleven) return "Mão de onze: vale 3 se for jogada"
        val mode = when (v.mode) {
            HandMode.MAO_DE_ONZE -> ", mão de onze"
            HandMode.MAO_DE_FERRO -> ", mão de ferro"
            HandMode.NORMAL -> ""
        }
        val p = v.pending
        val ask = if (p != null && !v.handOver) ", pedido de ${Words.raiseName(p.value)} esperando resposta" else ""
        return "Mão valendo ${Words.points(v.value)}$mode$ask"
    }

    fun viraText(v: TableView): String = narrator.vira(v.vira)

    fun cardLabel(v: TableView, card: Card, index: Int): String =
        if (v.blind) "Carta ${index + 1}, coberta" else Words.card(card, v.vira)

    fun cardsCount(n: Int): String = when (n) {
        0 -> "nenhuma carta"
        1 -> "1 carta"
        else -> "$n cartas"
    }

    private fun playLabel(v: TableView, p: PlayView): String {
        val what = when {
            p.card == null -> "carta coberta"
            p.covered -> "${Words.cardLower(p.card, v.vira)}, coberta"
            else -> Words.cardLower(p.card, v.vira)
        }
        return "${seats.noun(p.player)}: $what"
    }

    fun tableText(v: TableView): String {
        val sb = StringBuilder()
        v.tricks.forEachIndexed { i, t ->
            sb.append(Words.trickOrdinal(i).replaceFirstChar { it.uppercase() }).append(" rodada, ")
            sb.append(t.plays.joinToString(", ") { playLabel(v, it) })
            val wp = t.winnerPlayer
            sb.append(if (wp == null) ". Cangou. " else ". ${seats.who(wp)} ganhou. ")
        }
        if (v.table.isNotEmpty()) sb.append("Na mesa: ").append(v.table.joinToString(", ") { playLabel(v, it) }).append(". ")
        else if (!v.handOver) sb.append("Mesa vazia. ")
        sb.append(othersCardsText(v))
        return sb.toString()
    }

    private fun othersCardsText(v: TableView): String =
        if (!seats.duo) "${seats.who(1)} tem ${cardsCount(v.counts[1])}."
        else "Cartas na mão: " + (1 until v.players).joinToString(", ") { "${seats.noun(it)} com ${cardsCount(v.counts[it])}" } + "."

    fun othersCardsShort(v: TableView): String =
        if (!seats.duo) "${seats.shortName(1)}: ${cardsCount(v.counts[1])}"
        else (1 until v.players).joinToString("  ·  ") { "${seats.shortName(it)} ${v.counts[it]}" }

    fun tricksLine(v: TableView): String = v.tricks.mapIndexed { i, t ->
        "${i + 1}ª: " + (t.winnerSide?.let { seats.sideShort(it).lowercase() } ?: "cangou")
    }.joinToString("  ·  ")

    private fun myCardsText(v: TableView): String = when {
        v.myCards.isEmpty() -> "Você não tem mais cartas."
        v.blind -> "Suas cartas: ${cardsCount(v.myCards.size)} cobertas."
        else -> "Suas cartas: " + v.myCards.joinToString(", ") { Words.cardLower(it, v.vira) } + "."
    }

    /** Na mão de onze da sua dupla, você vê as cartas do parceiro antes de decidir. */
    fun partnerCardsText(v: TableView): String? =
        if (v.partnerCards.isEmpty()) null
        else "Cartas do parceiro: " + v.partnerCards.joinToString(", ") { Words.cardLower(it, v.vira) } + "."

    // ---------------- Utilidades

    private fun focusActions() = afterSpeech { if (screenReaderOn) actionFocus++ }

    private fun bump() { version++ }

    private fun say(text: CharSequence, flush: Boolean = false) {
        val s = text.toString().trim()
        if (s.isEmpty()) return
        status = s
        if (voiceActive) voice.speak(s, flush)
    }

    /** Aviso da sala do Bluetooth: aparece na tela e a voz fala. */
    private fun announce(text: String) {
        lobbyMessage = text
        if (voiceActive) voice.speak(text)
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
        link.closeAll()
        gameJobs.cancel()
        voice.shutdown()
        sfx.release()
    }

    private companion object {
        const val KEY_LEVEL = "nivel"
        const val KEY_VOICE = "voz"
        const val KEY_MUTED = "sons_mudos"
        const val KEY_COVERED = "carta_coberta"
        const val KEY_NAME = "nome"
        const val SHUFFLE_MS = 1100L
    }
}

package com.pdonizete.truco.bt

import com.pdonizete.truco.game.GameEvent
import com.pdonizete.truco.game.Move
import com.pdonizete.truco.game.TableView
import com.pdonizete.truco.game.decodeEvent
import com.pdonizete.truco.game.decodeMove
import com.pdonizete.truco.game.encode

/** Mensagens trocadas pelo Bluetooth: uma por linha, campos separados por |. */
object Proto {
    const val VERSION = 1
    const val HELLO = "HELLO"   // HELLO|versão|nome (convidado → anfitrião)
    const val REJECT = "REJECT" // REJECT|motivo
    const val ROSTER = "ROSTER" // ROSTER|nome,nome,... (anfitrião primeiro)
    const val START = "START"   // START|jogadores|seu lugar|nome de cada lugar (vazio = computador)
    const val STATE = "STATE"   // STATE|eventos separados por /|visão da mesa
    const val MOVE = "MOVE"     // MOVE|jogada
    const val END = "END"       // END|motivo
}

/** Envia linhas pelas conexões. No app é o [BtLink]; nos testes, uma rede falsa. */
interface Transport {
    fun send(link: Int, line: String)
    fun close(link: Int)
}

/** Marca o anfitrião na lista de lugares. */
const val HOST = -1

/**
 * Lugares da partida: HOST, o número da conexão de um amigo, ou null para o computador.
 * 1 contra 1: anfitrião e o único amigo. Duplas: o anfitrião no lugar 0, o parceiro no 2
 * (o escolhido ou o primeiro amigo) e os outros amigos nos lugares 1 e 3.
 */
fun seatPlan(duo: Boolean, friends: List<Int>, partner: Int?): List<Int?> {
    if (!duo) {
        require(friends.size == 1) { "1 contra 1 precisa de exatamente um amigo." }
        return listOf(HOST, friends[0])
    }
    require(friends.size in 1..3) { "Duplas precisam de 1 a 3 amigos." }
    val p = partner?.takeIf { it in friends } ?: friends.first()
    val rest = friends - p
    return listOf(HOST, rest.getOrNull(0), p, rest.getOrNull(1))
}

/** A mesma lista vista a partir do lugar [seat] (ele passa a ser o índice 0). */
fun <T> rotate(list: List<T>, seat: Int): List<T> = List(list.size) { list[(it + seat) % list.size] }

/** Lugares ([seats]) e nomes de cada lugar ([names], null = computador). */
data class Plan(val seats: List<Int?>, val names: List<String?>)

/**
 * Sala do Bluetooth em estrela: o anfitrião aceita até 3 amigos e é a autoridade da partida.
 * Cada convidado recebe só a própria visão da mesa e manda as jogadas ao anfitrião.
 * Sem Android: testada com uma rede falsa. Linhas malformadas são ignoradas.
 */
class BtRoom(private val net: Transport, private val listener: Listener) {
    interface Listener {
        fun onRoster() {}
        fun onRejected(reason: String) {}
        /** Convidado: a partida começou. [names] tem o nome de cada lugar (null = computador). */
        fun onStart(players: Int, seat: Int, names: List<String?>) {}
        /** Convidado: novos eventos e a visão atual da mesa. */
        fun onState(events: List<GameEvent>, view: TableView) {}
        /** Anfitrião: jogada de um convidado. */
        fun onMove(seat: Int, move: Move) {}
        fun onEnd(reason: String) {}
    }

    var myName: String = "Jogador"
        set(v) { field = clean(v) }

    var isHost = false; private set
    var inGame = false; private set

    /** Convidado: conexão com o anfitrião. */
    var hostLink: Int? = null; private set

    private val friendNames = LinkedHashMap<Int, String>()
    private var seatOfLink: Map<Int, Int> = emptyMap()

    /** Anfitrião: conexões dos amigos, na ordem de entrada. */
    val friends: List<Int> get() = friendNames.keys.toList()
    fun friendName(link: Int): String = friendNames[link] ?: "Amigo"

    /** Nomes na sala, anfitrião primeiro. */
    var roster: List<String> = emptyList(); private set

    val connected: Boolean get() = if (isHost) friendNames.isNotEmpty() else hostLink != null

    fun host() {
        reset()
        isHost = true
        roster = listOf(myName)
        listener.onRoster()
    }

    fun guest() {
        reset()
        isHost = false
        listener.onRoster()
    }

    /** Esquece a sala. Quem chama fecha as conexões. */
    fun reset() {
        friendNames.clear()
        seatOfLink = emptyMap()
        hostLink = null
        inGame = false
        roster = emptyList()
    }

    // ---------------- Eventos das conexões

    fun onOpened(link: Int, accepted: Boolean) {
        if (isHost && accepted) return // espera o HELLO
        if (!isHost && !accepted && hostLink == null) {
            hostLink = link
            net.send(link, "${Proto.HELLO}|${Proto.VERSION}|$myName")
            return
        }
        net.close(link)
    }

    fun onLine(link: Int, text: String) {
        val f = text.split('|')
        runCatching {
            if (isHost) fromGuest(link, f) else if (link == hostLink) fromHost(link, f)
        }
    }

    fun onClosed(link: Int) {
        if (isHost) {
            val name = friendNames.remove(link) ?: return
            if (inGame) {
                inGame = false
                val reason = "$name saiu. A partida terminou."
                broadcast("${Proto.END}|$reason")
                listener.onEnd(reason)
            }
            rosterChanged()
        } else if (link == hostLink) {
            hostLink = null
            roster = emptyList()
            val was = inGame
            inGame = false
            listener.onEnd(if (was) "A conexão com o anfitrião caiu. A partida terminou." else "A conexão com a sala caiu.")
            listener.onRoster()
        }
    }

    private fun fromGuest(link: Int, f: List<String>) {
        when (f[0]) {
            Proto.HELLO -> {
                if (link in friendNames) return
                val reason = when {
                    f.getOrNull(1)?.toIntOrNull() != Proto.VERSION ->
                        "Versões diferentes do Truco Acessível. Atualizem o aplicativo nos dois aparelhos."
                    inGame -> "A partida já começou."
                    friendNames.size >= 3 -> "A sala está cheia."
                    else -> null
                }
                // Quem recebe a recusa fecha a conexão, para a mensagem chegar antes.
                if (reason != null) {
                    net.send(link, "${Proto.REJECT}|$reason")
                    return
                }
                friendNames[link] = clean(f.getOrNull(2).orEmpty())
                rosterChanged()
            }
            Proto.MOVE -> {
                val seat = seatOfLink[link] ?: return
                if (inGame) listener.onMove(seat, decodeMove(f[1]))
            }
        }
    }

    private fun fromHost(link: Int, f: List<String>) {
        when (f[0]) {
            Proto.ROSTER -> {
                roster = f[1].split(',').filter { it.isNotEmpty() }
                listener.onRoster()
            }
            Proto.REJECT -> {
                hostLink = null
                net.close(link)
                listener.onRejected(f.getOrElse(1) { "" })
            }
            Proto.START -> {
                val players = f[1].toInt()
                val seat = f[2].toInt()
                val names = f[3].split(',').map { it.ifEmpty { null } }
                require(players == 2 || players == 4) { "Partida inválida." }
                require(seat in 0 until players && names.size == players) { "Lugar inválido." }
                inGame = true
                listener.onStart(players, seat, names)
            }
            Proto.STATE -> {
                val events = if (f[1].isEmpty()) emptyList() else f[1].split('/').map(::decodeEvent)
                listener.onState(events, TableView.decode(f[2]))
            }
            Proto.END -> {
                inGame = false
                listener.onEnd(f.getOrElse(1) { "" })
            }
        }
    }

    // ---------------- Anfitrião

    fun canStart(duo: Boolean): Boolean =
        isHost && if (duo) friendNames.size in 1..3 else friendNames.size == 1

    /** Começa (ou recomeça) a partida e avisa cada convidado do seu lugar. */
    fun start(duo: Boolean, partner: Int?): Plan? {
        if (!canStart(duo)) return null
        val seats = seatPlan(duo, friends, partner)
        val names = seats.map {
            when (it) {
                null -> null
                HOST -> myName
                else -> friendNames.getValue(it)
            }
        }
        seatOfLink = seats.withIndex()
            .filter { it.value != null && it.value != HOST }
            .associate { it.value!! to it.index }
        inGame = true
        val encoded = names.joinToString(",") { it.orEmpty() }
        for ((link, seat) in seatOfLink) net.send(link, "${Proto.START}|${seats.size}|$seat|$encoded")
        return Plan(seats, names)
    }

    /** Manda ao convidado do lugar [seat] os eventos (já vistos por ele) e a visão dele da mesa. */
    fun sendState(seat: Int, events: List<GameEvent>, view: TableView) {
        val link = seatOfLink.entries.firstOrNull { it.value == seat }?.key ?: return
        net.send(link, "${Proto.STATE}|${events.joinToString("/") { it.encode() }}|${view.encode()}")
    }

    /** Anfitrião: termina a partida e volta todos para a sala. */
    fun endGame(reason: String) {
        if (!isHost) return
        inGame = false
        broadcast("${Proto.END}|${clean(reason, 200)}")
    }

    // ---------------- Convidado

    fun sendMove(move: Move) {
        hostLink?.let { net.send(it, "${Proto.MOVE}|${move.encode()}") }
    }

    // ---------------- Auxiliares

    private fun rosterChanged() {
        roster = listOf(myName) + friendNames.values
        broadcast("${Proto.ROSTER}|${roster.joinToString(",")}")
        listener.onRoster()
    }

    private fun broadcast(line: String) = friendNames.keys.forEach { net.send(it, line) }

    companion object {
        /** Tira os separadores do protocolo e os caracteres de controle, e limita o tamanho. */
        fun clean(s: String, max: Int = 20): String =
            s.filter { it >= ' ' }.replace(Regex("[|,;/~.:]"), " ").trim().take(max).trim().ifEmpty { "Jogador" }
    }
}

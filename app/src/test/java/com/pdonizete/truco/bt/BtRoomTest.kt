package com.pdonizete.truco.bt

import com.pdonizete.truco.game.Difficulty
import com.pdonizete.truco.game.GameEvent
import com.pdonizete.truco.game.GameHost
import com.pdonizete.truco.game.Move
import com.pdonizete.truco.game.Response
import com.pdonizete.truco.game.TableView
import com.pdonizete.truco.game.forViewer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Rede falsa: entrega as linhas em ordem, como o Bluetooth faria. */
private class FakeNet {
    val queue = ArrayDeque<() -> Unit>()
    private var next = 1

    inner class Node(name: String) : Transport, BtRoom.Listener {
        val room = BtRoom(this, this).also { it.myName = name }
        val peers = HashMap<Int, Pair<Node, Int>>()
        val rejected = mutableListOf<String>()
        val ended = mutableListOf<String>()
        var started: Triple<Int, Int, List<String?>>? = null
        var lastView: TableView? = null
        val events = mutableListOf<GameEvent>()
        val moves = ArrayDeque<Pair<Int, Move>>()

        override fun send(link: Int, line: String) {
            val (other, otherLink) = peers[link] ?: return
            queue += { other.room.onLine(otherLink, line) }
        }

        override fun close(link: Int) {
            val (other, otherLink) = peers.remove(link) ?: return
            other.peers.remove(otherLink)
            queue += { room.onClosed(link); other.room.onClosed(otherLink) }
        }

        fun leave() = peers.keys.toList().forEach(::close)

        override fun onRejected(reason: String) { rejected += reason }
        override fun onStart(players: Int, seat: Int, names: List<String?>) { started = Triple(players, seat, names) }
        override fun onState(events: List<GameEvent>, view: TableView) { this.events += events; lastView = view }
        override fun onMove(seat: Int, move: Move) { moves += seat to move }
        override fun onEnd(reason: String) { ended += reason }
    }

    fun connect(guest: Node, host: Node) {
        val a = next++
        val b = next++
        guest.peers[a] = host to b
        host.peers[b] = guest to a
        guest.room.onOpened(a, accepted = false)
        host.room.onOpened(b, accepted = true)
        run()
    }

    fun run() {
        var guard = 0
        while (queue.isNotEmpty()) {
            queue.removeFirst()()
            check(guard++ < 100_000)
        }
    }
}

class BtRoomTest {
    private fun room(vararg guests: String): Pair<FakeNet, List<FakeNet.Node>> {
        val net = FakeNet()
        val host = net.Node("Paulo").also { it.room.host() }
        val nodes = listOf(host) + guests.map { g -> net.Node(g).also { it.room.guest(); net.connect(it, host) } }
        return net to nodes
    }

    @Test fun guestsJoinAndEveryoneSeesTheRoster() {
        val (_, n) = room("Ana")
        assertEquals(listOf("Paulo", "Ana"), n[0].room.roster)
        assertEquals(listOf("Paulo", "Ana"), n[1].room.roster)
        assertTrue(n[0].room.canStart(duo = false))
        assertTrue(n[0].room.canStart(duo = true))
        assertFalse(n[1].room.canStart(duo = false))
    }

    @Test fun fourthFriendAndWrongVersionAreRejected() {
        val (_, n) = room("Ana", "Bia", "Caio", "Davi")
        assertEquals(listOf("Paulo", "Ana", "Bia", "Caio"), n[0].room.roster)
        assertEquals(listOf("A sala está cheia."), n[4].rejected)
        assertNull(n[4].room.hostLink)
        assertFalse(n[0].room.canStart(duo = false))

        val (_, m) = room()
        m[0].room.onOpened(99, accepted = true)
        m[0].room.onLine(99, "HELLO|0|Velho")
        assertEquals(listOf("Paulo"), m[0].room.roster)
    }

    @Test fun seatPlansAndRotation() {
        assertEquals(listOf(HOST, 5), seatPlan(false, listOf(5), null))
        assertEquals(listOf(HOST, null, 5, null), seatPlan(true, listOf(5), null))
        assertEquals(listOf(HOST, 5, 7, null), seatPlan(true, listOf(5, 7), 7))
        assertEquals(listOf(HOST, 7, 5, 9), seatPlan(true, listOf(5, 7, 9), null))
        assertEquals(listOf("c", "d", "a", "b"), rotate(listOf("a", "b", "c", "d"), 2))
    }

    @Test fun startTellsEachGuestItsSeat() {
        val (net, n) = room("Ana", "Bia")
        val bia = n[0].room.friends[1]
        val plan = n[0].room.start(duo = true, partner = bia)!!
        net.run()
        val names = listOf("Paulo", "Ana", "Bia", null)
        assertEquals(names, plan.names)
        assertEquals(Triple(4, 1, names), n[1].started)
        assertEquals(Triple(4, 2, names), n[2].started)
        assertTrue(n[1].room.inGame)
    }

    @Test fun stateGoesOnlyToItsSeatAndMovesComeBackWithTheSeat() {
        val (net, n) = room("Ana")
        n[0].room.start(duo = false, partner = null)
        val game = GameHost(2, setOf(0, 1), Difficulty.MEDIO, false, Random(1))
        n[0].room.sendState(1, game.start(), game.view(1))
        net.run()
        assertEquals(game.view(1), n[1].lastView)
        assertEquals(listOf<GameEvent>(GameEvent.Shuffle(true)), n[1].events)
        n[1].room.sendMove(Move.Answer(Response.ACEITAR))
        net.run()
        assertEquals(1 to Move.Answer(Response.ACEITAR), n[0].moves.single())
    }

    @Test fun leavingDuringTheGameEndsItForEveryone() {
        val (net, n) = room("Ana", "Bia")
        n[0].room.start(duo = true, partner = null)
        net.run()
        n[1].leave()
        net.run()
        assertFalse(n[0].room.inGame)
        assertEquals(listOf("Ana saiu. A partida terminou."), n[0].ended)
        assertEquals(listOf("Ana saiu. A partida terminou."), n[2].ended)
        assertEquals(listOf("A conexão com o anfitrião caiu. A partida terminou."), n[1].ended)
        assertEquals(listOf("Paulo", "Bia"), n[2].room.roster)
    }

    @Test fun malformedLinesAreIgnored() {
        val (net, n) = room("Ana")
        n[0].room.start(duo = false, partner = null)
        net.run()
        val link = n[0].room.friends[0]
        n[0].room.onLine(link, "MOVE|xyz")
        n[0].room.onLine(link, "MOVE")
        n[1].room.onLine(n[1].room.hostLink!!, "STATE|zz|qq")
        n[1].room.onLine(n[1].room.hostLink!!, "START|7|9|a")
        assertTrue(n[0].moves.isEmpty())
        assertNull(n[1].lastView)
        assertEquals(2, n[1].started!!.first)
    }

    @Test fun namesCannotBreakTheProtocol() {
        assertEquals("A B C", BtRoom.clean("A|B,C"))
        assertEquals("Jogador", BtRoom.clean(" ;/ "))
        assertEquals(20, BtRoom.clean("x".repeat(50)).length)
    }

    @Test fun fullMatchesOverTheFakeNetwork() {
        repeat(15) { seed ->
            playMatch(2, seed)
            playMatch(4, seed)
        }
    }

    /** Escolha simples de uma pessoa a partir da visão dela. */
    private fun choose(v: TableView): Move? = when {
        v.dealing -> null
        v.handOver && !v.matchOver -> Move.Next
        v.elevenMine -> Move.Eleven(true)
        v.askedMe -> Move.Answer(Response.ACEITAR)
        v.canPlay -> Move.Play(v.myCards.first(), covered = v.canCover && v.myCards.size == 1)
        else -> null
    }

    private fun playMatch(players: Int, seed: Int) {
        val (net, n) = room("Ana")
        val host = n[0]
        val guest = n[1]
        val plan = host.room.start(duo = players == 4, partner = null)!!
        net.run()
        val humans = plan.seats.indices.filter { plan.seats[it] != null }.toSet()
        val guestSeat = plan.seats.indexOfFirst { it != null && it != HOST }
        assertEquals(guestSeat, guest.started!!.second)
        val game = GameHost(players, humans, Difficulty.MEDIO, seed % 2 == 0, Random(seed))

        fun publish(ev: List<GameEvent>) {
            host.room.sendState(guestSeat, ev.map { it.forViewer(guestSeat, players) }, game.view(guestSeat))
            net.run()
            if (ev.any { it is GameEvent.Shuffle }) publish(game.handStart())
        }

        publish(game.start())
        var guard = 0
        while (!game.match.isOver) {
            assertTrue(guard++ < 3000)
            val cpu = game.cpuStep()
            if (cpu != null) {
                publish(cpu)
                continue
            }
            // O convidado vê exatamente a visão dele, com só as cartas dele.
            assertEquals(game.view(guestSeat), guest.lastView)
            assertEquals(game.match.hand.hands[guestSeat], guest.lastView!!.myCards)
            val mine = choose(game.view(0))?.let { game.apply(0, it) }
            if (mine != null) {
                publish(mine)
                continue
            }
            val move = choose(guest.lastView!!)
            assertNotNull("convidado sem jogada no lugar $guestSeat", move)
            guest.room.sendMove(move!!)
            net.run()
            val (seat, received) = host.moves.removeFirst()
            assertEquals(guestSeat, seat)
            publish(game.apply(seat, received)!!)
        }
        assertTrue(guest.events.any { it is GameEvent.MatchEnd })
    }
}

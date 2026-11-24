package com.pdonizete.truco.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Aparelho encontrado na busca. */
data class BtDevice(val name: String, val address: String, internal val device: BluetoothDevice)

/** Eventos das conexões. Cada conexão tem um número próprio. */
sealed interface LinkEvent {
    /** [accepted] = true quando o outro aparelho conectou neste (este é o anfitrião). */
    data class Opened(val id: Int, val accepted: Boolean) : LinkEvent
    data class Line(val id: Int, val text: String) : LinkEvent
    data class Closed(val id: Int) : LinkEvent
}

/**
 * Bluetooth clássico (RFCOMM), uma linha de texto por mensagem.
 * O anfitrião abre a sala e aceita várias conexões; o convidado busca e conecta.
 * A tela pede as permissões antes de chamar estes métodos.
 */
@SuppressLint("MissingPermission")
class BtLink(context: Context) : Transport {
    private val app = context.applicationContext
    private val adapter: BluetoothAdapter? =
        (app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _events = MutableSharedFlow<LinkEvent>(extraBufferCapacity = 256)
    val events: SharedFlow<LinkEvent> = _events
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors: SharedFlow<String> = _errors
    private val _devices = MutableStateFlow<List<BtDevice>>(emptyList())
    val devices: StateFlow<List<BtDevice>> = _devices
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning
    private val _connecting = MutableStateFlow<String?>(null)
    val connecting: StateFlow<String?> = _connecting

    private class Conn(val socket: BluetoothSocket, val outbox: Channel<String>)

    private val conns = ConcurrentHashMap<Int, Conn>()
    private val ids = AtomicInteger()
    @Volatile private var server: BluetoothServerSocket? = null
    private var receiverOn = false

    val available: Boolean get() = adapter != null
    val enabled: Boolean get() = adapter?.isEnabled == true
    val hosting: Boolean get() = server != null

    /** Nome deste aparelho no Bluetooth, que os amigos veem na busca. */
    val deviceName: String get() = runCatching { adapter?.name }.getOrNull() ?: "este celular"

    val discoverable: Boolean
        get() = runCatching { adapter?.scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE }.getOrDefault(false)

    // ---------------- Busca

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val d: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) {
                        i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION") i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    d?.let(::addDevice)
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> _scanning.value = false
            }
        }
    }

    private fun addDevice(d: BluetoothDevice) {
        val name = runCatching { d.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: return
        if (_devices.value.any { it.address == d.address }) return
        _devices.value = _devices.value + BtDevice(name, d.address, d)
    }

    fun startScan() {
        val a = adapter ?: return
        if (!receiverOn) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
            receiverOn = true
        }
        _devices.value = emptyList()
        runCatching { a.bondedDevices }.getOrNull()?.forEach(::addDevice)
        runCatching { if (a.isDiscovering) a.cancelDiscovery() }
        _scanning.value = runCatching { a.startDiscovery() }.getOrDefault(false)
    }

    fun stopScan() {
        runCatching { adapter?.cancelDiscovery() }
        _scanning.value = false
        if (receiverOn) {
            runCatching { app.unregisterReceiver(receiver) }
            receiverOn = false
        }
    }

    // ---------------- Sala e conexões

    /** Abre a sala: aceita conexões até [closeAll]. */
    fun openRoom() {
        val a = adapter ?: return
        if (server != null) return
        val srv = runCatching { a.listenUsingRfcommWithServiceRecord(SERVICE_NAME, APP_UUID) }.getOrElse {
            _errors.tryEmit("Não foi possível abrir a sala. Confira se o Bluetooth está ligado.")
            return
        }
        server = srv
        scope.launch {
            while (server === srv) {
                val s = runCatching { srv.accept() }.getOrNull() ?: break
                attach(s, accepted = true)
            }
            if (server === srv) server = null
        }
    }

    /** Conecta na sala aberta no aparelho [dev]. */
    fun connect(dev: BtDevice) {
        stopScan()
        if (_connecting.value != null) return
        _connecting.value = dev.name
        scope.launch {
            try {
                val s = dev.device.createRfcommSocketToServiceRecord(APP_UUID)
                s.connect()
                _connecting.value = null
                attach(s, accepted = false)
            } catch (e: Exception) {
                _connecting.value = null
                _errors.tryEmit("Não foi possível conectar a ${dev.name}. Confira se a sala está aberta nesse celular.")
            }
        }
    }

    private fun attach(s: BluetoothSocket, accepted: Boolean) {
        val id = ids.incrementAndGet()
        val c = Conn(s, Channel(Channel.UNLIMITED))
        conns[id] = c
        _events.tryEmit(LinkEvent.Opened(id, accepted))
        // Escrita: uma linha de cada vez, na ordem.
        scope.launch {
            runCatching {
                val out = s.outputStream
                for (line in c.outbox) {
                    out.write((line + "\n").toByteArray(Charsets.UTF_8))
                    out.flush()
                }
            }.onFailure { close(id) }
        }
        // Leitura: cada linha vira um evento. Linhas longas demais derrubam a conexão.
        scope.launch {
            runCatching {
                s.inputStream.bufferedReader(Charsets.UTF_8).use { r ->
                    while (true) {
                        val line = r.readLine() ?: break
                        if (line.length > MAX_LINE) break
                        _events.emit(LinkEvent.Line(id, line))
                    }
                }
            }
            close(id)
        }
    }

    override fun send(link: Int, line: String) {
        conns[link]?.outbox?.trySend(line)
    }

    override fun close(link: Int) {
        val c = conns.remove(link) ?: return
        c.outbox.close()
        runCatching { c.socket.close() }
        _events.tryEmit(LinkEvent.Closed(link))
    }

    /** Fecha a sala, a busca e todas as conexões. */
    fun closeAll() {
        stopScan()
        val srv = server
        server = null
        runCatching { srv?.close() }
        conns.keys.toList().forEach(::close)
        _connecting.value = null
    }

    companion object {
        const val SERVICE_NAME = "TrucoAcessivel"
        val APP_UUID: UUID = UUID.fromString("3b7d5e2a-91c4-4f6e-a8d2-6c1f0e9b4a57")
        const val MAX_LINE = 16_000
    }
}

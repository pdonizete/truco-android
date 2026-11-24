package com.pdonizete.truco.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.pdonizete.truco.bt.BtDevice

private val BT_PERMISSIONS: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_ADVERTISE,
    ) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

/** Sala do Bluetooth: criar a sala (anfitrião) ou entrar na sala de um amigo (convidado). */
@Composable
fun BluetoothScreen(vm: AppViewModel) {
    val askLeave = rememberConfirm("Deseja sair da sala?") { vm.backToMenu() }
    val leave = { if (vm.mode == Mode.LOCAL) vm.backToMenu() else askLeave() }
    BackHandler { leave() }
    val ctx = LocalContext.current
    val devices by vm.link.devices.collectAsState()
    val scanning by vm.link.scanning.collectAsState()
    val connecting by vm.link.connecting.collectAsState()
    @Suppress("UNUSED_VARIABLE") val rv = vm.roomVersion
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun granted() = BT_PERMISSIONS.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }

    val discoverable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val enable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val action = pending
        pending = null
        if (vm.link.enabled) action?.invoke() else vm.notice("Ligue o Bluetooth para jogar.")
    }
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (!granted()) {
            pending = null
            vm.notice("Sem a permissão de Bluetooth não dá para jogar. Libere nas configurações do celular.")
        } else if (vm.link.enabled) {
            val action = pending
            pending = null
            action?.invoke()
        } else {
            enable.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }

    /** Garante a permissão e o Bluetooth ligado antes de rodar [action]. */
    fun withBluetooth(action: () -> Unit) {
        if (!vm.link.available) {
            vm.notice("Este celular não tem Bluetooth.")
            return
        }
        when {
            !granted() -> { pending = action; perms.launch(BT_PERMISSIONS) }
            !vm.link.enabled -> { pending = action; enable.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
            else -> action()
        }
    }

    fun makeVisible() {
        if (vm.link.discoverable) return
        runCatching {
            discoverable.launch(
                Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                    .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300),
            )
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Bg)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .semantics { isTraversalGroup = true }
            .padding(20.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Jogar pelo Bluetooth", Modifier.weight(1f), size = 26)
            ExitButton("Voltar") { leave() }
        }
        vm.lobbyMessage?.let {
            Text(
                it, color = Palette.Status, fontSize = 17.sp,
                modifier = Modifier
                    .padding(vertical = 6.dp)
                    .then(if (vm.voiceActive) Modifier else Modifier.semantics { liveRegion = LiveRegionMode.Polite }),
            )
        }
        when (vm.mode) {
            Mode.LOCAL -> StartChoice(
                vm,
                onHost = { withBluetooth { vm.hostRoom(); makeVisible() } },
                onJoin = { withBluetooth { vm.joinRoom() } },
            )
            Mode.HOST -> HostLobby(vm) { makeVisible() }
            Mode.GUEST -> GuestLobby(vm, devices, scanning, connecting) { withBluetooth { vm.scan() } }
        }
    }
}

@Composable
private fun StartChoice(vm: AppViewModel, onHost: () -> Unit, onJoin: () -> Unit) {
    OutlinedTextField(
        value = vm.playerName,
        onValueChange = vm::changeName,
        label = { Text("Seu nome") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
    Text(
        "Um celular cria a sala e os amigos entram nela. Podem jogar até 4 pessoas. Os lugares vazios ficam com o computador.",
        fontSize = 17.sp,
    )
    BigButton("Criar sala", Palette.Start, onClick = onHost)
    BigButton("Entrar na sala de um amigo", onClick = onJoin)
}

@Composable
private fun HostLobby(vm: AppViewModel, makeVisible: () -> Unit) {
    val room = vm.room
    val friends = room.friends
    SectionTitle("Sua sala")
    Text(
        "A sala está aberta neste celular, que aparece para os amigos como ${vm.link.deviceName}. " +
            "Peça para eles tocarem em Entrar na sala de um amigo e escolherem esse nome.",
        fontSize = 17.sp,
    )
    BigButton("Ficar visível para os amigos", Palette.Muted, onClick = makeVisible)

    val n = room.roster.size
    SectionTitle("Na sala: $n ${if (n == 1) "pessoa" else "pessoas"}")
    room.roster.forEachIndexed { i, name -> Text(if (i == 0) "$name (você)" else name, fontSize = 18.sp) }

    SectionTitle("Partida")
    if (friends.isEmpty()) Text("Esperando os amigos entrarem…", fontSize = 17.sp)
    if (friends.size >= 2) {
        Text("Escolha o seu parceiro:", fontSize = 17.sp)
        val chosen = vm.partnerChoice?.takeIf { it in friends } ?: friends.first()
        Column(Modifier.selectableGroup()) {
            friends.forEach { f ->
                RadioRow(room.friendName(f), "Joga na sua dupla.", chosen == f) { vm.choosePartner(f) }
            }
        }
    }
    if (room.canStart(duo = false)) {
        BigButton("Começar 1 contra 1 com ${room.friendName(friends[0])}", Palette.Start) { vm.hostStart(false) }
    }
    if (room.canStart(duo = true)) {
        BigButton("Começar em duplas", Palette.Start) { vm.hostStart(true) }
        when (3 - friends.size) {
            1 -> Text("O lugar vazio fica com o computador.", fontSize = 16.sp)
            2 -> Text("Os 2 lugares vazios ficam com o computador.", fontSize = 16.sp)
        }
    }
    Text(
        "Nível do computador: ${vm.difficulty.label}. Carta coberta: ${if (vm.coveredOption) "ligada" else "desligada"}. " +
            "Mude nas Configurações antes de criar a sala.",
        fontSize = 16.sp,
        modifier = Modifier.padding(vertical = 6.dp),
    )
    BigButton("Fechar a sala", Palette.Danger) { vm.leaveRoom() }
}

@Composable
private fun GuestLobby(
    vm: AppViewModel,
    devices: List<BtDevice>,
    scanning: Boolean,
    connecting: String?,
    onScan: () -> Unit,
) {
    val room = vm.room
    if (room.hostLink != null && room.roster.isNotEmpty()) {
        SectionTitle("Na sala de ${room.roster.first()}")
        room.roster.forEachIndexed { i, name -> Text(if (i == 0) "$name (anfitrião)" else name, fontSize = 18.sp) }
        Text("Esperando o anfitrião começar a partida.", fontSize = 17.sp, modifier = Modifier.padding(vertical = 6.dp))
        BigButton("Sair da sala", Palette.Danger) { vm.leaveRoom() }
        return
    }
    SectionTitle("Procurar a sala")
    Text("Quem criou a sala precisa estar com a tela da sala aberta.", fontSize = 17.sp)
    BigButton(if (scanning) "Buscando…" else "Buscar de novo", Palette.Start, onClick = onScan)
    connecting?.let { Text("Conectando a $it…", fontSize = 17.sp) }
    if (devices.isNotEmpty()) {
        SectionTitle("Aparelhos encontrados")
        devices.forEach { d -> BigButton(d.name, Palette.Action) { vm.connectTo(d) } }
    } else if (!scanning) {
        Text("Nenhum aparelho encontrado ainda.", fontSize = 17.sp)
    }
    BigButton("Voltar", Palette.Muted) { vm.leaveRoom() }
}

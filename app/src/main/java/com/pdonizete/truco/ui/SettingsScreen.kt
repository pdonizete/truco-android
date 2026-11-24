package com.pdonizete.truco.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pdonizete.truco.game.Difficulty

private fun levelDetail(d: Difficulty) = when (d) {
    Difficulty.FACIL -> "Joga quase ao acaso e pede pouco truco."
    Difficulty.MEDIO -> "Ganha a rodada com a carta mais barata e guarda as fortes."
    Difficulty.DIFICIL -> "Calcula a chance de cada carta, pede truco com mais coragem e blefa mais."
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    BackHandler { vm.backToMenu() }
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
            SectionTitle("Configurações", Modifier.weight(1f), size = 28)
            ExitButton("Voltar") { vm.backToMenu() }
        }
        SectionTitle("Nível do computador")
        Column(Modifier.selectableGroup()) {
            Difficulty.entries.forEach { d ->
                RadioRow(d.label, levelDetail(d), vm.difficulty == d) { vm.changeDifficulty(d) }
            }
        }
        SectionTitle("Som e voz")
        SettingSwitch(
            "Voz do app",
            "Fala as jogadas quando o leitor de tela está ligado. Desligada, o leitor de tela lê os avisos da linha de status.",
            vm.voiceOn,
        ) { vm.changeVoice(it) }
        SettingSwitch(
            "Silenciar sons",
            "Cala os sons das cartas, do truco e dos aplausos. A voz continua.",
            vm.soundsMuted,
        ) { vm.changeSoundsMuted(it) }
        SectionTitle("Regras")
        SettingSwitch(
            "Carta coberta",
            "A partir da segunda rodada, você pode jogar uma carta virada para baixo. Ela perde para qualquer carta aberta. Vale a partir da próxima partida.",
            vm.coveredOption,
        ) { vm.changeCovered(it) }
    }
}

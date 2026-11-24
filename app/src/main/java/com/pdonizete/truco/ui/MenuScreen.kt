package com.pdonizete.truco.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pdonizete.truco.game.Card
import com.pdonizete.truco.game.Rank
import com.pdonizete.truco.game.Suit

@Composable
fun MenuScreen(vm: AppViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Bg)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Enfeite: fora da árvore de acessibilidade.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.clearAndSetSemantics { }) {
            val vira = Card(Rank.THREE, Suit.OUROS)
            CardFace(Card(Rank.FOUR, Suit.PAUS), vira, width = 64.dp)
            CardFace(Card(Rank.SEVEN, Suit.COPAS), null, width = 64.dp)
            CardFace(Card(Rank.ACE, Suit.ESPADAS), null, width = 64.dp)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Truco Acessível", fontSize = 36.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Text("Truco paulista para jogar com ou sem leitor de tela", fontSize = 17.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        BigButton("Jogar contra o computador, nível ${vm.difficulty.label}", Palette.Start) { vm.startMatch(2) }
        BigButton("Jogar em duplas, nível ${vm.difficulty.label}", Palette.Start) { vm.startMatch(4) }
        BigButton("Configurações") { vm.openSettings() }
        BigButton("Como jogar", Palette.Muted) { vm.openHelp() }
    }
}

package com.pdonizete.truco.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pdonizete.truco.game.Card
import com.pdonizete.truco.game.Response
import com.pdonizete.truco.game.Words
import com.pdonizete.truco.game.nextRaise

private class Action(val label: String, val color: Color, val onClick: () -> Unit)

@Composable
fun GameScreen(vm: AppViewModel) {
    val askExit = rememberConfirm("Deseja sair da partida?") { vm.backToMenu() }
    val askRun = rememberConfirm("Deseja mesmo correr?") { vm.respond(Response.CORRER) }
    val askRunEleven = rememberConfirm("Deseja correr da mão de onze? O computador ganha 1 ponto.") { vm.decideEleven(false) }
    BackHandler { askExit() }

    // A mão é mutável: ler [version] faz a tela se redesenhar a cada jogada.
    @Suppress("UNUSED_VARIABLE") val version = vm.version
    val m = vm.match ?: return
    val h = m.hand

    val handHeading = remember { FocusRequester() }
    val firstCard = remember { FocusRequester() }
    val firstAction = remember { FocusRequester() }
    FocusOnToken(handHeading, vm.headingFocus)
    FocusOnToken(firstCard, vm.handFocus)
    FocusOnToken(firstAction, vm.actionFocus)

    var choosing by remember { mutableStateOf<Card?>(null) }
    val chosen = choosing?.takeIf { h.canPlay(ME) && it in h.hands[ME] }
    if (chosen != null) {
        AlertDialog(
            onDismissRequest = { choosing = null },
            title = { Text("Jogar ${Words.cardLower(chosen, h.vira)} aberta ou coberta?", fontSize = 20.sp) },
            text = {
                Column {
                    BigButton("Aberta", Palette.Start) { choosing = null; vm.playCard(chosen, false) }
                    BigButton("Coberta", Palette.Muted) { choosing = null; vm.playCard(chosen, true) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosing = null }) { Text("Cancelar", fontSize = 18.sp) } },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Bg)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .semantics { isTraversalGroup = true }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Placar. O botão Sair fica no canto, mas por último na varredura.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Placar", Modifier.weight(1f))
            ExitButton("Sair") { askExit() }
        }
        Text(
            vm.scoreDisplay(),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clearAndSetSemantics { contentDescription = vm.scoreDescription() },
        )
        Text(vm.handValueText(h), fontSize = 18.sp, textAlign = TextAlign.Center)
        // Linha de status. Com a voz do app ligada, o TalkBack não repete o que a voz já falou;
        // com a voz desligada, ela vira região ativa e o TalkBack lê cada aviso.
        Text(
            vm.status,
            color = Palette.Status,
            fontStyle = FontStyle.Italic,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .padding(vertical = 4.dp)
                .then(
                    if (vm.voiceActive) Modifier.clearAndSetSemantics { }
                    else Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                ),
        )

        SectionTitle("Vira")
        if (vm.dealing) {
            Text("Dando as cartas…", fontSize = 18.sp)
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.clearAndSetSemantics { contentDescription = vm.viraText(h) },
            ) {
                CardFace(h.vira, null, width = 64.dp)
                Text("Manilha: ${Words.manilha(h.vira)}", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        SectionTitle("Mesa")
        if (!vm.dealing) TableArea(vm, h)

        SectionTitle("Suas cartas", Modifier.focusRequester(handHeading).focusable())
        when {
            vm.dealing -> Text("Dando as cartas…", fontSize = 18.sp)
            h.hands[ME].isEmpty() -> Text("Você não tem mais cartas.", fontSize = 18.sp)
            else -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                h.hands[ME].forEachIndexed { i, card ->
                    key(card.id) {
                        val label = vm.cardLabel(h, card, i)
                        val play = { if (vm.tapCard(card)) choosing = card }
                        CardFace(
                            card, h.vira, width = 92.dp, faceDown = h.blind,
                            modifier = Modifier
                                .then(if (i == 0) Modifier.focusRequester(firstCard) else Modifier)
                                .clip(RoundedCornerShape(11.dp))
                                .clickable(onClick = play)
                                .clearAndSetSemantics {
                                    contentDescription = label
                                    role = Role.Button
                                    onClick { play(); true }
                                },
                        )
                    }
                }
            }
        }

        SectionTitle("Ações")
        val actions = buildList {
            val p = h.pending
            when {
                vm.dealing -> {}
                m.isOver -> {
                    add(Action("Nova partida", Palette.Start) { vm.startMatch() })
                    add(Action("Voltar ao menu", Palette.Muted) { vm.backToMenu() })
                }
                h.isOver -> add(Action("Próxima mão", Palette.Start) { vm.nextHand() })
                h.awaitingEleven && h.elevenSide == ME -> {
                    add(Action("Jogar a mão de onze", Palette.Start) { vm.decideEleven(true) })
                    add(Action("Correr da mão de onze", Palette.Danger) { askRunEleven() })
                }
                p != null && p.responderSide == ME -> {
                    add(Action("Aceitar ${Words.raiseName(p.value)}", Palette.Start) { vm.respond(Response.ACEITAR) })
                    nextRaise(p.value)?.takeIf { h.canEscalate(ME) }?.let { up ->
                        add(Action("Aumentar para ${Words.raiseName(up)}", Palette.Action) { vm.respond(Response.AUMENTAR) })
                    }
                    add(Action("Correr", Palette.Danger) { askRun() })
                }
                h.canRaise(ME) -> add(Action("Pedir ${Words.raiseName(nextRaise(h.value)!!)}", Palette.Action) { vm.raise() })
            }
            add(Action("Repetir", Palette.Muted) { vm.describe() })
        }
        actions.forEachIndexed { i, a ->
            key(a.label) {
                BigButton(a.label, a.color, if (i == 0) Modifier.focusRequester(firstAction) else Modifier, a.onClick)
            }
        }
    }
}

/** Cartas da rodada em andamento (ou da última rodada, se a mesa acabou de ser limpa). */
@Composable
private fun TableArea(vm: AppViewModel, h: com.pdonizete.truco.game.Hand) {
    val plays = if (h.table.isEmpty() && h.tricks.isNotEmpty()) h.tricks.last().plays else h.table
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Felt)
            .padding(10.dp)
            .clearAndSetSemantics { contentDescription = vm.tableText(h) },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (plays.isEmpty()) {
                Text("Mesa vazia", fontSize = 18.sp)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    plays.forEach { p ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (p.player == ME) "Você" else "Computador", fontSize = 16.sp)
                            CardFace(p.card, h.vira, width = 72.dp, faceDown = p.covered)
                        }
                    }
                }
            }
            val line = vm.tricksLine(h)
            if (line.isNotEmpty()) Text("Rodadas: $line", fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp))
            Text("Computador: ${vm.cardsCount(h.hands[CPU].size)}", fontSize = 16.sp)
        }
    }
}

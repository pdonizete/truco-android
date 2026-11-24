package com.pdonizete.truco.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pdonizete.truco.game.Card
import com.pdonizete.truco.game.Hand
import com.pdonizete.truco.game.Response
import com.pdonizete.truco.game.Words
import com.pdonizete.truco.game.nextRaise

private class Action(val label: String, val color: Color, val onClick: () -> Unit)

/**
 * Tela de jogo. Cabe inteira na tela, sem rolar, para a exploração por toque achar tudo:
 * placar em cima, vira e mesa no meio, suas cartas e as ações fixas embaixo.
 */
@Composable
fun GameScreen(vm: AppViewModel) {
    val askExit = rememberConfirm("Deseja sair da partida?") { vm.backToMenu() }
    val askRun = rememberConfirm("Deseja mesmo correr?") { vm.respond(Response.CORRER) }
    val askRunEleven = rememberConfirm(vm.runElevenQuestion()) { vm.decideEleven(false) }
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

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Palette.Bg)
            .safeDrawingPadding(),
    ) {
        // Tamanho das cartas a partir da tela: três cartas na largura e espaço para o resto na altura.
        val handW = minOf(
            (maxWidth - 48.dp) / 3,
            (maxHeight - 400.dp).coerceAtLeast(120.dp) * 0.45f / 1.45f,
            120.dp,
        ).coerceAtLeast(52.dp)
        val tableW = handW * 0.75f

        Column(
            Modifier
                .fillMaxSize()
                .semantics { isTraversalGroup = true }
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Placar. O botão Sair fica no canto, mas por último na varredura.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Placar", size = 18, top = 4.dp)
                Text(
                    vm.scoreDisplay(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clearAndSetSemantics { contentDescription = vm.scoreDescription() },
                )
                ExitButton("Sair") { askExit() }
            }
            Text(vm.handValueText(h), fontSize = 16.sp, textAlign = TextAlign.Center)
            // Linha de status. Com a voz do app ligada, o TalkBack não repete o que a voz já falou;
            // com a voz desligada, ela vira região ativa e o TalkBack lê cada aviso.
            Text(
                vm.status,
                color = Palette.Status,
                fontStyle = FontStyle.Italic,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .padding(vertical = 2.dp)
                    .then(
                        if (vm.voiceActive) Modifier.clearAndSetSemantics { }
                        else Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    ),
            )

            // Vira à esquerda e mesa à direita. Cada coluna é um grupo, para o TalkBack ler
            // a vira inteira antes da mesa.
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(
                    Modifier.fillMaxHeight().semantics { isTraversalGroup = true },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SectionTitle("Vira", size = 18, top = 4.dp)
                    if (vm.dealing) {
                        Text("Dando as cartas…", fontSize = 16.sp)
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clearAndSetSemantics { contentDescription = vm.viraText(h) },
                        ) {
                            CardFace(h.vira, null, width = tableW)
                            Text("Manilha: ${Words.manilha(h.vira)}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Column(
                    Modifier.weight(1f).fillMaxHeight().semantics { isTraversalGroup = true },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SectionTitle("Mesa", size = 18, top = 4.dp)
                    if (!vm.dealing) TableArea(vm, h, tableW, Modifier.fillMaxWidth().weight(1f))
                }
            }

            SectionTitle("Suas cartas", Modifier.focusRequester(handHeading).focusable(), size = 18, top = 6.dp)
            Box(Modifier.heightIn(min = handW * 1.45f), contentAlignment = Alignment.Center) {
                when {
                    vm.dealing -> Text("Dando as cartas…", fontSize = 18.sp)
                    h.hands[ME].isEmpty() -> Text("Você não tem mais cartas.", fontSize = 18.sp)
                    else -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        h.hands[ME].forEachIndexed { i, card ->
                            key(card.id) {
                                val label = vm.cardLabel(h, card, i)
                                val play = { if (vm.tapCard(card)) choosing = card }
                                CardFace(
                                    card, h.vira, width = handW, faceDown = h.blind,
                                    modifier = Modifier
                                        .then(if (i == 0) Modifier.focusRequester(firstCard) else Modifier)
                                        .clip(RoundedCornerShape(handW * 0.12f))
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
            }

            vm.partnerCardsText(h)?.let { Text(it, fontSize = 15.sp, textAlign = TextAlign.Center) }

            SectionTitle("Ações", size = 18, top = 6.dp)
            actions.chunked(2).forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEachIndexed { c, a ->
                        key(a.label) {
                            BigButton(
                                a.label,
                                a.color,
                                Modifier
                                    .weight(1f)
                                    .then(if (r == 0 && c == 0) Modifier.focusRequester(firstAction) else Modifier),
                                a.onClick,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Cartas da rodada em andamento (ou da última rodada, se a mesa acabou de ser limpa). */
@Composable
private fun TableArea(vm: AppViewModel, h: Hand, cardMax: Dp, modifier: Modifier) {
    val plays = if (h.table.isEmpty() && h.tricks.isNotEmpty()) h.tricks.last().plays else h.table
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Felt)
            .padding(8.dp)
            .clearAndSetSemantics { contentDescription = vm.tableText(h) },
        contentAlignment = Alignment.Center,
    ) {
        // Duas cartas por fileira; com 3 ou 4 cartas (duplas) são duas fileiras.
        // O tamanho das cartas sai do espaço que a mesa tem na tela.
        val gap = 12.dp
        val rows = if (plays.size > 2) 2 else 1
        val byWidth = (maxWidth - gap) / 2
        val byHeight = ((maxHeight - 44.dp) / rows - 20.dp) / 1.45f
        val cardW = minOf(cardMax, byWidth, byHeight).coerceAtLeast(36.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (plays.isEmpty()) Text("Mesa vazia", fontSize = 16.sp)
            plays.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { p ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(vm.seats.shortName(p.player), fontSize = 14.sp, maxLines = 1)
                            CardFace(p.card, h.vira, width = cardW, faceDown = p.covered)
                        }
                    }
                }
            }
            val line = vm.tricksLine(h)
            if (line.isNotEmpty()) Text(line, fontSize = 14.sp, textAlign = TextAlign.Center)
            Text(vm.othersCardsShort(h), fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }
}

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
import com.pdonizete.truco.game.Response
import com.pdonizete.truco.game.TableView
import com.pdonizete.truco.game.Words
import com.pdonizete.truco.game.nextRaise

private class Action(val label: String, val color: Color, val onClick: () -> Unit)

/**
 * Tela de jogo. Cabe inteira na tela, sem rolar, para a exploração por toque achar tudo:
 * placar em cima, vira e mesa no meio, suas cartas e as ações fixas embaixo.
 * Desenha a visão da mesa ([TableView]), igual no jogo local e no Bluetooth.
 */
@Composable
fun GameScreen(vm: AppViewModel) {
    val askExit = rememberConfirm(vm.exitQuestion()) { vm.backToMenu() }
    val askRun = rememberConfirm("Deseja mesmo correr?") { vm.respond(Response.CORRER) }
    val askRunEleven = rememberConfirm(vm.runElevenQuestion()) { vm.decideEleven(false) }
    BackHandler { askExit() }

    @Suppress("UNUSED_VARIABLE") val version = vm.version
    val handHeading = remember { FocusRequester() }
    val firstCard = remember { FocusRequester() }
    val firstAction = remember { FocusRequester() }
    FocusOnToken(handHeading, vm.headingFocus)
    FocusOnToken(firstCard, vm.handFocus)
    FocusOnToken(firstAction, vm.actionFocus)
    var choosing by remember { mutableStateOf<Card?>(null) }

    val v = vm.view
    if (v == null) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Palette.Bg)
                .safeDrawingPadding()
                .semantics { isTraversalGroup = true }
                .padding(20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Esperando o anfitrião", Modifier.weight(1f), size = 22)
                ExitButton("Sair") { askExit() }
            }
            Text("A partida vai começar em instantes.", fontSize = 18.sp)
        }
        return
    }

    val chosen = choosing?.takeIf { v.canPlay && it in v.myCards }
    if (chosen != null) {
        AlertDialog(
            onDismissRequest = { choosing = null },
            title = { Text("Jogar ${Words.cardLower(chosen, v.vira)} aberta ou coberta?", fontSize = 20.sp) },
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
        val p = v.pending
        when {
            v.dealing -> {}
            v.matchOver -> if (vm.isGuest) {
                add(Action("Sair da sala", Palette.Muted) { askExit() })
            } else {
                add(Action("Nova partida", Palette.Start) { vm.newMatch() })
                add(Action("Voltar ao menu", Palette.Muted) { askExit() })
            }
            v.handOver -> add(Action("Próxima mão", Palette.Start) { vm.nextHand() })
            v.elevenMine -> {
                add(Action("Jogar a mão de onze", Palette.Start) { vm.decideEleven(true) })
                add(Action("Correr da mão de onze", Palette.Danger) { askRunEleven() })
            }
            p != null && v.askedMe -> {
                add(Action("Aceitar ${Words.raiseName(p.value)}", Palette.Start) { vm.respond(Response.ACEITAR) })
                if (v.canEscalate) nextRaise(p.value)?.let { up ->
                    add(Action("Aumentar para ${Words.raiseName(up)}", Palette.Action) { vm.respond(Response.AUMENTAR) })
                }
                add(Action("Correr", Palette.Danger) { askRun() })
            }
            v.canRaise -> nextRaise(v.value)?.let { up ->
                add(Action("Pedir ${Words.raiseName(up)}", Palette.Action) { vm.raise() })
            }
        }
        add(Action("Repetir", Palette.Muted) { vm.describe() })
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Palette.Bg)
            .safeDrawingPadding(),
    ) {
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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Placar", size = 18, top = 4.dp)
                Text(
                    vm.scoreDisplay(v),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clearAndSetSemantics { contentDescription = vm.scoreDescription(v) },
                )
                ExitButton("Sair") { askExit() }
            }
            Text(vm.handValueText(v), fontSize = 16.sp, textAlign = TextAlign.Center)
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
                    if (v.dealing) {
                        Text("Dando as cartas…", fontSize = 16.sp)
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clearAndSetSemantics { contentDescription = vm.viraText(v) },
                        ) {
                            CardFace(v.vira, null, width = tableW)
                            Text("Manilha: ${Words.manilha(v.vira)}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Column(
                    Modifier.weight(1f).fillMaxHeight().semantics { isTraversalGroup = true },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SectionTitle("Mesa", size = 18, top = 4.dp)
                    if (!v.dealing) TableArea(vm, v, tableW, Modifier.fillMaxWidth().weight(1f))
                }
            }

            SectionTitle("Suas cartas", Modifier.focusRequester(handHeading).focusable(), size = 18, top = 6.dp)
            Box(Modifier.heightIn(min = handW * 1.45f), contentAlignment = Alignment.Center) {
                when {
                    v.dealing -> Text("Dando as cartas…", fontSize = 18.sp)
                    v.myCards.isEmpty() -> Text("Você não tem mais cartas.", fontSize = 18.sp)
                    else -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        v.myCards.forEachIndexed { i, card ->
                            key(card.id) {
                                val label = vm.cardLabel(v, card, i)
                                val play = { if (vm.tapCard(card)) choosing = card }
                                CardFace(
                                    card, v.vira, width = handW, faceDown = v.blind,
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

            vm.partnerCardsText(v)?.let { Text(it, fontSize = 15.sp, textAlign = TextAlign.Center) }

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
private fun TableArea(vm: AppViewModel, v: TableView, cardMax: Dp, modifier: Modifier) {
    val plays = if (v.table.isEmpty() && v.tricks.isNotEmpty()) v.tricks.last().plays else v.table
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Felt)
            .padding(8.dp)
            .clearAndSetSemantics { contentDescription = vm.tableText(v) },
        contentAlignment = Alignment.Center,
    ) {
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
                            CardFace(p.card ?: v.vira, v.vira, width = cardW, faceDown = p.card == null || p.covered)
                        }
                    }
                }
            }
            val line = vm.tricksLine(v)
            if (line.isNotEmpty()) Text(line, fontSize = 14.sp, textAlign = TextAlign.Center)
            Text(vm.othersCardsShort(v), fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }
}

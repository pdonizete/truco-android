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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Título e parágrafos de cada seção da ajuda. */
private val HELP: List<Pair<String, List<String>>> = listOf(
    "Com o leitor de tela" to listOf(
        "A tela de jogo tem títulos: Placar, Vira, Mesa, Suas cartas e Ações. Use o gesto de navegar por títulos para pular entre eles.",
        "Cada carta da sua mão é um botão. Toque duas vezes para jogar. As manilhas são anunciadas como manilha, e a manilha de paus como zap.",
        "A voz do app anuncia as jogadas. O botão Repetir fala de novo o placar, o valor da mão, a vira, a mesa e as suas cartas.",
        "O botão Sair fica no canto de cima e é o último da varredura. Sair e correr pedem confirmação.",
    ),
    "As cartas" to listOf(
        "O baralho tem 40 cartas, sem 8, 9, 10 e coringas. Cada jogador recebe 3.",
        "Das mais fracas para as mais fortes: 4, 5, 6, 7, dama, valete, rei, ás, 2 e 3. Cartas comuns de mesmo valor empatam, e a rodada cangou.",
    ),
    "Vira e manilha" to listOf(
        "Depois de dar as cartas, vira-se uma carta: a vira. A manilha é o valor seguinte ao da vira. Depois do 3 volta para o 4. Exemplo: vira rei, manilha ás.",
        "As manilhas ganham de qualquer carta comum. Entre elas, da mais fraca para a mais forte: ouros, espadas, copas e paus. A manilha de paus é o zap.",
    ),
    "Rodadas" to listOf(
        "A mão tem até três rodadas e ganha quem fizer duas. Quem ganha uma rodada começa a próxima.",
        "Se a primeira rodada cangar, quem ganhar a segunda leva a mão. Se a segunda ou a terceira cangar, leva quem ganhou a primeira. Se as três cangarem, leva quem é mão.",
    ),
    "Truco" to listOf(
        "A mão vale 1 ponto. Na sua vez, você pode pedir truco, e a mão passa a valer 3. O adversário aceita, corre ou aumenta para seis. Depois vêm nove e doze.",
        "Quem corre dá ao outro o valor de antes do pedido: correr do truco dá 1 ponto, do seis dá 3, do nove dá 6 e do doze dá 9.",
        "Quem fez o último pedido não pode pedir de novo. O próximo pedido é do adversário.",
    ),
    "Mão de onze" to listOf(
        "Quando um lado chega a 11 pontos, ele vê as cartas e decide se joga ou corre. Se jogar, a mão vale 3. Se correr, o adversário ganha 1 ponto. Na mão de onze ninguém pede truco.",
    ),
    "Mão de ferro" to listOf(
        "Se os dois lados têm 11 pontos, as cartas são jogadas sem ver e a mão vale 1. Também não há truco.",
    ),
    "Carta coberta" to listOf(
        "É uma opção das configurações. Ligada, a partir da segunda rodada você pode jogar uma carta virada para baixo. A carta coberta perde para qualquer carta aberta.",
    ),
    "Duplas" to listOf(
        "Na partida em duplas, você joga com um parceiro contra dois adversários. Os três são controlados pelo computador.",
        "A ordem na mesa é: você, o adversário da direita, o parceiro e o adversário da esquerda. Quem é mão passa para o próximo a cada mão.",
        "A rodada fica com a dupla que jogou a carta mais forte. Se as cartas mais fortes forem de duplas diferentes e empatarem, a rodada cangou.",
        "Quando a sua dupla pede truco, os adversários respondem. Quando os adversários pedem, quem responde é você.",
        "Na mão de onze da sua dupla, você ouve as cartas do parceiro antes de decidir se joga ou corre.",
    ),
    "Partida" to listOf("Vence quem chegar primeiro a 12 pontos."),
    "Créditos" to listOf("Inspirado no Dominó G, de geofilho."),
)

@Composable
fun HelpScreen(vm: AppViewModel) {
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
            SectionTitle("Como jogar", Modifier.weight(1f), size = 28)
            ExitButton("Voltar") { vm.backToMenu() }
        }
        HELP.forEach { (title, paragraphs) ->
            SectionTitle(title)
            paragraphs.forEach { Text(it, fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp)) }
        }
    }
}

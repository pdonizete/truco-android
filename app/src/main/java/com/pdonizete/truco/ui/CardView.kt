package com.pdonizete.truco.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pdonizete.truco.game.Card
import com.pdonizete.truco.game.Suit
import com.pdonizete.truco.game.isManilha
import com.pdonizete.truco.game.isZap

fun suitSymbol(s: Suit): String = when (s) {
    Suit.OUROS -> "♦"
    Suit.ESPADAS -> "♠"
    Suit.COPAS -> "♥"
    Suit.PAUS -> "♣"
}

private fun suitColor(s: Suit) = if (s == Suit.OUROS || s == Suit.COPAS) Palette.Red else Palette.Black

/**
 * Desenho de uma carta: valor e naipe grandes; manilha com borda dourada.
 * Não entra na árvore de acessibilidade: quem descreve a carta é o contêiner (clearAndSetSemantics).
 */
@Composable
fun CardFace(
    card: Card,
    vira: Card?,
    modifier: Modifier = Modifier,
    width: Dp = 80.dp,
    faceDown: Boolean = false,
) {
    val shape = RoundedCornerShape(width * 0.12f)
    val special = !faceDown && vira != null && card.isManilha(vira)
    Box(
        modifier
            .size(width, width * 1.45f)
            .clip(shape)
            .background(if (faceDown) Palette.Back else Palette.Paper)
            .border(if (special) 4.dp else 1.dp, if (special) Palette.Gold else Palette.CardEdge, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (faceDown) {
            Box(
                Modifier
                    .size(width * 0.7f, width * 1.15f)
                    .border(2.dp, Palette.Gold.copy(alpha = 0.6f), RoundedCornerShape(width * 0.08f)),
            )
        } else {
            val color = suitColor(card.suit)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(
                    card.rank.symbol, color = color, fontWeight = FontWeight.Bold,
                    fontSize = (width.value * 0.42f).sp, lineHeight = (width.value * 0.46f).sp,
                )
                Text(
                    suitSymbol(card.suit), color = color,
                    fontSize = (width.value * 0.40f).sp, lineHeight = (width.value * 0.44f).sp,
                )
                if (special) {
                    Text(
                        if (card.isZap(vira!!)) "ZAP" else "MANILHA", color = Palette.Black,
                        fontWeight = FontWeight.Bold, fontSize = (width.value * 0.12f).sp,
                    )
                }
            }
        }
    }
}

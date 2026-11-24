package com.pdonizete.truco.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/** Cores com bom contraste: texto claro no fundo verde escuro, cartas brancas com naipes escuros. */
object Palette {
    val Bg = Color(0xFF0F2619)
    val Felt = Color(0xFF145233)
    val Text = Color(0xFFF2F2F2)
    val Status = Color(0xFFFFD54F)
    val Action = Color(0xFF1F5FA8)
    val Start = Color(0xFF1E7B45)
    val Danger = Color(0xFFB03030)
    val Muted = Color(0xFF3D4F45)
    val Paper = Color(0xFFFFFDF7)
    val CardEdge = Color(0xFF8A8A8A)
    val Red = Color(0xFFC62828)
    val Black = Color(0xFF1A1A1A)
    val Gold = Color(0xFFFFC107)
    val Back = Color(0xFF7A1F2B)
}

@Composable
fun TrucoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Action,
            onPrimary = Color.White,
            secondary = Palette.Start,
            onSecondary = Color.White,
            background = Palette.Bg,
            surface = Palette.Bg,
            onBackground = Palette.Text,
            onSurface = Palette.Text,
        ),
    ) {
        CompositionLocalProvider(LocalContentColor provides Palette.Text, content = content)
    }
}

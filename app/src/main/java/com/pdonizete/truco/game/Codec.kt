package com.pdonizete.truco.game

// Auxiliares da codificação em texto usada pelo Bluetooth.

internal fun flag(x: Boolean): String = if (x) "1" else "0"

/** Número não negativo, ou null quando o texto é negativo (por exemplo -1). */
internal fun optInt(s: String): Int? = s.toInt().takeIf { it >= 0 }

internal fun items(s: String, sep: Char): List<String> = if (s.isEmpty()) emptyList() else s.split(sep)

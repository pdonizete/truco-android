package com.pdonizete.truco.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * true quando há um leitor de tela ligado (TalkBack ou outro com retorno falado).
 * Muda na hora se o leitor for ligado ou desligado e confere de novo ao voltar para o app.
 */
@Composable
fun rememberScreenReaderOn(): Boolean {
    val context = LocalContext.current
    val am = remember { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager }
    var on by remember { mutableStateOf(screenReaderActive(am)) }
    DisposableEffect(am) {
        val touch = AccessibilityManager.TouchExplorationStateChangeListener { on = screenReaderActive(am) }
        val state = AccessibilityManager.AccessibilityStateChangeListener { on = screenReaderActive(am) }
        am.addTouchExplorationStateChangeListener(touch)
        am.addAccessibilityStateChangeListener(state)
        onDispose {
            am.removeTouchExplorationStateChangeListener(touch)
            am.removeAccessibilityStateChangeListener(state)
        }
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) on = screenReaderActive(am)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return on
}

private fun screenReaderActive(am: AccessibilityManager): Boolean =
    am.isEnabled && (
        am.isTouchExplorationEnabled ||
            am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN).isNotEmpty()
        )

/** Diálogo Sim/Não. Devolve a função que abre o diálogo. O TalkBack leva o foco direto para ele. */
@Composable
fun rememberConfirm(question: String, onYes: () -> Unit): () -> Unit {
    var open by remember { mutableStateOf(false) }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(question, fontSize = 20.sp) },
            confirmButton = { TextButton(onClick = { open = false; onYes() }) { Text("Sim", fontSize = 18.sp) } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Não", fontSize = 18.sp) } },
        )
    }
    return { open = true }
}

/**
 * Botão de sair. Fica no fim da varredura do TalkBack (o foco não cai nele ao abrir a tela),
 * mas a exploração por toque encontra. O contêiner da tela precisa ter isTraversalGroup = true.
 */
@Composable
fun ExitButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.semantics { traversalIndex = 1f }) {
        Text(label, fontSize = 16.sp)
    }
}

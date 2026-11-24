package com.pdonizete.truco.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Pede o foco para [requester] cada vez que [token] muda (não na primeira composição). */
@Composable
fun FocusOnToken(requester: FocusRequester, token: Int) {
    val seen = remember { intArrayOf(token) }
    LaunchedEffect(token) {
        if (token == seen[0]) return@LaunchedEffect
        seen[0] = token
        delay(80)
        runCatching { requester.requestFocus() }
    }
}

/** Título de área, marcado como heading para o gesto de navegar por títulos. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, size: Int = 21) {
    Text(
        text,
        fontSize = size.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(top = 12.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
fun BigButton(label: String, color: Color = Palette.Action, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        shape = RoundedCornerShape(50),
        modifier = modifier
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .heightIn(min = 56.dp),
    ) {
        Text(label, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

/** Linha com título, explicação e chave. O TalkBack lê tudo junto, com o estado da chave. */
@Composable
fun SettingSwitch(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 19.sp)
            Text(detail, fontSize = 15.sp, color = Palette.Text.copy(alpha = 0.8f))
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Opção de escolha única (botão de opção) com explicação. */
@Composable
fun RadioRow(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(title, fontSize = 19.sp)
            Text(detail, fontSize = 15.sp, color = Palette.Text.copy(alpha = 0.8f))
        }
    }
}

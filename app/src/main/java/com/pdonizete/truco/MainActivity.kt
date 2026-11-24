package com.pdonizete.truco

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import com.pdonizete.truco.ui.AppViewModel
import com.pdonizete.truco.ui.GameScreen
import com.pdonizete.truco.ui.HelpScreen
import com.pdonizete.truco.ui.MenuScreen
import com.pdonizete.truco.ui.Screen
import com.pdonizete.truco.ui.SettingsScreen
import com.pdonizete.truco.ui.TrucoTheme
import com.pdonizete.truco.ui.rememberScreenReaderOn

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrucoTheme {
                // Sem leitor de tela, a voz do app fica calada.
                val reader = rememberScreenReaderOn()
                LaunchedEffect(reader) { vm.updateScreenReader(reader) }
                when (vm.screen) {
                    Screen.MENU -> MenuScreen(vm)
                    Screen.GAME -> GameScreen(vm)
                    Screen.HELP -> HelpScreen(vm)
                    Screen.SETTINGS -> SettingsScreen(vm)
                }
            }
        }
    }
}

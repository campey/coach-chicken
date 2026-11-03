package com.coachchicken

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val vm: CoachViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // transparent bars; icon colour follows the system light/dark setting
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    val state by vm.state.collectAsStateWithLifecycle()
                    CoachScreen(state, vm::start, vm::cancel, vm::dismissDone)
                }
            }
        }
    }

    // Leaving the foreground mid-plank cancels the session (no foreground service yet).
    override fun onStop() {
        super.onStop()
        vm.cancel()
    }
}

@Composable
private fun CoachScreen(state: UiState, onStart: () -> Unit, onStop: () -> Unit, onDone: () -> Unit) {
    KeepScreenOn(state.phase == Phase.RUNNING)
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(R.drawable.ic_chicken), contentDescription = null, modifier = Modifier.size(width = 56.dp, height = 110.dp))
        Text("Coach Chicken", style = MaterialTheme.typography.headlineMedium)
        when (state.phase) {
            Phase.RUNNING -> {
                Text(formatTime(state.remainingMs), fontSize = 96.sp)
                OutlinedButton(onStop, Modifier.fillMaxWidth()) { Text("Stop") }
            }
            Phase.IDLE, Phase.DONE -> {
                if (state.phase == Phase.DONE) Text("Plank done! 🥚", style = MaterialTheme.typography.titleLarge)
                CalendarView(state.calendar, Modifier.fillMaxWidth())
                if (state.phase == Phase.DONE) {
                    Button(onDone, Modifier.fillMaxWidth()) { Text("OK") }
                } else {
                    Button(onStart, Modifier.fillMaxWidth()) { Text("Start plank") }
                }
            }
        }
    }
}

@Composable
private fun KeepScreenOn(on: Boolean) {
    val view = LocalView.current
    DisposableEffect(on) {
        view.keepScreenOn = on
        onDispose { view.keepScreenOn = false }
    }
}

/** Seconds shown rounded up, so the display reads 1:00 at the start and 0:00 only at the end. */
private fun formatTime(remainingMs: Long): String {
    val s = ((remainingMs + 999) / 1000).toInt()
    return "%d:%02d".format(s / 60, s % 60)
}

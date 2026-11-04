package com.coachchicken

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.TextButton
import com.coachchicken.core.CalendarDay
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
                    val context = LocalContext.current
                    val clipboard = LocalClipboardManager.current
                    // The session opens either way; without camera permission only the buttons work.
                    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                        vm.openSession(camera = granted)
                    }
                    val onStartSession = {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                        if (granted) vm.openSession(camera = true) else askCamera.launch(Manifest.permission.CAMERA)
                    }
                    val copyLog: (() -> Unit)? =
                        if (vm.hasPoseLog()) ({ clipboard.setText(AnnotatedString(vm.poseLogText())) }) else null

                    val day = state.day
                    if (day != null) {
                        DayScreen(day, vm::closeDay)
                    } else when (state.phase) {
                        Phase.READY, Phase.PLANKING -> SessionScreen(
                            state = state,
                            onPose = vm::onPose,
                            onStartPlank = vm::startPlank,
                            onEnd = { vm.endPlank() },
                            onClose = vm::closeSession,
                            onFlip = vm::flipCamera,
                            onCopyLog = copyLog,
                        )
                        Phase.HOME, Phase.DONE -> HomeScreen(state, onStartSession, vm::dismissDone, copyLog) { vm.openDay(it.date) }
                    }
                }
            }
        }
    }

    // Leaving the foreground winds the session up (no foreground service yet). Rotating is not leaving.
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) vm.onAppStopped()
    }
}

@Composable
private fun HomeScreen(state: UiState, onStartSession: () -> Unit, onDone: () -> Unit, onCopyLog: (() -> Unit)?, onDay: (CalendarDay) -> Unit) {
    // Scrolls when it doesn't fit (landscape), and is centred when it does.
    BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = maxHeight).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(painterResource(R.drawable.ic_chicken), contentDescription = null, modifier = Modifier.size(width = 56.dp, height = 110.dp))
            Text("Coach Chicken", style = MaterialTheme.typography.headlineMedium)
            val card = state.card
            if (state.phase == Phase.DONE) {
                if (card != null) PlankCardView(card)
                else Text("Plank done! 🥚 ${formatElapsed(state.elapsedMs)}", style = MaterialTheme.typography.titleLarge)
            }
            if (state.phase == Phase.DONE) Button(onDone, Modifier.fillMaxWidth()) { Text("OK") }
            CalendarView(state.calendar, Modifier.fillMaxWidth(), onDayClick = onDay)
            if (state.phase != Phase.DONE) {
                Button(onStartSession, Modifier.fillMaxWidth()) { Text("Start session") }
            }
            if (onCopyLog != null) {
                OutlinedButton(onCopyLog, Modifier.fillMaxWidth()) { Text("Copy pose log") }
            }
        }
    }
}

/** The cards of one past day: swipe between that day's planks. */
@Composable
private fun DayScreen(day: DayView, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val pager = rememberPagerState { day.cards.size }
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(day.date.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())), style = MaterialTheme.typography.titleLarge)
                if (day.cards.size > 1) Text("${pager.currentPage + 1} of ${day.cards.size} · swipe for the others", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClose) { Text("Close") }
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), pageSpacing = 12.dp, verticalAlignment = Alignment.Top) { page ->
            Column(Modifier.verticalScroll(rememberScrollState())) { PlankCardView(day.cards[page]) }
        }
    }
}

@Composable
internal fun KeepScreenOn(on: Boolean) {
    val view = LocalView.current
    DisposableEffect(on) {
        view.keepScreenOn = on
        onDispose { view.keepScreenOn = false }
    }
}

/** Countdown display: rounded up, so it reads 1:00 at the start and 0:00 only at the goal. */
internal fun formatTime(remainingMs: Long): String {
    val s = ((remainingMs + 999) / 1000).toInt()
    return "%d:%02d".format(s / 60, s % 60)
}

/** Elapsed display: rounded down. */
internal fun formatElapsed(ms: Long): String {
    val s = (ms / 1000).toInt()
    return "%d:%02d".format(s / 60, s % 60)
}

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.TextButton
import com.coachchicken.core.CalendarDay
import com.coachchicken.core.SessionRecord
import java.time.Instant
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
                        Phase.HOME -> HomeScreen(state, onStartSession, copyLog) { vm.openDay(it.date) }
                        Phase.DONE -> {
                            val card = state.card ?: CardData(SessionRecord("plank", Instant.now(), state.elapsedMs, true), null)
                            DoneScreen(card, vm::dismissDone)
                        }
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
internal fun HomeScreen(state: UiState, onStartSession: () -> Unit, onCopyLog: (() -> Unit)?, onDay: (CalendarDay) -> Unit) {
    val done = state.streak.doneToday
    val accent = if (done) StreakGold else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        // a title bar, which also brings the content down the screen
        Text(
            "Coach Chicken", Modifier.fillMaxWidth().padding(vertical = 18.dp),
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // the streak: big and bright once today's plank is in, muted grey until then; the chicken stands beside it
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painterResource(R.drawable.ic_chicken), contentDescription = null,
                    modifier = Modifier.fillMaxHeight().aspectRatio(56f / 110f), contentScale = ContentScale.Fit,
                )
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(state.streak.days.toString(), fontSize = 88.sp, lineHeight = 92.sp, fontWeight = FontWeight.ExtraBold, color = accent)
                    Text("day streak!", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = accent)
                }
            }
            // the big call to action, or the affirmation once it's done
            Surface(
                shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(2.dp, if (done) StreakGold.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(if (done) "✅" else "⏱️", fontSize = 34.sp) // a check when it's done, a stopwatch when a plank is due
                        Text(
                            if (done) "Today's plank is done. Streak extended!" else "Do a plank today to extend your streak!",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (done) OutlinedButton(onStartSession, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("ANOTHER SESSION", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp) }
                    else Button(onStartSession, Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) { Text("START SESSION", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp) }
                }
            }
            Text("Calendar", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Surface(shape = RoundedCornerShape(24.dp), color = Color.Transparent, border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant)) {
                CalendarView(state.calendar, Modifier.fillMaxWidth().padding(16.dp), onDayClick = onDay)
            }
            if (onCopyLog != null) {
                OutlinedButton(onCopyLog, Modifier.fillMaxWidth()) { Text("Copy pose log") }
            }
        }
    }
}

private val StreakGold = Color(0xFFFFB300)

/** The cards of one past day: swipe up and down between that day's planks. */
@Composable
private fun DayScreen(day: DayView, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val pager = rememberPagerState { day.cards.size }
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(day.date.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())), style = MaterialTheme.typography.titleLarge)
                if (day.cards.size > 1) Text("${pager.currentPage + 1} of ${day.cards.size} · swipe up for the others", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClose) { Text("Close") }
        }
        // swipe up and down between the day's planks; the top of the next card peeks in below the current one
        val peek = if (day.cards.size > 1) 64.dp else 0.dp
        VerticalPager(
            pager, Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = peek), pageSpacing = 12.dp,
            beyondViewportPageCount = 1,
        ) { page ->
            FitHeight(Modifier.fillMaxSize()) { PlankCardView(day.cards[page], active = pager.currentPage == page) }
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

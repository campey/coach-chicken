package com.coachchicken

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import java.time.LocalDate
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
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
        Reminders.ensureChannel(this)
        Reminders.schedule(this) // the next daily reminder, if they are on
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

                    var lore by rememberSaveable { mutableStateOf(false) }
                    var showReminders by rememberSaveable { mutableStateOf(false) }
                    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
                        if (uri != null) {
                            val result = runCatching { BackupManager(context).export(uri) }
                            Toast.makeText(context, result.fold({ "Backed up $it planks" }, { "The backup failed: ${it.message}" }), Toast.LENGTH_LONG).show()
                        }
                    }
                    val restoreBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                        if (uri != null) {
                            val result = runCatching { BackupManager(context).restore(uri) }
                            val message = result.fold(
                                { r -> if (r == null) "That file is not an Aura Farm backup" else "Restored ${r.sessionsAdded} planks (${r.replaysAdded} replays)" },
                                { "The restore failed: ${it.message}" },
                            )
                            vm.reloadHome()
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    }
                    val reminderSettings = remember { ReminderSettings(context) }
                    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                        reminderSettings.enabled = granted
                        Reminders.schedule(context)
                        if (!granted) Toast.makeText(context, "Notifications are off for Aura Farm in the phone's settings", Toast.LENGTH_LONG).show()
                    }
                    val uriHandler = LocalUriHandler.current
                    val day = state.day
                    if (showReminders) {
                        ReminderDialog(
                            initialEnabled = reminderSettings.enabled,
                            onDismiss = { showReminders = false },
                            onTest = { Reminders.scheduleTest(context); Toast.makeText(context, "A test cluck is coming in a minute. Lock your phone!", Toast.LENGTH_LONG).show() },
                            onSave = { enabled ->
                                showReminders = false
                                if (enabled && !Reminders.notificationsAllowed(context)) {
                                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) // saves once they answer
                                } else {
                                    reminderSettings.enabled = enabled
                                    Reminders.schedule(context)
                                }
                            },
                        )
                    }
                    if (lore) {
                        LoreScreen { lore = false }
                    } else if (day != null) {
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
                        Phase.HOME -> HomeScreen(
                            state, onStartSession, copyLog,
                            onLore = { lore = true },
                            onReminders = { showReminders = true },
                            onBackup = { exportBackup.launch("aura-farm-backup-${LocalDate.now()}.zip") },
                            onRestore = { restoreBackup.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                            onIssues = { uriHandler.openUri(ISSUES_URL) },
                        ) { vm.openDay(it.date) }
                        Phase.DONE -> {
                            val card = state.card ?: CardData(SessionRecord("plank", Instant.now(), state.elapsedMs, true), null)
                            DoneFlow(card, vm::dismissDone)
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
internal fun HomeScreen(
    state: UiState, onStartSession: () -> Unit, onCopyLog: (() -> Unit)?, onLore: () -> Unit = {}, onIssues: () -> Unit = {},
    onReminders: () -> Unit = {}, onBackup: () -> Unit = {}, onRestore: () -> Unit = {},
    onDay: (CalendarDay) -> Unit,
) {
    val done = state.streak.doneToday
    // the home screen is always dark navy, in the game style, whatever the system theme says
    MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF0B1230), surface = Color(0xFF0B1230), onSurface = Color.White)) {
    val accent = if (done) StreakGold else Color.White.copy(alpha = 0.32f)
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF16205A), Color(0xFF0B1230), Color(0xFF070B1F)))).systemBarsPadding()) {
        // a title bar, which also brings the content down the screen
        var menuOpen by remember { mutableStateOf(false) }
        Box(Modifier.fillMaxWidth()) {
            Text(
                "AURA FARM", Modifier.fillMaxWidth().padding(vertical = 18.dp),
                fontSize = 22.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, letterSpacing = 2.sp,
                color = Color.White, textAlign = TextAlign.Center,
            )
            Box(Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)) {
                IconButton({ menuOpen = true }) {
                    val bar = Color.White
                    Canvas(Modifier.size(22.dp)) { // a hamburger: three bars
                        for (k in 0..2) drawLine(bar, Offset(0f, size.height * (0.2f + 0.3f * k)), Offset(size.width, size.height * (0.2f + 0.3f * k)), 2.5.dp.toPx(), StrokeCap.Round)
                    }
                }
                DropdownMenu(menuOpen, { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(if (onCopyLog != null) "Copy pose log" else "Copy pose log (nothing recorded yet)") },
                        enabled = onCopyLog != null,
                        onClick = { menuOpen = false; onCopyLog?.invoke() },
                    )
                    DropdownMenuItem(text = { Text("Daily reminder") }, onClick = { menuOpen = false; onReminders() })
                    DropdownMenuItem(text = { Text("Back up data") }, onClick = { menuOpen = false; onBackup() })
                    DropdownMenuItem(text = { Text("Restore data") }, onClick = { menuOpen = false; onRestore() })
                    DropdownMenuItem(text = { Text("A chicken?") }, onClick = { menuOpen = false; onLore() })
                    DropdownMenuItem(text = { Text("Log an issue") }, onClick = { menuOpen = false; onIssues() })
                }
            }
        }
        // a slanted yellow stripe under the title, like a game menu's header
        Box(Modifier.fillMaxWidth().height(4.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, HudYellow, Color.Transparent))))
        // no scrolling: the calendar shows as many weeks as fit under the streak and the call to action (1 to 5)
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
        val weeks = ((maxHeight - 476.dp) / 62.dp).toInt().coerceIn(1, 5)
        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // the streak: big and bright once today's plank is in, muted until then; the chicken stands beside it
            // inset to line up with the edges of the button in the card below (the chicken's picture has blank space at its left, hence the smaller start)
            Row(Modifier.fillMaxWidth().padding(start = 9.dp, end = 8.dp).height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painterResource(R.drawable.ic_chicken), contentDescription = null,
                    modifier = Modifier.fillMaxHeight().aspectRatio(56f / 110f), contentScale = ContentScale.Fit,
                )
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HudTile(fill = HudNavy.copy(alpha = 0.9f), edge = if (done) StreakGold else Color.White.copy(alpha = 0.25f), lean = 20.dp) {
                        Text(
                            state.streak.days.toString(), fontSize = 80.sp, lineHeight = 84.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic,
                            color = accent, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                        )
                    }
                    Text("DAY STREAK!", fontSize = 24.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, letterSpacing = 1.sp, color = accent)
                }
            }
            // the big call to action, or the affirmation once it's done
            HudTile(Modifier.fillMaxWidth(), edge = if (done) StreakGold else HudCyan, lean = 18.dp) {
                Column(Modifier.fillMaxWidth().padding(vertical = 20.dp, horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(if (done) "✅" else "⏱️", fontSize = 34.sp) // a check when it's done, a stopwatch when a plank is due
                        Text(
                            if (done) "Today's plank is done. Streak extended!" else "Do a plank today to extend your streak!",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic, color = Color.White,
                        )
                    }
                    if (done) HudButton("Another session", onStartSession, Modifier.fillMaxWidth(), HudStyle.SECONDARY, height = 52.dp)
                    else HudButton("Start session", onStartSession, Modifier.fillMaxWidth(), HudStyle.PRIMARY, height = 60.dp)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("STREAK CALENDAR", fontSize = 22.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, letterSpacing = 1.sp, color = Color.White)
                Box(Modifier.width(84.dp).height(5.dp).clip(SlantShape(5.dp)).background(HudYellow))
            }
            HudTile(Modifier.fillMaxWidth(), fill = HudNavy.copy(alpha = 0.55f), edge = HudCyan.copy(alpha = 0.55f), lean = 16.dp) {
                CalendarView(state.calendar.takeLast(weeks), Modifier.fillMaxWidth().padding(16.dp), onDayClick = onDay)
            }
        }
        }
    }
    }
}

private val StreakGold = Color(0xFFFFB300)
private const val ISSUES_URL = "https://github.com/campey/coach-chicken/issues"

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

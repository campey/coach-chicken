package com.coachchicken

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coachchicken.core.Scene
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val BEAT_MS = 2400

/**
 * What happens when a plank is completed: a win beat in the field (a golden egg pops, sparkles, "Plank complete!"), still
 * sideways as the session was, then the screen turns to portrait by itself and the card is shown.
 */
@Composable
fun DoneFlow(card: CardData, onDone: () -> Unit) {
    val activity = LocalContext.current.findActivity()
    var beatOver by remember { mutableStateOf(false) }
    var forced by remember { mutableStateOf(false) }
    val portrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    DisposableEffect(Unit) { onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED } }
    LaunchedEffect(beatOver) {
        activity?.requestedOrientation = if (beatOver) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        if (beatOver) { delay(1500); forced = true } // never wait for the rotation for ever
    }
    if (beatOver && (portrait || forced)) DoneScreen(card, onDone)
    else WinBeat(card.record.durationMs, card.recording?.scene ?: Scene.FARM) { beatOver = true }
}

@Composable
internal fun WinBeat(elapsedMs: Long, scene: Scene, onFinished: () -> Unit) {
    val t = remember { Animatable(0f) }
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { while (true) withFrameNanos { clock.floatValue = it / 1e9f } }
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(BEAT_MS, easing = LinearEasing))
        onFinished()
    }
    Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onFinished() }) {
        SceneWorld(scene, { WorldView() }, { clock.floatValue }, Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) { drawWinEgg(t.value, clock.floatValue) }
        // the banner slides in from the left once the egg has popped, like a game's victory screen
        val bannerIn = remember { Animatable(0f) }
        LaunchedEffect(Unit) { delay(350); bannerIn.animateTo(1f, tween(380)) }
        WinBanner(elapsedMs, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp), shown = { bannerIn.value })
    }
}

/** "Plank complete!" over the time, in the game-style HUD; [shown] (0..1) slides it in from the left. */
@Composable
internal fun WinBanner(elapsedMs: Long, modifier: Modifier = Modifier, shown: () -> Float = { 1f }) {
    Column(
        modifier.graphicsLayer { translationX = -(1f - shown()) * 900f; alpha = shown().coerceIn(0f, 1f) },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HudBanner("Plank complete!")
        HudTimer(formatElapsed(elapsedMs), reached = true)
    }
}

/** The golden egg of the win beat at animation progress [t] (0..1) and clock [sec]: it pops in, glows and sparkles. */
internal fun DrawScope.drawWinEgg(t: Float, sec: Float) {
        val p = (t / 0.2f).coerceIn(0f, 1f)
        // a pop with a little overshoot
        val s = 1f + 2.7f * (p - 1f) * (p - 1f) * (p - 1f) + 1.7f * (p - 1f) * (p - 1f)
        val scale = (if (p < 1f) s.coerceAtLeast(0f) else 1f) * (1f + 0.025f * sin(sec * 5f))
        val cx = size.width / 2; val cy = size.height * 0.4f
        val h = size.height * 0.34f * scale; val w = h * 0.76f
        if (h < 2f) return // not popped in yet: a gradient with no radius throws
        drawCircle(Brush.radialGradient(listOf(Color(0x99FFC107), Color(0x00FFC107)), Offset(cx, cy), h * 1.5f), h * 1.5f, Offset(cx, cy))
        drawOval(
            Brush.radialGradient(listOf(Color(0xFFFFF3B0), Color(0xFFFFC107), Color(0xFFD98E00)), Offset(cx - w * 0.2f, cy - h * 0.25f), h * 0.9f),
            Offset(cx - w / 2, cy - h / 2), Size(w, h),
        )
        // sparkles circling the egg
        if (p >= 1f) for (i in 0 until 8) {
            val a = sec * 0.9f + i * (2 * PI.toFloat() / 8)
            val r = h * (0.78f + 0.12f * sin(sec * 3f + i))
            drawStar(Offset(cx + cos(a) * r * 1.05f, cy + sin(a) * r * 0.8f), (6 + 4 * ((i + 1) % 3)).dp.toPx(), Color(0xFFFFE082))
        }
}

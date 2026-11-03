package com.coachchicken

import android.content.res.Configuration
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.coachchicken.core.Landmark
import com.coachchicken.core.PlankGrade
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

private val Gold = Color(0xFFFFB300)       // a perfect plank, with its aura
private val Yellow = Color(0xFFFFF176)     // within tolerance: still a plank
private val Grey = Color(0xFF9E9E9E)        // not a plank
private val Green = Color(0xFF43A047)      // the goal zone
private val Scrim = Color(0xB3000000)

// Shoulders, arms, torso, legs: the joints that matter for a plank.
private val BONES = listOf(
    11 to 12, 11 to 13, 13 to 15, 12 to 14, 14 to 16,
    11 to 23, 12 to 24, 23 to 24, 23 to 25, 25 to 27, 24 to 26, 26 to 28,
)
private val JOINTS = (11..16) + (23..28)

/**
 * The session: waiting for the plank (READY), then timing it (PLANKING). With the camera on it shows the
 * preview and skeleton and starts/ends the plank by itself; the buttons are always there as a fallback.
 */
@Composable
fun SessionScreen(
    state: UiState,
    onPose: (List<Landmark>?, Float) -> Unit,
    onStartPlank: () -> Unit,
    onEnd: () -> Unit,
    onClose: () -> Unit,
    onFlip: () -> Unit,
    onCopyLog: (() -> Unit)?,
) {
    val onCam = state.cameraOn
    val fg = if (onCam) Color.White else MaterialTheme.colorScheme.onBackground
    val scrim = if (onCam) Scrim else Color.Transparent
    val reached = state.phase == Phase.PLANKING && state.elapsedMs >= state.goalMs
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    KeepScreenOn(true)
    Box(Modifier.fillMaxSize().background(if (onCam) Color.Black else MaterialTheme.colorScheme.background)) {
        if (onCam) {
            CameraLayer(state, onPose)
            state.pose?.let {
                // Matches the log's t_ms, so a screen recording can be lined up with a pasted log.
                Text(
                    "t=%.1fs".format(it.logMs / 1000.0),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(8.dp)
                        .background(Scrim, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }

        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (!onCam && !landscape) {
                    Image(painterResource(R.drawable.ic_chicken), contentDescription = null, modifier = Modifier.size(width = 56.dp, height = 110.dp))
                }
                if (state.phase == Phase.PLANKING) {
                    Text(
                        if (reached) "+" + formatElapsed(state.elapsedMs - state.goalMs) else formatTime(state.goalMs - state.elapsedMs),
                        fontSize = if (landscape) 44.sp else 72.sp,
                        color = if (reached) Gold else fg,
                        modifier = Modifier.background(scrim, RoundedCornerShape(16.dp)).padding(horizontal = 20.dp),
                    )
                }
                Text(
                    statusText(state, reached),
                    color = fg,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp).background(scrim, RoundedCornerShape(12.dp)).padding(12.dp),
                )
                val progress = state.pose?.holdProgress ?: 0f
                if (state.phase == Phase.READY && onCam && progress > 0f) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.padding(top = 8.dp).fillMaxWidth(0.6f))
                }
            }

            val primaryLabel = if (state.phase == Phase.READY) "Start plank" else if (reached) "Done" else "Stop"
            val primaryClick = if (state.phase == Phase.READY) onStartPlank else onEnd
            val outlined = ButtonDefaults.outlinedButtonColors(containerColor = scrim, contentColor = fg)
            val extraButtons: @Composable RowScope.() -> Unit = {
                if (state.phase == Phase.READY) {
                    OutlinedButton(onClose, Modifier.weight(1f), colors = outlined) { Text("Close") }
                }
                if (onCam) {
                    OutlinedButton(onFlip, Modifier.weight(1f), colors = outlined) { Text(if (state.frontCamera) "Rear cam" else "Front cam") }
                }
                if (onCam && onCopyLog != null) {
                    OutlinedButton(onCopyLog, Modifier.weight(1f), colors = outlined) { Text("Copy log") }
                }
            }
            if (landscape) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(primaryClick, Modifier.weight(2f)) { Text(primaryLabel) }
                    extraButtons()
                }
            } else {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(primaryClick, Modifier.fillMaxWidth()) { Text(primaryLabel) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { extraButtons() }
                }
            }
        }
    }
}

/** Camera preview, skeleton overlay and the pose analyser; only exists while the camera is on. */
@Composable
private fun CameraLayer(state: UiState, onPose: (List<Landmark>?, Float) -> Unit) {
    val context = LocalContext.current
    val latestOnPose = rememberUpdatedState(onPose)
    val analyzer = remember { PoseAnalyzer(context) { lm, aspect -> latestOnPose.value(lm, aspect) } }
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            executor.awaitTermination(500, TimeUnit.MILLISECONDS)
            analyzer.close()
        }
    }
    CameraPreview(front = state.frontCamera, analyzer = analyzer, executor = executor, modifier = Modifier.fillMaxSize())
    PoseOverlay(state.pose, mirror = state.frontCamera, modifier = Modifier.fillMaxSize())
}

private fun statusText(state: UiState, reached: Boolean): String = when {
    state.phase == Phase.PLANKING && reached ->
        if (state.cameraOn) "Goal reached! Keep going, then get up or tap Done." else "Goal reached! Keep going, then tap Done."
    state.phase == Phase.PLANKING && state.pose?.endsInMs != null ->
        "Back into plank! Ending in ${(state.pose.endsInMs + 999) / 1000}s"
    state.phase == Phase.PLANKING && state.pose?.hipInZone == false ->
        "Planking. Aim your hips for the green zone."
    state.phase == Phase.PLANKING ->
        if (state.cameraOn) "Planking. Get up or tap Stop to end early." else "Planking. Tap Stop to end early."
    !state.cameraOn -> "Get into position, then tap Start plank."
    state.pose == null || state.pose.grade == PlankGrade.NO_BODY ->
        "Prop the phone so the camera sees you side-on, then plank (or tap Start plank)."
    state.pose.headOutOfFrame ->
        "Your head is out of frame. Move the phone further back, or turn it to landscape."
    state.pose.grade == PlankGrade.NOT_PLANK -> "Get into plank position."
    else -> "Hold it... then keep your hips in the green."
}

@Composable
private fun CameraPreview(front: Boolean, analyzer: ImageAnalysis.Analyzer, executor: java.util.concurrent.Executor, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    // The activity handles rotation itself (so the session survives it); re-bind with the new display rotation so
    // the preview and the analysed frames stay upright.
    val configuration = LocalConfiguration.current
    val rotation = remember(configuration) { view.display?.rotation ?: Surface.ROTATION_0 }
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
    }
    DisposableEffect(front, rotation) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val selector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .build()
            val preview = Preview.Builder().setResolutionSelector(selector).setTargetRotation(rotation).build()
                .also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(selector)
                .setTargetRotation(rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { it.setAnalyzer(executor, analyzer) }
            val camera = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, camera, preview, analysis)
        }, ContextCompat.getMainExecutor(context))
        onDispose { runCatching { future.get().unbindAll() } }
    }
    AndroidView({ previewView }, modifier)
}

private val GoldGlow = Color(0xFFFFB300)
private val SparkleGold = Color(0xFFFFE082)
private val LEFT_SIDE = setOf(11, 13, 15, 23, 25, 27)
private val RIGHT_SIDE = setOf(12, 14, 16, 24, 26, 28)

/** A cheap deterministic hash to float in 0..1, so each sparkle keeps its own character from frame to frame. */
private fun rnd(seed: Int): Float {
    var x = seed * -1640531527
    x = x xor (x ushr 15); x *= 0x2c1b3c6d
    x = x xor (x ushr 12); x *= 0x297a2d39
    x = x xor (x ushr 15)
    return (x and 0xFFFFFF) / 16777216f
}

private fun DrawScope.drawStar(c: Offset, r: Float, color: Color) {
    val k = 0.22f // how fat the four points are
    val path = Path().apply {
        moveTo(c.x, c.y - r); lineTo(c.x + r * k, c.y - r * k); lineTo(c.x + r, c.y); lineTo(c.x + r * k, c.y + r * k)
        lineTo(c.x, c.y + r); lineTo(c.x - r * k, c.y + r * k); lineTo(c.x - r, c.y); lineTo(c.x - r * k, c.y - r * k); close()
    }
    drawPath(path, color)
}

/** Twinkling stars spawned along the bones: more of them, and wider, the longer the perfect form is held. */
private fun DrawScope.drawSparkles(bones: List<Pair<Offset, Offset>>, aura: Float, streak: Float, nowMs: Long) {
    if (bones.isEmpty()) return
    val count = (6 + 30 * streak * aura).toInt()
    for (i in 0 until count) {
        val period = 700 + (rnd(i * 4) * 700).toInt()
        val t = nowMs + (rnd(i * 4 + 1) * period).toLong()
        val cycle = (t / period).toInt()
        val local = (t % period).toFloat() / period
        val twinkle = sin(PI * local).toFloat()
        val bone = bones[(rnd(i * 131 + cycle * 977 + 1) * bones.size).toInt().coerceAtMost(bones.size - 1)]
        val d = bone.second - bone.first
        val len = hypot(d.x, d.y).coerceAtLeast(1f)
        val along = bone.first + d * rnd(i * 131 + cycle * 977 + 2)
        val spread = (14 + 30 * streak).dp.toPx()
        val side = (rnd(i * 131 + cycle * 977 + 3) - 0.5f) * 2f * spread
        val pos = Offset(along.x - d.y / len * side, along.y + d.x / len * side - local * 10.dp.toPx())
        val r4 = rnd(i * 131 + cycle * 977 + 4)
        val size = (4 + 8 * r4 + 6 * streak).dp.toPx() * (0.4f + 0.6f * twinkle)
        val color = if (r4 > 0.5f) Color.White else SparkleGold
        drawStar(pos, size, color.copy(alpha = (twinkle * (0.55f + 0.45f * aura)).coerceIn(0f, 1f)))
    }
}

/**
 * Draws the green goal zone, then the skeleton, over a FIT_CENTER preview: the image is letterboxed, so normalised
 * coordinates are mapped into that box. Skeleton: gold when the plank is perfect (with a pulsing aura and twinkling
 * sparkles that grow the longer the form is held), plain yellow when it's within tolerance, grey when it isn't a
 * plank. The side the plank is judged on is drawn full size; the far side is smaller and dimmer. Only the judged
 * hip turns green while it is inside the zone.
 */
@Composable
private fun PoseOverlay(pose: PoseUi?, mirror: Boolean, modifier: Modifier) {
    val sparkling = (pose?.aura ?: 0f) > 0.12f
    var clockMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(sparkling) {
        while (sparkling) withFrameNanos { clockMs = it / 1_000_000 } // only animates while there is something to animate
    }
    Canvas(modifier) {
        if (pose == null) return@Canvas
        val nowMs = clockMs
        val viewAspect = size.width / size.height
        val (w, h) = if (viewAspect > pose.aspect) (size.height * pose.aspect) to size.height
        else size.width to (size.width / pose.aspect)
        val ox = (size.width - w) / 2
        val oy = (size.height - h) / 2
        fun at(x: Float, y: Float) = Offset(ox + (if (mirror) 1 - x else x) * w, oy + y * h)
        fun at(l: Landmark) = at(l.x, l.y)

        pose.target?.let { t ->
            val a = at(t.ax, t.ay)
            val b = at(t.bx, t.by)
            val len = hypot(b.x - a.x, b.y - a.y)
            if (len > 1f) {
                val reach = t.halfWidth * h // the zone's half-width, in pixels
                val nx = -(b.y - a.y) / len * reach
                val ny = (b.x - a.x) / len * reach
                val zone = Path().apply {
                    moveTo(a.x + nx, a.y + ny); lineTo(b.x + nx, b.y + ny)
                    lineTo(b.x - nx, b.y - ny); lineTo(a.x - nx, a.y - ny); close()
                }
                drawPath(zone, Green.copy(alpha = 0.28f))
                drawLine(Green.copy(alpha = 0.7f), Offset(a.x + nx, a.y + ny), Offset(b.x + nx, b.y + ny), 2.dp.toPx())
                drawLine(Green.copy(alpha = 0.7f), Offset(a.x - nx, a.y - ny), Offset(b.x - nx, b.y - ny), 2.dp.toPx())
                drawLine(
                    Green, a, b, 3.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(28f, 18f)),
                )
            }
        }

        if (pose.landmarks.size < 29) return@Canvas
        val lms = pose.landmarks
        fun visible(i: Int) = lms[i].visibility >= 0.3f
        val base = when (pose.grade) {
            PlankGrade.PERFECT -> Gold
            PlankGrade.OK -> Yellow
            else -> Grey
        }
        val visibleBones = BONES.filter { (a, b) -> visible(a) && visible(b) }.map { (a, b) -> at(lms[a]) to at(lms[b]) }

        if (pose.aura > 0.02f) { // the cheesy part: a pulsing gold glow, bigger the longer the form is held
            val pulse = 0.88f + 0.12f * sin(nowMs / 1200.0 * 2 * PI).toFloat()
            val power = pose.aura * pulse
            val grow = 1f + 1.2f * pose.streak
            listOf(52f to 0.04f, 40f to 0.06f, 30f to 0.08f, 22f to 0.10f, 15f to 0.14f, 9f to 0.20f).forEach { (width, alpha) ->
                val a = (alpha * power * (1f + 0.5f * pose.streak)).coerceAtMost(0.6f)
                visibleBones.forEach { (p, q) ->
                    drawLine(GoldGlow.copy(alpha = a), p, q, width.dp.toPx() * (0.45f + 0.55f * power) * grow, StrokeCap.Round)
                }
            }
            JOINTS.forEach { // soft radial halos: bright gold in the middle, fading to nothing
                if (visible(it)) {
                    val c = at(lms[it])
                    val r = (12 + 22 * power * grow).dp.toPx()
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(SparkleGold.copy(alpha = 0.55f * power), GoldGlow.copy(alpha = 0.20f * power), Color.Transparent),
                            center = c, radius = r,
                        ),
                        radius = r, center = c,
                    )
                }
            }
            drawSparkles(visibleBones, pose.aura, pose.streak, nowMs)
        }

        val far = when (pose.judgedSide) { 'L' -> RIGHT_SIDE; 'R' -> LEFT_SIDE; else -> emptySet() }
        visibleBones.forEach { (p, q) -> drawLine(base, p, q, 6.dp.toPx(), StrokeCap.Round) }
        JOINTS.forEach {
            if (visible(it)) {
                val isFar = it in far
                drawCircle(if (isFar) base.copy(alpha = 0.55f) else base, (if (isFar) 4.5f else 7f).dp.toPx(), at(lms[it]))
            }
        }
        if (pose.hipInZone == true) {
            val judgedHip = if (pose.judgedSide == 'L') 23 else 24
            if (visible(judgedHip)) drawCircle(Green, 9.dp.toPx(), at(lms[judgedHip]))
        }
    }
}

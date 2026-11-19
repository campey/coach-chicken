package com.coachchicken

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import com.coachchicken.core.Exercise
import com.coachchicken.core.Exercises
import com.coachchicken.core.PoseIdx
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
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
import com.coachchicken.core.PlankTolerances
import com.coachchicken.core.plankMetrics
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
    // A camera session switches the screen to landscape by itself: held upright, the whole screen turns sideways, which
    // is the cue to turn the phone to match. It goes back to following the phone when the session closes.
    val activity = LocalContext.current.findActivity()
    DisposableEffect(onCam) {
        if (onCam) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    // The start sequence: looking for you -> found -> the camera bounces away -> just the avatar in a field.
    val progress = remember { Animatable(0f) }
    LaunchedEffect(state.phase, onCam) {
        if (onCam && state.phase == Phase.PLANKING) progress.animateTo(1f, tween(2400, easing = LinearEasing)) else progress.animateTo(0f, tween(450))
    }
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(onCam) { while (onCam) withFrameNanos { clock.floatValue = it / 1e9f } } // read only while drawing, so no recomposition
    val worldView = rememberWorldView(enabled = onCam && progress.value > 0.02f)
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().background(if (onCam) Color.Black else MaterialTheme.colorScheme.background)) {
        // in landscape with the camera on, the frame takes the left of the screen at full height and the controls the rest
        val frameW = minOf(maxWidth, maxHeight * (4f / 3f))
        val panelW = maxWidth - frameW
        val sidePanel = landscape && onCam && panelW >= 180.dp
        if (onCam) {
            CameraLayer(state, onPose, progress.value, { worldView.value }, { clock.floatValue })
            state.pose?.let {
                // Matches the log's t_ms, so a screen recording can be lined up with a pasted log.
                Text(
                    "t=%.1fs  y%.0f p%.0f r%.0f".format(it.logMs / 1000.0, worldView.value.yaw, worldView.value.pitch, worldView.value.roll),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.BottomStart).systemBarsPadding().padding(8.dp),
                )
            }
        }

        val planking = state.phase == Phase.PLANKING
        val primaryLabel = if (state.phase == Phase.READY) "Start plank" else if (reached) "Done" else "Stop"
        val primaryClick = if (state.phase == Phase.READY) onStartPlank else onEnd
        val primaryStyle = if (state.phase == Phase.READY || reached) HudStyle.PRIMARY else HudStyle.STOP
        val extras = buildList {
            if (state.phase == Phase.READY) add("Close" to onClose)
            if (onCam) add((if (state.frontCamera) "Rear cam" else "Front cam") to onFlip)
            if (onCam && onCopyLog != null && !planking) add("Copy log" to onCopyLog)
        }
        val extraButtons: @Composable RowScope.() -> Unit = {
            extras.forEach { (label, click) -> HudButton(label, click, Modifier.weight(1f)) }
        }
        val timerText = if (reached) "+" + formatElapsed(state.elapsedMs - state.goalMs) else formatTime(state.goalMs - state.elapsedMs)
        val status = statusText(state, reached)
        val hold = state.pose?.holdProgress ?: 0f

        if (sidePanel) {
            SideHud(planking, reached, status, hold, timerText, primaryLabel, primaryClick, primaryStyle, extras, panelW)
        } else
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (!onCam && !landscape) {
                    Image(painterResource(R.drawable.ic_chicken), contentDescription = null, modifier = Modifier.size(width = 56.dp, height = 110.dp))
                }
                if (landscape) {
                    // the controls go along the top, next to the timer, and the bottom stays clear
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HudButton(primaryLabel, primaryClick, Modifier.weight(2f), primaryStyle)
                            extraButtons()
                        }
                        if (planking) HudTimer(timerText, reached)
                    }
                } else if (planking) {
                    HudTimer(timerText, reached)
                }
                HudTag(status, Modifier.padding(top = 10.dp))
                if (state.phase == Phase.READY && onCam && hold > 0f) {
                    LinearProgressIndicator(progress = { hold }, modifier = Modifier.padding(top = 8.dp).fillMaxWidth(0.6f))
                }
            }

            if (!landscape) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HudButton(primaryLabel, primaryClick, Modifier.fillMaxWidth(), primaryStyle, height = 56.dp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { extraButtons() }
                }
            }
        }
    }
}

/** The landscape camera HUD: the hint over the picture and, while planking, two small pieces in the corners; otherwise the controls in the free strip. */
@Composable
internal fun SideHud(
    planking: Boolean, reached: Boolean, status: String, hold: Float, timerText: String,
    primaryLabel: String, primaryClick: () -> Unit, primaryStyle: HudStyle, extras: List<Pair<String, () -> Unit>>, panelW: androidx.compose.ui.unit.Dp,
) {
    Box(Modifier.fillMaxSize().systemBarsPadding().padding(12.dp)) {
        // the hint, over the picture
        Column(Modifier.align(Alignment.TopCenter), horizontalAlignment = Alignment.CenterHorizontally) {
            HudTag(status, Modifier.widthIn(max = 420.dp))
            if (!planking && hold > 0f) {
                LinearProgressIndicator(progress = { hold }, modifier = Modifier.padding(top = 8.dp).width(180.dp))
            }
        }
        if (planking) {
            // while planking the player takes the middle of the screen, so only two small pieces stay on it, stacked on the
            // right edge at mid-height where they clear the frame's corner marks
            Column(Modifier.align(Alignment.CenterEnd), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                HudTimer(timerText, reached)
                HudButton(primaryLabel, primaryClick, Modifier.width(150.dp), primaryStyle)
            }
        } else {
            // the controls live in the free strip beside the picture
            Column(
                Modifier.align(Alignment.CenterEnd).width(panelW - 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HudButton(primaryLabel, primaryClick, Modifier.fillMaxWidth(), primaryStyle, height = 60.dp)
                extras.forEach { (label, click) -> HudButton(label, click, Modifier.fillMaxWidth()) }
            }
        }
    }
}

/** Camera preview, skeleton overlay and the pose analyser; only exists while the camera is on. */
@Composable
private fun CameraLayer(
    state: UiState, onPose: (List<Landmark>?, Float) -> Unit, progress: Float, view: () -> WorldView, nowSec: () -> Float,
) {
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
    // the target ghost shows until a plank is found, then fades away
    val inPosition = state.pose?.grade.let { it == PlankGrade.OK || it == PlankGrade.PERFECT }
    // ...and the finding-you overlay (dim layer, dashed frame, crosshair) clears the moment a body is seen
    val bodyFound = state.pose?.grade.let { it != null && it != PlankGrade.NO_BODY }
    val ghostTarget = if (state.phase != Phase.READY || inPosition) 0f else if (bodyFound) 0.55f else 1f
    val ghost by androidx.compose.animation.core.animateFloatAsState(ghostTarget, tween(450), label = "ghost")
    val findingAlpha by androidx.compose.animation.core.animateFloatAsState(if (bodyFound) 0f else 1f, tween(350), label = "finding")
    StartSequenceLayers(
        progress, view, nowSec, Modifier.fillMaxSize(), positionAlpha = findingAlpha,
        camera = { m -> CameraPreview(front = state.frontCamera, analyzer = analyzer, executor = executor, modifier = m) },
        pose = { avatar -> PoseOverlay(state.pose, mirror = state.frontCamera, modifier = Modifier.fillMaxSize(), avatar = avatar, view = view, templateAlpha = ghost) },
    )
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
        "Your head is out of frame. Move the phone further back."
    state.pose.feetOutOfFrame && state.phase == Phase.READY ->
        if (state.pose.aspect < 1f) "Your feet are out of frame. In portrait the phone needs to be about 2 m away, or turn it sideways."
        else "Your feet are out of frame. Move the phone further back."
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
    // Flipping between the two landscapes (or back) does not change the configuration, so watch the display itself
    var rotation by remember { mutableIntStateOf(view.display?.rotation ?: Surface.ROTATION_0) }
    DisposableEffect(configuration) {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
        val l = object : android.hardware.display.DisplayManager.DisplayListener {
            override fun onDisplayAdded(id: Int) = Unit
            override fun onDisplayRemoved(id: Int) = Unit
            override fun onDisplayChanged(id: Int) { rotation = view.display?.rotation ?: rotation }
        }
        rotation = view.display?.rotation ?: rotation
        dm.registerDisplayListener(l, null)
        onDispose { dm.unregisterDisplayListener(l) }
    }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_START // the whole frame, pinned to the left (top): the rest of the screen is for the controls
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE // a texture view, so it can scale and fade
        }
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
            SessionDebug.sink?.invoke("CAMERA_BOUND display_rot=$rotation front=$front")
        }, ContextCompat.getMainExecutor(context))
        onDispose { runCatching { future.get().unbindAll() } }
    }
    AndroidView({ previewView }, modifier)
}

private val Ink = Color(0xFF3B2A20)         // the hand-drawn outline
private val GoldGlow = Color(0xFFFFB300)
private val SparkleGold = Color(0xFFFFE082)

internal fun DrawScope.drawStar(c: Offset, r: Float, color: Color) {
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
internal fun PoseOverlay(
    pose: PoseUi?, mirror: Boolean, modifier: Modifier, avatar: Float = 0f, view: () -> WorldView = { WorldView() },
    /** The exercise whose ghost template is drawn as the target to get into, faded in by [templateAlpha] (0..1). */
    exercise: Exercise = Exercises.PLANK, templateAlpha: Float = 0f,
) {
    val sparkling = (pose?.aura ?: 0f) > 0.12f
    var clockMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(sparkling) {
        while (sparkling) withFrameNanos { clockMs = it / 1_000_000 } // only animates while there is something to animate
    }
    // which way the player faces, as it appears on screen: the ghost turns to match once a body has been seen
    val facingRight = remember { booleanArrayOf(false) }
    Canvas(modifier) {
        val aspect = pose?.aspect ?: (4f / 3f)
        val viewAspect = size.width / size.height
        // the whole camera frame, like the live preview; the avatar lives inside this same frame, so the corner marks
        // keep meaning something once the camera has gone
        val w = if (viewAspect > aspect) size.height * aspect else size.width
        val h = if (viewAspect > aspect) size.height else size.width / aspect
        // pinned left while the camera shows; once the scene is up the frame (and the player in it) glides to the centre
        val ox = (size.width - w) / 2 * avatar.coerceIn(0f, 1f)
        val oy = 0f
        fun at(x: Float, y: Float) = Offset(ox + (if (mirror) 1 - x else x) * w, oy + y * h)
        drawCaptureMarks(ox, oy, w, h, 1f)
        if (pose != null && pose.grade != PlankGrade.NO_BODY && pose.landmarks.size >= PoseIdx.COUNT) {
            val nose = pose.landmarks[PoseIdx.NOSE]; val hip = pose.landmarks[PoseIdx.L_HIP]
            if (nose.visibility > 0.3f && hip.visibility > 0.3f) {
                val nx = if (mirror) 1 - nose.x else nose.x; val hx = if (mirror) 1 - hip.x else hip.x
                facingRight[0] = nx > hx
            }
        }
        if (templateAlpha > 0.01f && avatar < 0.01f) drawTemplate(exercise, facingRight[0], ox, oy, w, h, templateAlpha * (0.85f + 0.3f * (0.5f + 0.5f * sin(clockMs / 700.0).toFloat())))
        if (pose == null) return@Canvas
        // once it is an avatar it lives in the field: it turns with the phone like the scene does, and stands on the grass
        val v = view()
        val pxDegX = size.width / (if (size.width > size.height) 80f else 55f)
        val pxDegY = size.height / (if (size.width > size.height) 38f else 70f)
        withTransform({
            if (avatar > 0f) {
                rotate(v.roll * avatar, Offset(size.width / 2, size.height / 2))
                translate(-v.yaw * pxDegX * avatar, v.pitch * pxDegY * avatar)
            }
        }) {
            if (avatar > 0f) drawGroundShadow(pose, avatar, ::at)
            drawPoseScene(pose, clockMs, heightPx = h) { x, y -> at(x, y) }
        }
    }
}

/** A soft shadow under the body and darker spots under the hands, knees and feet, so it reads as lying on the grass. */
internal fun DrawScope.drawGroundShadow(pose: PoseUi, avatar: Float, at: (Float, Float) -> Offset) {
    val lms = pose.landmarks
    if (lms.size < PoseIdx.COUNT) return
    fun p(i: Int): Offset? = lms[i].takeIf { it.visibility >= 0.3f }?.let { at(it.x, it.y) }
    val touching = listOf(13, 14, 15, 16, 25, 26, 27, 28).mapNotNull(::p)
    val all = listOf(0, 11, 12, 13, 14, 15, 16, 23, 24, 25, 26, 27, 28).mapNotNull(::p)
    if (touching.isEmpty() || all.isEmpty()) return
    val ground = touching.maxOf { it.y } + 7.dp.toPx()
    val cx = (all.minOf { it.x } + all.maxOf { it.x }) / 2
    val half = (all.maxOf { it.x } - all.minOf { it.x }) / 2 + 14.dp.toPx()
    drawOval(Color(0xFF0A1A08).copy(alpha = 0.32f * avatar), Offset(cx + 10.dp.toPx() - half, ground - 9.dp.toPx()), Size(2 * half, 20.dp.toPx()))
    listOf(15, 16, 27, 28).mapNotNull(::p).forEach {
        drawOval(Color(0xFF0A1A08).copy(alpha = 0.55f * avatar), Offset(it.x - 17.dp.toPx(), ground - 6.5.dp.toPx()), Size(34.dp.toPx(), 13.dp.toPx()))
    }
}

/**
 * The goal zone, aura glow, sparkles and skeleton for one pose. [map] takes normalised image coordinates to canvas
 * pixels and must scale y by the image height in pixels, which is [heightPx] (the zone's width is in that unit).
 * Shared by the live overlay and the summary card's replay. [baseColor] overrides the grade's colour (to blend them).
 */
internal fun DrawScope.drawPoseScene(
    pose: PoseUi, nowMs: Long, baseColor: Color? = null, heightPx: Float = size.height, yaw: Float = 0f, pitch: Float = 0f,
    map: (Float, Float) -> Offset,
) {
        fun at(x: Float, y: Float) = map(x, y)
        fun at(l: Landmark) = at(l.x, l.y)
        val h = heightPx

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

        if (pose.landmarks.size < 29) return
        val lms = pose.landmarks
        fun visible(i: Int) = lms[i].visibility >= 0.3f
        val base = baseColor ?: when (pose.grade) {
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

        val palette = BonePalette(ink = Ink, fill = base, shine = Color.White)
        drawBoneSkeleton(lms, { at(it) }, pose.judgedSide, palette, yaw, pitch)
        drawFormDots(pose) { at(it) }
        if (pose.hipInZone == true) {
            val judgedHip = if (pose.judgedSide == 'L') 23 else 24
            if (visible(judgedHip)) drawCircle(Green, 9.dp.toPx(), at(lms[judgedHip]))
        }
}

private val TemplateBlue = Color(0xFF00B4FF)
private val FrameGreen = Color(0xFF5FD078)

/**
 * The ghost of [exercise]: a semi-transparent electric blue skeleton where the player is expected to be. It is drawn into
 * one layer so the bones do not show through each other, then faded to about a fifth of full strength.
 */
private fun DrawScope.drawTemplate(exercise: Exercise, facingRight: Boolean, ox: Float, oy: Float, w: Float, h: Float, strength: Float) {
    val lms = MutableList(PoseIdx.COUNT) { Landmark(0f, 0f, 0f) }
    exercise.template.forEach { (i, xy) -> lms[i] = Landmark(if (facingRight) 1f - xy.first else xy.first, xy.second, 1f) }
    drawIntoCanvas { canvas ->
        val paint = androidx.compose.ui.graphics.Paint().apply { alpha = (0.2f * strength).coerceIn(0f, 1f) }
        canvas.saveLayer(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height), paint)
        drawBoneSkeleton(lms, { Offset(ox + it.x * w, oy + it.y * h) }, 'L', BonePalette(ink = Color(0xFFD6F4FF), fill = TemplateBlue, shine = Color.White), 0f, 0f)
        canvas.restore()
    }
}

private val InLine = Color(0xFF5FD078)
private val NearLine = Color(0xFFFFB300)
private val OffLine = Color(0xFFFF6E40)

/**
 * The three form dots, shoulder, hip and knee on the side being judged, joined by a line: green while the thigh carries
 * straight on from the torso (a plank, or a knee plank), amber as it starts to bend, orange when it is bent as on all fours.
 */
private fun DrawScope.drawFormDots(pose: PoseUi, at: (Landmark) -> Offset) {
    val lms = pose.landmarks
    if (lms.size < PoseIdx.COUNT || pose.grade == PlankGrade.NO_BODY) return
    val m = plankMetrics(lms, pose.aspect) ?: return
    val line = m.kneeLineDeg ?: return
    val ids = if (m.side == 'L') intArrayOf(PoseIdx.L_SHOULDER, PoseIdx.L_HIP, PoseIdx.L_KNEE) else intArrayOf(PoseIdx.R_SHOULDER, PoseIdx.R_HIP, PoseIdx.R_KNEE)
    if (ids.any { lms[it].visibility < 0.3f }) return
    val tol = PlankTolerances()
    val color = if (line >= tol.perfectKneeLineDeg) InLine else if (line >= tol.okKneeLineDeg) NearLine else OffLine
    val pts = ids.map { at(lms[it]) }
    drawLine(color.copy(alpha = 0.85f), pts[0], pts[1], 2.5.dp.toPx(), StrokeCap.Round)
    drawLine(color.copy(alpha = 0.85f), pts[1], pts[2], 2.5.dp.toPx(), StrokeCap.Round)
    pts.forEach {
        drawCircle(Color.White, 6.5.dp.toPx(), it)
        drawCircle(color, 4.5.dp.toPx(), it)
    }
}

/** Small green corner marks on the edge of the camera frame, so it is clear where the motion capture works. */
private fun DrawScope.drawCaptureMarks(left: Float, top: Float, width: Float, height: Float, alpha: Float) {
    if (alpha <= 0.01f) return
    val c = FrameGreen.copy(alpha = 0.9f * alpha)
    val arm = 24.dp.toPx(); val sw = 3.dp.toPx(); val inset = 4.dp.toPx()
    val l = left + inset; val t = top + inset; val r = left + width - inset; val b = top + height - inset
    for ((x, y, dx, dy) in listOf(listOf(l, t, 1f, 1f), listOf(r, t, -1f, 1f), listOf(l, b, 1f, -1f), listOf(r, b, -1f, -1f))) {
        drawLine(c, Offset(x, y), Offset(x + dx * arm, y), sw, StrokeCap.Round)
        drawLine(c, Offset(x, y), Offset(x, y + dy * arm), sw, StrokeCap.Round)
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

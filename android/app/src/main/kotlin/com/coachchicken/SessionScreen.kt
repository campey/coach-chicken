package com.coachchicken

import android.util.Size
import androidx.camera.core.AspectRatio
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.coachchicken.core.Landmark
import com.coachchicken.core.PoseReading
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private val Gold = Color(0xFFFFD54F)
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

    KeepScreenOn(true)
    Box(Modifier.fillMaxSize().background(if (onCam) Color.Black else MaterialTheme.colorScheme.background)) {
        if (onCam) {
            CameraLayer(state, onPose)
        }

        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (!onCam) {
                    Image(painterResource(R.drawable.ic_chicken), contentDescription = null, modifier = Modifier.size(width = 56.dp, height = 110.dp))
                }
                if (state.phase == Phase.PLANKING) {
                    Text(
                        if (reached) "+" + formatElapsed(state.elapsedMs - state.goalMs) else formatTime(state.goalMs - state.elapsedMs),
                        fontSize = 72.sp,
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
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.phase == Phase.READY) {
                    Button(onStartPlank, Modifier.fillMaxWidth()) { Text("Start plank") }
                } else {
                    Button(onEnd, Modifier.fillMaxWidth()) { Text(if (reached) "Done" else "Stop") }
                }
                val extras = ButtonDefaults.outlinedButtonColors(containerColor = scrim, contentColor = fg)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.phase == Phase.READY) {
                        OutlinedButton(onClose, Modifier.weight(1f), colors = extras) { Text("Close") }
                    }
                    if (onCam) {
                        OutlinedButton(onFlip, Modifier.weight(1f), colors = extras) { Text(if (state.frontCamera) "Rear cam" else "Front cam") }
                    }
                    if (onCam && onCopyLog != null) {
                        OutlinedButton(onCopyLog, Modifier.weight(1f), colors = extras) { Text("Copy log") }
                    }
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
    state.phase == Phase.PLANKING ->
        if (state.cameraOn) "Planking. Get up or tap Stop to end early." else "Planking. Tap Stop to end early."
    !state.cameraOn -> "Get into position, then tap Start plank."
    state.pose == null || state.pose.reading == PoseReading.NO_BODY ->
        "Prop the phone so the camera sees you side-on, head to toe, then plank (or tap Start plank)."
    state.pose.reading == PoseReading.NOT_PLANK -> "Get into plank position."
    else -> "Hold it..."
}

@Composable
private fun CameraPreview(front: Boolean, analyzer: ImageAnalysis.Analyzer, executor: java.util.concurrent.Executor, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
    }
    DisposableEffect(front) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val selector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .build()
            val preview = Preview.Builder().setResolutionSelector(selector).build()
                .also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(selector)
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

/** Draws the skeleton over a FIT_CENTER preview: the image is letterboxed, so map normalised coords into that box. */
@Composable
private fun PoseOverlay(pose: PoseUi?, mirror: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        if (pose == null || pose.landmarks.size < 29) return@Canvas
        val viewAspect = size.width / size.height
        val (w, h) = if (viewAspect > pose.aspect) (size.height * pose.aspect) to size.height
        else size.width to (size.width / pose.aspect)
        val ox = (size.width - w) / 2
        val oy = (size.height - h) / 2
        fun at(l: Landmark) = Offset(ox + (if (mirror) 1 - l.x else l.x) * w, oy + l.y * h)

        val color = if (pose.reading == PoseReading.PLANK) Gold else Color.White
        fun visible(i: Int) = pose.landmarks[i].visibility >= 0.5f
        BONES.forEach { (a, b) ->
            if (visible(a) && visible(b)) drawLine(color, at(pose.landmarks[a]), at(pose.landmarks[b]), 6.dp.toPx(), StrokeCap.Round)
        }
        JOINTS.forEach { if (visible(it)) drawCircle(color, 7.dp.toPx(), at(pose.landmarks[it])) }
    }
}

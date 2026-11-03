package com.coachchicken

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.coachchicken.core.Landmark
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker

/**
 * Runs MediaPipe Pose on camera frames (on the analysis thread) and reports the first person's
 * landmarks plus the upright image's aspect ratio (width / height). Reports null when nobody is found.
 */
class PoseAnalyzer(
    context: Context,
    private val onResult: (landmarks: List<Landmark>?, aspect: Float) -> Unit,
) : ImageAnalysis.Analyzer, AutoCloseable {

    private val landmarker: PoseLandmarker? = runCatching {
        val base = BaseOptions.builder().setModelAssetPath(MODEL).build()
        PoseLandmarker.createFromOptions(
            context,
            PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(base)
                .setRunningMode(RunningMode.VIDEO)
                .setNumPoses(1)
                .build(),
        )
    }.onFailure { Log.e(TAG, "Could not load pose model", it) }.getOrNull()

    private var lastTimestampMs = -1L

    override fun analyze(image: ImageProxy) {
        try {
            val lm = landmarker ?: return
            val ts = image.imageInfo.timestamp / 1_000_000
            if (ts <= lastTimestampMs) return // MediaPipe needs strictly increasing timestamps
            lastTimestampMs = ts

            val raw = image.toBitmap()
            val rotation = image.imageInfo.rotationDegrees
            val upright = if (rotation == 0) raw else
                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)

            val result = lm.detectForVideo(BitmapImageBuilder(upright).build(), ts)
            val points = result.landmarks().firstOrNull()?.map { Landmark(it.x(), it.y(), it.visibility().orElse(0f)) }
            onResult(points, upright.width.toFloat() / upright.height)
        } catch (t: Throwable) {
            Log.w(TAG, "Pose analysis failed", t)
        } finally {
            image.close()
        }
    }

    override fun close() {
        landmarker?.close()
    }

    private companion object {
        const val MODEL = "pose_landmarker_lite.task"
        const val TAG = "PoseAnalyzer"
    }
}

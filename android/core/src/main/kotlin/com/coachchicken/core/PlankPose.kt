package com.coachchicken.core

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot

/** A pose landmark in image-normalised coordinates (0..1, y pointing down). */
data class Landmark(val x: Float, val y: Float, val visibility: Float = 1f)

/** Indices into MediaPipe's 33-point pose model. */
object PoseIdx {
    const val L_SHOULDER = 11; const val R_SHOULDER = 12
    const val L_ELBOW = 13; const val R_ELBOW = 14
    const val L_WRIST = 15; const val R_WRIST = 16
    const val L_HIP = 23; const val R_HIP = 24
    const val L_KNEE = 25; const val R_KNEE = 26
    const val L_ANKLE = 27; const val R_ANKLE = 28
    const val COUNT = 33
}

enum class PoseReading { NO_BODY, NOT_PLANK, PLANK }

/** The numbers [readPlank] decides on, so they can be logged and the thresholds tuned. */
data class PlankMetrics(
    /** 'L' or 'R': the side of the body the camera sees best. */
    val side: Char,
    /** Angle of the shoulder-to-ankle line above horizontal, degrees (0 = flat). */
    val bodyAngleDeg: Double,
    /** Angle at the hip between shoulder and ankle, degrees (180 = perfectly straight). */
    val hipAngleDeg: Double,
    /** How far the elbow sits below the shoulder, as a fraction of torso length. */
    val elbowDropRatio: Double,
    /** How far the elbow is sideways of the shoulder, as a fraction of torso length. */
    val elbowOffsetRatio: Double,
    /** Lowest visibility among the four joints used. */
    val minVisibility: Float,
)

/** Measures the better-seen side of the body, or null if there aren't enough landmarks. */
fun plankMetrics(landmarks: List<Landmark>, aspect: Float = 1f): PlankMetrics? {
    if (landmarks.size < PoseIdx.COUNT) return null
    fun side(vararg idx: Int) = idx.map { landmarks[it] }
    val left = side(PoseIdx.L_SHOULDER, PoseIdx.L_ELBOW, PoseIdx.L_HIP, PoseIdx.L_ANKLE)
    val right = side(PoseIdx.R_SHOULDER, PoseIdx.R_ELBOW, PoseIdx.R_HIP, PoseIdx.R_ANKLE)
    val useLeft = left.sumOf { it.visibility.toDouble() } >= right.sumOf { it.visibility.toDouble() }
    val chosen = if (useLeft) left else right

    val (s, e, h, a) = chosen
    fun px(l: Landmark) = l.x * aspect
    val torso = hypot((px(s) - px(h)).toDouble(), (s.y - h.y).toDouble())
    val bodyAngle = Math.toDegrees(atan2(abs(a.y - s.y).toDouble(), abs(px(a) - px(s)).toDouble()))
    val hipAngle = angleAt(px(h), h.y, px(s), s.y, px(a), a.y)
    return PlankMetrics(
        side = if (useLeft) 'L' else 'R',
        bodyAngleDeg = bodyAngle,
        hipAngleDeg = hipAngle,
        elbowDropRatio = if (torso < 1e-6) 0.0 else (e.y - s.y) / torso,
        elbowOffsetRatio = if (torso < 1e-6) Double.MAX_VALUE else abs(px(e) - px(s)) / torso,
        minVisibility = chosen.minOf { it.visibility },
    )
}

/**
 * Is this a side-on plank? Uses whichever side of the body the camera sees best.
 *  - the shoulder-to-ankle line is within [maxBodyAngleDeg] of horizontal,
 *  - the body is straight at the hip (no sagging or piking),
 *  - the upper arm props the shoulder up: the elbow sits well below the shoulder, roughly underneath it.
 *    This is what tells a plank from simply lying on the floor.
 *
 * [aspect] is image width / height, so angles are measured in true proportions, not normalised ones.
 */
fun readPlank(
    landmarks: List<Landmark>,
    aspect: Float = 1f,
    minVisibility: Float = 0.5f,
    maxBodyAngleDeg: Double = 30.0,
    minHipAngleDeg: Double = 150.0,
): PoseReading {
    val m = plankMetrics(landmarks, aspect) ?: return PoseReading.NO_BODY
    if (m.minVisibility < minVisibility) return PoseReading.NO_BODY
    val plank = m.bodyAngleDeg <= maxBodyAngleDeg &&
        m.hipAngleDeg >= minHipAngleDeg &&
        m.elbowDropRatio >= 0.25 &&
        m.elbowOffsetRatio <= 0.8
    return if (plank) PoseReading.PLANK else PoseReading.NOT_PLANK
}

/** Angle in degrees at vertex (vx, vy) between the rays to (ax, ay) and (bx, by). */
private fun angleAt(vx: Float, vy: Float, ax: Float, ay: Float, bx: Float, by: Float): Double {
    val ux = (ax - vx).toDouble(); val uy = (ay - vy).toDouble()
    val wx = (bx - vx).toDouble(); val wy = (by - vy).toDouble()
    val denom = hypot(ux, uy) * hypot(wx, wy)
    if (denom < 1e-9) return 0.0
    return Math.toDegrees(acos(((ux * wx + uy * wy) / denom).coerceIn(-1.0, 1.0)))
}

private operator fun <T> List<T>.component4(): T = this[3]

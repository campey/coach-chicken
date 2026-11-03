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
    /**
     * The lowest body point that was visible enough to measure the body line to:
     * 'A' ankle, else 'K' knee, else 'H' hip only (legs out of frame; the line is then just the torso).
     */
    val lower: Char,
    /** Angle of the shoulder-to-lower-point line above horizontal, degrees (0 = flat). */
    val bodyAngleDeg: Double,
    /** Angle at the hip between shoulder and lower point, degrees (180 = perfectly straight; 180 when [lower] is 'H'). */
    val hipAngleDeg: Double,
    /** How far the elbow sits below the shoulder, as a fraction of torso length. */
    val elbowDropRatio: Double,
    /** How far the elbow is sideways of the shoulder, as a fraction of torso length. */
    val elbowOffsetRatio: Double,
    /** Lowest visibility among the joints used. */
    val minVisibility: Float,
)

/**
 * Measures the better-seen side of the body, or null if there aren't enough landmarks.
 * Missing legs are tolerated: ankle if visible (>= [minVisibility]), else knee, else the hip alone.
 */
fun plankMetrics(landmarks: List<Landmark>, aspect: Float = 1f, minVisibility: Float = 0.5f): PlankMetrics? {
    if (landmarks.size < PoseIdx.COUNT) return null
    fun score(sho: Int, elb: Int, hip: Int, kne: Int, ank: Int) =
        listOf(sho, elb, hip).sumOf { landmarks[it].visibility.toDouble() } +
            maxOf(landmarks[kne].visibility, landmarks[ank].visibility)
    val useLeft = score(PoseIdx.L_SHOULDER, PoseIdx.L_ELBOW, PoseIdx.L_HIP, PoseIdx.L_KNEE, PoseIdx.L_ANKLE) >=
        score(PoseIdx.R_SHOULDER, PoseIdx.R_ELBOW, PoseIdx.R_HIP, PoseIdx.R_KNEE, PoseIdx.R_ANKLE)
    val s = landmarks[if (useLeft) PoseIdx.L_SHOULDER else PoseIdx.R_SHOULDER]
    val e = landmarks[if (useLeft) PoseIdx.L_ELBOW else PoseIdx.R_ELBOW]
    val h = landmarks[if (useLeft) PoseIdx.L_HIP else PoseIdx.R_HIP]
    val knee = landmarks[if (useLeft) PoseIdx.L_KNEE else PoseIdx.R_KNEE]
    val ankle = landmarks[if (useLeft) PoseIdx.L_ANKLE else PoseIdx.R_ANKLE]
    val (lowerCode, lowerPoint) = when {
        ankle.visibility >= minVisibility -> 'A' to ankle
        knee.visibility >= minVisibility -> 'K' to knee
        else -> 'H' to h
    }

    fun px(l: Landmark) = l.x * aspect
    val torso = hypot((px(s) - px(h)).toDouble(), (s.y - h.y).toDouble())
    val bodyAngle = Math.toDegrees(atan2(abs(lowerPoint.y - s.y).toDouble(), abs(px(lowerPoint) - px(s)).toDouble()))
    val hipAngle = if (lowerCode == 'H') 180.0 else angleAt(px(h), h.y, px(s), s.y, px(lowerPoint), lowerPoint.y)
    return PlankMetrics(
        side = if (useLeft) 'L' else 'R',
        lower = lowerCode,
        bodyAngleDeg = bodyAngle,
        hipAngleDeg = hipAngle,
        elbowDropRatio = if (torso < 1e-6) 0.0 else (e.y - s.y) / torso,
        elbowOffsetRatio = if (torso < 1e-6) Double.MAX_VALUE else abs(px(e) - px(s)) / torso,
        minVisibility = minOf(s.visibility, e.visibility, h.visibility, if (lowerCode == 'H') 1f else lowerPoint.visibility),
    )
}

/**
 * Is this a side-on plank? Uses whichever side of the body the camera sees best.
 *  - the shoulder-to-ankle line is within [maxBodyAngleDeg] of horizontal
 *    (to the knee, or just the hip, when the legs are out of frame),
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
    maxBodyAngleDeg: Double = 20.0,
    minHipAngleDeg: Double = 150.0,
): PoseReading {
    val m = plankMetrics(landmarks, aspect, minVisibility) ?: return PoseReading.NO_BODY
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


package com.coachchicken.core

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot

/** A pose landmark in image-normalised coordinates (0..1, y pointing down). */
data class Landmark(val x: Float, val y: Float, val visibility: Float = 1f)

/** Indices into MediaPipe's 33-point pose model. */
object PoseIdx {
    const val NOSE = 0
    const val L_SHOULDER = 11; const val R_SHOULDER = 12
    const val L_ELBOW = 13; const val R_ELBOW = 14
    const val L_WRIST = 15; const val R_WRIST = 16
    const val L_HIP = 23; const val R_HIP = 24
    const val L_KNEE = 25; const val R_KNEE = 26
    const val L_ANKLE = 27; const val R_ANKLE = 28
    const val COUNT = 33
}

enum class PoseReading { NO_BODY, NOT_PLANK, PLANK }

/** How good the plank looks: PERFECT is gold, OK is within tolerance (still a plank), NOT_PLANK is not. */
enum class PlankGrade {
    NO_BODY, NOT_PLANK, OK, PERFECT;

    val reading: PoseReading
        get() = when (this) {
            NO_BODY -> PoseReading.NO_BODY
            NOT_PLANK -> PoseReading.NOT_PLANK
            else -> PoseReading.PLANK
        }
}

/**
 * Limits for "within tolerance" (OK) and "perfect". The OK limits are deliberately generous: they were tuned on a
 * real phone recording (front camera, loose jersey, legs partly out of frame) where a good plank wobbled around
 * a 155 degree hip angle with dips into the 130s and elbow points drifting about.
 */
data class PlankTolerances(
    val minVisibility: Float = 0.3f,
    /** A leg point outside the image is only trusted if MediaPipe is at least this sure of it... */
    val offFrameMinVisibility: Float = 0.5f,
    /** ...and it is no further than this (in image widths/heights) beyond the edge. */
    val offFrameMargin: Float = 0.4f,
    val okBodyDeg: Double = 28.0,
    val okHipDeg: Double = 130.0,
    val okElbowDrop: Double = 0.10,
    val okElbowOffset: Double = 0.9,
    // PERFECT deliberately has no body-angle limit: that angle is measured against the camera's horizon, so a phone
    // propped at a slight tilt (one real run read a straight plank as 12-17 degrees) must not cost a perfect grade.
    // Perfect is about the shape of the body (straight hips, forearm propped) and staying in the green zone.
    val perfectHipDeg: Double = 160.0,
    val perfectElbowDrop: Double = 0.30,
    val perfectElbowOffset: Double = 0.45,
)

/** The numbers [gradePlank] decides on, so they can be logged and the thresholds tuned. */
data class PlankMetrics(
    /** 'L' or 'R': the side of the body the camera sees best. */
    val side: Char,
    /**
     * The lowest body point that was visible enough, and inside the frame, to measure the body line to:
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

private fun Landmark.inFrame() = x in -0.02f..1.02f && y in -0.02f..1.02f
private fun Landmark.within(margin: Float) = x in -margin..(1f + margin) && y in -margin..(1f + margin)

/** The joints a plank is judged on: the better-seen side of the body, with the lowest usable leg point. */
internal class PlankJoints(
    val left: Boolean,
    val shoulder: Landmark,
    val elbow: Landmark,
    val hip: Landmark,
    /** 'A' ankle, 'K' knee, 'H' hip only. */
    val lowerCode: Char,
    val lower: Landmark,
)

internal fun pickJoints(
    landmarks: List<Landmark>,
    minVisibility: Float,
    offFrameMinVisibility: Float = 0.5f,
    offFrameMargin: Float = 0.4f,
): PlankJoints? {
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
    fun usable(l: Landmark) =
        (l.visibility >= minVisibility && l.inFrame()) ||
            (l.visibility >= offFrameMinVisibility && l.within(offFrameMargin))
    return when {
        usable(ankle) -> PlankJoints(useLeft, s, e, h, 'A', ankle)
        usable(knee) -> PlankJoints(useLeft, s, e, h, 'K', knee)
        else -> PlankJoints(useLeft, s, e, h, 'H', h)
    }
}

/**
 * Measures the better-seen side of the body, or null if there aren't enough landmarks.
 * Missing legs are tolerated: ankle if usable, else knee, else the hip alone. A leg point inside the image is usable
 * if it is visible enough. One beyond the edge is MediaPipe's extrapolation of where the leg must be: on a real phone
 * recording it was plausible but noisy, so it is only used when the model is fairly sure and it isn't far off-screen.
 */
fun plankMetrics(
    landmarks: List<Landmark>,
    aspect: Float = 1f,
    minVisibility: Float = 0.3f,
    offFrameMinVisibility: Float = 0.5f,
    offFrameMargin: Float = 0.4f,
): PlankMetrics? {
    val j = pickJoints(landmarks, minVisibility, offFrameMinVisibility, offFrameMargin) ?: return null
    val s = j.shoulder; val e = j.elbow; val h = j.hip; val lowerPoint = j.lower
    fun px(l: Landmark) = l.x * aspect
    val torso = hypot((px(s) - px(h)).toDouble(), (s.y - h.y).toDouble())
    val bodyAngle = Math.toDegrees(atan2(abs(lowerPoint.y - s.y).toDouble(), abs(px(lowerPoint) - px(s)).toDouble()))
    val hipAngle = if (j.lowerCode == 'H') 180.0 else angleAt(px(h), h.y, px(s), s.y, px(lowerPoint), lowerPoint.y)
    return PlankMetrics(
        side = if (j.left) 'L' else 'R',
        lower = j.lowerCode,
        bodyAngleDeg = bodyAngle,
        hipAngleDeg = hipAngle,
        elbowDropRatio = if (torso < 1e-6) 0.0 else (e.y - s.y) / torso,
        elbowOffsetRatio = if (torso < 1e-6) Double.MAX_VALUE else abs(px(e) - px(s)) / torso,
        minVisibility = minOf(s.visibility, e.visibility, h.visibility, if (j.lowerCode == 'H') 1f else lowerPoint.visibility),
    )
}

/**
 * Grades already-measured [PlankMetrics] (visibility is checked by the caller). [targetOffset] is how far the hip is
 * from the green goal zone (1 = its edge); when given, a perfect plank must be inside the zone.
 */
fun gradeMetrics(m: PlankMetrics, tol: PlankTolerances = PlankTolerances(), targetOffset: Float? = null): PlankGrade {
    val ok = m.bodyAngleDeg <= tol.okBodyDeg &&
        m.hipAngleDeg >= tol.okHipDeg &&
        m.elbowDropRatio >= tol.okElbowDrop &&
        m.elbowOffsetRatio <= tol.okElbowOffset
    if (!ok) return PlankGrade.NOT_PLANK
    val perfect = m.hipAngleDeg >= tol.perfectHipDeg &&
        m.elbowDropRatio >= tol.perfectElbowDrop &&
        m.elbowOffsetRatio <= tol.perfectElbowOffset &&
        (targetOffset == null || targetOffset <= 1f)
    return if (perfect) PlankGrade.PERFECT else PlankGrade.OK
}

/**
 * Is this a side-on plank, and how good? Uses whichever side of the body the camera sees best. With a [target] (the green
 * goal zone), a perfect plank must also keep its hip inside it.
 *  - the shoulder-to-ankle line is near horizontal (to the knee, or just the hip, when the legs are out of frame),
 *  - the body is straight at the hip (no sagging or piking),
 *  - the upper arm props the shoulder up: the elbow sits well below the shoulder, roughly underneath it.
 *    This is what tells a plank from simply lying on the floor.
 *
 * [aspect] is image width / height, so angles are measured in true proportions, not normalised ones.
 */
fun gradePlank(
    landmarks: List<Landmark>,
    aspect: Float = 1f,
    tol: PlankTolerances = PlankTolerances(),
    target: PlankTarget? = null,
): PlankGrade {
    val m = plankMetrics(landmarks, aspect, tol.minVisibility, tol.offFrameMinVisibility, tol.offFrameMargin)
        ?: return PlankGrade.NO_BODY
    if (m.minVisibility < tol.minVisibility) return PlankGrade.NO_BODY
    val offset = target?.let { t -> plankHip(landmarks, tol, t.side)?.let { t.offset(it, aspect) } }
    return gradeMetrics(m, tol, offset)
}

/** [gradePlank] collapsed to "is it a plank at all" (OK or PERFECT). */
fun readPlank(landmarks: List<Landmark>, aspect: Float = 1f, tol: PlankTolerances = PlankTolerances()): PoseReading =
    gradePlank(landmarks, aspect, tol).reading

/** Angle in degrees at vertex (vx, vy) between the rays to (ax, ay) and (bx, by). */
private fun angleAt(vx: Float, vy: Float, ax: Float, ay: Float, bx: Float, by: Float): Double {
    val ux = (ax - vx).toDouble(); val uy = (ay - vy).toDouble()
    val wx = (bx - vx).toDouble(); val wy = (by - vy).toDouble()
    val denom = hypot(ux, uy) * hypot(wx, wy)
    if (denom < 1e-9) return 0.0
    return Math.toDegrees(acos(((ux * wx + uy * wy) / denom).coerceIn(-1.0, 1.0)))
}


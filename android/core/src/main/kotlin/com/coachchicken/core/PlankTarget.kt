package com.coachchicken.core

import kotlin.math.abs
import kotlin.math.hypot

/**
 * The green goal zone: a corridor around the ideal straight body line, from the shoulder to the ankle (knee, or
 * straight on through the hip when the legs are out of frame). The yellow skeleton is meant to sit inside it:
 * a sagging or piking hip leaves the zone.
 *
 * Endpoints are normalised image coordinates; [halfWidth] is in image-height units (so it's aspect independent).
 */
data class PlankTarget(val ax: Float, val ay: Float, val bx: Float, val by: Float, val halfWidth: Float) {
    /** Distance of [p] from the target line as a multiple of the half-width: 0 = on the line, 1 = the zone's edge. */
    fun offset(p: Landmark, aspect: Float): Float {
        val x1 = ax * aspect; val y1 = ay
        val dx = bx * aspect - x1; val dy = by - y1
        val len = hypot(dx, dy)
        if (len < 1e-6f || halfWidth < 1e-6f) return Float.MAX_VALUE
        val cross = (p.x * aspect - x1) * dy - (p.y - y1) * dx
        return abs(cross) / len / halfWidth
    }

    fun contains(p: Landmark, aspect: Float) = offset(p, aspect) <= 1f
}

/**
 * Locks a target from the current pose: the straight line from the shoulder to the lowest usable leg point.
 * With no usable leg point it runs from the shoulder straight through the hip and on. Null if no body.
 */
fun lockTarget(landmarks: List<Landmark>, aspect: Float = 1f, minVisibility: Float = 0.3f): PlankTarget? {
    val j = pickJoints(landmarks, minVisibility) ?: return null
    val s = j.shoulder; val h = j.hip
    val torso = hypot(((s.x - h.x) * aspect).toDouble(), (s.y - h.y).toDouble()).toFloat()
    if (torso < 1e-3f) return null
    val (bx, by) = if (j.lowerCode == 'H') {
        val k = 2.2f // on through the hip towards the legs that are out of frame
        (s.x + (h.x - s.x) * k) to (s.y + (h.y - s.y) * k)
    } else {
        j.lower.x to j.lower.y
    }
    return PlankTarget(s.x, s.y, bx, by, halfWidth = 0.25f * torso)
}

/** The hip of the better-seen side, for judging it against a [PlankTarget]. */
fun plankHip(landmarks: List<Landmark>, minVisibility: Float = 0.3f): Landmark? = pickJoints(landmarks, minVisibility)?.hip

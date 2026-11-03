package com.coachchicken.core

/**
 * Turns noisy per-frame [PoseReading]s into start/stop events.
 * Start: a plank must be held for [startHoldMs]. Stop: the plank must be lost for [endGraceMs]
 * (so a dropped frame or two doesn't end the session).
 */
class PlankTracker(
    private val startHoldMs: Long = 1_500,
    private val endGraceMs: Long = 2_000,
) {
    enum class Event { STARTED, ENDED }

    var active = false
        private set
    private var plankSince: Long? = null
    private var lostSince: Long? = null

    fun update(nowMs: Long, reading: PoseReading): Event? {
        val inPlank = reading == PoseReading.PLANK
        if (!active) {
            if (!inPlank) {
                plankSince = null
                return null
            }
            val since = plankSince ?: nowMs.also { plankSince = it }
            if (nowMs - since >= startHoldMs) {
                active = true
                plankSince = null
                lostSince = null
                return Event.STARTED
            }
            return null
        }
        if (inPlank) {
            lostSince = null
            return null
        }
        val since = lostSince ?: nowMs.also { lostSince = it }
        if (nowMs - since >= endGraceMs) {
            active = false
            lostSince = null
            plankSince = null
            return Event.ENDED
        }
        return null
    }

    /** 0..1: how far through the start hold we are (1 once a plank has started). */
    fun holdProgress(nowMs: Long): Float =
        if (active) 1f else plankSince?.let { ((nowMs - it) / startHoldMs.toFloat()).coerceIn(0f, 1f) } ?: 0f

    fun reset() {
        active = false
        plankSince = null
        lostSince = null
    }
}

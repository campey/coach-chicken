package com.coachchicken.core

import kotlin.math.exp

/**
 * Turns noisy per-frame [PoseReading]s into start/stop events with a low-pass filter.
 *
 * Each frame is 1 (plank) or 0 (not); an exponential moving average [smoothed] (time constant [filterTauMs]) is
 * the filter. Because it is an average rather than "N frames in a row", scattered bad frames inside a good plank
 * (a loose sleeve, a wobbly joint) barely move it, which is what a real plank looks like to a pose detector.
 *  - Start: [smoothed] stays at or above [startAbove] for [startHoldMs].
 *  - Lapse: [smoothed] falls below [lapseBelow]. The lapse is dated from the last frame that WAS a plank.
 *  - Warning: a single WARNING [warnAfterMs] into a lapse (if the current frame is still not a plank).
 *  - Recovered: [smoothed] climbs back to [recoverAbove] (hysteresis: higher than [lapseBelow]); RECOVERED is
 *    raised if a warning had been given, so it can be silenced.
 *  - Stop: the lapse lasts [endGraceMs]; [lastEndedLostAtMs] is then when the plank was last held.
 */
class PlankTracker(
    private val startHoldMs: Long = 1_000,
    val endGraceMs: Long = 3_000,
    private val warnAfterMs: Long = 1_000,
    private val filterTauMs: Long = 700,
    private val startAbove: Float = 0.7f,
    private val lapseBelow: Float = 0.45f,
    private val recoverAbove: Float = 0.65f,
) {
    enum class Event { STARTED, ENDED, WARNING, RECOVERED }

    var active = false
        private set

    /** The low-passed "how plank-like is it" signal, 0..1. */
    var smoothed = 0f
        private set

    /** Start of the lapse that ended the most recent plank (null until one has ended). */
    var lastEndedLostAtMs: Long? = null
        private set

    private var lastUpdateMs: Long? = null
    private var lastPlankAtMs: Long? = null
    private var armedSince: Long? = null
    private var lostSince: Long? = null
    private var warned = false
    private var awaitingFirstPlank = false

    fun update(nowMs: Long, reading: PoseReading): Event? {
        val inPlank = reading == PoseReading.PLANK
        val x = if (inPlank) 1f else 0f
        val previous = lastUpdateMs
        smoothed = if (previous == null) x
        else smoothed + (x - smoothed) * (1f - exp(-(nowMs - previous).coerceAtLeast(0).toFloat() / filterTauMs))
        lastUpdateMs = nowMs
        if (inPlank) lastPlankAtMs = nowMs

        if (!active) {
            if (smoothed < startAbove) {
                armedSince = null
                return null
            }
            val since = armedSince ?: nowMs.also { armedSince = it }
            if (nowMs - since >= startHoldMs) {
                active = true
                armedSince = null
                lostSince = null
                warned = false
                return Event.STARTED
            }
            return null
        }

        if (awaitingFirstPlank) {
            // Started from a button: don't treat "not in position yet" as getting up. Armed once a plank has
            // been held for the same [startHoldMs] as a camera start, so a single stray frame can't arm it.
            if (smoothed < startAbove) {
                armedSince = null
            } else {
                val since = armedSince ?: nowMs.also { armedSince = it }
                if (nowMs - since >= startHoldMs) {
                    awaitingFirstPlank = false
                    armedSince = null
                }
            }
            return null
        }

        if (lostSince == null) {
            if (smoothed < lapseBelow) {
                lostSince = lastPlankAtMs ?: nowMs
                warned = false
            }
        } else if (smoothed >= recoverAbove) {
            lostSince = null
            val wasWarned = warned
            warned = false
            if (wasWarned) return Event.RECOVERED
        }

        val lost = lostSince
        if (lost != null) {
            if (nowMs - lost >= endGraceMs) {
                active = false
                lastEndedLostAtMs = lost
                lostSince = null
                warned = false
                return Event.ENDED
            }
            if (!inPlank && !warned && nowMs - lost >= warnAfterMs) {
                warned = true
                return Event.WARNING
            }
        }
        return null
    }

    /**
     * The plank was started some other way (a button). The tracker then watches for the plank to be
     * seen and only afterwards for it to end, so getting into position never counts as getting up.
     */
    fun startedExternally() {
        reset()
        active = true
        awaitingFirstPlank = true
    }

    /** True while a lapse has been warned about and the plank hasn't yet recovered or ended. */
    val isWarning: Boolean get() = active && warned && lostSince != null

    /** How long the current lapse has lasted (0 when the plank is fine). */
    fun lapseMs(nowMs: Long): Long = lostSince?.let { nowMs - it } ?: 0L

    /** 0..1: how far we are towards starting (1 once a plank has started). */
    fun holdProgress(nowMs: Long): Float = when {
        active -> 1f
        armedSince != null -> 0.5f + 0.5f * ((nowMs - armedSince!!) / startHoldMs.toFloat()).coerceIn(0f, 1f)
        else -> (0.5f * smoothed / startAbove).coerceIn(0f, 0.5f)
    }

    fun reset() {
        active = false
        smoothed = 0f
        lastUpdateMs = null
        lastPlankAtMs = null
        armedSince = null
        lostSince = null
        warned = false
        awaitingFirstPlank = false
    }
}

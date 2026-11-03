package com.coachchicken.core

/**
 * Turns noisy per-frame [PoseReading]s into start/stop events.
 *  - Start: a plank must be held for [startHoldMs].
 *  - Stop: the plank must be lost for [endGraceMs] (a debounce / low-pass filter), so adjusting your form or a
 *    dropped frame or two doesn't end it.
 *  - Warning: once a lapse has lasted [warnAfterMs] a single WARNING is raised, so you can get back in time;
 *    if you then get back, RECOVERED is raised (so the warning can be silenced).
 *  - A lapse is only forgiven once the plank has been back for [recoverHoldMs]; a brief false "plank"
 *    reading while getting up must not restart the grace period.
 * When a plank ends, [lastEndedLostAtMs] is when it was last held (the start of the lapse).
 */
class PlankTracker(
    private val startHoldMs: Long = 1_500,
    val endGraceMs: Long = 3_000,
    private val recoverHoldMs: Long = 500,
    private val warnAfterMs: Long = 1_000,
) {
    enum class Event { STARTED, ENDED, WARNING, RECOVERED }

    var active = false
        private set
    private var plankSince: Long? = null
    private var lostSince: Long? = null
    private var recoverSince: Long? = null
    private var awaitingFirstPlank = false
    private var warned = false

    /** Start of the lapse that ended the most recent plank (null until one has ended). */
    var lastEndedLostAtMs: Long? = null
        private set

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
        if (awaitingFirstPlank) {
            // Started from a button: don't treat "not in position yet" as getting up.
            if (inPlank) {
                val since = recoverSince ?: nowMs.also { recoverSince = it }
                if (nowMs - since >= recoverHoldMs) {
                    awaitingFirstPlank = false
                    recoverSince = null
                }
            } else {
                recoverSince = null
            }
            return null
        }
        if (inPlank) {
            if (lostSince != null) {
                val since = recoverSince ?: nowMs.also { recoverSince = it }
                if (nowMs - since >= recoverHoldMs) {
                    lostSince = null
                    recoverSince = null
                    val wasWarned = warned
                    warned = false
                    if (wasWarned) return Event.RECOVERED
                }
            }
        } else {
            recoverSince = null
            if (lostSince == null) lostSince = nowMs
        }
        val lost = lostSince
        if (lost != null) {
            if (nowMs - lost >= endGraceMs) {
                active = false
                lastEndedLostAtMs = lost
                lostSince = null
                recoverSince = null
                plankSince = null
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

    /** True while a lapse has been warned about and the plank hasn't yet recovered or ended. */
    val isWarning: Boolean get() = active && warned && lostSince != null

    /** How long the current lapse has lasted (0 when the plank is fine). */
    fun lapseMs(nowMs: Long): Long = lostSince?.let { nowMs - it } ?: 0L

    /**
     * The plank was started some other way (a button). The tracker then watches for the plank to be
     * seen and only afterwards for it to end, so getting into position never counts as getting up.
     */
    fun startedExternally() {
        reset()
        active = true
        awaitingFirstPlank = true
    }

    /** 0..1: how far through the start hold we are (1 once a plank has started). */
    fun holdProgress(nowMs: Long): Float =
        if (active) 1f else plankSince?.let { ((nowMs - it) / startHoldMs.toFloat()).coerceIn(0f, 1f) } ?: 0f

    fun reset() {
        active = false
        plankSince = null
        lostSince = null
        recoverSince = null
        awaitingFirstPlank = false
        warned = false
    }
}

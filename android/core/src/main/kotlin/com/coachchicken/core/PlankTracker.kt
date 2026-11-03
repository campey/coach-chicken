package com.coachchicken.core

/**
 * Turns noisy per-frame [PoseReading]s into start/stop events.
 *  - Start: a plank must be held for [startHoldMs].
 *  - Stop: the plank must be lost for [endGraceMs], so a dropped frame or two doesn't end the session.
 *  - A lapse is only forgiven once the plank has been back for [recoverHoldMs]; a brief false "plank"
 *    reading while getting up must not restart the grace period.
 * When a plank ends, [lastEndedLostAtMs] is when it was last held (the start of the lapse).
 */
class PlankTracker(
    private val startHoldMs: Long = 1_500,
    private val endGraceMs: Long = 2_000,
    private val recoverHoldMs: Long = 500,
) {
    enum class Event { STARTED, ENDED }

    var active = false
        private set
    private var plankSince: Long? = null
    private var lostSince: Long? = null
    private var recoverSince: Long? = null
    private var awaitingFirstPlank = false

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
                }
            }
        } else {
            recoverSince = null
            if (lostSince == null) lostSince = nowMs
        }
        val lost = lostSince
        if (lost != null && nowMs - lost >= endGraceMs) {
            active = false
            lastEndedLostAtMs = lost
            lostSince = null
            recoverSince = null
            plankSince = null
            return Event.ENDED
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

    /** 0..1: how far through the start hold we are (1 once a plank has started). */
    fun holdProgress(nowMs: Long): Float =
        if (active) 1f else plankSince?.let { ((nowMs - it) / startHoldMs.toFloat()).coerceIn(0f, 1f) } ?: 0f

    fun reset() {
        active = false
        plankSince = null
        lostSince = null
        recoverSince = null
        awaitingFirstPlank = false
    }
}

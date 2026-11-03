package com.coachchicken.core

/** A one-off sound to play at a point in the session. The quiet cluck loop is not a cue: it runs throughout. */
enum class Cue { LOUD_CLUCK, SQUAWK }

/**
 * Pure description of what happens when during a plank.
 * A loud cluck every [loudEveryMs], then a squawk (instead of a cluck) at [durationMs].
 */
class PlankTimeline(
    val durationMs: Long = DEFAULT_DURATION_MS,
    val loudEveryMs: Long = DEFAULT_LOUD_EVERY_MS,
) {
    init {
        require(durationMs > 0 && loudEveryMs > 0)
    }

    /** Cues that fall in the half-open interval (prevMs, nowMs]. */
    fun cuesBetween(prevMs: Long, nowMs: Long): List<Cue> {
        val cues = mutableListOf<Cue>()
        var t = (Math.floorDiv(prevMs, loudEveryMs) + 1) * loudEveryMs
        while (t <= nowMs && t < durationMs) {
            cues += Cue.LOUD_CLUCK
            t += loudEveryMs
        }
        if (prevMs < durationMs && nowMs >= durationMs) cues += Cue.SQUAWK
        return cues
    }

    fun isComplete(elapsedMs: Long): Boolean = elapsedMs >= durationMs

    fun remainingMs(elapsedMs: Long): Long = (durationMs - elapsedMs).coerceAtLeast(0)

    companion object {
        const val DEFAULT_DURATION_MS = 60_000L
        const val DEFAULT_LOUD_EVERY_MS = 10_000L
    }
}

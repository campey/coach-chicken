package com.coachchicken.core

import kotlin.math.exp

/**
 * The gold aura around a perfect plank. Two signals, both 0..1:
 *  - [value]: the glow itself. Builds within a few seconds of perfect form and fades quickly when it isn't.
 *  - [streak]: how long the form has been held. Builds slowly (so it is still growing at 60 seconds) and drains
 *    steadily; it drives the size of the glow and the number of sparkles.
 */
class AuraMeter(
    private val riseTauMs: Long = 2_000,
    private val fallTauMs: Long = 600,
    private val streakRiseTauMs: Long = 15_000,
    private val streakFallTauMs: Long = 4_000,
) {
    var value = 0f
        private set
    var streak = 0f
        private set
    private var lastMs: Long? = null

    fun update(nowMs: Long, perfect: Boolean): Float {
        val dt = (lastMs?.let { (nowMs - it).coerceAtLeast(0) } ?: 0L).toFloat()
        lastMs = nowMs
        val target = if (perfect) 1f else 0f
        value += (target - value) * (1f - exp(-dt / (if (target > value) riseTauMs else fallTauMs)))
        streak += (target - streak) * (1f - exp(-dt / (if (target > streak) streakRiseTauMs else streakFallTauMs)))
        return value
    }

    fun reset() {
        value = 0f
        streak = 0f
        lastMs = null
    }
}

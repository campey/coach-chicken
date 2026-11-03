package com.coachchicken.core

import kotlin.math.exp

/**
 * The gold "aura" around a perfect plank: builds up slowly while the plank is perfect and fades quickly when it
 * isn't, so it rewards holding good form rather than flickering with each frame. [value] is 0..1.
 */
class AuraMeter(
    private val riseTauMs: Long = 2_000,
    private val fallTauMs: Long = 600,
) {
    var value = 0f
        private set
    private var lastMs: Long? = null

    fun update(nowMs: Long, perfect: Boolean): Float {
        val dt = lastMs?.let { (nowMs - it).coerceAtLeast(0) } ?: 0L
        lastMs = nowMs
        val target = if (perfect) 1f else 0f
        val tau = if (target > value) riseTauMs else fallTauMs
        value += (target - value) * (1f - exp(-dt.toFloat() / tau))
        return value
    }

    fun reset() {
        value = 0f
        lastMs = null
    }
}

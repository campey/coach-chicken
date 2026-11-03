package com.coachchicken.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuraMeterTest {
    private fun AuraMeter.run(from: Long, to: Long, perfect: Boolean, step: Long = 100): Float {
        var t = from
        var v = value
        while (t <= to) { v = update(t, perfect); t += step }
        return v
    }

    @Test fun startsAtZero() = assertEquals(0f, AuraMeter().value)

    @Test fun buildsUpGraduallyWhilePerfect() {
        val a = AuraMeter()
        val early = a.run(0, 1_000, true)
        val mid = a.run(1_100, 3_000, true)
        val late = a.run(3_100, 10_000, true)
        assertTrue(early in 0.2f..0.5f, "early $early")
        assertTrue(mid > early && mid < late, "mid $mid late $late")
        assertTrue(late > 0.95f, "late $late")
    }

    @Test fun fadesQuicklyWhenNoLongerPerfect() {
        val a = AuraMeter()
        a.run(0, 10_000, true)
        val after = a.run(10_100, 12_000, false)
        assertTrue(after < 0.05f, "after $after")
    }

    @Test fun aBriefLapseOnlyDipsIt() {
        val a = AuraMeter()
        a.run(0, 10_000, true)
        val dip = a.run(10_100, 10_300, false)
        assertTrue(dip > 0.6f, "dip $dip")
    }

    @Test fun resetClears() {
        val a = AuraMeter()
        a.run(0, 5_000, true)
        a.reset()
        assertEquals(0f, a.value)
    }
}

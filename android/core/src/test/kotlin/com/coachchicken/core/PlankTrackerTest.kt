package com.coachchicken.core

import com.coachchicken.core.PlankTracker.Event
import com.coachchicken.core.PoseReading.NOT_PLANK
import com.coachchicken.core.PoseReading.NO_BODY
import com.coachchicken.core.PoseReading.PLANK
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlankTrackerTest {
    private fun PlankTracker.feed(vararg steps: Pair<Long, PoseReading>) = steps.map { (t, r) -> update(t, r) }

    @Test fun startsAfterTheHold() {
        val t = PlankTracker()
        assertEquals(listOf(null, null, Event.STARTED), t.feed(0L to PLANK, 1_000L to PLANK, 1_500L to PLANK))
        assertTrue(t.active)
    }

    @Test fun aBlipResetsTheHold() {
        val t = PlankTracker()
        t.feed(0L to PLANK, 1_000L to NOT_PLANK)
        assertNull(t.update(1_500L, PLANK))
        assertNull(t.update(2_900L, PLANK))
        assertEquals(Event.STARTED, t.update(3_000L, PLANK))
    }

    @Test fun briefDropoutDoesNotEnd() {
        val t = PlankTracker()
        t.feed(0L to PLANK, 1_500L to PLANK)
        assertNull(t.update(2_000L, NO_BODY))
        assertNull(t.update(3_000L, NOT_PLANK))
        assertNull(t.update(3_500L, PLANK)) // recovering...
        assertNull(t.update(4_100L, PLANK)) // ...back for 0.6s: the lapse is forgiven
        assertNull(t.update(5_000L, NOT_PLANK))
        assertNull(t.update(6_000L, NOT_PLANK))
        assertTrue(t.active)
    }

    @Test fun endsAfterTheGracePeriod() {
        val t = PlankTracker()
        t.feed(0L to PLANK, 1_500L to PLANK)
        assertNull(t.update(2_000L, NOT_PLANK))
        assertEquals(Event.ENDED, t.update(4_000L, NOT_PLANK))
        assertTrue(!t.active)
    }

    @Test fun needsAFreshHoldAfterEnding() {
        val t = PlankTracker()
        t.feed(0L to PLANK, 1_500L to PLANK, 2_000L to NOT_PLANK, 4_000L to NOT_PLANK)
        assertNull(t.update(5_000L, PLANK))
        assertEquals(Event.STARTED, t.update(6_500L, PLANK))
    }

    @Test fun holdProgress() {
        val t = PlankTracker()
        assertEquals(0f, t.holdProgress(0))
        t.update(1_000L, PLANK)
        assertEquals(0.5f, t.holdProgress(1_750L))
        t.update(2_500L, PLANK)
        assertEquals(1f, t.holdProgress(2_500L))
    }

    @Test fun aBriefFalsePlankWhileGettingUpDoesNotRestartTheGrace() {
        val t = PlankTracker()
        t.feed(0L to PLANK, 1_500L to PLANK)
        assertNull(t.update(10_000L, NOT_PLANK)) // starts getting up
        assertNull(t.update(11_000L, PLANK))     // 0.2s blip that looks like a plank
        assertNull(t.update(11_200L, NOT_PLANK))
        assertEquals(Event.ENDED, t.update(12_000L, NOT_PLANK)) // still ends 2s after the first lapse
    }

    @Test fun remembersWhenThePlankWasLastHeld() {
        val t = PlankTracker()
        assertNull(t.lastEndedLostAtMs)
        t.feed(0L to PLANK, 1_500L to PLANK, 10_000L to NOT_PLANK, 12_000L to NOT_PLANK)
        assertEquals(10_000L, t.lastEndedLostAtMs)
    }

    @Test fun lapseThatRecoversFullyStartsFreshNextTime() {
        val t = PlankTracker()
        t.feed(0L to PLANK, 1_500L to PLANK, 3_000L to NOT_PLANK, 3_500L to PLANK, 4_100L to PLANK)
        assertNull(t.update(6_000L, NOT_PLANK))  // new lapse starts at 6.0s, not 3.0s
        assertNull(t.update(7_900L, NOT_PLANK))
        assertEquals(Event.ENDED, t.update(8_000L, NOT_PLANK))
        assertEquals(6_000L, t.lastEndedLostAtMs)
    }
}

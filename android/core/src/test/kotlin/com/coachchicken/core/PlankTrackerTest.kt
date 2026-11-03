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
        assertNull(t.update(2_900L, NOT_PLANK))
        assertNull(t.update(3_000L, PLANK)) // recovering...
        assertNull(t.update(3_600L, PLANK)) // ...back for 0.6s: the lapse is forgiven
        assertNull(t.update(5_000L, NOT_PLANK))
        assertNull(t.update(5_900L, NOT_PLANK)) // a fresh lapse, only 0.9s old
        assertTrue(t.active)
    }

    @Test fun endsAfterTheGracePeriod() {
        val t = PlankTracker()
        t.feed(0L to PLANK, 1_500L to PLANK)
        assertNull(t.update(2_000L, NOT_PLANK))
        assertEquals(Event.WARNING, t.update(3_000L, NOT_PLANK))
        assertNull(t.update(4_900L, NOT_PLANK))
        assertEquals(Event.ENDED, t.update(5_000L, NOT_PLANK)) // 3s after the lapse began
        assertTrue(!t.active)
    }

    @Test fun needsAFreshHoldAfterEnding() {
        val t = PlankTracker()
        t.feed(0L to PLANK, 1_500L to PLANK, 2_000L to NOT_PLANK, 3_000L to NOT_PLANK, 5_000L to NOT_PLANK)
        assertNull(t.update(6_000L, PLANK))
        assertEquals(Event.STARTED, t.update(7_500L, PLANK))
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
        assertEquals(Event.WARNING, t.update(11_200L, NOT_PLANK))
        assertNull(t.update(12_900L, NOT_PLANK))
        assertEquals(Event.ENDED, t.update(13_000L, NOT_PLANK)) // still ends 3s after the first lapse
    }

    @Test fun remembersWhenThePlankWasLastHeld() {
        val t = PlankTracker()
        assertNull(t.lastEndedLostAtMs)
        t.feed(0L to PLANK, 1_500L to PLANK, 10_000L to NOT_PLANK, 13_000L to NOT_PLANK)
        assertEquals(10_000L, t.lastEndedLostAtMs)
    }

    @Test fun lapseThatRecoversFullyStartsFreshNextTime() {
        val t = PlankTracker()
        t.feed(0L to PLANK, 1_500L to PLANK, 3_000L to NOT_PLANK, 3_500L to PLANK, 4_100L to PLANK)
        assertNull(t.update(6_000L, NOT_PLANK))  // new lapse starts at 6.0s, not 3.0s
        assertEquals(Event.WARNING, t.update(7_000L, NOT_PLANK))
        assertNull(t.update(8_900L, NOT_PLANK))
        assertEquals(Event.ENDED, t.update(9_000L, NOT_PLANK))
        assertEquals(6_000L, t.lastEndedLostAtMs)
    }

    @Test fun buttonStartDoesNotEndWhileGettingIntoPosition() {
        val t = PlankTracker()
        t.startedExternally()
        assertTrue(t.active)
        assertNull(t.update(0L, NOT_PLANK))
        assertNull(t.update(10_000L, NOT_PLANK)) // still getting down, well past the grace period
        assertNull(t.update(30_000L, NO_BODY))
        assertTrue(t.active)
    }

    @Test fun buttonStartThenPlankSeenThenGettingUpEnds() {
        val t = PlankTracker()
        t.startedExternally()
        t.feed(5_000L to PLANK, 5_600L to PLANK) // seen for 0.6s: armed
        assertNull(t.update(20_000L, NOT_PLANK))
        assertEquals(Event.WARNING, t.update(22_000L, NOT_PLANK))
        assertEquals(Event.ENDED, t.update(23_000L, NOT_PLANK))
        assertEquals(20_000L, t.lastEndedLostAtMs)
    }

    @Test fun aSingleFalsePlankFrameDoesNotArmAButtonStart() {
        val t = PlankTracker()
        t.startedExternally()
        t.feed(1_000L to PLANK, 1_100L to NOT_PLANK) // blip
        assertNull(t.update(9_000L, NOT_PLANK))
        assertNull(t.update(20_000L, NOT_PLANK))
        assertTrue(t.active)
    }

    @Test fun resetDisarms() {
        val t = PlankTracker()
        t.startedExternally()
        t.reset()
        assertTrue(!t.active)
        assertNull(t.update(0L, NOT_PLANK))
    }

    // --- the warning ---------------------------------------------------------------------

    @Test fun warnsOnceAboutASecondIntoALapse() {
        val t = PlankTracker()
        t.feed(0L to PLANK, 1_500L to PLANK)
        assertNull(t.update(5_000L, NOT_PLANK))
        assertNull(t.update(5_900L, NOT_PLANK))
        assertEquals(Event.WARNING, t.update(6_000L, NOT_PLANK))
        assertTrue(t.isWarning)
        assertNull(t.update(7_000L, NOT_PLANK)) // not repeated
        assertNull(t.update(7_900L, NOT_PLANK))
    }

    @Test fun noWarningForABriefAdjustment() {
        val t = PlankTracker()
        t.feed(0L to PLANK, 1_500L to PLANK)
        assertNull(t.update(5_000L, NOT_PLANK))
        assertNull(t.update(5_700L, NOT_PLANK)) // wobbled for 0.7s...
        assertNull(t.update(5_800L, PLANK))
        assertNull(t.update(6_400L, PLANK))     // ...and is back
        assertTrue(!t.isWarning)
    }

    @Test fun warnsAgainOnTheNextLapse() {
        val t = PlankTracker()
        t.feed(0L to PLANK, 1_500L to PLANK, 5_000L to NOT_PLANK)
        assertEquals(Event.WARNING, t.update(6_000L, NOT_PLANK))
        t.feed(6_500L to PLANK, 7_100L to PLANK) // recovered
        assertTrue(!t.isWarning)
        t.update(10_000L, NOT_PLANK)
        assertEquals(Event.WARNING, t.update(11_000L, NOT_PLANK))
    }

    @Test fun noWarningWhileAButtonStartIsStillGettingIntoPosition() {
        val t = PlankTracker()
        t.startedExternally()
        assertNull(t.update(0L, NOT_PLANK))
        assertNull(t.update(5_000L, NOT_PLANK))
        assertTrue(!t.isWarning)
    }

    @Test fun lapseLengthIsReported() {
        val t = PlankTracker()
        t.feed(0L to PLANK, 1_500L to PLANK)
        assertEquals(0L, t.lapseMs(2_000L))
        t.update(5_000L, NOT_PLANK)
        assertEquals(1_200L, t.lapseMs(6_200L))
        assertEquals(3_000L, t.endGraceMs)
    }

    @Test fun recoveringAfterAWarningRaisesRecoveredOnce() {
        val t = PlankTracker()
        t.feed(0L to PLANK, 1_500L to PLANK, 5_000L to NOT_PLANK)
        assertEquals(Event.WARNING, t.update(6_000L, NOT_PLANK))
        assertNull(t.update(6_500L, PLANK))                       // recovering...
        assertEquals(Event.RECOVERED, t.update(7_100L, PLANK))    // ...back for 0.6s
        assertNull(t.update(7_600L, PLANK))
        assertTrue(!t.isWarning)
    }

    @Test fun noRecoveredWithoutAWarning() {
        val t = PlankTracker()
        t.feed(0L to PLANK, 1_500L to PLANK, 5_000L to NOT_PLANK, 5_300L to PLANK)
        assertNull(t.update(5_900L, PLANK))
    }
}

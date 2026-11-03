package com.coachchicken.core

import com.coachchicken.core.PlankTracker.Event
import com.coachchicken.core.PoseReading.NOT_PLANK
import com.coachchicken.core.PoseReading.PLANK
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Frames arrive ~every 100ms here (the real camera is faster, which only smooths things further). */
class PlankTrackerTest {
    private class Run(val tracker: PlankTracker = PlankTracker()) {
        val events = mutableListOf<Pair<Long, Event>>()

        fun feed(from: Long, to: Long, reading: PoseReading, step: Long = 100) {
            var t = from
            while (t <= to) {
                tracker.update(t, reading)?.let { events += t to it }
                t += step
            }
        }

        fun feedPattern(from: Long, to: Long, step: Long = 100, reading: (Int) -> PoseReading) {
            var t = from
            var i = 0
            while (t <= to) {
                tracker.update(t, reading(i++))?.let { events += t to it }
                t += step
            }
        }

        val kinds get() = events.map { it.second }
        fun at(e: Event) = events.first { it.second == e }.first
    }

    // --- starting ------------------------------------------------------------------------

    @Test fun startsAfterAHeldPlank() {
        val r = Run()
        r.feed(0, 3_000, PLANK)
        assertEquals(listOf(Event.STARTED), r.kinds)
        assertTrue(r.at(Event.STARTED) in 800..2_200, "started at ${r.at(Event.STARTED)}")
        assertTrue(r.tracker.active)
    }

    @Test fun aBlipDoesNotStart() {
        val r = Run()
        r.feed(0, 200, PLANK)
        r.feed(300, 5_000, NOT_PLANK)
        assertTrue(r.events.isEmpty())
        assertTrue(!r.tracker.active)
    }

    @Test fun halfBadFramesWhileGettingIntoPositionDoNotStart() {
        val r = Run()
        r.feedPattern(0, 10_000) { if (it % 2 == 0) PLANK else NOT_PLANK }
        assertTrue(r.events.isEmpty())
    }

    // --- the low-pass filter: scattered bad frames inside a plank --------------------------

    @Test fun aThirdOfFramesBeingBadNeverEndsAPlank() {
        // the real-phone failure: a good plank with scattered NOT_PLANK frames kept "restarting" a lapse
        val r = Run()
        r.feed(0, 1_500, PLANK)
        r.feedPattern(1_600, 60_000) { if (it % 3 == 0) NOT_PLANK else PLANK }
        assertEquals(listOf(Event.STARTED), r.kinds)
        assertTrue(r.tracker.active)
    }

    @Test fun clustersOfBadFramesUnderAHalfSecondAreIgnored() {
        val r = Run()
        r.feed(0, 1_500, PLANK)
        for (k in 0 until 20) { // every 2s: 0.4s of bad frames
            val t0 = 2_000L + k * 2_000
            r.feed(t0, t0 + 400, NOT_PLANK)
            r.feed(t0 + 500, t0 + 1_900, PLANK)
        }
        assertEquals(listOf(Event.STARTED), r.kinds)
    }

    // --- a real lapse -------------------------------------------------------------------

    @Test fun aRealLapseWarnsThenEndsThreeSecondsAfterTheLastGoodFrame() {
        val r = Run()
        r.feed(0, 5_000, PLANK)
        r.feed(5_100, 12_000, NOT_PLANK)
        assertEquals(listOf(Event.STARTED, Event.WARNING, Event.ENDED), r.kinds)
        assertEquals(6_000L, r.at(Event.WARNING))  // 1s after the last good frame
        assertEquals(8_000L, r.at(Event.ENDED))    // 3s after it
        assertEquals(5_000L, r.tracker.lastEndedLostAtMs) // back-dated to when the plank was last held
        assertTrue(!r.tracker.active)
    }

    @Test fun aBriefDropoutRaisesNothing() {
        val r = Run()
        r.feed(0, 5_000, PLANK)
        r.feed(5_100, 5_600, NOT_PLANK) // 0.6s
        r.feed(5_700, 15_000, PLANK)
        assertEquals(listOf(Event.STARTED), r.kinds)
        assertTrue(r.tracker.active)
    }

    @Test fun gettingBackWithinTheGraceSilencesTheWarning() {
        val r = Run()
        r.feed(0, 5_000, PLANK)
        r.feed(5_100, 6_500, NOT_PLANK) // lapse, warned at 6.0s
        r.feed(6_600, 12_000, PLANK)
        assertEquals(listOf(Event.STARTED, Event.WARNING, Event.RECOVERED), r.kinds)
        assertTrue(r.tracker.active)
        assertTrue(!r.tracker.isWarning)
    }

    @Test fun warnsAgainOnTheNextLapse() {
        val r = Run()
        r.feed(0, 5_000, PLANK)
        r.feed(5_100, 6_500, NOT_PLANK)
        r.feed(6_600, 12_000, PLANK)
        r.feed(12_100, 13_500, NOT_PLANK)
        assertEquals(listOf(Event.STARTED, Event.WARNING, Event.RECOVERED, Event.WARNING), r.kinds)
    }

    @Test fun needsAFreshStartAfterEnding() {
        val r = Run()
        r.feed(0, 5_000, PLANK)
        r.feed(5_100, 9_000, NOT_PLANK) // ended
        r.feed(9_100, 9_500, PLANK)
        assertTrue(!r.tracker.active)
        r.feed(9_600, 12_000, PLANK)
        assertEquals(listOf(Event.STARTED, Event.WARNING, Event.ENDED, Event.STARTED), r.kinds)
    }

    @Test fun lapseIsReportedWhileItHappens() {
        val r = Run()
        r.feed(0, 5_000, PLANK)
        r.feed(5_100, 6_200, NOT_PLANK)
        assertEquals(1_200L, r.tracker.lapseMs(6_200))
        assertTrue(r.tracker.isWarning)
        assertEquals(3_000L, r.tracker.endGraceMs)
    }

    // --- started from a button ----------------------------------------------------------

    @Test fun buttonStartDoesNotEndWhileGettingIntoPosition() {
        val r = Run()
        r.tracker.startedExternally()
        assertTrue(r.tracker.active)
        r.feed(0, 30_000, NOT_PLANK) // far past the grace period, still getting down
        assertTrue(r.events.isEmpty())
        assertTrue(r.tracker.active)
    }

    @Test fun buttonStartThenPlankSeenThenGettingUpEnds() {
        val r = Run()
        r.tracker.startedExternally()
        r.feed(0, 5_000, NOT_PLANK)
        r.feed(5_100, 20_000, PLANK)
        r.feed(20_100, 30_000, NOT_PLANK)
        assertEquals(listOf(Event.WARNING, Event.ENDED), r.kinds)
        assertEquals(20_000L, r.tracker.lastEndedLostAtMs)
    }

    @Test fun aSingleFalsePlankFrameDoesNotArmAButtonStart() {
        val r = Run()
        r.tracker.startedExternally()
        r.feed(1_000, 1_100, PLANK) // blip
        r.feed(1_200, 30_000, NOT_PLANK)
        assertTrue(r.events.isEmpty())
        assertTrue(r.tracker.active)
    }

    @Test fun resetDisarms() {
        val t = PlankTracker()
        t.startedExternally()
        t.reset()
        assertTrue(!t.active)
        assertNull(t.update(0L, NOT_PLANK))
    }

    // --- signals for the UI ----------------------------------------------------------------

    @Test fun holdProgressRisesToOneOnceStarted() {
        val r = Run()
        assertEquals(0f, r.tracker.holdProgress(0))
        var last = 0f
        var t = 0L
        while (!r.tracker.active) {
            r.tracker.update(t, PLANK)
            val p = r.tracker.holdProgress(t)
            assertTrue(p >= last, "progress went backwards at $t: $last -> $p")
            last = p
            t += 100
        }
        assertEquals(1f, r.tracker.holdProgress(t))
    }

    @Test fun smoothedSignalTracksPlankiness() {
        val r = Run()
        r.feed(0, 3_000, PLANK)
        assertTrue(r.tracker.smoothed > 0.95f)
        r.feed(3_100, 6_000, NOT_PLANK)
        assertTrue(r.tracker.smoothed < 0.1f)
    }
}

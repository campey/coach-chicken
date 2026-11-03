package com.coachchicken.core

import com.coachchicken.core.PlankTracker.Event
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A real landscape run on a phone (build with the low-pass filter, tolerance, grades and green zone):
 * got into position, a 62 second plank graded PERFECT on every sampled row, then got up.
 * Replays that run's grade run-lengths (sampled at the log's ~4 Hz) through the tracker and aura.
 * Real timings from the log: STARTED 9.9s, WARNING 72.7s, ENDED 74.7s, plank back-dated to end at 71.7s (61.8s held).
 */
class PerfectRunReplayTest {
    private val stepMs = 265L

    private fun gradeAt(t: Long): PlankGrade = when {
        t < 8_100 -> PlankGrade.NOT_PLANK           // getting into position (portrait, then landscape)
        t < 9_000 -> PlankGrade.OK                  // 8.2s: in position, within tolerance
        t < 9_300 -> PlankGrade.PERFECT
        t < 9_450 -> PlankGrade.OK                  // one blip at 9.26s
        t < 70_900 -> PlankGrade.PERFECT            // the plank
        t < 71_700 -> PlankGrade.OK                 // hips start rising: the hip leaves the green zone
        else -> PlankGrade.NOT_PLANK                // up
    }

    private val events = mutableListOf<Pair<Long, Event>>()
    private val tracker = PlankTracker()
    private val aura = AuraMeter()
    private val auraAt = mutableMapOf<Long, Float>()

    init {
        var t = 0L
        while (t <= 76_000) {
            val grade = gradeAt(t)
            tracker.update(t, grade.reading)?.let { events += t to it }
            auraAt[t] = aura.update(t, grade == PlankGrade.PERFECT)
            t += stepMs
        }
    }

    private fun at(e: Event) = events.first { it.second == e }.first

    @Test fun exactlyThreeEventsStartWarningEnd() {
        assertEquals(listOf(Event.STARTED, Event.WARNING, Event.ENDED), events.map { it.second }, "events $events")
    }

    @Test fun startsAboutASecondAfterGettingIntoPosition() = assertTrue(at(Event.STARTED) in 9_000..10_800, "started ${at(Event.STARTED)}")

    @Test fun nothingFiresDuringTheWholePlank() {
        assertTrue(events.none { it.t in at(Event.STARTED) + 1..70_900 }, "events $events")
    }

    @Test fun theWarningAndEndComeAfterTheLapseNotBefore() {
        assertTrue(at(Event.WARNING) in 72_000..73_500, "warning ${at(Event.WARNING)}")
        assertTrue(at(Event.ENDED) in 74_000..75_500, "ended ${at(Event.ENDED)}")
        assertTrue(at(Event.ENDED) - at(Event.WARNING) in 1_500..2_500)
    }

    @Test fun theEndIsBackDatedToWhenThePlankWasLastHeldAndEarnsTheEgg() {
        val lostAt = tracker.lastEndedLostAtMs!!
        assertTrue(lostAt in 71_000..72_200, "lapse began $lostAt")
        val held = lostAt - at(Event.STARTED)
        assertTrue(held in 60_000..63_000, "held ${held}ms")
        assertTrue(PlankTimeline().isComplete(held), "a ${held}ms plank reaches the 60s goal")
    }

    @Test fun theAuraBuildsToFullAndFadesAfterwards() {
        val early = auraAt.filterKeys { it in 10_000..11_000 }.values.max()
        assertTrue(early in 0.3f..0.7f, "aura at ~10.5s $early")
        assertTrue(auraAt.filterKeys { it in 19_000..21_000 }.values.min() > 0.95f)
        assertTrue(auraAt.filterKeys { it in 40_000..60_000 }.values.min() > 0.99f)
        assertTrue(auraAt.filterKeys { it in 74_000..76_000 }.values.max() < 0.05f)
    }

    private val Pair<Long, Event>.t get() = first

}

/**
 * A second real landscape run: a 63s plank, PERFECT on ~94% of the sampled rows. The rest were isolated OK frames
 * (the elbow landmark wobbling) scattered through the plank, then the hips lifting at the end. The log's aura dipped
 * by only ~6% at each blip, which is one ~33ms camera frame, so the replay runs at camera frame rate with each blip
 * a single frame. The aura must ride through them and nothing may fire until the real lapse.
 */
class PerfectRunWithBlipsReplayTest {
    private val stepMs = 33L
    private val blips = listOf(39_900L, 43_700L, 49_200L, 51_350L, 56_500L, 57_050L, 64_050L, 71_400L, 71_650L, 72_650L)

    private fun gradeAt(t: Long): PlankGrade = when {
        t < 8_100 -> PlankGrade.NOT_PLANK
        t < 9_000 -> PlankGrade.OK
        t < 73_100 -> if (blips.any { it / stepMs == t / stepMs }) PlankGrade.OK else PlankGrade.PERFECT
        t < 73_900 -> PlankGrade.OK          // the hips lift: out of the green zone, still planking
        else -> PlankGrade.NOT_PLANK
    }

    private val events = mutableListOf<Pair<Long, PlankTracker.Event>>()
    private val tracker = PlankTracker()
    private val aura = AuraMeter()
    private var minAuraAfterBuildUp = 1f

    init {
        var t = 0L
        while (t <= 77_500) {
            val g = gradeAt(t)
            tracker.update(t, g.reading)?.let { events += t to it }
            val a = aura.update(t, g == PlankGrade.PERFECT)
            if (t in 20_000..71_000) minAuraAfterBuildUp = minOf(minAuraAfterBuildUp, a)
            t += stepMs
        }
    }

    @Test fun scatteredOkBlipsNeverRaiseAnEvent() {
        assertEquals(listOf(PlankTracker.Event.STARTED, PlankTracker.Event.WARNING, PlankTracker.Event.ENDED), events.map { it.second }, "events $events")
    }

    @Test fun theAuraRidesThroughTheBlips() {
        assertTrue(minAuraAfterBuildUp > 0.85f, "aura dipped to $minAuraAfterBuildUp")
    }

    @Test fun theEndIsBackDatedToTheLastHeldFrameAndEarnsTheEgg() {
        val lostAt = tracker.lastEndedLostAtMs!!
        val started = events.first { it.second == PlankTracker.Event.STARTED }.first
        assertTrue(lostAt in 72_800..74_200, "lapse began $lostAt")
        assertTrue(PlankTimeline().isComplete(lostAt - started), "held ${lostAt - started}ms")
    }
}

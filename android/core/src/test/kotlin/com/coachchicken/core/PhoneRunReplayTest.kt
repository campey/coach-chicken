package com.coachchicken.core

import com.coachchicken.core.PlankTracker.Event
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Replays the per-frame verdicts of a real phone run through the tracker. The original build (a "frames in a row"
 * debounce) split the first ~30s plank in two with a false end at 21.4s, and gave false warnings and a false end at
 * 63.0s into the second plank, while the person was visibly still planking. These verdicts are even from the old,
 * stricter detector, so this is a worst case for the filter.
 */
class PhoneRunReplayTest {
    private data class Out(val t: Long, val event: Event)

    private val events: List<Out>
    private val lapseStarts = mutableListOf<Long>()

    init {
        val lines = javaClass.getResourceAsStream("/phone_run1_verdicts.csv")!!.bufferedReader().readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }.drop(1)
        var tracker = PlankTracker()
        val out = mutableListOf<Out>()
        for (line in lines) {
            val (t, c) = line.split(',')
            val reading = when (c) { "P" -> PoseReading.PLANK; "N" -> PoseReading.NOT_PLANK; else -> PoseReading.NO_BODY }
            val e = tracker.update(t.toLong(), reading)
            if (e != null) out += Out(t.toLong(), e)
            if (e == Event.ENDED) { lapseStarts += tracker.lastEndedLostAtMs!!; tracker = PlankTracker() }
        }
        events = out
    }

    private fun of(e: Event) = events.filter { it.event == e }.map { it.t }

    @Test fun theFirstPlankIsOneUnbrokenPlankOfAboutThirtySeconds() {
        val started = of(Event.STARTED).first()
        assertTrue(started in 4_000..6_000, "started at $started")
        // no warning and no end anywhere while the plank is actually being held (the old build ended it at 21.4s)
        assertTrue(events.none { it.event != Event.STARTED && it.t in started..33_000 }, "events $events")
        val held = lapseStarts.first() - started
        assertTrue(held in 26_000..30_000, "held ${held}ms")
    }

    @Test fun theFirstPlankEndsAfterTheRealGetUpBackDatedToIt() {
        assertTrue(lapseStarts.first() in 32_000..33_500, "lapse began ${lapseStarts.first()}")
        assertTrue(of(Event.ENDED).first() in 35_000..36_500, "ended ${of(Event.ENDED).first()}")
    }

    @Test fun noFalseEndsDuringTheSecondPlank() {
        val second = of(Event.STARTED)[1]
        assertTrue(second in 38_000..46_000, "second plank started at $second")
        assertEquals(2, of(Event.ENDED).size, "ends: ${of(Event.ENDED)}") // exactly the two real get-ups
        assertTrue(lapseStarts[1] in 65_000..67_000, "second plank ended (back-dated) at ${lapseStarts[1]}")
        assertTrue(lapseStarts[1] - second in 21_000..25_000, "held ${lapseStarts[1] - second}ms")
    }

    @Test fun noWarningsWhileTheSecondPlankIsHeldExceptABriefStrictDetectorLapse() {
        val second = of(Event.STARTED)[1]
        val warnings = of(Event.WARNING).filter { it in second..66_000 }
        // the old strict detector really did lose the plank for a moment at ~62.9s; the warning is silenced by
        // RECOVERED half a second later. Everything else in the plank is quiet.
        assertTrue(warnings.size <= 1, "warnings $warnings")
        if (warnings.isNotEmpty()) assertTrue(of(Event.RECOVERED).any { it in warnings.first()..(warnings.first() + 1_500) })
    }
}

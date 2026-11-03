package com.coachchicken.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Replays real MediaPipe landmarks recorded from a side-on plank tutorial video:
 * plank from the start, a zoom shot where the ankle leaves the frame (~15.8-16.8s),
 * then he lifts his hips from ~27s and stands by ~29s.
 */
class RecordedVideoReplayTest {
    private val aspect = 1920f / 1080f
    private val joints = listOf(11, 12, 13, 14, 23, 24, 25, 26, 27, 28)

    private data class Frame(val tMs: Long, val landmarks: List<Landmark>?)

    private fun frames(): List<Frame> {
        val text = javaClass.getResourceAsStream("/plank_video_landmarks.csv")!!.bufferedReader().readText()
        return text.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.drop(1).map { line ->
            val v = line.split(',')
            val l = MutableList(PoseIdx.COUNT) { Landmark(0f, 0f, 0f) }
            joints.forEachIndexed { k, idx -> l[idx] = Landmark(v[1 + 3 * k].toFloat(), v[2 + 3 * k].toFloat(), v[3 + 3 * k].toFloat()) }
            Frame(v[0].toLong(), l)
        }.toList()
    }

    private val warnings = mutableListOf<Long>()

    private fun replay(): Triple<Long?, Long?, Long?> {
        val tracker = PlankTracker()
        var started: Long? = null
        var ended: Long? = null
        warnings.clear()
        for (f in frames()) {
            val reading = readPlank(f.landmarks!!, aspect)
            when (tracker.update(f.tMs, reading)) {
                PlankTracker.Event.STARTED -> started = started ?: f.tMs
                PlankTracker.Event.ENDED -> ended = ended ?: f.tMs
                PlankTracker.Event.WARNING -> warnings += f.tMs
                PlankTracker.Event.RECOVERED -> Unit
                null -> Unit
            }
        }
        return Triple(started, ended, tracker.lastEndedLostAtMs)
    }

    @Test fun readsThePlankFromTheFirstFrames() {
        val early = frames().filter { it.tMs < 10_000 }
        assertTrue(early.all { readPlank(it.landmarks!!, aspect) == PoseReading.PLANK })
    }

    @Test fun startsAfterTheHold() {
        val (started, _, _) = replay()
        assertNotNull(started)
        assertTrue(started in 1_000..2_200, "started at $started")
    }

    @Test fun theZoomShotStillReadsAsAPlankWithoutAnkles() {
        // ~12-17s: zoomed in, legs out of frame. Previously a second of NO_BODY; now measured from the torso.
        val zoom = frames().filter { it.tMs in 13_000..16_500 }
        assertTrue(zoom.isNotEmpty())
        assertTrue(zoom.any { plankMetrics(it.landmarks!!, aspect)!!.lower != 'A' }, "expected some frames without ankles")
        assertTrue(zoom.all { readPlank(it.landmarks!!, aspect) == PoseReading.PLANK })
    }

    @Test fun theZoomShotDoesNotEndThePlank() {
        // ankle out of frame at ~15.8-16.8s reads as NO_BODY; the grace period must absorb it
        val (_, ended, _) = replay()
        assertTrue(ended == null || ended > 26_000, "ended at $ended")
    }

    @Test fun endsSoonAfterGettingUp() {
        val (_, ended, lostAt) = replay()
        assertNotNull(ended)
        assertNotNull(lostAt)
        // with the generous tolerance the slow hip-lift (from ~27s) is accepted until the body visibly tilts (~28.4s)
        assertTrue(lostAt in 27_500..29_000, "lapse began at $lostAt")
        assertTrue(ended in 30_500..32_500, "ended at $ended")
        assertEquals(true, ended - lostAt >= 3_000)
    }

    @Test fun warnsOnceWhenGettingUpAndNeverDuringThePlank() {
        replay()
        assertEquals(1, warnings.size, "warnings at $warnings")
        assertTrue(warnings.single() in 28_500..30_500, "warned at ${warnings.single()}")
    }
}

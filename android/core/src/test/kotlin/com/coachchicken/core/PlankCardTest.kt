package com.coachchicken.core

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlankCardTest {
    private fun rec(frames: List<RecFrame>, durationMs: Long = 20_000) =
        PlankRecording(Instant.parse("2026-11-04T05:19:13Z"), 1.3333f, true, 60_000, durationMs, false, frames)

    private fun frame(tMs: Long, grade: Int = 3, hip: Float? = 175f, joints: FloatArray? = null) = RecFrame(tMs, grade, 0.5f, hip, joints)

    @Test fun codecRoundTrips() {
        val j = FloatArray(PlankRecordingCodec.JOINT_VALUES) { it / 30f }.also { it[3] = Float.NaN }
        val r = rec(listOf(frame(-500, 1, null), frame(0, 3, 172.5f, j)))
        val back = assertNotNull(PlankRecordingCodec.decode(PlankRecordingCodec.encode(r)))
        assertEquals(r.startedAt, back.startedAt)
        assertEquals(true, back.mirror)
        assertEquals(2, back.frames.size)
        assertEquals(-500, back.frames[0].tMs)
        assertNull(back.frames[0].hipDeg)
        assertNull(back.frames[0].joints)
        assertEquals(172.5f, back.frames[1].hipDeg)
        assertTrue(back.frames[1].joints!![3].isNaN())
        assertEquals(0.2f, back.frames[1].joints!![6], 0.001f)
    }

    @Test fun oldRecordingsWithoutTheHeadPointsStillLoad() {
        val old = "#plank v1 startedAt=2026-11-04T05:19:13Z aspect=1.3333 mirror=true goalMs=60000 durationMs=5000 completed=true\n" +
            "0,3,0.50,172.0," + (0 until 26).joinToString(",") { "0.5" } + "\n"
        val back = assertNotNull(PlankRecordingCodec.decode(old))
        val j = back.frames[0].joints!!
        assertEquals(PlankRecordingCodec.JOINT_VALUES, j.size)
        assertEquals(0.5f, j[25], 0.001f)
        assertTrue(j[26].isNaN()) // the eyes, ears and mouth were not recorded
    }

    @Test fun decodeRejectsGarbage() {
        assertNull(PlankRecordingCodec.decode(""))
        assertNull(PlankRecordingCodec.decode("hello"))
    }

    @Test fun recorderKeepsTheLeadInOnly() {
        val rec = PlankRecorder(minIntervalMs = 250, leadInMs = 10_000)
        for (ms in 0..30_000L step 100) rec.add(ms, 3, 0f, 170f, null)
        val frames = rec.framesFor(plankStartMs = 20_000, untilMs = 25_000)
        assertTrue(frames.first().tMs in -10_000..-9_500)
        assertTrue(frames.last().tMs in 4_500..5_000)
        assertTrue(frames.zipWithNext().all { (a, b) -> b.tMs - a.tMs >= 250 })
    }

    @Test fun smoothingCalmsNoise() {
        val frames = (0..80).map { frame(it * 250L, hip = if (it % 2 == 0) 170f else 180f) }
        val track = CardTrack(rec(frames, 20_000))
        val mid = track.hip[40]
        assertEquals(175.0, mid, 2.0)
    }

    @Test fun missingHipStaysMissing() {
        val frames = (0..20).map { frame(it * 250L, hip = if (it == 10) null else 175f) }
        val track = CardTrack(rec(frames, 5_000))
        assertTrue(track.hip[10].isNaN())
        assertTrue(!track.hip[9].isNaN())
    }

    @Test fun timerStopsBeforeTheBodyDiverges() {
        // 12 s of a steady plank, then the hip collapses over the last 2 s
        val frames = (0..56).map { i ->
            val t = i * 250L
            val hip = if (t < 12_000) 175f else 175f - (t - 12_000) / 10f
            frame(t, grade = if (hip > 160) 3 else 1, hip = hip)
        }
        val track = CardTrack(rec(frames, 14_000))
        assertTrue(track.stopS in 11.0..13.6, "stop was ${track.stopS}")
    }

    @Test fun stopIsTheLastPerfectFrameNotTheLastOkOne() {
        val frames = (0..60).map { i -> frame(i * 250L, grade = if (i < 40) 3 else 2, hip = 170f) } // perfect to 9.75 s, then ok
        val track = CardTrack(rec(frames, 15_000))
        assertEquals(9.75, track.stopS, 0.01)
    }

    @Test fun targetSurvivesTheCodec() {
        val r = PlankRecording(Instant.parse("2026-11-04T05:19:13Z"), 1.5f, false, 60_000, 1000, true, emptyList(), PlankTarget(0.1f, 0.2f, 0.9f, 0.5f, 0.07f, 'L'))
        val back = assertNotNull(PlankRecordingCodec.decode(PlankRecordingCodec.encode(r)))
        assertEquals('L', back.target!!.side)
        assertEquals(0.9f, back.target!!.bx, 0.001f)
    }

    @Test fun statsCoverThePlankOnly() {
        val frames = (-20..40).map { i -> frame(i * 250L, grade = if (i < 0) 1 else 3, hip = 176f) }
        val s = CardTrack(rec(frames, 10_000)).stats
        assertEquals(1f, s.perfectFraction, 0.01f)
        assertEquals(176f, s.avgHipDeg!!, 0.5f)
        assertTrue(s.bestStreakMs in 8_000..10_000)
    }
}

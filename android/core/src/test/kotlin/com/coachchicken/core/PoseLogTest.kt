package com.coachchicken.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PoseLogTest {
    private fun pose(): List<Landmark> {
        val l = MutableList(PoseIdx.COUNT) { Landmark(0f, 0f, 0f) }
        l[11] = Landmark(0.25f, 0.50f); l[13] = Landmark(0.25f, 0.62f)
        l[23] = Landmark(0.50f, 0.54f); l[27] = Landmark(0.80f, 0.58f)
        return l
    }

    private fun rows(log: PoseLog) = log.render().lines().filter { it.isNotBlank() && !it.startsWith("#") }.drop(1) // drop header

    @Test fun rowHasAColumnPerHeaderField() {
        val log = PoseLog()
        log.addFrame(0, "IDLE", pose(), 0.75f, PoseReading.PLANK)
        val header = log.render().lines().first { it.startsWith("t_ms,") }
        assertEquals(header.split(',').size, rows(log).single().split(',').size)
    }

    @Test fun rowsCarryTheVerdictAndMetrics() {
        val log = PoseLog()
        log.addFrame(1234, "RUNNING", pose(), 1f, PoseReading.PLANK)
        val row = rows(log).single()
        assertTrue(row.startsWith("1234,RUNNING,PLANK,1.000,L,"), row)
        assertTrue("0.250,0.500" in row, row) // left shoulder x,y
    }

    @Test fun framesAreThrottled() {
        val log = PoseLog(minIntervalMs = 250)
        listOf(0L, 100L, 249L, 250L, 400L, 520L).forEach { log.addFrame(it, "IDLE", pose(), 1f, PoseReading.NOT_PLANK) }
        assertEquals(listOf("0", "250", "520"), rows(log).map { it.substringBefore(',') })
    }

    @Test fun noPersonStillLogsARow() {
        val log = PoseLog()
        log.addFrame(0, "IDLE", null, 0.75f, PoseReading.NO_BODY)
        val row = rows(log).single()
        val header = log.render().lines().first { it.startsWith("t_ms,") }
        assertEquals(header.split(',').size, row.split(',').size)
        assertTrue(row.startsWith("0,IDLE,NO_BODY,0.750,"), row)
    }

    @Test fun eventsAreInterleavedAsComments() {
        val log = PoseLog()
        log.addFrame(0, "IDLE", pose(), 1f, PoseReading.PLANK)
        log.addEvent(1500, "STARTED")
        assertTrue("# 1500 STARTED" in log.render())
    }

    @Test fun keepsOnlyTheMostRecentLines() {
        val log = PoseLog(capacity = 3, minIntervalMs = 0)
        (0L..9L).forEach { log.addFrame(it, "IDLE", pose(), 1f, PoseReading.PLANK) }
        assertEquals(listOf("7", "8", "9"), rows(log).map { it.substringBefore(',') })
    }

    @Test fun clearStartsOverWithNewMeta() {
        val log = PoseLog()
        log.addFrame(0, "IDLE", pose(), 1f, PoseReading.PLANK)
        log.clear("camera=front")
        assertEquals(0, log.size)
        assertTrue("# camera=front" in log.render())
    }
}

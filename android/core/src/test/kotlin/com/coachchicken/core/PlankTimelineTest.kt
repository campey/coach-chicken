package com.coachchicken.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlankTimelineTest {
    private val t = PlankTimeline()

    @Test fun noCuesAtStart() = assertEquals(emptyList(), t.cuesBetween(0, 0))

    @Test fun noCueBeforeFirstTen() = assertEquals(emptyList(), t.cuesBetween(0, 9_999))

    @Test fun loudClucksEveryTenSeconds() {
        assertEquals(listOf(Cue.LOUD_CLUCK), t.cuesBetween(9_900, 10_000))
        assertEquals(listOf(Cue.LOUD_CLUCK), t.cuesBetween(49_900, 50_100))
    }

    @Test fun cueBoundaryIsHalfOpen() {
        // exactly on a boundary fires once, in the tick that reaches it
        assertEquals(listOf(Cue.LOUD_CLUCK), t.cuesBetween(9_000, 10_000))
        assertEquals(emptyList(), t.cuesBetween(10_000, 10_500))
    }

    @Test fun squawkReplacesCluckAtSixty() {
        assertEquals(listOf(Cue.SQUAWK), t.cuesBetween(59_900, 60_000))
    }

    @Test fun squawkFiresOnlyOnce() {
        assertEquals(emptyList(), t.cuesBetween(60_000, 61_000))
    }

    @Test fun fiveClucksThenSquawkAcrossWholeSession() {
        val all = t.cuesBetween(0, 60_000)
        assertEquals(List(5) { Cue.LOUD_CLUCK } + Cue.SQUAWK, all)
    }

    @Test fun lateTickStillCatchesSkippedCues() {
        assertEquals(List(2) { Cue.LOUD_CLUCK }, t.cuesBetween(8_000, 25_000))
    }

    @Test fun noDuplicatesWhenTickedInSmallSteps() {
        val all = (0 until 650).flatMap { t.cuesBetween(it * 100L, (it + 1) * 100L) }
        assertEquals(List(5) { Cue.LOUD_CLUCK } + Cue.SQUAWK, all)
    }

    @Test fun completionAndRemaining() {
        assertTrue(!t.isComplete(59_999))
        assertTrue(t.isComplete(60_000))
        assertEquals(1L, t.remainingMs(59_999))
        assertEquals(0L, t.remainingMs(70_000))
    }

    @Test fun nothingPlaysAfterTheGoalSoThePlankCanKeepGoing() {
        assertEquals(emptyList(), t.cuesBetween(61_000, 125_000))
        assertEquals(emptyList(), t.cuesBetween(60_000, 90_000))
    }
}

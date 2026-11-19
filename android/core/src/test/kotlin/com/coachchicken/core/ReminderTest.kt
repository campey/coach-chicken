package com.coachchicken.core

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReminderTest {
    private val zone = ZoneId.of("Asia/Kuala_Lumpur")
    private fun at(h: Int, m: Int = 0, day: Int = 10) = ZonedDateTime.of(2026, 11, day, h, m, 0, 0, zone)

    @Test fun beforeSixTheMorningIsNext() = assertEquals(ReminderSlot.MORNING to at(6), nextSlot(at(3)))

    @Test fun afterTheMorningTheMiddayIsNext() = assertEquals(ReminderSlot.MIDDAY to at(13), nextSlot(at(6, 0).plusSeconds(30)))

    @Test fun afterMiddayTheStreakSaverIsNext() = assertEquals(ReminderSlot.STREAK_SAVER to at(21), nextSlot(at(15)))

    @Test fun afterTheStreakSaverTomorrowMorning() = assertEquals(ReminderSlot.MORNING to at(6, day = 11), nextSlot(at(21, 0).plusSeconds(30)))

    @Test fun nothingIsSentOnceTodayIsDone() {
        for (slot in ReminderSlot.entries) assertFalse(shouldSend(slot, Streak(5, true)))
    }

    @Test fun theStreakSaverNeedsAStreak() {
        assertFalse(shouldSend(ReminderSlot.STREAK_SAVER, Streak(0, false)))
        assertTrue(shouldSend(ReminderSlot.STREAK_SAVER, Streak(3, false)))
        assertTrue(shouldSend(ReminderSlot.MORNING, Streak(0, false)))
        assertTrue(shouldSend(ReminderSlot.MIDDAY, Streak(0, false)))
    }

    @Test fun theStreakSaverNamesTheStreak() {
        val t = ReminderCopy.pick(ReminderSlot.STREAK_SAVER, Streak(12, false), LocalDate.of(2026, 11, 10))
        assertTrue("12" in t.body && "%" !in t.body, t.body)
    }

    @Test fun aLineNeverRepeatsOnConsecutiveDays() {
        for (slot in ReminderSlot.entries) for (d in 0 until 30) {
            val a = ReminderCopy.pick(slot, Streak(5, false), LocalDate.of(2026, 11, 1).plusDays(d.toLong()))
            val b = ReminderCopy.pick(slot, Streak(5, false), LocalDate.of(2026, 11, 1).plusDays(d + 1L))
            assertTrue(a.title != b.title, "$slot repeated '${a.title}' on consecutive days")
        }
    }
}

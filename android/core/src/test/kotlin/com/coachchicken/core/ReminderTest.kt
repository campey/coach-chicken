package com.coachchicken.core

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderTest {
    private val zone = ZoneId.of("Asia/Kuala_Lumpur")
    private fun at(h: Int, m: Int) = ZonedDateTime.of(2026, 11, 10, h, m, 0, 0, zone)

    @Test fun laterTodayWhenTheTimeIsAhead() = assertEquals(at(18, 0), nextFireAt(at(9, 30), 18, 0))

    @Test fun tomorrowWhenTheTimeHasPassed() = assertEquals(at(18, 0).plusDays(1), nextFireAt(at(18, 0), 18, 0))

    @Test fun tomorrowJustAfterFiring() = assertEquals(at(18, 0).plusDays(1), nextFireAt(at(18, 0).plusSeconds(20), 18, 0))

    @Test fun theStreakNumberIsInTheTextWhenThereIsOne() {
        val t = ReminderCopy.pick(Streak(12, false), LocalDate.of(2026, 11, 10))
        assertTrue("12" in t.body, t.body)
    }

    @Test fun noStreakCopyDoesNotMentionDays() {
        val t = ReminderCopy.pick(Streak(0, false), LocalDate.of(2026, 11, 10))
        assertTrue("days" !in t.body && "%" !in t.body, t.body)
    }

    @Test fun theLineChangesFromDayToDay() {
        val lines = (0 until 6).map { ReminderCopy.pick(Streak(5, false), LocalDate.of(2026, 11, 10).plusDays(it.toLong())).title }.toSet()
        assertTrue(lines.size > 3, "only ${lines.size} different titles in six days")
    }
}

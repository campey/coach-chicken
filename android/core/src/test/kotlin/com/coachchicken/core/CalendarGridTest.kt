package com.coachchicken.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CalendarGridTest {
    // Wednesday
    private val today = LocalDate.of(2026, 11, 4)

    @Test fun fiveWeeksOfSevenDays() {
        val grid = buildCalendar(today, emptyMap())
        assertEquals(5, grid.size)
        assertTrue(grid.all { it.size == 7 })
    }

    @Test fun weeksStartOnSunday() {
        val grid = buildCalendar(today, emptyMap())
        assertTrue(grid.all { it.first().date.dayOfWeek == DayOfWeek.SUNDAY })
        assertEquals(LocalDate.of(2026, 11, 1), grid.last().first().date)
        assertEquals(LocalDate.of(2026, 10, 4), grid.first().first().date)
    }

    @Test fun todayOnSundayIsFirstCellOfLastWeek() {
        val sunday = LocalDate.of(2026, 11, 1)
        assertEquals(sunday, buildCalendar(sunday, emptyMap()).last().first().date)
    }

    @Test fun marksTodayFutureAndTrained() {
        val morning = LocalTime.of(7, 30)
        val trained = mapOf(LocalDate.of(2026, 11, 3) to listOf(morning), LocalDate.of(2026, 10, 10) to listOf(morning))
        val days = buildCalendar(today, trained).flatten()
        assertEquals(1, days.count { it.isToday })
        assertTrue(days.single { it.isToday }.date == today)
        assertEquals(3, days.count { it.isFuture }) // Thu, Fri, Sat
        assertEquals(trained.keys, days.filter { it.trained }.map { it.date }.toSet())
        assertFalse(days.single { it.isToday }.trained)
    }

    @Test fun sessionTimesCarriedOntoTheirDay() {
        val times = listOf(LocalTime.of(7, 0), LocalTime.of(18, 30))
        val day = buildCalendar(today, mapOf(today to times)).flatten().single { it.isToday }
        assertEquals(times, day.sessions)
    }

    @Test fun clockMarkAngles() {
        assertEquals(0f, clockMark(LocalTime.of(0, 0)).angleDegrees)
        assertEquals(90f, clockMark(LocalTime.of(3, 0)).angleDegrees)
        assertEquals(180f, clockMark(LocalTime.of(6, 0)).angleDegrees)
        assertEquals(195f, clockMark(LocalTime.of(18, 30)).angleDegrees)
        assertEquals(0f, clockMark(LocalTime.of(12, 0)).angleDegrees) // noon is also at the top
    }

    @Test fun clockMarkAmPm() {
        assertTrue(clockMark(LocalTime.of(0, 0)).isAm)
        assertTrue(clockMark(LocalTime.of(11, 59)).isAm)
        assertFalse(clockMark(LocalTime.of(12, 0)).isAm)
        assertFalse(clockMark(LocalTime.of(23, 59)).isAm)
    }
}

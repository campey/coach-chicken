package com.coachchicken.core

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CalendarGridTest {
    // Wednesday
    private val today = LocalDate.of(2026, 11, 4)

    @Test fun fiveWeeksOfSevenDays() {
        val grid = buildCalendar(today, emptySet())
        assertEquals(5, grid.size)
        assertTrue(grid.all { it.size == 7 })
    }

    @Test fun weeksStartOnSunday() {
        val grid = buildCalendar(today, emptySet())
        assertTrue(grid.all { it.first().date.dayOfWeek == DayOfWeek.SUNDAY })
        assertEquals(LocalDate.of(2026, 11, 1), grid.last().first().date)
        assertEquals(LocalDate.of(2026, 10, 4), grid.first().first().date)
    }

    @Test fun todayOnSundayIsFirstCellOfLastWeek() {
        val sunday = LocalDate.of(2026, 11, 1)
        assertEquals(sunday, buildCalendar(sunday, emptySet()).last().first().date)
    }

    @Test fun marksTodayFutureAndTrained() {
        val trained = setOf(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 10, 10))
        val days = buildCalendar(today, trained).flatten()
        assertEquals(1, days.count { it.isToday })
        assertTrue(days.single { it.isToday }.date == today)
        assertEquals(3, days.count { it.isFuture }) // Thu, Fri, Sat
        assertEquals(trained, days.filter { it.trained }.map { it.date }.toSet())
        assertFalse(days.single { it.isToday }.trained)
    }
}

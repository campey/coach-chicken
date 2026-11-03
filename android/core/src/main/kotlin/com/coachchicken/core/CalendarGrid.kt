package com.coachchicken.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class CalendarDay(
    val date: LocalDate,
    val trained: Boolean,
    val isToday: Boolean,
    val isFuture: Boolean,
)

/**
 * The last [weeks] weeks ending with the week containing [today], Sunday-first.
 * Returns one list of 7 days per week, oldest week first.
 */
fun buildCalendar(
    today: LocalDate,
    trainedDays: Set<LocalDate>,
    weeks: Int = 5,
): List<List<CalendarDay>> {
    require(weeks > 0)
    val thisWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    val first = thisWeekStart.minusWeeks((weeks - 1).toLong())
    return (0 until weeks).map { w ->
        (0 until 7).map { d ->
            val date = first.plusDays((w * 7 + d).toLong())
            CalendarDay(
                date = date,
                trained = date in trainedDays,
                isToday = date == today,
                isFuture = date.isAfter(today),
            )
        }
    }
}

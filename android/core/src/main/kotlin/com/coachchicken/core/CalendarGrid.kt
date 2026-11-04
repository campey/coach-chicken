package com.coachchicken.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

data class CalendarDay(
    val date: LocalDate,
    /** Start times of completed sessions that day; empty if not trained. */
    val sessions: List<LocalTime>,
    val isToday: Boolean,
    val isFuture: Boolean,
) {
    val trained: Boolean get() = sessions.isNotEmpty()
}

/** Where a session sits on the egg's inside: clock-face angle (0 = 12 o'clock, clockwise) and AM/PM. */
data class ClockMark(val angleDegrees: Float, val isAm: Boolean)

fun clockMark(time: LocalTime): ClockMark {
    val hours12 = (time.hour % 12) + time.minute / 60f
    return ClockMark(angleDegrees = hours12 / 12f * 360f, isAm = time.hour < 12)
}

/**
 * The last [weeks] weeks ending with the week containing [today], Sunday-first.
 * Returns one list of 7 days per week, oldest week first.
 */
fun buildCalendar(
    today: LocalDate,
    sessionsByDay: Map<LocalDate, List<LocalTime>>,
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
                sessions = sessionsByDay[date].orEmpty(),
                isToday = date == today,
                isFuture = date.isAfter(today),
            )
        }
    }
}

/** The run of consecutive trained days. [doneToday] is false until today has a completed plank. */
data class Streak(val days: Int, val doneToday: Boolean)

/**
 * The current streak: consecutive trained days ending today, or ending yesterday while today is still to do
 * (so the number stays put, but [Streak.doneToday] is false until today's plank is in).
 */
fun currentStreak(trainedDays: Set<LocalDate>, today: LocalDate): Streak {
    val doneToday = today in trainedDays
    var day = if (doneToday) today else today.minusDays(1)
    var n = 0
    while (day in trainedDays) { n++; day = day.minusDays(1) }
    return Streak(n, doneToday)
}

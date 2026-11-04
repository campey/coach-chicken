package com.coachchicken.core

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class StreakTest {
    private val today = LocalDate.of(2026, 11, 4)
    private fun days(vararg back: Int) = back.map { today.minusDays(it.toLong()) }.toSet()

    @Test fun noTrainingIsNoStreak() = assertEquals(Streak(0, false), currentStreak(emptySet(), today))

    @Test fun todayCountsOnceDone() = assertEquals(Streak(4, true), currentStreak(days(0, 1, 2, 3), today))

    @Test fun theNumberHoldsUntilTodayIsDone() = assertEquals(Streak(3, false), currentStreak(days(1, 2, 3), today))

    @Test fun aGapEndsTheStreak() = assertEquals(Streak(2, true), currentStreak(days(0, 1, 3, 4), today))

    @Test fun missingYesterdayAndTodayIsZero() = assertEquals(Streak(0, false), currentStreak(days(2, 3), today))
}

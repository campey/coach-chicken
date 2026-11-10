package com.coachchicken.core

import java.time.LocalDate
import java.time.ZonedDateTime

/** The next moment the daily reminder should fire: today at [hour]:[minute] if that is still ahead of [now], else tomorrow. */
fun nextFireAt(now: ZonedDateTime, hour: Int, minute: Int): ZonedDateTime {
    val today = now.toLocalDate().atTime(hour, minute).atZone(now.zone)
    return if (today.isAfter(now)) today else now.toLocalDate().plusDays(1).atTime(hour, minute).atZone(now.zone)
}

/** What the notification says. */
data class ReminderText(val title: String, val body: String)

/** Plucky, cluck-flavoured reminder copy. Varies by day so it doesn't nag with the same line, and mentions a streak that is at stake. */
object ReminderCopy {
    private val withStreak = listOf(
        ReminderText("Bawk! Plank o'clock 🐔", "%d days in a row. Don't let the eggs go cold. One minute!"),
        ReminderText("Psst. Cluck cluck.", "Your %d-day streak is looking a bit scrambled. A minute of plank sorts it out."),
        ReminderText("Your chicken is watching 👀", "%d days and counting. One plank keeps the streak."),
        ReminderText("Egg-cellent streak! 🥚", "%d days! Keep it going: down on the floor, one minute."),
        ReminderText("Cluck. Cluck. Cluck.", "That is %d days of clucking you into shape. Today's plank is waiting."),
        ReminderText("Squawk! Streak alert 🚨", "%d days on the line. Sixty seconds and it's safe."),
    )
    private val noStreak = listOf(
        ReminderText("Bawk bawk! Plank o'clock 🐔", "One minute. Even a chick can do a minute."),
        ReminderText("Cluck cluck…", "Your plank is waiting. The chicken believes in you."),
        ReminderText("Time to lay down a plank 🥚", "A minute on the floor starts a streak. Let's go!"),
        ReminderText("The chicken is not useless!", "Prove it. One minute of plank."),
    )

    fun pick(streak: Streak, date: LocalDate): ReminderText {
        val pool = if (streak.days > 0) withStreak else noStreak
        val t = pool[Math.floorMod(date.toEpochDay(), pool.size.toLong()).toInt()]
        return if (streak.days > 0) t.copy(body = t.body.format(streak.days)) else t
    }
}

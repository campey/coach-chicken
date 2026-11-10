package com.coachchicken.core

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** The three daily nudges. The streak saver only goes out to someone with a streak to lose. */
enum class ReminderSlot(val time: LocalTime) {
    MORNING(LocalTime.of(6, 0)),
    MIDDAY(LocalTime.of(13, 0)),
    STREAK_SAVER(LocalTime.of(21, 0)),
}

/** Today's plank done means no more nudges; the streak saver also needs a streak to save. */
fun shouldSend(slot: ReminderSlot, streak: Streak): Boolean =
    !streak.doneToday && (slot != ReminderSlot.STREAK_SAVER || streak.days > 0)

/** The next slot to fire after [now], and when: the earliest slot time still ahead, else the first one tomorrow. */
fun nextSlot(now: ZonedDateTime): Pair<ReminderSlot, ZonedDateTime> {
    val today = now.toLocalDate()
    for (slot in ReminderSlot.entries) {
        val at = today.atTime(slot.time).atZone(now.zone)
        if (at.isAfter(now)) return slot to at
    }
    val first = ReminderSlot.entries.first()
    return first to today.plusDays(1).atTime(first.time).atZone(now.zone)
}

/** What the notification says. */
data class ReminderText(val title: String, val body: String)

/** Plucky, cluck-flavoured copy. Varies by day so a line never repeats back to back, and a streak at stake is named. */
object ReminderCopy {
    private val morning = listOf(
        ReminderText("Cock-a-doodle-doo! 🐓", "Good morning! Start the day with a minute of plank."),
        ReminderText("Bawk! Rise and shine ☀️", "The chicken is up. One minute of plank before the day gets going?"),
        ReminderText("Morning, champ! 🥚", "Lay down a plank first thing and the rest of the day is a breeze."),
        ReminderText("Cluck cluck, early bird 🐔", "Get the plank done early. Sixty seconds!"),
    )
    private val midday = listOf(
        ReminderText("Lunchtime cluck 🐔", "No plank yet today. A minute before lunch?"),
        ReminderText("Bawk bawk! Plank o'clock", "Halfway through the day and the plank is still waiting."),
        ReminderText("Psst. Cluck cluck.", "Quick, a minute on the floor. The chicken is watching 👀"),
        ReminderText("The chicken is not useless!", "Prove it. One minute of plank."),
    )
    private val saver = listOf(
        ReminderText("Squawk! Streak alert 🚨", "%d days on the line and today's plank isn't in. Sixty seconds and it's safe."),
        ReminderText("Don't let the eggs go cold 🥚", "Your %d-day streak needs a plank tonight. One minute!"),
        ReminderText("Bawk bawk BAWK!", "%d days in a row, and tonight is the last call. Plank time!"),
        ReminderText("Streak scrambled? Not yet!", "%d days. A minute of plank keeps it whole."),
    )

    fun pick(slot: ReminderSlot, streak: Streak, date: LocalDate): ReminderText {
        val pool = when (slot) {
            ReminderSlot.MORNING -> morning
            ReminderSlot.MIDDAY -> midday
            ReminderSlot.STREAK_SAVER -> saver
        }
        val t = pool[Math.floorMod(date.toEpochDay(), pool.size.toLong()).toInt()]
        return if (slot == ReminderSlot.STREAK_SAVER) t.copy(body = t.body.format(streak.days)) else t
    }
}

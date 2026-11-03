package com.coachchicken.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class SessionRecord(
    val exercise: String,
    val startedAt: Instant,
    val durationMs: Long,
    val completed: Boolean,
)

/** One line per session: `startedAt,exercise,durationMs,completed`. Same spirit as the prototype's CSV log. */
object SessionCsv {
    const val HEADER = "startedAt,exercise,durationMs,completed"

    fun format(r: SessionRecord): String =
        "${r.startedAt},${r.exercise},${r.durationMs},${r.completed}"

    /** Returns null for the header, blank lines, or anything malformed. */
    fun parse(line: String): SessionRecord? {
        val parts = line.trim().split(',')
        if (parts.size != 4) return null
        return try {
            SessionRecord(
                exercise = parts[1],
                startedAt = Instant.parse(parts[0]),
                durationMs = parts[2].toLong(),
                completed = parts[3].toBooleanStrict(),
            )
        } catch (e: Exception) {
            null
        }
    }

    fun parseAll(text: String): List<SessionRecord> = text.lineSequence().mapNotNull(::parse).toList()
}

/** Local start times of completed sessions, grouped by local day (in [zone]), each day's list sorted. */
fun completedSessionTimes(records: List<SessionRecord>, zone: ZoneId): Map<LocalDate, List<LocalTime>> =
    records.filter { it.completed }
        .map { it.startedAt.atZone(zone) }
        .groupBy({ it.toLocalDate() }, { it.toLocalTime() })
        .mapValues { (_, times) -> times.sorted() }

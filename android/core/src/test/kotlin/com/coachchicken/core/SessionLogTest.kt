package com.coachchicken.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionLogTest {
    private val rec = SessionRecord("plank", Instant.parse("2026-11-03T23:30:00Z"), 60_000, true)

    @Test fun roundTrip() = assertEquals(rec, SessionCsv.parse(SessionCsv.format(rec)))

    @Test fun skipsHeaderBlankAndGarbage() {
        val text = listOf(SessionCsv.HEADER, "", "nonsense", SessionCsv.format(rec), "a,b,c,d").joinToString("\n")
        assertEquals(listOf(rec), SessionCsv.parseAll(text))
        assertNull(SessionCsv.parse(SessionCsv.HEADER))
    }

    @Test fun onlyCompletedSessionsCount() {
        val incomplete = rec.copy(startedAt = Instant.parse("2026-11-02T10:00:00Z"), completed = false)
        val days = trainedDays(listOf(rec, incomplete), ZoneId.of("UTC"))
        assertEquals(setOf(LocalDate.of(2026, 11, 3)), days)
    }

    @Test fun daysUseLocalZone() {
        // 23:30Z is already the next day in Auckland (UTC+13 in November)
        val days = trainedDays(listOf(rec), ZoneId.of("Pacific/Auckland"))
        assertEquals(setOf(LocalDate.of(2026, 11, 4)), days)
    }
}

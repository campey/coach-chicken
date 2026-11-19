package com.coachchicken.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BackupTest {
    private fun rec(t: String, ms: Long = 60_000) = SessionRecord("plank", Instant.parse(t), ms, true)
    private fun csv(vararg r: SessionRecord) = SessionCsv.HEADER + "\n" + r.joinToString("") { SessionCsv.format(it) + "\n" }

    @Test fun roundTrips() {
        val out = ByteArrayOutputStream()
        Backup.write(BackupContents(csv(rec("2026-11-03T09:00:00Z")), mapOf("1762160400000.txt" to "#plank v1 ...\n1,2,3")), "0.5.4", out)
        val back = assertNotNull(Backup.read(ByteArrayInputStream(out.toByteArray())))
        assertEquals(csv(rec("2026-11-03T09:00:00Z")), back.sessionsCsv)
        assertEquals(mapOf("1762160400000.txt" to "#plank v1 ...\n1,2,3"), back.planks)
    }

    @Test fun somethingThatIsNotABackupIsRejected() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { it.putNextEntry(ZipEntry("holiday.jpg")); it.write(byteArrayOf(1, 2, 3)); it.closeEntry() }
        assertNull(Backup.read(ByteArrayInputStream(out.toByteArray())))
    }

    @Test fun oddlyNamedEntriesAreIgnoredSoNothingCanEscapeTheFolder() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use {
            it.putNextEntry(ZipEntry("planks/../../evil.txt")); it.write("x".toByteArray()); it.closeEntry()
            it.putNextEntry(ZipEntry("planks/123.txt")); it.write("ok".toByteArray()); it.closeEntry()
        }
        assertEquals(mapOf("123.txt" to "ok"), Backup.read(ByteArrayInputStream(out.toByteArray()))!!.planks)
    }

    @Test fun mergeKeepsBothAndDoesNotDuplicate() {
        val onPhone = csv(rec("2026-11-04T09:00:00Z"), rec("2026-11-05T09:00:00Z"))
        val backup = csv(rec("2026-11-03T09:00:00Z"), rec("2026-11-04T09:00:00Z"))
        val (merged, added) = Backup.mergeSessions(onPhone, backup)
        assertEquals(1, added)
        assertEquals(csv(rec("2026-11-03T09:00:00Z"), rec("2026-11-04T09:00:00Z"), rec("2026-11-05T09:00:00Z")), merged)
    }

    @Test fun mergeIntoAnEmptyPhoneRestoresEverything() {
        val backup = csv(rec("2026-11-03T09:00:00Z"), rec("2026-11-04T09:00:00Z"))
        assertEquals(backup to 2, Backup.mergeSessions("", backup))
    }
}

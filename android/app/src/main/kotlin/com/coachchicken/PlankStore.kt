package com.coachchicken

import android.content.Context
import com.coachchicken.core.PlankRecording
import com.coachchicken.core.PlankRecordingCodec
import java.io.File
import java.time.Instant

/** One small text file per plank in the app's private storage, named by the plank's start time, so its card can be replayed. */
class PlankStore(context: Context) {
    private val dir = File(context.filesDir, "planks")

    private fun fileFor(startedAt: Instant) = File(dir, "${startedAt.toEpochMilli()}.txt")

    @Synchronized
    fun save(r: PlankRecording) {
        dir.mkdirs()
        fileFor(r.startedAt).writeText(PlankRecordingCodec.encode(r))
    }

    /** Null when there is no recording for that plank (it predates recording, or the file is unreadable). */
    @Synchronized
    fun load(startedAt: Instant): PlankRecording? {
        val f = fileFor(startedAt)
        return if (f.exists()) PlankRecordingCodec.decode(f.readText()) else null
    }

    /** Every saved replay as file name to text, for a backup. */
    @Synchronized
    fun readAllRaw(): Map<String, String> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(".txt") }?.associate { it.name to it.readText() } ?: emptyMap()

    /** Writes a replay from a backup unless one with that name is already here. True if it was written. */
    @Synchronized
    fun writeRawIfMissing(name: String, text: String): Boolean {
        dir.mkdirs()
        val f = File(dir, name)
        if (f.exists()) return false
        f.writeText(text)
        return true
    }
}

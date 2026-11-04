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
}

package com.coachchicken

import android.content.Context
import com.coachchicken.core.SessionCsv
import com.coachchicken.core.SessionRecord
import java.io.File

/** Append-only CSV log in the app's private storage. */
class SessionStore(context: Context) {
    private val file = File(context.filesDir, "sessions.csv")

    @Synchronized
    fun append(record: SessionRecord) {
        if (!file.exists()) file.writeText(SessionCsv.HEADER + "\n")
        file.appendText(SessionCsv.format(record) + "\n")
    }

    @Synchronized
    fun readAll(): List<SessionRecord> =
        if (file.exists()) SessionCsv.parseAll(file.readText()) else emptyList()

    /** The raw log text, for a backup. */
    @Synchronized
    fun readText(): String = if (file.exists()) file.readText() else ""

    /** Replaces the log with [text] (the merged log, when restoring a backup). */
    @Synchronized
    fun writeText(text: String) = file.writeText(text)
}

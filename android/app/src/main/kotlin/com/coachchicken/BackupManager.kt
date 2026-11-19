package com.coachchicken

import android.content.Context
import android.net.Uri
import com.coachchicken.core.Backup
import com.coachchicken.core.BackupContents

/** What a restore changed. */
data class RestoreResult(val sessionsAdded: Int, val replaysAdded: Int)

/** Backs up the session log and the saved replays to a file the person chooses, and merges one back in. */
class BackupManager(private val context: Context) {
    private val sessions = SessionStore(context)
    private val planks = PlankStore(context)

    /** Writes the backup to [uri]; returns how many sessions it holds. */
    fun export(uri: Uri): Int {
        val text = sessions.readText()
        val contents = BackupContents(text, planks.readAllRaw())
        val out = context.contentResolver.openOutputStream(uri) ?: error("cannot open the file to write")
        out.use { Backup.write(contents, BuildConfig.VERSION_NAME, it) }
        return com.coachchicken.core.SessionCsv.parseAll(text).size
    }

    /** Merges the backup at [uri] into what is on the phone. Null if the file is not a backup. */
    fun restore(uri: Uri): RestoreResult? {
        val input = context.contentResolver.openInputStream(uri) ?: error("cannot open the file to read")
        val contents = input.use { Backup.read(it) } ?: return null
        val (merged, added) = Backup.mergeSessions(sessions.readText(), contents.sessionsCsv)
        if (added > 0) sessions.writeText(merged)
        val replays = contents.planks.count { (name, text) -> planks.writeRawIfMissing(name, text) }
        return RestoreResult(added, replays)
    }
}

package com.coachchicken.core

import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** What a backup file holds: the session log and each plank's replay, as text. */
data class BackupContents(val sessionsCsv: String, val planks: Map<String, String>)

/**
 * The backup file is a plain zip: `sessions.csv`, one `planks/<start time in ms>.txt` per saved replay, and a short
 * `about.txt`. Restoring merges: nothing that is already on the phone is overwritten or removed.
 */
object Backup {
    private const val SESSIONS = "sessions.csv"
    private const val PLANKS_DIR = "planks/"
    private val plankName = Regex("""^\d{1,16}\.txt$""")

    fun write(contents: BackupContents, appVersion: String, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            fun put(name: String, text: String) { zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() }
            put("about.txt", "Coach Chicken backup, app $appVersion.\nRestore it from the app's menu; it merges, it never removes what is already there.\n")
            put(SESSIONS, contents.sessionsCsv)
            contents.planks.forEach { (name, text) -> if (plankName.matches(name)) put(PLANKS_DIR + name, text) }
        }
    }

    /** Null if [input] is not a backup (no session log and no replays). Entries with unexpected names are ignored. */
    fun read(input: InputStream): BackupContents? {
        var sessions: String? = null
        val planks = LinkedHashMap<String, String>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val text = zip.readBytes().toString(Charsets.UTF_8)
                when {
                    entry.name == SESSIONS -> sessions = text
                    entry.name.startsWith(PLANKS_DIR) && plankName.matches(entry.name.removePrefix(PLANKS_DIR)) -> planks[entry.name.removePrefix(PLANKS_DIR)] = text
                }
            }
        }
        return if (sessions == null && planks.isEmpty()) null else BackupContents(sessions ?: "", planks)
    }

    /** The union of two session logs (a session is identified by its start time and exercise), oldest first, and how many came only from [incoming]. */
    fun mergeSessions(existing: String, incoming: String): Pair<String, Int> {
        val have = SessionCsv.parseAll(existing)
        val keys = have.map { it.startedAt to it.exercise }.toHashSet()
        val added = SessionCsv.parseAll(incoming).filter { keys.add(it.startedAt to it.exercise) }
        val all = (have + added).sortedBy { it.startedAt }
        return (SessionCsv.HEADER + "\n" + all.joinToString("") { SessionCsv.format(it) + "\n" }) to added.size
    }
}

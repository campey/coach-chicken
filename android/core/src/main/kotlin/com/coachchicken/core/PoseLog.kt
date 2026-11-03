package com.coachchicken.core

/**
 * A compact, pasteable log of what the camera saw: one CSV row per (throttled) frame with the detector's
 * grade (NO_BODY / NOT_PLANK / OK / PERFECT), the filtered plank signal and aura, the hip's distance from the green target zone (tgt_off: 1 = the zone's edge), the numbers behind it, and the key joints, interleaved with `# event` lines.
 * Keeps only the most recent [capacity] lines.
 */
class PoseLog(
    private val capacity: Int = 600,
    private val minIntervalMs: Long = 250,
) {
    private val lines = ArrayDeque<String>()
    private var meta = ""
    private var lastFrameMs = Long.MIN_VALUE

    fun clear(meta: String = "") {
        lines.clear()
        this.meta = meta
        lastFrameMs = Long.MIN_VALUE
    }

    fun addEvent(tMs: Long, text: String) = push("# $tMs $text")

    /** Adds a row unless the previous one was less than [minIntervalMs] ago. */
    fun addFrame(
        tMs: Long,
        phase: String,
        landmarks: List<Landmark>?,
        aspect: Float,
        grade: PlankGrade,
        smoothed: Float = 0f,
        aura: Float = 0f,
        targetOffset: Float? = null,
    ) {
        if (lastFrameMs != Long.MIN_VALUE && tMs - lastFrameMs < minIntervalMs) return
        lastFrameMs = tMs
        val m = landmarks?.let { plankMetrics(it, aspect) }
        val joints = LOGGED.joinToString(",") { i ->
            val l = landmarks?.getOrNull(i)
            if (l == null) "," else "${f(l.x.toDouble(), 3)},${f(l.y.toDouble(), 3)}"
        }
        val metrics = if (m == null) ",,,,,,"
        else "${m.side},${m.lower},${f(m.bodyAngleDeg, 1)},${f(m.hipAngleDeg, 1)},${f(m.elbowDropRatio, 2)},${f(m.elbowOffsetRatio, 2)},${f(m.minVisibility.toDouble(), 2)}"
        push("$tMs,$phase,$grade,${f(smoothed.toDouble(), 2)},${f(aura.toDouble(), 2)},${targetOffset?.let { f(it.toDouble(), 2) } ?: ""},${f(aspect.toDouble(), 3)},$metrics,$joints")
    }

    fun render(): String = buildString {
        append("# Coach Chicken pose log. Rows: ").append(HEADER).append('\n')
        append("# Landmarks are normalised image coords (x right, y down), joints: ").append(NAMES.joinToString(" ")).append('\n')
        if (meta.isNotEmpty()) append("# ").append(meta).append('\n')
        append(HEADER).append('\n')
        lines.forEach { append(it).append('\n') }
    }

    val size: Int get() = lines.size

    private fun push(line: String) {
        lines.addLast(line)
        while (lines.size > capacity) lines.removeFirst()
    }

    private fun f(v: Double, digits: Int) = "%.${digits}f".format(java.util.Locale.ROOT, v)

    companion object {
        /** Nose, shoulders, elbows, wrists, hips, knees, ankles. */
        val LOGGED = listOf(0, 11, 12, 13, 14, 15, 16, 23, 24, 25, 26, 27, 28)
        private val NAMES = listOf("nose", "lsho", "rsho", "lelb", "relb", "lwri", "rwri", "lhip", "rhip", "lkne", "rkne", "lank", "rank")
        private val HEADER = "t_ms,phase,grade,plank_s,aura,tgt_off,aspect,side,lower,body_deg,hip_deg,elbow_drop,elbow_off,min_vis," +
            NAMES.joinToString(",") { "${it}_x,${it}_y" }
    }
}

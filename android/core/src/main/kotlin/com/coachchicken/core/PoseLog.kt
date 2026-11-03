package com.coachchicken.core

/**
 * A compact, pasteable log of what the camera saw: one CSV row per (throttled) frame with the detector's
 * verdict, the numbers behind it, and the key joints, interleaved with `# event` lines.
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
        reading: PoseReading,
    ) {
        if (lastFrameMs != Long.MIN_VALUE && tMs - lastFrameMs < minIntervalMs) return
        lastFrameMs = tMs
        val m = landmarks?.let { plankMetrics(it, aspect) }
        val joints = LOGGED.joinToString(",") { i ->
            val l = landmarks?.getOrNull(i)
            if (l == null) "," else "${f(l.x.toDouble(), 3)},${f(l.y.toDouble(), 3)}"
        }
        val metrics = if (m == null) ",,,,,"
        else "${m.side},${f(m.bodyAngleDeg, 1)},${f(m.hipAngleDeg, 1)},${f(m.elbowDropRatio, 2)},${f(m.elbowOffsetRatio, 2)},${f(m.minVisibility.toDouble(), 2)}"
        push("$tMs,$phase,$reading,${f(aspect.toDouble(), 3)},$metrics,$joints")
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
        private val HEADER = "t_ms,phase,reading,aspect,side,body_deg,hip_deg,elbow_drop,elbow_off,min_vis," +
            NAMES.joinToString(",") { "${it}_x,${it}_y" }
    }
}

package com.coachchicken.core

import java.time.Instant
import java.util.Locale

/**
 * One sampled moment of a plank, for replaying it later on the summary card.
 * [tMs] is relative to the plank's start (negative = the lead-in), [grade] is 0 no body, 1 not a plank, 2 ok, 3 perfect,
 * [hipDeg] is null when the hip angle could not be measured, and [joints] holds the x,y of [PoseLog.LOGGED] joints
 * (26 floats, NaN for a joint that was not visible) or is null when no body was seen.
 */
class RecFrame(val tMs: Long, val grade: Int, val aura: Float, val hipDeg: Float?, val joints: FloatArray?)

/** A finished plank, kept so its card can be replayed. [mirror] is true for the front camera (the preview is mirrored). */
class PlankRecording(
    val startedAt: Instant,
    val aspect: Float,
    val mirror: Boolean,
    val goalMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val frames: List<RecFrame>,
    /** The green goal zone as it was when the plank started, drawn on the form tile. */
    val target: PlankTarget? = null,
)

/** Plain text, one file per plank: a `#plank` header line, then `t_ms,grade,aura,hip,x0,y0,...` rows. */
object PlankRecordingCodec {
    const val JOINT_VALUES = 26

    fun encode(r: PlankRecording): String = buildString {
        append("#plank v1 startedAt=${r.startedAt} aspect=${f(r.aspect, 4)} mirror=${r.mirror} goalMs=${r.goalMs} durationMs=${r.durationMs} completed=${r.completed}${r.target?.let { " target=${f(it.ax,4)};${f(it.ay,4)};${f(it.bx,4)};${f(it.by,4)};${f(it.halfWidth,4)};${it.side}" } ?: ""}\n")
        for (fr in r.frames) {
            append(fr.tMs).append(',').append(fr.grade).append(',').append(f(fr.aura, 2)).append(',')
            append(fr.hipDeg?.let { f(it, 1) } ?: "")
            for (k in 0 until JOINT_VALUES) {
                append(',')
                val v = fr.joints?.get(k)
                if (v != null && !v.isNaN()) append(f(v, 3))
            }
            append('\n')
        }
    }

    /** Null if the text is not a recording. Malformed rows are skipped. */
    fun decode(text: String): PlankRecording? {
        val lines = text.lineSequence().filter { it.isNotBlank() }.iterator()
        if (!lines.hasNext()) return null
        val head = lines.next()
        if (!head.startsWith("#plank v1")) return null
        val kv = head.split(' ').drop(2).mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
        return try {
            val frames = ArrayList<RecFrame>()
            for (line in lines) {
                val p = line.split(',')
                if (p.size != 4 + JOINT_VALUES) continue
                val joints = if (p.drop(4).all { it.isEmpty() }) null
                else FloatArray(JOINT_VALUES) { p[4 + it].toFloatOrNull() ?: Float.NaN }
                frames += RecFrame(p[0].toLong(), p[1].toInt(), p[2].toFloat(), p[3].toFloatOrNull(), joints)
            }
            PlankRecording(
                startedAt = Instant.parse(kv.getValue("startedAt")),
                aspect = kv.getValue("aspect").toFloat(),
                mirror = kv.getValue("mirror").toBooleanStrict(),
                goalMs = kv.getValue("goalMs").toLong(),
                durationMs = kv.getValue("durationMs").toLong(),
                completed = kv.getValue("completed").toBooleanStrict(),
                frames = frames,
                target = kv["target"]?.split(';')?.takeIf { it.size == 6 }?.let {
                    PlankTarget(it[0].toFloat(), it[1].toFloat(), it[2].toFloat(), it[3].toFloat(), it[4].toFloat(), it[5][0])
                },
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun f(v: Float, digits: Int) = "%.${digits}f".format(Locale.ROOT, v)
}

/**
 * Collects samples (about 4 a second) from the moment a session opens, so that when the plank starts the seconds
 * before it are still there as lead-in. Keeps only the most recent [capacity] samples.
 */
class PlankRecorder(
    private val capacity: Int = 500,
    private val minIntervalMs: Long = 250,
    private val leadInMs: Long = 10_000,
) {
    private class Sample(val tMs: Long, val frame: RecFrame)
    private val samples = ArrayDeque<Sample>()
    private var lastMs = Long.MIN_VALUE

    val size: Int get() = samples.size

    fun clear() {
        samples.clear()
        lastMs = Long.MIN_VALUE
    }

    /** [tMs] is on any steady clock; [grade] is 0..3, see [RecFrame]. Skipped if the last sample was too recent. */
    fun add(tMs: Long, grade: Int, aura: Float, hipDeg: Float?, joints: FloatArray?) {
        if (lastMs != Long.MIN_VALUE && tMs - lastMs < minIntervalMs) return
        lastMs = tMs
        samples.addLast(Sample(tMs, RecFrame(tMs, grade, aura, hipDeg, joints)))
        while (samples.size > capacity) samples.removeFirst()
    }

    /** The samples from [leadInMs] before [plankStartMs] up to [untilMs], with times made relative to the plank start. */
    fun framesFor(plankStartMs: Long, untilMs: Long): List<RecFrame> =
        samples.filter { it.tMs >= plankStartMs - leadInMs && it.tMs <= untilMs }
            .map { RecFrame(it.tMs - plankStartMs, it.frame.grade, it.frame.aura, it.frame.hipDeg, it.frame.joints) }
}

/** The 26 joint values for a pose, in [PoseLog.LOGGED] order; a joint below [minVisibility] is NaN. */
fun jointsOf(landmarks: List<Landmark>, minVisibility: Float = 0.3f): FloatArray {
    val out = FloatArray(PlankRecordingCodec.JOINT_VALUES)
    PoseLog.LOGGED.forEachIndexed { k, idx ->
        val l = landmarks.getOrNull(idx)
        val ok = l != null && l.visibility >= minVisibility
        out[2 * k] = if (ok) l!!.x else Float.NaN
        out[2 * k + 1] = if (ok) l!!.y else Float.NaN
    }
    return out
}

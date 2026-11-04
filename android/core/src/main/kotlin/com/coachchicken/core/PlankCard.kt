package com.coachchicken.core

import kotlin.math.exp

data class CardStats(
    val avgHipDeg: Float?,
    val minHipDeg: Float?,
    val maxHipDeg: Float?,
    /** 0..1 of the plank's time graded perfect. */
    val perfectFraction: Float,
    val bestStreakMs: Long,
)

/**
 * Everything the summary card draws, prepared from a [PlankRecording]: the hip angle, grade, aura and joints smoothed
 * over time (so replaying looks calm rather than jittery), and where the plank stops being a plank.
 * Times are seconds relative to the plank start.
 */
class CardTrack(val rec: PlankRecording) {
    val n = rec.frames.size
    val t = DoubleArray(n) { rec.frames[it].tMs / 1000.0 }
    private val rawGrade = IntArray(n) { rec.frames[it].grade }
    private val rawHip = DoubleArray(n) { rec.frames[it].hipDeg?.toDouble() ?: Double.NaN }

    val hip: DoubleArray = gaussian(t, rawHip, 0.6, keepMissing = true)
    /** 0 = not a plank, 0.5 = ok, 1 = perfect, smoothed: drives the colours. */
    val gradeScore: DoubleArray = gaussian(t, DoubleArray(n) { when (rawGrade[it]) { 3 -> 1.0; 2 -> 0.5; else -> 0.0 } }, 0.7, keepMissing = false)
    val aura: DoubleArray = gaussian(t, DoubleArray(n) { rec.frames[it].aura.toDouble() }, 0.5, keepMissing = false)
    /** The slow-building bonus (0..1): grows over about 15 s of perfect form, fades over about 4 s without it. */
    val streak: DoubleArray = run {
        val out = DoubleArray(n)
        var s = 0.0
        for (i in 0 until n) {
            val dt = if (i == 0) 0.0 else t[i] - t[i - 1]
            val target = if (rawGrade[i] == 3) 1.0 else 0.0
            s += (target - s) * (1 - exp(-dt / (if (target > s) 15.0 else 4.0)))
            out[i] = s
        }
        out
    }
    /** Per frame: 26 smoothed values (NaN = missing), or null when no body was seen. */
    val joints: List<DoubleArray?> = run {
        val sm = (0 until PlankRecordingCodec.JOINT_VALUES).map { k ->
            gaussian(t, DoubleArray(n) { rec.frames[it].joints?.get(k)?.toDouble() ?: Double.NaN }, 0.3, keepMissing = true)
        }
        List(n) { i -> if (rec.frames[i].joints == null) null else DoubleArray(PlankRecordingCodec.JOINT_VALUES) { k -> sm[k][i] } }
    }

    /**
     * Where the timer stops: the last frame of perfect form that is still before the final curve out. It needs two
     * perfect samples in a row (a lone blip does not count), a smoothed grade of at least ok, and the smoothed hip
     * still near its usual angle, so the form does not read as dropped at the end. With no such frame, the last frame
     * that is ok with the hip near its usual angle.
     */
    val stopS: Double = run {
        val durS = rec.durationMs / 1000.0
        val inPlank = (0 until n).filter { t[it] >= 0 && t[it] <= durS }
        val hs = inPlank.mapNotNull { hip[it].takeUnless { v -> v.isNaN() } }.sorted()
        val thr = maxOf(160.0, (if (hs.isEmpty()) 170.0 else hs[hs.size / 2]) - 6.0)
        val nearUsual = { i: Int -> hip[i].isNaN() || hip[i] >= thr }
        var stop = inPlank.lastOrNull { it > 0 && rawGrade[it] == 3 && rawGrade[it - 1] == 3 && gradeScore[it] >= 0.5 && nearUsual(it) }?.let { t[it] }
            ?: inPlank.lastOrNull { rawGrade[it] >= 2 && nearUsual(it) }?.let { t[it] }
            ?: 0.0
        maxOf(stop, 1.0).coerceAtMost(maxOf(durS, 1.0))
    }

    val startS: Double = if (n == 0) 0.0 else t[0]
    val endS: Double = if (n == 0) 0.0 else t[n - 1]

    val stats: CardStats = run {
        var perf = 0.0; var tot = 0.0; var run3 = 0.0; var best = 0.0
        var sum = 0.0; var cnt = 0.0; var mn = Double.MAX_VALUE; var mx = 0.0
        for (i in 0 until n) {
            if (t[i] < 0) continue
            if (t[i] >= stopS) break
            val next = if (i < n - 1) t[i + 1] else t[i] + 0.25
            val dt = minOf(next - t[i], stopS - t[i])
            tot += dt
            if (rawGrade[i] == 3) { perf += dt; run3 += dt; best = maxOf(best, run3) } else run3 = 0.0
            val h = hip[i]
            if (!h.isNaN()) { sum += h * dt; cnt += dt; mn = minOf(mn, h); mx = maxOf(mx, h) }
        }
        CardStats(
            avgHipDeg = if (cnt > 0) (sum / cnt).toFloat() else null,
            minHipDeg = if (cnt > 0) mn.toFloat() else null,
            maxHipDeg = if (cnt > 0) mx.toFloat() else null,
            perfectFraction = if (tot > 0) (perf / tot).toFloat() else 0f,
            bestStreakMs = (best * 1000).toLong(),
        )
    }

    /** Index of the last frame at or before [tSec] (0 if before the first). */
    fun indexAt(tSec: Double): Int {
        var lo = 0; var hi = n - 1
        while (lo < hi) { val m = (lo + hi + 1) / 2; if (t[m] <= tSec) lo = m else hi = m - 1 }
        return lo
    }
}

/** Symmetric Gaussian smoothing over time. NaN is "missing": it is ignored as input, and kept as NaN when [keepMissing]. */
internal fun gaussian(t: DoubleArray, v: DoubleArray, sigma: Double, keepMissing: Boolean): DoubleArray {
    val n = v.size
    return DoubleArray(n) { i ->
        if (keepMissing && v[i].isNaN()) return@DoubleArray Double.NaN
        var s = 0.0; var w = 0.0
        var j = i
        while (j >= 0 && t[i] - t[j] < 3 * sigma) {
            if (!v[j].isNaN()) { val d = t[i] - t[j]; val k = exp(-d * d / (2 * sigma * sigma)); s += k * v[j]; w += k }
            j--
        }
        j = i + 1
        while (j < n && t[j] - t[i] < 3 * sigma) {
            if (!v[j].isNaN()) { val d = t[j] - t[i]; val k = exp(-d * d / (2 * sigma * sigma)); s += k * v[j]; w += k }
            j++
        }
        if (w > 0) s / w else Double.NaN
    }
}

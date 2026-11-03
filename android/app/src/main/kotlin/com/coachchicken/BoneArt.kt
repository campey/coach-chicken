package com.coachchicken

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.coachchicken.core.Landmark
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Ink, fill and highlight colours of a skeleton. The fill carries the grade (gold / yellow / grey). */
internal class BonePalette(val ink: Color, val fill: Color, val shine: Color)

/** A cheap deterministic hash to a float in 0..1, so every wobble stays the same from frame to frame. */
internal fun rnd(seed: Int): Float {
    var x = seed * -1640531527
    x = x xor (x ushr 15); x *= 0x2c1b3c6d
    x = x xor (x ushr 12); x *= 0x297a2d39
    x = x xor (x ushr 15)
    return (x and 0xFFFFFF) / 16777216f
}

private fun mid(a: Offset, b: Offset) = Offset((a.x + b.x) / 2, (a.y + b.y) / 2)

/** A closed, smooth, slightly organic outline through [pts] (quadratic curves between the midpoints). */
private fun smoothClosed(pts: List<Offset>): Path {
    val n = pts.size
    val path = Path()
    val start = mid(pts[n - 1], pts[0])
    path.moveTo(start.x, start.y)
    for (i in 0 until n) {
        val c = pts[i]
        val m = mid(c, pts[(i + 1) % n])
        path.quadraticBezierTo(c.x, c.y, m.x, m.y)
    }
    path.close()
    return path
}

/** A lumpy ellipse: the knobs of the bones, the vertebrae, the cranium. */
private fun blob(c: Offset, rx: Float, ry: Float, angle: Float, seed: Int, n: Int = 10): Path {
    val ca = cos(angle)
    val sa = sin(angle)
    return smoothClosed(List(n) { i ->
        val t = (2 * PI * i / n).toFloat()
        val j = 0.9f + 0.2f * rnd(seed * 37 + i)
        val x = cos(t) * rx * j
        val y = sin(t) * ry * j
        Offset(c.x + x * ca - y * sa, c.y + x * sa + y * ca)
    })
}

/** A shaft that is wide at the ends and narrower in the middle, with hand-drawn wobble on both edges. */
private fun shaft(a: Offset, b: Offset, wEnd: Float, wMid: Float, seed: Int): Path {
    val d = b - a
    val len = d.getDistance().coerceAtLeast(1f)
    val u = d / len
    val n = Offset(-u.y, u.x)
    val steps = 4
    fun width(t: Float) = wMid + (wEnd - wMid) * ((cos(2 * PI * t).toFloat() + 1f) / 2f)
    val left = List(steps + 1) { i ->
        val t = i / steps.toFloat()
        a + d * t + n * (width(t) * (0.92f + 0.16f * rnd(seed * 11 + i)))
    }
    val right = List(steps + 1) { i ->
        val t = i / steps.toFloat()
        a + d * t - n * (width(t) * (0.92f + 0.16f * rnd(seed * 13 + i + 50)))
    }
    return smoothClosed(left + right.reversed())
}

/**
 * Draws all the parts with a thick ink stroke first, then fills them all: only the outer edge of the union keeps
 * the outline, which is how a hand-inked drawing looks (no lines crossing the inside of the bone).
 */
private fun DrawScope.inked(parts: List<Path>, pal: BonePalette, alpha: Float, inkW: Float) {
    parts.forEach { drawPath(it, pal.ink.copy(alpha = alpha), style = Stroke(width = inkW * 2, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
    parts.forEach { drawPath(it, pal.fill.copy(alpha = alpha)) }
}

/** One long bone: a waisted shaft with a pair of knobs at each end, and a short highlight along it. */
private fun DrawScope.bone(a: Offset, b: Offset, width: Float, knob: Float, seed: Int, pal: BonePalette, alpha: Float, inkW: Float) {
    val d = b - a
    val len = d.getDistance()
    if (len < 6f) return
    val u = d / len
    val n = Offset(-u.y, u.x)
    val parts = ArrayList<Path>()
    parts += shaft(a, b, width * 0.85f, width * 0.5f, seed)
    parts += blob(a + n * (knob * 0.55f) + u * (knob * 0.2f), knob, knob * 0.9f, 0f, seed * 5 + 1)
    parts += blob(a - n * (knob * 0.55f) + u * (knob * 0.2f), knob * 0.9f, knob, 0f, seed * 5 + 2)
    parts += blob(b + n * (knob * 0.55f) - u * (knob * 0.2f), knob * 0.95f, knob, 0f, seed * 5 + 3)
    parts += blob(b - n * (knob * 0.55f) - u * (knob * 0.2f), knob, knob * 0.9f, 0f, seed * 5 + 4)
    inked(parts, pal, alpha, inkW)
    drawLine(
        pal.shine.copy(alpha = alpha * 0.6f), a + d * 0.2f + n * (width * 0.15f), a + d * 0.75f + n * (width * 0.15f),
        strokeWidth = max(width * 0.22f, 1.5f), cap = StrokeCap.Round,
    )
}

/**
 * The whole skeleton, hand-drawn: spine (vertebrae), ribs, pelvis, skull, and for each arm a humerus with a radius
 * and ulna, and for each leg a femur with a tibia and fibula. The far side of the body is dimmer and thinner.
 * [at] maps a landmark to a screen position; joints below 0.3 visibility are left out.
 */
internal fun DrawScope.drawBoneSkeleton(lms: List<Landmark>, at: (Landmark) -> Offset, judgedSide: Char?, pal: BonePalette) {
    fun vis(i: Int) = lms[i].visibility >= 0.3f
    fun pos(i: Int) = at(lms[i])
    val inkW = 2.2.dp.toPx()

    val shoulders = listOf(11, 12).filter(::vis)
    val hips = listOf(23, 24).filter(::vis)
    val neck = if (shoulders.isEmpty()) null else shoulders.map(::pos).reduce { a, b -> mid(a, b) }
    val pelvis = if (hips.isEmpty()) null else hips.map(::pos).reduce { a, b -> mid(a, b) }
    val torso = if (neck != null && pelvis != null) (pelvis - neck).getDistance().coerceAtLeast(60.dp.toPx()) else 160.dp.toPx()

    val farSide = when (judgedSide) { 'L' -> 12; 'R' -> 11; else -> -1 } // the shoulder index of the far side
    fun limb(sho: Int, elb: Int, wri: Int, hip: Int, kne: Int, ank: Int, far: Boolean, seed: Int) {
        val k = if (far) 0.8f else 1f
        val alpha = if (far) 0.6f else 1f
        val w = max(torso * 0.035f, 2.5.dp.toPx()) * k
        val knob = max(torso * 0.05f, 4.dp.toPx()) * k
        if (vis(sho) && vis(elb)) bone(pos(sho), pos(elb), w * 1.1f, knob, seed, pal, alpha, inkW)
        if (vis(elb) && vis(wri)) {
            val e = pos(elb); val wr = pos(wri)
            val d = wr - e; val len = d.getDistance().coerceAtLeast(1f); val n = Offset(-d.y / len, d.x / len)
            val o = w * 0.8f
            bone(e + n * o, wr - n * (o * 0.4f), w * 0.55f, knob * 0.6f, seed + 1, pal, alpha, inkW) // radius
            bone(e - n * o, wr + n * (o * 0.4f), w * 0.55f, knob * 0.6f, seed + 2, pal, alpha, inkW) // ulna
        }
        if (vis(hip) && vis(kne)) bone(pos(hip), pos(kne), w * 1.4f, knob * 1.2f, seed + 3, pal, alpha, inkW) // femur
        if (vis(kne) && vis(ank)) {
            val kn = pos(kne); val an = pos(ank)
            val d = an - kn; val len = d.getDistance().coerceAtLeast(1f); val n = Offset(-d.y / len, d.x / len)
            bone(kn + n * (w * 0.5f), an + n * (w * 0.3f), w * 1.0f, knob * 0.9f, seed + 4, pal, alpha, inkW)  // tibia
            bone(kn - n * (w * 0.9f), an - n * (w * 0.7f), w * 0.5f, knob * 0.5f, seed + 5, pal, alpha, inkW)  // fibula
        }
    }

    // far side first, so the near side overlaps it
    val leftFar = farSide == 11
    val rightFar = farSide == 12
    if (leftFar) limb(11, 13, 15, 23, 25, 27, true, 100)
    if (rightFar) limb(12, 14, 16, 24, 26, 28, true, 200)

    if (neck != null && pelvis != null) {
        val axis = pelvis - neck
        val u = axis / axis.getDistance().coerceAtLeast(1f)
        val perp = Offset(-u.y, u.x)
        // the ribs hang towards where the elbows are (towards the floor in a plank)
        val elbow = listOf(13, 14).filter(::vis).map(::pos).firstOrNull()
        val side = if (elbow != null && (elbow - neck).let { it.x * perp.x + it.y * perp.y } < 0f) -perp else perp
        val ribW = max(torso * 0.022f, 3.dp.toPx())
        for (k in 0 until 5) {
            val t = 0.10f + 0.10f * k
            val p = neck + axis * t
            val reach = torso * (0.15f + 0.10f * sin(PI * (k + 0.5) / 5).toFloat())
            val tip = p + side * reach + u * (torso * 0.05f)
            val ctrl = p + side * (reach * 0.95f) - u * (torso * 0.01f)
            val rib = Path().apply { moveTo(p.x, p.y); quadraticBezierTo(ctrl.x, ctrl.y, tip.x, tip.y) }
            drawPath(rib, pal.ink, style = Stroke(width = ribW + inkW * 2, cap = StrokeCap.Round))
            drawPath(rib, pal.fill, style = Stroke(width = ribW, cap = StrokeCap.Round))
        }
        // vertebrae, small at the neck and bigger towards the pelvis
        val count = 9
        for (i in 0 until count) {
            val t = (i + 0.5f) / count
            val c = neck + axis * t
            val r = torso * (0.028f + 0.030f * t)
            inked(listOf(blob(c, r * 1.25f, r, atan2f(u.y, u.x), 900 + i, 8)), pal, 1f, inkW)
        }
        // pelvis: a flared bowl across the end of the spine
        val pc = pelvis
        fun pp(x: Float, y: Float) = pc + u * (x * torso) + perp * (y * torso)
        val pts = listOf(
            -0.06f to -0.17f, 0.00f to -0.19f, 0.06f to -0.14f, 0.12f to -0.07f, 0.14f to 0f, 0.12f to 0.07f,
            0.06f to 0.14f, 0.00f to 0.19f, -0.06f to 0.17f, -0.02f to 0.07f, -0.02f to -0.07f,
        ).mapIndexed { i, (x, y) -> pp(x + (rnd(700 + i) - 0.5f) * 0.012f, y + (rnd(720 + i) - 0.5f) * 0.012f) }
        val pelvisPath = smoothClosed(pts)
        inked(listOf(pelvisPath), pal, 1f, inkW)
        drawPath(blob(pp(0.07f, 0f), torso * 0.03f, torso * 0.04f, 0f, 760), pal.ink) // the hole in the pelvis
        // skull at the nose
        val nose = lms[0]
        if (nose.visibility >= 0.3f) {
            val c = at(nose)
            val down = (neck - c).let { val l = it.getDistance().coerceAtLeast(1f); it / l }
            val dperp = Offset(-down.y, down.x)
            val r = max(torso * 0.16f, 14.dp.toPx())
            val cranium = blob(c - down * (r * 0.15f), r, r * 0.92f, 0f, 800, 12)
            val jaw = blob(c + down * (r * 0.85f), r * 0.55f, r * 0.4f, atan2f(down.y, down.x), 801, 8)
            inked(listOf(cranium, jaw), pal, 1f, inkW)
            for (s in listOf(-1f, 1f)) { // eye sockets
                drawPath(blob(c + dperp * (s * r * 0.42f) + down * (r * 0.1f), r * 0.24f, r * 0.28f, 0f, 810 + s.toInt()), pal.ink)
            }
            for (i in 0..2) { // teeth
                val tc = c + down * (r * 0.95f) + dperp * ((i - 1) * r * 0.2f)
                drawLine(pal.ink, tc - down * (r * 0.12f), tc + down * (r * 0.12f), strokeWidth = inkW * 0.8f, cap = StrokeCap.Round)
            }
        }
    }

    if (!leftFar) limb(11, 13, 15, 23, 25, 27, false, 100)
    if (!rightFar) limb(12, 14, 16, 24, 26, 28, false, 200)
}

private fun atan2f(y: Float, x: Float) = kotlin.math.atan2(y, x)

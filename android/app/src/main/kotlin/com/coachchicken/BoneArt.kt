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

/** A bone drawn as a plain thick line: an ink stroke with the fill colour on top. */
private fun DrawScope.thinBone(a: Offset, b: Offset, width: Float, pal: BonePalette, alpha: Float, inkW: Float) {
    val w = max(width, 2.5.dp.toPx())
    drawLine(pal.ink.copy(alpha = alpha), a, b, strokeWidth = w + inkW * 2, cap = StrokeCap.Round)
    drawLine(pal.fill.copy(alpha = alpha), a, b, strokeWidth = w, cap = StrokeCap.Round)
}

/**
 * The whole skeleton, hand-drawn: spine (vertebrae), ribs, pelvis, and for each arm a humerus with a radius
 * and ulna, and for each leg a femur with a tibia and fibula. The far side of the body is dimmer and thinner.
 * [at] maps a landmark to a screen position; joints below 0.3 visibility are left out.
 */
internal fun DrawScope.drawBoneSkeleton2D(lms: List<Landmark>, at: (Landmark) -> Offset, judgedSide: Char?, pal: BonePalette) {
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
        if (vis(elb) && vis(wri)) { // radius and ulna: just two thin inked lines
            val e = pos(elb); val wr = pos(wri)
            val d = wr - e; val len = d.getDistance().coerceAtLeast(1f); val n = Offset(-d.y / len, d.x / len)
            val o = w * 0.55f
            thinBone(e + n * o, wr + n * (o * 0.6f), w * 0.32f, pal, alpha, inkW)
            thinBone(e - n * o, wr - n * (o * 0.6f), w * 0.32f, pal, alpha, inkW)
        }
        if (vis(hip) && vis(kne)) bone(pos(hip), pos(kne), w * 1.4f, knob * 1.2f, seed + 3, pal, alpha, inkW) // femur
        if (vis(kne) && vis(ank)) { // tibia and fibula: the same simple lines
            val kn = pos(kne); val an = pos(ank)
            val d = an - kn; val len = d.getDistance().coerceAtLeast(1f); val n = Offset(-d.y / len, d.x / len)
            thinBone(kn + n * (w * 0.5f), an + n * (w * 0.35f), w * 0.5f, pal, alpha, inkW)
            thinBone(kn - n * (w * 0.5f), an - n * (w * 0.35f), w * 0.3f, pal, alpha, inkW)
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
        // the ribs hang towards where the elbows are (towards the floor in a plank): that is the front of the body,
        // so the spine, seen from the side, runs along the opposite edge: the back
        val elbow = listOf(13, 14).filter(::vis).map(::pos).firstOrNull()
        val side = if (elbow != null && (elbow - neck).let { it.x * perp.x + it.y * perp.y } < 0f) -perp else perp
        val backOffset = side * (-torso * 0.13f)
        val spineTop = neck + backOffset
        val ribW = max(torso * 0.022f, 3.dp.toPx())
        for (k in 0 until 5) {
            val t = 0.10f + 0.10f * k
            val p = spineTop + axis * t
            val reach = torso * (0.15f + 0.10f * sin(PI * (k + 0.5) / 5).toFloat()) + torso * 0.13f // round to the front
            val tip = p + side * reach + u * (torso * 0.05f)
            val ctrl = p + side * (reach * 0.95f) - u * (torso * 0.01f)
            val rib = Path().apply { moveTo(p.x, p.y); quadraticBezierTo(ctrl.x, ctrl.y, tip.x, tip.y) }
            drawPath(rib, pal.ink, style = Stroke(width = ribW + inkW * 2, cap = StrokeCap.Round))
            drawPath(rib, pal.fill, style = Stroke(width = ribW, cap = StrokeCap.Round))
        }
        // vertebrae along the back, small at the neck and bigger towards the pelvis
        val count = 9
        for (i in 0 until count) {
            val t = (i + 0.5f) / count
            val c = spineTop + axis * t
            val r = torso * (0.028f + 0.030f * t)
            inked(listOf(blob(c, r * 1.25f, r, atan2f(u.y, u.x), 900 + i, 8)), pal, 1f, inkW)
        }
        // pelvis: a flared bowl across the end of the spine
        val pc = pelvis + backOffset * 0.5f // the pelvis sits against the base of the spine
        fun pp(x: Float, y: Float) = pc + u * (x * torso) + perp * (y * torso)
        val pts = listOf(
            -0.06f to -0.17f, 0.00f to -0.19f, 0.06f to -0.14f, 0.12f to -0.07f, 0.14f to 0f, 0.12f to 0.07f,
            0.06f to 0.14f, 0.00f to 0.19f, -0.06f to 0.17f, -0.02f to 0.07f, -0.02f to -0.07f,
        ).mapIndexed { i, (x, y) -> pp(x + (rnd(700 + i) - 0.5f) * 0.012f, y + (rnd(720 + i) - 0.5f) * 0.012f) }
        val pelvisPath = smoothClosed(pts)
        inked(listOf(pelvisPath), pal, 1f, inkW)
        drawPath(blob(pp(0.07f, 0f), torso * 0.03f, torso * 0.04f, 0f, 760), pal.ink) // the hole in the pelvis
    }

    if (!leftFar) limb(11, 13, 15, 23, 25, 27, false, 100)
    if (!rightFar) limb(12, 14, 16, 24, 26, 28, false, 200)
}

private fun atan2f(y: Float, x: Float) = kotlin.math.atan2(y, x)


// ---- the 3D skeleton ------------------------------------------------------------------------------------------------
// A procedural model posed from the 2D joints: the left and right limbs sit at fixed depths either side of the body, so
// the whole thing can be turned (yaw about the vertical, pitch about the horizontal) and still look like a solid skeleton.

private class V3(val x: Float, val y: Float, val z: Float)
private class Prim(val z: Float, val draw: () -> Unit)

/** The skull in profile, as (forward, down) in skull units: back of the head round the crown, brow, nose, teeth, chin, jaw. */
private val SKULL = listOf(
    -1.15f to 0.10f, -1.0f to -0.65f, -0.45f to -1.05f, 0.25f to -1.10f, 0.85f to -0.80f, 1.08f to -0.45f, 1.0f to -0.10f,
    1.38f to 0.28f, 1.15f to 0.50f, 1.10f to 0.78f, 1.12f to 0.98f, 1.0f to 1.20f, 0.40f to 1.25f, 0.0f to 1.0f,
    -0.15f to 0.55f, -0.60f to 0.50f, -1.10f to 0.45f,
)

/**
 * The whole skeleton, hand-drawn, in 3D: spine, ribs, sternum, collar bones, shoulder blades, pelvis, skull, and arm and
 * leg bones. [yaw] and [pitch] (radians) turn the view; 0, 0 is the plain side view. The head is placed from the ear and
 * nose (measured) when the near ear is visible, otherwise estimated from the nose alone. Parts further from the camera
 * are dimmer and drawn first. Falls back to the flat drawing when the shoulders or hips are missing.
 */
internal fun DrawScope.drawBoneSkeleton(
    lms: List<Landmark>, at: (Landmark) -> Offset, judgedSide: Char?, pal: BonePalette, yaw: Float = 0f, pitch: Float = 0f,
) {
    fun vis(i: Int) = (lms.getOrNull(i)?.visibility ?: 0f) >= 0.3f
    fun pos(i: Int) = at(lms[i])
    val inkW = 2.2.dp.toPx()
    val shoulders = listOf(11, 12).filter(::vis)
    val hips = listOf(23, 24).filter(::vis)
    if (shoulders.isEmpty() || hips.isEmpty()) return drawBoneSkeleton2D(lms, at, judgedSide, pal)
    val neck = shoulders.map(::pos).reduce { a, b -> mid(a, b) }
    val pelvis = hips.map(::pos).reduce { a, b -> mid(a, b) }
    val torso = (pelvis - neck).getDistance().coerceAtLeast(60.dp.toPx())

    val near = judgedSide ?: 'R'
    fun zSign(idx: Int): Float = if ((idx % 2 == 1) == (near == 'L')) 1f else -1f // odd MediaPipe indices are the left side
    val hwS = torso * 0.17f
    val hwH = torso * 0.11f
    fun depthOf(idx: Int): Float = when (idx) {
        in 23..28 -> zSign(idx) * hwH * (if (idx >= 27) 0.85f else 1f)
        in 11..16 -> zSign(idx) * hwS
        else -> 0f
    }
    val c = mid(neck, pelvis)
    val cy = cos(yaw); val sy = sin(yaw); val cp = cos(pitch); val sp = sin(pitch)
    fun rot(x: Float, y: Float, z: Float): V3 {
        val dx = x - c.x; val dy = y - c.y
        val x1 = dx * cy + z * sy; val z1 = -dx * sy + z * cy
        return V3(c.x + x1, c.y + dy * cp - z1 * sp, dy * sp + z1 * cp)
    }
    fun rot(o: Offset, z: Float) = rot(o.x, o.y, z)
    fun xy(v: V3) = Offset(v.x, v.y)
    fun joint(idx: Int): V3? = if (vis(idx)) rot(pos(idx), depthOf(idx)) else null
    fun fade(z: Float) = 0.6f + 0.4f * (0.5f + z / (torso * 0.45f)).coerceIn(0f, 1f)

    val prims = ArrayList<Prim>()
    fun add(z: Float, draw: () -> Unit) { prims += Prim(z, draw) }
    val ww = max(torso * 0.035f, 2.5.dp.toPx())
    val knob = max(torso * 0.05f, 4.dp.toPx())

    // the arms and legs
    fun limb(sho: Int, elb: Int, wri: Int, hip: Int, kne: Int, ank: Int, seed: Int) {
        val s = joint(sho); val e = joint(elb); val h = joint(hip); val k = joint(kne)
        fun seg(a: V3, b: V3, draw: (Float) -> Unit) { val z = (a.z + b.z) / 2; add(z) { draw(fade(z)) } }
        if (s != null && e != null) seg(s, e) { a -> bone(xy(s), xy(e), ww * 1.1f, knob, seed, pal, a, inkW) }
        fun twin(i0: Int, i1: Int, off: Float, wA: Float, wB: Float) { // two thin bones side by side in depth
            if (!vis(i0) || !vis(i1)) return
            val p0 = pos(i0); val p1 = pos(i1)
            for ((n, w) in listOf(1f to wA, -1f to wB)) {
                val p = rot(p0, depthOf(i0) + n * off); val q = rot(p1, depthOf(i1) + n * off * 0.6f)
                val z = (p.z + q.z) / 2
                add(z) { thinBone(xy(p), xy(q), w, pal, fade(z), inkW) }
            }
        }
        twin(elb, wri, ww * 0.55f, ww * 0.32f, ww * 0.32f)
        if (h != null && k != null) seg(h, k) { a -> bone(xy(h), xy(k), ww * 1.4f, knob * 1.2f, seed + 3, pal, a, inkW) }
        twin(kne, ank, ww * 0.5f, ww * 0.5f, ww * 0.3f)
    }
    limb(11, 13, 15, 23, 25, 27, 100)
    limb(12, 14, 16, 24, 26, 28, 200)

    // spine, ribs, breastbone, collar bones, shoulder blades
    val axis = pelvis - neck
    val u = axis / axis.getDistance().coerceAtLeast(1f)
    val perp = Offset(-u.y, u.x)
    val elbow = listOf(13, 14).filter(::vis).map(::pos).firstOrNull()
    val side = if (elbow != null && (elbow - neck).let { it.x * perp.x + it.y * perp.y } < 0f) -perp else perp
    val backOff = side * (-torso * 0.13f)
    val top = neck + backOff
    val ribW = max(torso * 0.022f, 3.dp.toPx())
    fun spineAt(t: Float) = top + axis * t
    for (k in 0 until 5) {
        val t = 0.10f + 0.10f * k
        val p = spineAt(t)
        val bulge = sin(PI * (k + 0.5) / 5).toFloat()
        val reach = torso * (0.15f + 0.10f * bulge) + torso * 0.13f
        val rw = hwS * (0.8f + 0.25f * bulge)
        for (sgn in listOf(1f, -1f)) {
            val p1 = p + side * (reach * 0.3f); val p2 = p + side * (reach * 0.85f) + u * (torso * 0.03f); val p3 = p + side * reach + u * (torso * 0.05f)
            val pts = (0..8).map { i ->
                val f = i / 8f; val g = 1 - f
                val b0 = g * g * g; val b1 = 3 * g * g * f; val b2 = 3 * g * f * f; val b3 = f * f * f
                rot(b0 * p.x + b1 * p1.x + b2 * p2.x + b3 * p3.x, b0 * p.y + b1 * p1.y + b2 * p2.y + b3 * p3.y, b1 * sgn * rw * 0.95f + b2 * sgn * rw * 0.6f)
            }
            val z = pts.map { it.z }.average().toFloat()
            add(z) {
                val a = fade(z)
                val path = Path().apply { moveTo(pts[0].x, pts[0].y); for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y) }
                drawPath(path, pal.ink.copy(alpha = a), style = Stroke(width = ribW + inkW * 2, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(path, pal.fill.copy(alpha = a), style = Stroke(width = ribW, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
    run { // breastbone
        val a = rot(spineAt(0.1f) + side * (torso * 0.28f), 0f); val b = rot(spineAt(0.5f) + side * (torso * 0.28f), 0f)
        add(a.z) { thinBone(xy(a), xy(b), ribW * 1.4f, pal, 1f, inkW) }
    }
    for (idx in shoulders) {
        val s = pos(idx); val z = depthOf(idx)
        val a = rot(neck + backOff * 0.2f, 0f); val b = rot(s, z)
        val zz = (a.z + b.z) / 2
        add(zz) { thinBone(xy(a), xy(b), ww * 0.35f, pal, fade(zz), inkW) } // collar bone
        val sc = rot(s + backOff * 0.55f, z * 1.05f)
        add(sc.z) { inked(listOf(blob(xy(sc), torso * 0.07f, torso * 0.09f, atan2f(u.y, u.x), 650 + (idx % 2) * 7, 8)), pal, fade(sc.z), inkW) } // shoulder blade
    }
    for (i in 0 until 9) {
        val t = (i + 0.5f) / 9
        val v = rot(spineAt(t), 0f)
        val r = torso * (0.028f + 0.030f * t)
        add(v.z) { inked(listOf(blob(xy(v), r * 1.25f, r, atan2f(u.y, u.x), 900 + i, 8)), pal, 1f, inkW) }
    }
    run { // pelvis: two flared wings either side of the sacrum
        val pc = pelvis + backOff * 0.5f
        fun pp(x: Float, y: Float) = pc + u * (x * torso) + perp * (y * torso)
        val base = listOf(
            -0.06f to -0.17f, 0.00f to -0.19f, 0.06f to -0.14f, 0.12f to -0.07f, 0.14f to 0f, 0.12f to 0.07f,
            0.06f to 0.14f, 0.00f to 0.19f, -0.06f to 0.17f, -0.02f to 0.07f, -0.02f to -0.07f,
        )
        for (sgn in listOf(1f, -1f)) {
            val pts = base.mapIndexed { i, (x, y) -> val q = pp(x + (rnd(700 + i) - 0.5f) * 0.012f, y + (rnd(720 + i) - 0.5f) * 0.012f); xy(rot(q, sgn * hwH * 0.95f)) }
            val z = base.indices.map { i -> rot(pp(base[i].first, base[i].second), sgn * hwH * 0.95f).z }.average().toFloat()
            add(z) { inked(listOf(smoothClosed(pts)), pal, fade(z), inkW) }
        }
        val sac = rot(pc, 0f)
        add(sac.z) { inked(listOf(blob(xy(sac), torso * 0.05f, torso * 0.10f, atan2f(perp.y, perp.x), 770, 8)), pal, 1f, inkW) }
    }

    run { // the skull: three plates of one profile stacked in depth, so it turns like a solid head
        val face0 = u * -1f
        val nose = if (vis(0)) pos(0) else null
        val earIdx = if (near == 'L') 7 else 8
        val ear = when { vis(earIdx) -> pos(earIdx); vis(7) -> pos(7); vis(8) -> pos(8); else -> null }
        var s = torso * 0.16f
        val face: Offset; val chin: Offset; val centre: Offset
        if (nose != null && ear != null && (nose - ear).getDistance() > torso * 0.05f) {
            // measured: the ear-to-nose line is the head axis; in the skull model the ear hole and nose tip are about 11 degrees apart
            val v = (nose - ear) / (nose - ear).getDistance()
            var pv = Offset(-v.y, v.x)
            if (pv.x * side.x + pv.y * side.y < 0f) pv = -pv
            val dl = 0.19f
            face = v * cos(dl) + pv * sin(dl)
            chin = pv * cos(dl) - v * sin(dl)
            s = (1.6f * (nose - ear).getDistance() / 1.455f).coerceIn(torso * 0.11f, torso * 0.17f)
            centre = ear + face * (0.05f * s) - chin * (0.55f * s)
        } else {
            // estimated from the nose alone: how far below the neck line it sits tells how far the head hangs
            var phi = 1.45f
            if (nose != null) {
                val hv = nose - neck
                val dn = hv.x * side.x + hv.y * side.y; val fw = hv.x * face0.x + hv.y * face0.y
                phi = (0.87f + 1.5f * atan2f(dn, fw)).coerceIn(0.3f, 1.9f)
            }
            face = face0 * cos(phi) + side * sin(phi)
            chin = side * cos(phi) - face0 * sin(phi)
            centre = if (nose != null && (nose - neck).getDistance() in (torso * 0.1f)..(torso * 0.6f)) nose - face * (s * 1.38f) - chin * (s * 0.28f)
            else neck + face0 * (torso * 0.24f) + backOff * 0.2f
        }
        fun sk(f: Float, d: Float, z: Float) = rot(centre + face * (f * s) + chin * (d * s), z)
        for (z in listOf(-0.42f * s, 0f, 0.42f * s)) {
            val pts = SKULL.mapIndexed { i, (f, d) -> xy(sk(f + (rnd(850 + i) - 0.5f) * 0.06f, d + (rnd(870 + i) - 0.5f) * 0.06f, z)) }
            val zz = sk(0f, 0f, z).z
            add(zz) {
                val a = fade(zz)
                inked(listOf(smoothClosed(pts)), pal, a, inkW)
                if (z > 0f) { // the near plate carries the features: eye socket, ear hole, nose hole, teeth
                    val e = xy(sk(0.72f, 0.05f, z)); val nh = xy(sk(1.2f, 0.38f, z)); val eh = xy(sk(-0.05f, 0.55f, z))
                    val t0 = xy(sk(1.08f, 0.86f, z)); val t1 = xy(sk(0.5f, 0.92f, z))
                    drawPath(blob(e, s * 0.3f, s * 0.34f, 0f, 830, 8), pal.ink.copy(alpha = a))
                    drawPath(blob(nh, s * 0.1f, s * 0.14f, 0f, 840, 6), pal.ink.copy(alpha = a))
                    drawPath(blob(eh, s * 0.1f, s * 0.1f, 0f, 850, 6), pal.ink.copy(alpha = a))
                    val tw = max(1.2f, s * 0.07f)
                    drawLine(pal.ink.copy(alpha = a), t0, t1, tw, StrokeCap.Round)
                    for (k in 0..4) {
                        val f = k / 4f
                        val p = t0 + (t1 - t0) * f
                        drawLine(pal.ink.copy(alpha = a), p, p - chin * (s * 0.16f), tw, StrokeCap.Round)
                    }
                }
            }
        }
    }
    prims.sortedBy { it.z }.forEach { it.draw() }
}

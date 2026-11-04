package com.coachchicken

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coachchicken.core.CardTrack
import com.coachchicken.core.Landmark
import com.coachchicken.core.PlankGrade
import com.coachchicken.core.PoseLog
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private val CGold = Color(0xFFFFB300)
private val CYellow = Color(0xFFFFF176)
private val CGrey = Color(0xFF9E9E9E)
private val CInk = Color(0xFF3B2A20)
private val Violet = Color(0xFFA58CFF)
private const val REPLAY_SPEED = 20.0
/** The space left of the plot in both the timeline and the hip chart, so the two line up; the mascot lives in it. */
private val Gutter = 72.dp
private val MascotWidth = 64.dp
private val ChartRight = 10.dp

private fun fmt(sec: Double): String {
    val s = kotlin.math.floor(abs(sec)).toInt()
    return (if (sec < 0 && s > 0) "−" else "") + "%d:%02d".format(s / 60, s % 60)
}

/** Grey -> yellow -> gold as the (smoothed) grade score goes 0 -> 0.5 -> 1. */
private fun gradeColor(score: Double): Color =
    if (score < 0.5) lerp(CGrey, CYellow, (score * 2).toFloat()) else lerp(CYellow, CGold, ((score - 0.5) * 2).toFloat())

/**
 * The summary card of one plank: a replay timeline with a stopwatch, the skeleton's form with the goal zone, and the
 * hip angle. Without a saved recording (older planks) it shows just the header and the length.
 */
@Composable
fun PlankCardView(data: CardData, modifier: Modifier = Modifier, active: Boolean = true) {
    val rec = data.recording
    val track = remember(rec) { rec?.let { CardTrack(it) } }
    val whenText = remember(data.record) {
        DateTimeFormatter.ofPattern("HH:mm EEE dd MMM ''yy", Locale.getDefault()).format(data.record.startedAt.atZone(ZoneId.systemDefault()))
    }
    Column(
        modifier.fillMaxWidth().border(2.dp, CGold, RoundedCornerShape(26.dp)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (track == null) {
            Tile {
                Header(data.record.completed, whenText, data.record.durationMs / 1000.0)
                Text(
                    "No replay was saved for this plank.",
                    Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            ReplayTiles(track, data, whenText, active)
        }
    }
}

@Composable
private fun Tile(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) { content() }
    }
}

@Composable
private fun Header(completed: Boolean, whenText: String, durationS: Double) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Image(painterResource(R.drawable.ic_chicken), contentDescription = null, modifier = Modifier.size(width = 22.dp, height = 44.dp))
        HeaderText(completed, whenText, durationS, Modifier.weight(1f))
    }
}

/** The title, the date and the duration pill. */
@Composable
private fun HeaderText(completed: Boolean, whenText: String, durationS: Double, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(if (completed) "Plank complete" else "Plank ended early", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(whenText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(shape = CircleShape, color = CGold.copy(alpha = 0.25f)) {
            Text(
                fmt(durationS),
                Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                fontWeight = FontWeight.ExtraBold, color = CGold, textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ColumnScope.ReplayTiles(track: CardTrack, data: CardData, whenText: String, active: Boolean) {
    var pos by remember(track) { mutableDoubleStateOf(0.0) }
    var playing by remember(track) { mutableStateOf(true) }
    var wallMs by remember { mutableLongStateOf(0L) }
    var restart by remember(track) { mutableIntStateOf(0) }
    val stop = track.stopS
    // coming into view (the card becoming the current page) restarts the replay; leaving it pauses
    LaunchedEffect(active, track) {
        if (active) { pos = 0.0; playing = true; restart++ } else playing = false
    }

    LaunchedEffect(track, playing, restart) {
        if (!playing) return@LaunchedEffect
        if (pos >= stop - 0.01) pos = 0.0
        var last = withFrameNanos { it }
        while (pos < stop) {
            val now = withFrameNanos { it }
            pos = min(stop, pos + min((now - last) / 1e9, 0.1) * REPLAY_SPEED)
            last = now
            wallMs = now / 1_000_000
        }
        playing = false
    }
    val i = track.indexAt(pos)
    val inPlank = pos >= 0 && pos <= stop + 1e-6
    val gs = if (inPlank) track.gradeScore[i] else 0.0
    val aura = if (inPlank) (track.aura[i] * ((gs - 0.4) / 0.5).coerceIn(0.0, 1.0)) else 0.0
    // the form sways gently on its own (it can't be turned by touch)
    LaunchedEffect(track) { while (true) withFrameNanos { wallMs = it / 1_000_000 } }
    val swayT = wallMs / 1000.0
    val yaw = (0.32 * sin(swayT * 0.8)).toFloat()
    val pitch = (0.1 * sin(swayT * 0.55 + 1.0)).toFloat()
    val scrub: (Double) -> Unit = { playing = false; pos = it }

    Tile {
        // the mascot fills the left gutter, the full height of the tile; the plot to its right lines up with the hip chart's
        Row(Modifier.height(IntrinsicSize.Min)) {
            Image(
                painterResource(R.drawable.ic_chicken), contentDescription = null,
                modifier = Modifier.width(MascotWidth).fillMaxHeight(), contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.width(Gutter - MascotWidth))
            Column(Modifier.weight(1f)) {
                HeaderText(data.record.completed, whenText, stop)
                TimelineBar(track, pos, playing, wallMs, scrub)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          // the clock is centred in the space left of the button, so the two never overlap
          Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    fmt(min(pos, stop)),
                    fontSize = 46.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold,
                    color = if (pos < 0) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    else if (pos >= 60.0 - 0.01) CGold else MaterialTheme.colorScheme.onSurface,
                )
                Text("/ ${fmt(track.rec.goalMs / 1000.0)}", Modifier.padding(bottom = 8.dp), fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
            OutlinedButton(
                onClick = { playing = !playing },
                Modifier.size(44.dp), shape = CircleShape, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) { TransportIcon(if (playing) 0 else if (pos >= stop - 0.01) 2 else 1, MaterialTheme.colorScheme.onSurface) }
        }
            }
        }
    }
    Tile {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Form", fontWeight = FontWeight.ExtraBold)
            Text(
                if (pos > stop) "after the plank" else if (pos < 0) "before the plank" else if (gs > 0.75) "perfect" else if (gs > 0.3) "good" else "not a plank",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FormCanvas(track, pos, gs, aura, wallMs, yaw, pitch)
    }
    Tile {
        val shown = track.hip.getOrNull(i).takeIf { it != null && !it.isNaN() }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Hip angle", fontWeight = FontWeight.ExtraBold)
            Text(shown?.let { "${it.roundToInt()}°" } ?: "–", fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val s = track.stats
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatChip("avg", s.avgHipDeg?.let { "${it.roundToInt()}°" } ?: "–", Modifier.weight(1f))
            StatChip("range", if (s.minHipDeg != null) "${s.minHipDeg!!.roundToInt()}–${s.maxHipDeg!!.roundToInt()}°" else "–", Modifier.weight(1f))
            StatChip("perfect", "${(s.perfectFraction * 100).roundToInt()}%", Modifier.weight(1f))
            StatChip("best streak", fmt(s.bestStreakMs / 1000.0), Modifier.weight(1f))
        }
        HipChart(track, pos, scrub)
    }
}

/** Pause (0), play (1) or replay (2), drawn so it stays crisp at any size. */
@Composable
private fun TransportIcon(kind: Int, color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val w = size.width; val h = size.height
        when (kind) {
            0 -> {
                drawRoundRect(color, Offset(w * 0.2f, h * 0.15f), Size(w * 0.2f, h * 0.7f), CornerRadius(2.dp.toPx()))
                drawRoundRect(color, Offset(w * 0.6f, h * 0.15f), Size(w * 0.2f, h * 0.7f), CornerRadius(2.dp.toPx()))
            }
            1 -> drawPath(Path().apply { moveTo(w * 0.28f, h * 0.15f); lineTo(w * 0.88f, h * 0.5f); lineTo(w * 0.28f, h * 0.85f); close() }, color)
            else -> {
                val stroke = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round)
                drawArc(color, startAngle = -60f, sweepAngle = 300f, useCenter = false, topLeft = Offset(w * 0.12f, h * 0.12f), size = Size(w * 0.76f, h * 0.76f), style = stroke)
                // arrowhead at the arc's start (upper right), pointing along the direction of travel
                drawPath(Path().apply { moveTo(w * 0.86f, h * 0.06f); lineTo(w * 0.86f, h * 0.36f); lineTo(w * 0.58f, h * 0.24f); close() }, color)
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)) {
        Column(Modifier.padding(vertical = 7.dp, horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** Lets a finger tap or drag along a chart: [padPx] is the unused margin each side, mapping x to a time in the track. */
private fun Modifier.scrubbable(track: CardTrack, padLeftPx: Float, padRightPx: Float, onScrub: (Double) -> Unit): Modifier =
    pointerInput(track) {
        fun timeAt(x: Float): Double {
            val f = ((x - padLeftPx) / max(1f, size.width - padLeftPx - padRightPx)).coerceIn(0f, 1f)
            return track.startS + f * (track.endS - track.startS)
        }
        detectTapGestures { onScrub(timeAt(it.x)) }
    }.pointerInput(track) {
        fun timeAt(x: Float): Double {
            val f = ((x - padLeftPx) / max(1f, size.width - padLeftPx - padRightPx)).coerceIn(0f, 1f)
            return track.startS + f * (track.endS - track.startS)
        }
        // horizontal drags only: a vertical swipe on a chart passes through to the card pager
        detectHorizontalDragGestures { change, _ -> change.consume(); onScrub(timeAt(change.position.x)) }
    }

/**
 * Shows [content] at its natural size, or scaled down (from the top centre) when it would be taller than the space it is
 * given, so the whole card, including its bottom border, is always visible.
 */
@Composable
fun FitHeight(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val child = measurables.first().measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        val avail = constraints.maxHeight
        val scale = if (avail != Constraints.Infinity && child.height > avail) avail.toFloat() / child.height else 1f
        layout(child.width, if (avail != Constraints.Infinity) min(child.height, avail) else child.height) {
            child.placeWithLayer(0, 0) { scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0.5f, 0f) }
        }
    }
}

private fun frac(track: CardTrack, t: Double): Float =
    if (track.endS <= track.startS) 0f else ((t - track.startS) / (track.endS - track.startS)).coerceIn(0.0, 1.0).toFloat()

@Composable
private fun TimelineBar(track: CardTrack, pos: Double, playing: Boolean, wallMs: Long, onScrub: (Double) -> Unit) {
    val tm = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val density = androidx.compose.ui.platform.LocalDensity.current
    val padL = 0f
    val padR = with(density) { ChartRight.toPx() }
    Canvas(Modifier.fillMaxWidth().height(64.dp).scrubbable(track, padL, padR, onScrub)) {
        val stop = track.stopS
        val x0 = padL; val x1 = size.width - padR
        fun xOf(t: Double) = x0 + (x1 - x0) * frac(track, t)
        val barTop = 24.dp.toPx(); val barH = 18.dp.toPx()
        val bar = Path().apply { addRoundRect(RoundRect(x0, barTop, x1, barTop + barH, CornerRadius(barH / 2))) }
        drawPath(bar, muted.copy(alpha = 0.2f))
        clipPath(bar) {
            // outside the plank: dim; inside: coloured by grade, revealed as the playhead moves
            for (k in 0 until track.n) {
                val a = track.t[k]; val b = if (k < track.n - 1) track.t[k + 1] else track.endS
                if (b <= a) continue
                val inside = a >= 0 && a < stop
                if (!inside) drawRect(muted.copy(alpha = 0.3f), Offset(xOf(a), barTop), Size(max(0.6f, xOf(b) - xOf(a) + 0.6f), barH))
            }
            clipRect(left = xOf(0.0), right = xOf(min(pos, stop)) + if (pos >= stop) 3f else 0f) {
                for (k in 0 until track.n) {
                    val a = track.t[k]; if (a < 0 || a >= stop) continue
                    val b = min(if (k < track.n - 1) track.t[k + 1] else track.endS, stop)
                    drawRect(gradeColor(track.gradeScore[k]), Offset(xOf(a), barTop), Size(max(0.6f, xOf(b) - xOf(a) + 0.6f), barH))
                }
            }
            drawRect(Color.White.copy(alpha = 0.18f), Offset(x0, barTop + 2.dp.toPx()), Size(x1 - x0, 4.dp.toPx()))
        }
        val goalS = track.rec.goalMs / 1000.0
        var s = 10
        while (s < stop - 1) { // a dot every 10 s that lights up as the playhead passes
            if (abs(s - goalS) >= 1) drawCircle(if (pos >= s) Color.White else surface, 2.6.dp.toPx(), Offset(xOf(s.toDouble()), barTop + barH / 2), alpha = 0.8f)
            s += 10
        }
        if (goalS <= track.endS) { // the goal egg
            val c = Offset(xOf(goalS), barTop - 12.dp.toPx())
            val egg = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c.x - 7.dp.toPx(), c.y - 10.dp.toPx(), c.x + 7.dp.toPx(), c.y + 9.dp.toPx())) }
            if (stop >= goalS) drawPath(egg, CGold) else drawPath(egg, CGold, style = Stroke(1.8.dp.toPx()))
        }
        drawLine(muted, Offset(xOf(0.0), barTop - 6.dp.toPx()), Offset(xOf(0.0), barTop + barH + 4.dp.toPx()), 1.2.dp.toPx())
        drawLine(muted, Offset(xOf(stop), barTop - 6.dp.toPx()), Offset(xOf(stop), barTop + barH + 4.dp.toPx()), 1.2.dp.toPx())
        label(tm, "start", Offset(xOf(0.0), barTop + barH + 6.dp.toPx()), muted, rightAlign = false)
        label(tm, "up ${fmt(stop)}", Offset(xOf(stop), barTop + barH + 6.dp.toPx()), muted, rightAlign = true)
        val c = Offset(xOf(pos.coerceIn(track.startS, track.endS)), barTop + barH / 2)
        if (pos >= 0 && pos <= stop + 1e-6) { // the sparkle, spinning gently while it is in the plank
            drawCircle(CGold.copy(alpha = 0.25f), 14.dp.toPx(), c)
            drawStar(c, 13.dp.toPx(), Color(0xFFFFF6C8))
        } else { // a grey diamond before the start and after the stop
            val r = 8.dp.toPx()
            val d = Path().apply { moveTo(c.x, c.y - r); lineTo(c.x + r, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r, c.y); close() }
            drawPath(d, CGrey)
            drawPath(d, surface, style = Stroke(2.dp.toPx()))
        }
    }
}

private fun DrawScope.label(tm: TextMeasurer, text: String, at: Offset, color: Color, rightAlign: Boolean) {
    val m = tm.measure(text, TextStyle(fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = color))
    drawText(m, topLeft = Offset(if (rightAlign) at.x - m.size.width else at.x, at.y))
}

@Composable
private fun FormCanvas(track: CardTrack, pos: Double, gs: Double, aura: Double, wallMs: Long, yaw: Float, pitch: Float) {
    val rec = track.rec
    // frame the skeleton once, from where the body was during the plank
    val fit = remember(track) {
        val xs = ArrayList<Double>(); val ys = ArrayList<Double>()
        for (k in 0 until track.n) {
            if (track.t[k] < 0 || track.t[k] > track.stopS) continue
            val j = track.joints[k] ?: continue
            for (q in 0 until 13) {
                val x = j[2 * q]; val y = j[2 * q + 1]
                if (x.isNaN() || y.isNaN()) continue
                xs += if (rec.mirror) rec.aspect - x * rec.aspect else x * rec.aspect
                ys += y
            }
        }
        fun pct(v: List<Double>, f: Double): Double = v.sorted().let { if (it.isEmpty()) 0.0 else it[((it.size - 1) * f).toInt()] }
        doubleArrayOf(pct(xs, 0.02), pct(xs, 0.98), pct(ys, 0.02), pct(ys, 0.98))
    }
    Canvas(Modifier.fillMaxWidth().aspectRatio(2.4f).padding(top = 6.dp)) {
        val w = size.width; val h = size.height
        val bw = max(0.05, fit[1] - fit[0]); val bh = max(0.05, fit[3] - fit[2])
        val s = min(w * 0.8 / bw, h * 0.6 / bh).toFloat()
        val ox = (w / 2 - s * (fit[0] + fit[1]) / 2).toFloat(); val oy = (h / 2 - s * (fit[2] + fit[3]) / 2).toFloat()
        val i = track.indexAt(pos)
        val a = track.joints.getOrNull(i) ?: (0 until i).reversed().firstNotNullOfOrNull { track.joints[it] } ?: return@Canvas
        val b = track.joints.getOrNull(min(i + 1, track.n - 1)) ?: a
        val f = if (i < track.n - 1 && track.t[i + 1] > track.t[i]) ((pos - track.t[i]) / (track.t[i + 1] - track.t[i])).coerceIn(0.0, 1.0) else 0.0
        val lms = MutableList(33) { Landmark(0f, 0f, 0f) }
        PoseLog.LOGGED.forEachIndexed { q, idx ->
            val x0 = a[2 * q]; val y0 = a[2 * q + 1]
            if (x0.isNaN() || y0.isNaN()) return@forEachIndexed
            val x1 = b[2 * q].takeIf { !it.isNaN() } ?: x0; val y1 = b[2 * q + 1].takeIf { !it.isNaN() } ?: y0
            lms[idx] = Landmark((x0 + (x1 - x0) * f).toFloat(), (y0 + (y1 - y0) * f).toFloat(), 1f)
        }
        val inPlank = pos >= 0 && pos <= track.stopS + 1e-6
        val streak = if (inPlank) track.streak[i].toFloat() else 0f
        val grade = if (gs > 0.75) PlankGrade.PERFECT else if (gs > 0.3) PlankGrade.OK else PlankGrade.NOT_PLANK
        val pose = PoseUi(lms, rec.aspect, grade, 0f, aura = aura.toFloat(), streak = streak, judgedSide = rec.target?.side, target = rec.target)
        drawPoseScene(pose, wallMs, baseColor = gradeColor(gs), heightPx = s, yaw = yaw, pitch = pitch) { x, y ->
            Offset(ox + (if (rec.mirror) 1 - x else x) * rec.aspect * s, oy + y * s)
        }
    }
}

@Composable
private fun HipChart(track: CardTrack, pos: Double, onScrub: (Double) -> Unit) {
    val tm = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val density = androidx.compose.ui.platform.LocalDensity.current
    val padL = with(density) { Gutter.toPx() }; val padR = with(density) { ChartRight.toPx() }
    Canvas(Modifier.fillMaxWidth().height(140.dp).scrubbable(track, padL, padR, onScrub)) {
        val top = 8.dp.toPx(); val bottom = size.height - 20.dp.toPx()
        val lo = 150f; val hi = 200f
        fun xOf(t: Double) = padL + (size.width - padL - padR) * frac(track, t)
        fun yOf(v: Double) = top + (bottom - top) * (1 - (v.toFloat().coerceIn(lo, hi) - lo) / (hi - lo))
        drawRect(CGold.copy(alpha = 0.2f), Offset(padL, yOf(200.0)), Size(size.width - padL - padR, yOf(160.0) - yOf(200.0)))
        drawRect(CYellow.copy(alpha = 0.16f), Offset(padL, yOf(160.0)), Size(size.width - padL - padR, yOf(150.0) - yOf(160.0)))
        listOf(150, 160, 180, 200).forEach {
            drawLine(muted.copy(alpha = 0.25f), Offset(padL, yOf(it.toDouble())), Offset(size.width - padR, yOf(it.toDouble())), 1f)
            val m = tm.measure("$it°", TextStyle(fontSize = 9.5.sp, fontFamily = FontFamily.Monospace, color = muted))
            drawText(m, topLeft = Offset(padL - 6.dp.toPx() - m.size.width, yOf(it.toDouble()) - m.size.height / 2f))
        }
        var s = ceil(track.startS / 10).toInt() * 10
        while (s <= track.endS) {
            val m = tm.measure(fmt(s.toDouble()), TextStyle(fontSize = 9.5.sp, fontFamily = FontFamily.Monospace, color = muted))
            drawText(m, topLeft = Offset(xOf(s.toDouble()) - m.size.width / 2f, size.height - 16.dp.toPx()))
            s += 10
        }
        drawRect(muted.copy(alpha = 0.16f), Offset(padL, top), Size(xOf(0.0) - padL, bottom - top))
        drawRect(muted.copy(alpha = 0.16f), Offset(xOf(track.stopS), top), Size(size.width - padR - xOf(track.stopS), bottom - top))
        val path = Path(); var pen = false
        for (k in 0 until track.n) {
            val v = track.hip[k]
            if (v.isNaN()) { pen = false; continue }
            if (pen) path.lineTo(xOf(track.t[k]), yOf(v)) else { path.moveTo(xOf(track.t[k]), yOf(v)); pen = true }
        }
        drawPath(path, muted.copy(alpha = 0.45f), style = Stroke(1.5.dp.toPx()))
        val cx = xOf(pos.coerceIn(track.startS, track.endS))
        clipRect(left = 0f, right = cx) { drawPath(path, ink, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round)) }
        drawLine(Violet, Offset(cx, top), Offset(cx, bottom), 1.5.dp.toPx())
        val v = track.hip.getOrNull(track.indexAt(pos))
        if (v != null && !v.isNaN()) drawCircle(Violet, 4.5.dp.toPx(), Offset(cx, yOf(v)))
    }
}

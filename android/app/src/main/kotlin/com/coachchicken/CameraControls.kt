package com.coachchicken

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Camera-app controls for the session screen: round grey buttons, a white ring shutter with a red record dot, a red
// timer pill, a stop capsule and a vertical mode strip, laid out the way a phone's own camera lays them out in
// landscape, so what each one does is obvious without reading it.

private val CamGrey = Color(0x99474747)
private val CamRed = Color(0xFFE5533D)

/** A round translucent button with [icon] drawn inside, like the flip and gallery buttons of a camera app. */
@Composable
fun CameraRoundButton(onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 56.dp, icon: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier.size(size).scale(if (pressed) 0.92f else 1f).clip(CircleShape).background(CamGrey)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { icon() }
}

/** The record button: a white ring with a red dot, which squashes a little when pressed. */
@Composable
fun ShutterButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier.size(88.dp).clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(88.dp).clip(CircleShape).border(4.dp, Color.White, CircleShape))
        Box(Modifier.size(if (pressed) 52.dp else 66.dp).clip(CircleShape).background(CamRed))
    }
}

/** The stop control while recording: a grey capsule with a white square. */
@Composable
fun StopCapsule(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(width = 72.dp, height = 72.dp).clip(RoundedCornerShape(36.dp)).background(CamGrey)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Box(Modifier.size(26.dp).clip(RoundedCornerShape(5.dp)).background(Color.White)) }
}

/** The elapsed-time pill of a recording: red with white digits (gold once the goal is reached). */
@Composable
fun RecordPill(text: String, reached: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(if (reached) Color(0xFFFFB300) else CamRed).padding(horizontal = 22.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = if (reached) Color(0xFF2B1B00) else Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** A hint over the picture: a rounded, translucent black pill, like a camera app's tips. */
@Composable
fun HintPill(text: String, modifier: Modifier = Modifier) {
    Text(
        text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium,
        modifier = modifier.clip(RoundedCornerShape(20.dp)).background(Color(0x99000000)).padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** The mode strip at the screen edge: the exercises, written upwards, with the current one bright and bold. */
@Composable
fun ModeStrip(names: List<String>, selected: Int, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        names.forEachIndexed { i, name ->
            Text(
                name.uppercase(), color = if (i == selected) Color.White else Color.White.copy(alpha = 0.55f),
                fontSize = 15.sp, fontWeight = if (i == selected) FontWeight.Black else FontWeight.Medium, letterSpacing = 1.sp,
                modifier = Modifier.padding(vertical = 14.dp).vertical(),
            )
        }
    }
}

/** Lays text out sideways, reading from the bottom up. */
private fun Modifier.vertical(): Modifier = this.layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(p.height, p.width) { p.place(-(p.width / 2 - p.height / 2), -(p.height / 2 - p.width / 2)) }
}.rotate(-90f)

/** Two curved arrows chasing each other: switch camera. */
@Composable
fun FlipIcon(modifier: Modifier = Modifier) {
    Canvas(modifier.size(30.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.width * 0.34f
        val stroke = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round)
        val topLeft = Offset(c.x - r, c.y - r)
        for (start in listOf(205f, 25f)) {
            drawArc(Color.White, startAngle = start, sweepAngle = 125f, useCenter = false, topLeft = topLeft, size = Size(2 * r, 2 * r), style = stroke)
            // an arrowhead at the end of the arc, pointing along it
            val a = ((start + 125f) * PI / 180).toFloat()
            val tip = Offset(c.x + r * cos(a), c.y + r * sin(a))
            val t = Offset(-sin(a), cos(a)); val n = Offset(cos(a), sin(a))
            val h = 5.5.dp.toPx()
            val head = Path().apply {
                moveTo(tip.x + t.x * h, tip.y + t.y * h)
                lineTo(tip.x + n.x * h * 0.9f, tip.y + n.y * h * 0.9f)
                lineTo(tip.x - n.x * h * 0.9f, tip.y - n.y * h * 0.9f)
                close()
            }
            drawPath(head, Color.White)
        }
    }
}

/** A cross: close the session. */
@Composable
fun CloseIcon(modifier: Modifier = Modifier) {
    Canvas(modifier.size(22.dp)) {
        val s = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round).width
        drawLine(Color.White, Offset(size.width * 0.1f, size.height * 0.1f), Offset(size.width * 0.9f, size.height * 0.9f), s, StrokeCap.Round)
        drawLine(Color.White, Offset(size.width * 0.9f, size.height * 0.1f), Offset(size.width * 0.1f, size.height * 0.9f), s, StrokeCap.Round)
    }
}

/** A small round "LOG" button: copies the pose log (a development aid). */
@Composable
fun LogButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    CameraRoundButton(onClick, modifier, size = 44.dp) { Text("LOG", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
}

/** Spacer helper so call sites read as a layout. */
@Composable
fun GapW(w: Dp) = Spacer(Modifier.width(w))

package com.coachchicken

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coachchicken.core.CalendarDay
import com.coachchicken.core.clockMark
import kotlin.math.cos
import kotlin.math.sin

private val GoldLight = Color(0xFFFFE27A)
private val GoldDark = Color(0xFFC9900C)

private val dayHeaders = listOf("S", "M", "T", "W", "T", "F", "S")

@Composable
fun CalendarView(weeks: List<List<CalendarDay>>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            dayHeaders.forEach {
                Text(
                    it,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        weeks.forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day -> DayCell(day, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(day: CalendarDay, modifier: Modifier) {
    // Cells are taller than wide so the golden ring reads as an egg.
    Box(modifier.aspectRatio(0.8f).padding(2.dp), contentAlignment = Alignment.Center) {
        if (day.trained) {
            Canvas(Modifier.matchParentSize()) {
                val stroke = 3.dp.toPx()
                drawOval(
                    brush = Brush.verticalGradient(listOf(GoldLight, GoldDark)),
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke),
                )
                // One mark per session, placed like an hour hand on a 12-hour dial just inside the ring.
                val inset = stroke + 5.dp.toPx()
                val rx = size.width / 2 - inset
                val ry = size.height / 2 - inset
                day.sessions.forEach { time ->
                    val mark = clockMark(time)
                    val a = Math.toRadians(mark.angleDegrees.toDouble())
                    val at = Offset(
                        center.x + rx * sin(a).toFloat(),
                        center.y - ry * cos(a).toFloat(),
                    )
                    drawSessionMark(at, mark.isAm)
                }
            }
        }
        Text(
            day.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
            color = when {
                day.isFuture -> MaterialTheme.colorScheme.outlineVariant
                day.isToday -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/** Horizontal line with a half-disc above it for AM (sunrise) or below it for PM (sunset). */
private fun DrawScope.drawSessionMark(at: Offset, isAm: Boolean) {
    val r = 2.5.dp.toPx()
    val line = 1.5.dp.toPx()
    drawArc(
        color = GoldDark,
        startAngle = if (isAm) 180f else 0f, // 180..360 is the top half, 0..180 the bottom half
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(at.x - r, at.y - r),
        size = Size(2 * r, 2 * r),
    )
    drawLine(
        color = GoldDark,
        start = Offset(at.x - r - 1.dp.toPx(), at.y),
        end = Offset(at.x + r + 1.dp.toPx(), at.y),
        strokeWidth = line,
        cap = StrokeCap.Round,
    )
}

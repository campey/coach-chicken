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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coachchicken.core.CalendarDay

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
                    color = Color.Gray,
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
            }
        }
        Text(
            day.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
            color = when {
                day.isFuture -> Color.LightGray
                day.isToday -> MaterialTheme.colorScheme.primary
                else -> Color.DarkGray
            },
        )
    }
}

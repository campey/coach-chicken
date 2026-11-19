package com.coachchicken

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The session's heads-up display, in a game-menu language: slanted tiles, hard offset shadows, bold italic capitals and
// saturated colours that read at a glance across the room.

private val HudNavy = Color(0xFF0B1230)
private val HudBlue = Color(0xFF2338A8)
private val HudCyan = Color(0xFF4FD8FF)
private val HudYellow = Color(0xFFFFE000)
private val HudPink = Color(0xFFFF3D77)

/** A parallelogram: the top edge shifted right by [lean] of the height, like a game-menu tile. */
private val Slanted: Shape = GenericShape { size, _ ->
    val lean = size.height * 0.28f
    moveTo(lean, 0f); lineTo(size.width, 0f); lineTo(size.width - lean, size.height); lineTo(0f, size.height); close()
}

enum class HudStyle { PRIMARY, SECONDARY, STOP }

/** A chunky slanted button: bold italic capitals, a hard shadow underneath, and a little squash when pressed. */
@Composable
fun HudButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, style: HudStyle = HudStyle.SECONDARY, height: Dp = 48.dp) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val (fill, edge, ink) = when (style) {
        HudStyle.PRIMARY -> Triple(HudYellow, Color(0xFFFFF6A0), Color(0xFF14183A))
        HudStyle.STOP -> Triple(HudPink, Color(0xFFFFB3CC), Color.White)
        HudStyle.SECONDARY -> Triple(HudBlue, HudCyan, Color.White)
    }
    Box(
        modifier.height(height).scale(if (pressed) 0.96f else 1f).clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // the hard shadow, then the tile; the text is the child that gives the button its width
        Box(Modifier.matchParentSize().offset(3.dp, 4.dp).clip(Slanted).background(HudNavy))
        Box(Modifier.matchParentSize().clip(Slanted).background(fill).border(BorderStroke(2.dp, edge), Slanted))
        Text(
            label.uppercase(), color = ink, fontSize = if (style == HudStyle.PRIMARY) 18.sp else 15.sp, fontWeight = FontWeight.Black,
            fontStyle = FontStyle.Italic, letterSpacing = 0.8.sp, textAlign = TextAlign.Center, maxLines = 1,
            modifier = Modifier.padding(horizontal = 26.dp),
        )
    }
}

/** The countdown (or the overtime) in a slanted tile with a gold edge once the goal is reached. */
@Composable
fun HudTimer(text: String, reached: Boolean, modifier: Modifier = Modifier) {
    val edge = if (reached) Color(0xFFFFC107) else HudCyan
    Box(modifier.height(64.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize().offset(3.dp, 4.dp).clip(Slanted).background(Color(0xFF05081A)))
        Box(Modifier.matchParentSize().clip(Slanted).background(HudNavy.copy(alpha = 0.92f)).border(BorderStroke(2.dp, edge), Slanted))
        Text(
            text, color = if (reached) Color(0xFFFFD54F) else Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black,
            fontStyle = FontStyle.Italic, maxLines = 1, modifier = Modifier.padding(horizontal = 30.dp),
        )
    }
}

/** A short message over the picture: a translucent slanted tag with a bright bar at its left. */
@Composable
fun HudTag(text: String, modifier: Modifier = Modifier) {
    Row(modifier.clip(Slanted).background(HudNavy.copy(alpha = 0.78f)), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(6.dp).height(40.dp).background(HudCyan))
        Text(
            text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic,
            modifier = Modifier.padding(start = 14.dp, end = 26.dp, top = 9.dp, bottom = 9.dp),
        )
    }
}

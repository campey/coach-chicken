package com.coachchicken

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * After a completed plank: just the card, with a share button and a big Done button pinned at the bottom
 * (the card scrolls only if the screen is too short to show all of it, e.g. in landscape).
 */
@Composable
fun DoneScreen(card: CardData, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    val backdrop = MaterialTheme.colorScheme.surface
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FitHeight(Modifier.weight(1f).fillMaxWidth()) {
            // the card is also recorded into a layer, so the share button can send exactly what is on screen as a picture
            Box(
                Modifier.drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                }.background(backdrop).padding(4.dp),
            ) { PlankCardView(card) }
        }
        Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        runCatching {
                            val bitmap = layer.toImageBitmap().asAndroidBitmap()
                            shareCard(context, bitmap, card)
                        }
                    }
                },
                Modifier.size(56.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) { ShareIcon(MaterialTheme.colorScheme.primary) }
            Button(onDone, Modifier.weight(1f).fillMaxSize(), shape = RoundedCornerShape(16.dp)) {
                Text("DONE", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            }
        }
    }
}

/** An arrow rising out of an open box. */
@Composable
private fun ShareIcon(color: Color) {
    Canvas(Modifier.size(26.dp)) {
        val w = size.width; val h = size.height
        val stroke = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPath(Path().apply { moveTo(w * 0.5f, h * 0.62f); lineTo(w * 0.5f, h * 0.08f) }, color, style = stroke)
        drawPath(Path().apply { moveTo(w * 0.28f, h * 0.28f); lineTo(w * 0.5f, h * 0.06f); lineTo(w * 0.72f, h * 0.28f) }, color, style = stroke)
        drawPath(
            Path().apply { moveTo(w * 0.2f, h * 0.4f); lineTo(w * 0.1f, h * 0.4f); lineTo(w * 0.1f, h * 0.94f); lineTo(w * 0.9f, h * 0.94f); lineTo(w * 0.9f, h * 0.4f); lineTo(w * 0.8f, h * 0.4f) },
            color, style = stroke,
        )
    }
}

/** Writes the card picture to the cache and opens the share sheet with it. */
private suspend fun shareCard(context: Context, bitmap: Bitmap, card: CardData) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        File(dir, "plank.png").also { f -> FileOutputStream(f).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val seconds = card.record.durationMs / 1000
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, "Plank %d:%02d 🥚 Coach Chicken".format(seconds / 60, seconds % 60))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
}

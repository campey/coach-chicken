package com.coachchicken

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import com.coachchicken.core.Scene
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Where the phone is pointing, in degrees: [yaw] is the turn since the session opened (positive = turned right),
 * [pitch] how far it looks above (positive) or below the horizon, [roll] how far the world is tilted on the screen.
 */
data class WorldView(val yaw: Float = 0f, val pitch: Float = 0f, val roll: Float = 0f)

/** Where the sensor and screen facts for the pose log go (set by the view model). */
object SessionDebug { @Volatile var sink: ((String) -> Unit)? = null }

/** Live phone orientation from the rotation sensor (3 degrees of freedom), steering the field behind the skeleton. */
@Composable
fun rememberWorldView(enabled: Boolean): State<WorldView> {
    val context = LocalContext.current
    val view = LocalView.current
    val state = remember { mutableStateOf(WorldView()) }
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        var heading0: Float? = null
        var pitch0: Float? = null
        var smooth = WorldView()
        var lastLog = 0L
        val r = FloatArray(9)
        // moves [cur] towards [target]: ignores tiny sensor noise, and otherwise eases so the scene never twitches
        fun ease(cur: Float, target: Float): Float {
            val d = target - cur
            return if (kotlin.math.abs(d) < 0.35f) cur else cur + (d - 0.35f * kotlin.math.sign(d)) * 0.08f
        }
        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) = Unit
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(r, e.values)
                // the way the screen looks (into the screen, like looking through a window), in east/north/up
                val de = -r[2]; val dn = -r[5]; val du = -r[8]
                // which way is up, in the phone's own axes, then in the screen's axes for the current rotation
                val ux = r[6]; val uy = r[7]
                val (sx, sy) = when (view.display?.rotation ?: Surface.ROTATION_0) {
                    Surface.ROTATION_90 -> -uy to ux
                    Surface.ROTATION_180 -> -ux to -uy
                    Surface.ROTATION_270 -> uy to -ux
                    else -> ux to uy
                }
                val heading = Math.toDegrees(atan2(de, dn).toDouble()).toFloat()
                val h0 = heading0 ?: heading.also { heading0 = it }
                var yaw = heading - h0
                while (yaw > 180f) yaw -= 360f
                while (yaw < -180f) yaw += 360f
                // pitch is measured from where the phone was propped when the session opened (a propped phone leans back,
                // which would otherwise push the whole horizon off the screen)
                val pitchAbs = Math.toDegrees(asin(du.coerceIn(-1f, 1f)).toDouble()).toFloat()
                val p0 = pitch0 ?: pitchAbs.also { pitch0 = it }
                val pitch = (pitchAbs - p0).coerceIn(-20f, 20f)
                val rollRaw = Math.toDegrees(atan2(sx, sy).toDouble()).toFloat()
                val roll = rollRaw.coerceIn(-20f, 20f)
                smooth = WorldView(ease(smooth.yaw, yaw.coerceIn(-60f, 60f)), ease(smooth.pitch, pitch), ease(smooth.roll, roll))
                if (smooth != state.value) state.value = smooth
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastLog > 500) {
                    lastLog = now
                    SessionDebug.sink?.invoke(
                        "VIEW rot=${view.display?.rotation} up_dev=%.2f,%.2f,%.2f screen_up=%.2f,%.2f ".format(r[6], r[7], r[8], sx, sy) +
                            "yaw=%.0f pitch=%.0f(abs %.0f) roll=%.0f".format(smooth.yaw, smooth.pitch, pitchAbs, smooth.roll),
                    )
                }
            }
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
    }
    return state
}

// ---- the stages of the start sequence, from one progress number ----------------------------------------------------

private fun clamp01(v: Float) = v.coerceIn(0f, 1f)
private fun easeOut(t: Float) = 1f - (1f - t) * (1f - t) * (1f - t)

/** progress 0 = looking for you, 0.2 = found, then the camera bounces and zooms away, and 1 = just the avatar in the field. */
internal class StartStages(progress: Float) {
    /** 0..1: the frame has locked on (turned green). */
    val found = clamp01(progress / 0.12f)
    /** The camera picture's scale: a dip, a pop, then a fast zoom at the viewer. */
    val cameraScale: Float = run {
        val u = clamp01((progress - 0.18f) / 0.4f)
        when {
            progress < 0.18f -> 1f
            u < 0.22f -> 1f - 0.07f * easeOut(u / 0.22f)
            u < 0.45f -> 0.93f + 0.17f * easeOut((u - 0.22f) / 0.23f)
            else -> 1.10f + 4.9f * Math.pow(clamp01((u - 0.45f) / 0.55f).toDouble(), 2.2).toFloat()
        }
    }
    val cameraAlpha = 1f - clamp01((progress - 0.3f) / 0.22f)
    /** The dark layer, dashed frame and crosshair. */
    val overlayAlpha = 1f - clamp01((progress - 0.16f) / 0.14f)
    val worldAlpha = clamp01((progress - 0.28f) / 0.25f)
    /** 0..1: the skeleton has become part of the world (follows the phone's orientation). */
    val avatar = clamp01((progress - 0.4f) / 0.4f)
}

private val Found = Color(0xFF5FD078)

/**
 * The dark layer over the camera while it looks for you: a dashed rounded frame and crosshair in the middle, and
 * everything outside the frame fading quickly to black. The frame turns solid green when [found] reaches 1.
 */
@Composable
internal fun PositionOverlay(found: Float, nowSec: () -> Float, modifier: Modifier = Modifier, frameAspect: Float = 4f / 3f) {
    Canvas(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val w = size.width; val h = size.height
        // the camera frame is pinned top-left; the dashed target sits in the middle of it
        val frameW = if (w / h > frameAspect) h * frameAspect else w
        val frameH = if (w / h > frameAspect) h else w / frameAspect
        val fw = frameW * 0.78f
        val fh = frameH * 0.5f
        val tl = Offset((frameW - fw) / 2, (frameH - fh) / 2 + frameH * 0.04f)
        val radius = minOf(fw, fh) * 0.16f
        // the dark layer, with a feathered hole where the frame is
        drawRect(Color.Black.copy(alpha = 0.42f)) // a viewfinder: the picture shows across the whole screen, a little dimmed
        val steps = 18
        val feather = minOf(w, h) * 0.05f
        for (i in 0..steps) {
            val grow = feather * (1f - i / steps.toFloat())
            drawRoundRect(
                Color.Black, Offset(tl.x - grow, tl.y - grow), Size(fw + 2 * grow, fh + 2 * grow),
                CornerRadius(radius + grow), alpha = 0.9f / (steps + 1) * 1.6f, blendMode = BlendMode.DstOut,
            )
        }
        val c = Color.White.lerpTo(Found, found)
        val stroke = 2.5.dp.toPx()
        val dash = 9.dp.toPx()
        val effect = if (found < 0.5f) PathEffect.dashPathEffect(floatArrayOf(dash, dash * 0.9f), (nowSec() * 18.dp.toPx()) % (dash * 1.9f)) else null
        val pulse = 1f + 0.035f * sin(found * PI.toFloat())
        scale(pulse, pulse, Offset(tl.x + fw / 2, tl.y + fh / 2)) {
            drawRoundRect(c.copy(alpha = 0.92f), tl, Size(fw, fh), CornerRadius(radius), style = Stroke(stroke, cap = StrokeCap.Round, pathEffect = effect))
            val cx = tl.x + fw / 2; val cy = tl.y + fh / 2; val gap = 8.dp.toPx(); val arm = 14.dp.toPx()
            val cc = c.copy(alpha = 0.6f + 0.35f * found)
            drawLine(cc, Offset(cx, cy - gap - arm), Offset(cx, cy - gap), stroke, StrokeCap.Round)
            drawLine(cc, Offset(cx, cy + gap), Offset(cx, cy + gap + arm), stroke, StrokeCap.Round)
            drawLine(cc, Offset(cx - gap - arm, cy), Offset(cx - gap, cy), stroke, StrokeCap.Round)
            drawLine(cc, Offset(cx + gap, cy), Offset(cx + gap + arm, cy), stroke, StrokeCap.Round)
        }
    }
}

private fun Color.lerpTo(o: Color, f: Float) = androidx.compose.ui.graphics.lerp(this, o, f)

// ---- the field ------------------------------------------------------------------------------------------------------

private const val WORLD_PERIOD = 2600f
private val TREE_X = FloatArray(30) { rnd(it * 7 + 1) * WORLD_PERIOD }
private val TREE_D = FloatArray(30) { 0.35f + 0.6f * rnd(it * 7 + 2) }
private val TREE_S = FloatArray(30) { 0.7f + 0.6f * rnd(it * 7 + 3) }
private val TREE_K = FloatArray(30) { rnd(it * 7 + 4) }
private val FIELD_PALETTE = listOf(0xFFE0C15F, 0xFF8FB85A, 0xFFC9A64A, 0xFF6EA24E, 0xFFA8794A, 0xFF9BC26A).map { Color(it) }

/**
 * A field with a barn, trees and crops under a daytime sky, steered by the phone's [view]: yaw pans it (near things
 * move more than far ones), pitch moves the horizon, roll tilts it. Everything is sized from the canvas so it works
 * in either orientation.
 */
internal fun DrawScope.drawFieldWorld(view: WorldView, nowSec: Float) {
    val w = size.width; val h = size.height
    val k = h / 680f // the scene was drawn for a 680-pixel-high canvas
    val hfov = if (w > h) 80f else 55f
    val vfov = if (w > h) 38f else 70f
    val pxDegX = w / hfov
    val pxDegY = h / vfov
    val yawPx = view.yaw * pxDegX
    val hz = h * 0.36f + view.pitch * pxDegY
    fun sx(x: Float, d: Float): Float = (((x * k - yawPx * d) % (WORLD_PERIOD * k)) + WORLD_PERIOD * k) % (WORLD_PERIOD * k) - 320f * k
    rotate(view.roll, Offset(w / 2, h / 2)) {
        scale(1f, 1f, Offset(w / 2, h / 2)) { // (no overscan: the scene is drawn wider than the screen, so a tilt shows no gaps)
            // sky and sun
            drawRect(
                Brush.verticalGradient(listOf(Color(0xFF3B6DB4), Color(0xFF9CC4EA), Color(0xFFFFE3B0)), startY = hz - h * 0.75f, endY = hz),
                Offset(-w, -2 * h), Size(3 * w, hz + 2 * h),
            )
            val sunX = sx(WORLD_PERIOD * 0.6f, 0.04f); val sunY = hz - h * 0.1f
            drawCircle(Brush.radialGradient(listOf(Color(0xF2FFF0BE), Color(0x80FFE196), Color(0x00FFE196)), Offset(sunX, sunY), 260f * k), 260f * k, Offset(sunX, sunY))
            for (i in 0 until 10) {
                val d = 0.05f + 0.08f * rnd(i * 5 + 12)
                val cx = sx(rnd(i * 5 + 9) * WORLD_PERIOD + nowSec * 14f * d, d)
                val cy = hz - h * (0.12f + (0.04f + 0.2f * rnd(i * 5 + 10)) * 1.4f)
                val s = (0.7f + rnd(i * 5 + 11)) * k
                listOf(Triple(0f, 0f, 46f), Triple(40f, 8f, 36f), Triple(-38f, 10f, 32f), Triple(12f, -14f, 34f)).forEach { (dx, dy, r) ->
                    drawOval(Color.White.copy(alpha = 0.88f), Offset(cx + dx * s - r * s * 1.5f, cy + dy * s - r * s * 0.8f), Size(r * s * 3f, r * s * 1.6f))
                }
            }
            // far hills
            fun hill(y: Float, col: Color, amp: Float, d: Float, ph: Float) {
                val p = Path().apply {
                    moveTo(-w, hz + 30f * k)
                    var x = -w
                    while (x <= 2 * w) {
                        lineTo(x, hz - y * k - amp * k * sin((x - yawPx * d) / (130f * k) + ph) - amp * k * 0.5f * sin((x - yawPx * d) / (53f * k) + ph * 2))
                        x += 24f * k
                    }
                    lineTo(2 * w, hz + 30f * k); close()
                }
                drawPath(p, col)
            }
            hill(14f, Color(0xFF8FA9C8), 22f, 0.12f, 0f); hill(4f, Color(0xFF6F9A86), 16f, 0.22f, 2f)
            // fields: bands that widen towards you, a patchwork of crops with rows
            var yPrev = hz
            val bands = 9
            for (i in 0 until bands) {
                val y1 = hz + h * 0.5f * Math.pow(((i + 1) / bands.toFloat()).toDouble(), 1.7).toFloat()
                val d = 0.22f + i * 0.09f
                val seg = 170f * k * (1 + i * 0.42f)
                var x = -seg * 2
                while (x < w + seg * 2) {
                    val gx = Math.floor(((x + yawPx * d) / seg).toDouble()).toInt()
                    val px = gx * seg - yawPx * d
                    drawRect(FIELD_PALETTE[(rnd(gx * 13 + i * 7) * FIELD_PALETTE.size).toInt().coerceAtMost(FIELD_PALETTE.size - 1)], Offset(px, yPrev), Size(seg + 1f, y1 - yPrev + 1f))
                    var r = 0f
                    while (r < seg) { drawLine(Color(0x293C3214), Offset(px + r, yPrev), Offset(px + r, y1), 1.4f); r += (14f + i * 3f) * k }
                    x += seg
                }
                yPrev = y1
            }
            // the barn
            val bw = 250f * k; val bx = sx(WORLD_PERIOD * 0.3f, 0.5f); val by = hz + 8f * k; val bh = bw * 0.52f
            drawRect(Color(0xFF8C2A22), Offset(bx - bw / 2, by - bh), Size(bw, bh))
            val roof = Path().apply {
                moveTo(bx - bw / 2 - 10 * k, by - bh + 4 * k); lineTo(bx - bw * 0.36f, by - bh - bh * 0.34f); lineTo(bx, by - bh - bh * 0.5f)
                lineTo(bx + bw * 0.36f, by - bh - bh * 0.34f); lineTo(bx + bw / 2 + 10 * k, by - bh + 4 * k); close()
            }
            drawPath(roof, Color(0xFF6E1F19)); drawPath(roof, Color(0xFFF4EAD6), style = Stroke(4f * k))
            drawRect(Color(0xFF4D1410), Offset(bx - 34 * k, by - bh * 0.78f), Size(68 * k, bh * 0.78f))
            drawRect(Color(0xFFF4EAD6), Offset(bx - 34 * k, by - bh * 0.78f), Size(68 * k, bh * 0.78f), style = Stroke(3.5f * k))
            drawLine(Color(0xFFF4EAD6), Offset(bx - 34 * k, by - bh * 0.78f), Offset(bx + 34 * k, by), 3.5f * k)
            drawLine(Color(0xFFF4EAD6), Offset(bx + 34 * k, by - bh * 0.78f), Offset(bx - 34 * k, by), 3.5f * k)
            drawRect(Color(0xF2FFCD64), Offset(bx - 12 * k, by - bh - bh * 0.3f), Size(24 * k, 20 * k))
            // trees, far to near
            for (i in TREE_X.indices.sortedBy { TREE_D[it] }) {
                val x = sx(TREE_X[i], TREE_D[i]); val y = hz + (10f + TREE_D[i] * 46f) * k; val th = (50f + 150f * TREE_D[i]) * TREE_S[i] * k
                drawRect(Color(0xFF5A3D22), Offset(x - th * 0.04f, y - th * 0.42f), Size(th * 0.08f, th * 0.42f))
                val g = listOf(Color(0xFF3F7A36), Color(0xFF4E8A3A), Color(0xFF356A30))[(TREE_K[i] * 3).toInt().coerceAtMost(2)]
                listOf(Triple(0f, -0.62f, 0.3f), Triple(-0.18f, -0.5f, 0.22f), Triple(0.18f, -0.5f, 0.22f), Triple(0f, -0.8f, 0.22f)).forEach { (dx, dy, r) ->
                    drawCircle(g, r * th, Offset(x + dx * th, y + dy * th))
                }
                drawCircle(Color(0x2EFFE696), 0.18f * th, Offset(x + 0.08f * th, y - 0.66f * th))
            }
            // the grass in front, and a fence
            val fy = hz + h * 0.13f
            drawRect(Brush.verticalGradient(listOf(Color(0xFF6AA046), Color(0xFF2B5524)), startY = fy, endY = h * 1.2f), Offset(-w, fy), Size(3 * w, h * 1.2f))
            val post = 84f * k
            var fx = -100f * k
            while (fx < w + 100f * k) {
                val px = Math.floor(((fx + yawPx) / post).toDouble()).toFloat() * post - yawPx
                drawRect(Color(0xFFE9DFC8), Offset(px, fy - 40f * k), Size(9f * k, 52f * k)); fx += post
            }
            drawRect(Color(0xFFE9DFC8), Offset(-w, fy - 28f * k), Size(3 * w, 6f * k))
            drawRect(Color(0xFFE9DFC8), Offset(-w, fy - 6f * k), Size(3 * w, 6f * k))
            drawRect(Brush.verticalGradient(listOf(Color(0x47FFF0C8), Color(0x00FFF0C8)), startY = hz, endY = hz + h * 0.2f), Offset(-w, hz), Size(3 * w, h * 0.2f))
        }
    }
    drawRect(Brush.verticalGradient(listOf(Color(0x0D000000), Color(0x1F000000), Color(0x73000000)), startY = 0f, endY = h)) // keeps the gold skeleton the brightest thing
}

/** Draws the [scene] (the location a plank happens in) steered by [view]. New locations get a branch here. */
internal fun DrawScope.drawScene(scene: Scene, view: WorldView, nowSec: Float) = when (scene) {
    Scene.FARM -> drawFieldWorld(view, nowSec)
}

/** The scene behind everything, shown as the camera clears away. */
@Composable
internal fun SceneWorld(scene: Scene, view: () -> WorldView, nowSec: () -> Float, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawScene(scene, view(), nowSec()) }
}

/**
 * The layers of the session start, driven by one [progress] number (0 looking, 1 avatar only): the field, the camera
 * (given by [camera], which is told how to scale and fade), the dark position layer, and the skeleton on top.
 */
@Composable
internal fun StartSequenceLayers(
    progress: Float, view: () -> WorldView, nowSec: () -> Float, modifier: Modifier = Modifier, scene: Scene = Scene.FARM, positionAlpha: Float = 1f,
    camera: @Composable (Modifier) -> Unit, pose: @Composable (avatar: Float) -> Unit,
) {
    val s = StartStages(progress)
    Box(modifier.background(Color.Black)) {
        if (s.worldAlpha > 0f) SceneWorld(scene, view, nowSec, Modifier.fillMaxSize().graphicsLayer { alpha = s.worldAlpha })
        // one call site, always: moving the camera between two places would rebuild it and restart the camera mid-start
        camera(Modifier.fillMaxSize().graphicsLayer { scaleX = s.cameraScale; scaleY = s.cameraScale; alpha = s.cameraAlpha })
        if (s.overlayAlpha * positionAlpha > 0.01f) PositionOverlay(s.found, nowSec, Modifier.fillMaxSize().graphicsLayer { alpha = s.overlayAlpha * positionAlpha })
        pose(s.avatar)
    }
}

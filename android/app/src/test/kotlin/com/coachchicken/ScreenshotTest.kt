package com.coachchicken

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.coachchicken.core.Streak
import com.coachchicken.core.buildCalendar
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test

class ScreenshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "android:Theme.Material.NoActionBar")

    @Test fun home() {
        val today = LocalDate.of(2026, 11, 4)
        val cal = buildCalendar(today, mapOf(today.minusDays(1) to listOf(LocalTime.of(9, 5)), today.minusDays(2) to listOf(LocalTime.of(18, 30), LocalTime.of(7, 10))))
        paparazzi.snapshot {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { HomeScreen(UiState(calendar = cal, streak = Streak(2, false)), {}, null) {} }
            }
        }
    }

    @Test fun lore() {
        paparazzi.snapshot { MaterialTheme(colorScheme = darkColorScheme()) { Surface(Modifier.fillMaxSize()) { LoreScreen {} } } }
    }

    private fun card(): CardData {
        val text = javaClass.classLoader!!.getResource("run.txt")!!.readText()
        val rec = com.coachchicken.core.PlankRecordingCodec.decode(text)!!
        return CardData(com.coachchicken.core.SessionRecord("plank", rec.startedAt, rec.durationMs, true), rec)
    }

    @Test fun plankCard() {
        paparazzi.snapshot { MaterialTheme(colorScheme = darkColorScheme()) { Surface(Modifier.fillMaxSize()) { androidx.compose.foundation.layout.Box(Modifier.padding(16.dp)) { PlankCardView(card()) } } } }
    }

    /** The first frame of the win beat has the egg at scale 0; that used to crash on a zero-radius gradient. */
    @Test fun winBeat() {
        paparazzi.snapshot { MaterialTheme(colorScheme = darkColorScheme()) { Surface(Modifier.fillMaxSize()) { WinBeat(62_000, com.coachchicken.core.Scene.FARM) {} } } }
    }

    /** The egg at every stage of the pop, including the zero-size start. */
    @Test fun winEggStages() {
        for (p in listOf(0f, 0.02f, 0.1f, 0.2f, 0.6f, 1f)) {
            paparazzi.snapshot(name = "egg${(p * 100).toInt()}") {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) { drawWinEgg(p, 1.3f) }
            }
        }
    }

    /** The win beat's banner once it has slid in, over the field. */
    @Test fun winBanner() {
        paparazzi.snapshot { MaterialTheme(colorScheme = darkColorScheme()) { Surface(Modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                SceneWorld(com.coachchicken.core.Scene.FARM, { WorldView() }, { 1f }, Modifier.fillMaxSize())
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) { drawWinEgg(1f, 1.3f) }
                WinBanner(62_000, Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(bottom = 24.dp))
            }
        } } }
    }

    @Test fun doneScreen() {
        paparazzi.snapshot { MaterialTheme(colorScheme = darkColorScheme()) { Surface(Modifier.fillMaxSize()) { DoneScreen(card()) {} } } }
    }
}

/** A short phone: the calendar drops to fewer weeks instead of scrolling. */
class ShortScreenshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5, theme = "android:Theme.Material.NoActionBar")

    @Test fun homeOnAShortPhone() {
        val today = LocalDate.of(2026, 11, 4)
        val cal = buildCalendar(today, mapOf(today.minusDays(1) to listOf(LocalTime.of(9, 5))))
        paparazzi.snapshot {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { HomeScreen(UiState(calendar = cal, streak = Streak(1, true)), {}, null) {} }
            }
        }
    }
}

/** The session start sequence, frame by frame, in landscape with a stand-in camera feed. */
class StartSequenceScreenshotTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(orientation = com.android.resources.ScreenOrientation.LANDSCAPE, screenWidth = 1920, screenHeight = 1080),
        theme = "android:Theme.Material.NoActionBar",
    )

    private fun pose(): PoseUi {
        val text = javaClass.classLoader!!.getResource("run.txt")!!.readText()
        val rec = com.coachchicken.core.PlankRecordingCodec.decode(text)!!
        val frame = rec.frames.first { it.tMs >= 20_000 && it.joints != null }
        val lms = MutableList(33) { com.coachchicken.core.Landmark(0f, 0f, 0f) }
        com.coachchicken.core.PoseLog.LOGGED.forEachIndexed { q, idx ->
            val x = frame.joints!![2 * q]; val y = frame.joints!![2 * q + 1]
            if (!x.isNaN() && !y.isNaN()) lms[idx] = com.coachchicken.core.Landmark(x, y, 1f)
        }
        return PoseUi(lms, rec.aspect, com.coachchicken.core.PlankGrade.PERFECT, 1f, aura = 0.8f, streak = 0.5f, judgedSide = rec.target?.side, target = rec.target)
    }

    private fun fromPoints(vararg pts: Pair<Int, Pair<Float, Float>>): PoseUi {
        val lms = MutableList(33) { com.coachchicken.core.Landmark(0f, 0f, 0f) }
        pts.forEach { (i, xy) -> lms[i] = com.coachchicken.core.Landmark(xy.first, xy.second, 1f) }
        return PoseUi(lms, 1.3333f, com.coachchicken.core.PlankGrade.OK, 1f, aura = 0f, streak = 0f)
    }

    /** The three form dots on a full plank, a knee plank and all fours (right side), with the capture corner marks. */
    @Test fun formDots() {
        val plank = fromPoints(12 to (0.25f to 0.50f), 14 to (0.25f to 0.62f), 24 to (0.50f to 0.54f), 26 to (0.65f to 0.56f), 28 to (0.80f to 0.58f))
        val knee = fromPoints(12 to (0.25f to 0.50f), 14 to (0.25f to 0.62f), 24 to (0.50f to 0.56f), 26 to (0.68f to 0.62f), 28 to (0.78f to 0.52f))
        val fours = fromPoints(12 to (0.30f to 0.50f), 14 to (0.30f to 0.64f), 24 to (0.60f to 0.50f), 26 to (0.60f to 0.76f), 28 to (0.86f to 0.78f))
        for ((name, pz) in listOf("plank" to plank, "knee" to knee, "fours" to fours)) {
            paparazzi.snapshot(name = name) {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF3A6B30))) {
                    PoseOverlay(pz, mirror = false, modifier = Modifier.fillMaxSize(), avatar = 1f)
                }
            }
        }
    }

    /** The get-into-position screen: the blue ghost template where the player should be, before anyone is found. */
    @Test fun templateGhost() {
        paparazzi.snapshot(name = "ghost") {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFF5B6B58), androidx.compose.ui.graphics.Color(0xFF2B2F2A))))) {
                PoseOverlay(null, mirror = true, modifier = Modifier.fillMaxSize(), templateAlpha = 1f)
            }
        }
    }

    /** The HUD in the get-into-position state and while planking, over a stand-in picture. */
    @Test fun hud() {
        for (planking in listOf(false, true)) {
            paparazzi.snapshot(name = if (planking) "hudPlanking" else "hudReady") {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF3A6B30))) {
                    PoseOverlay(null, mirror = true, modifier = Modifier.fillMaxSize(), avatar = if (planking) 1f else 0f, templateAlpha = if (planking) 0f else 1f)
                    SideHud(
                        planking, false, if (planking) "Planking. Get up or tap the stop button to end early." else "Get into plank position.", 0f, "0:58",
                        {}, {}, {}, {}, {}, listOf("Plank", "More"), 240.dp,
                    )
                }
            }
        }
    }

    @Test fun sequence() {
        val p = pose()
        for (progress in listOf(0f, 0.08f, 0.2f, 0.3f, 0.38f, 0.5f, 1f)) {
            paparazzi.snapshot(name = "p${(progress * 100).toInt()}") {
                StartSequenceLayers(
                    progress, { WorldView(yaw = 5f, pitch = 2f, roll = 3f) }, { 1f }, Modifier.fillMaxSize(),
                    camera = { m ->
                        // the live preview: the whole 4:3 frame, pinned to the left
                        androidx.compose.foundation.layout.Box(m) {
                            androidx.compose.foundation.layout.Box(
                                Modifier.fillMaxHeight().aspectRatio(4f / 3f)
                                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFF5B6B58), androidx.compose.ui.graphics.Color(0xFF2B2F2A)))),
                            )
                        }
                    },
                    pose = { av -> PoseOverlay(p, mirror = true, modifier = Modifier.fillMaxSize(), avatar = av, view = { WorldView(yaw = 5f, pitch = 2f, roll = 3f) }) },
                )
            }
        }
    }
}

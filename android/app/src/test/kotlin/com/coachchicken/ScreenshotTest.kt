package com.coachchicken

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
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

    private fun card(): CardData {
        val text = javaClass.classLoader!!.getResource("run.txt")!!.readText()
        val rec = com.coachchicken.core.PlankRecordingCodec.decode(text)!!
        return CardData(com.coachchicken.core.SessionRecord("plank", rec.startedAt, rec.durationMs, true), rec)
    }

    @Test fun plankCard() {
        paparazzi.snapshot { MaterialTheme(colorScheme = darkColorScheme()) { Surface(Modifier.fillMaxSize()) { androidx.compose.foundation.layout.Box(Modifier.padding(16.dp)) { PlankCardView(card()) } } } }
    }

    @Test fun doneScreen() {
        paparazzi.snapshot { MaterialTheme(colorScheme = darkColorScheme()) { Surface(Modifier.fillMaxSize()) { DoneScreen(card()) {} } } }
    }
}

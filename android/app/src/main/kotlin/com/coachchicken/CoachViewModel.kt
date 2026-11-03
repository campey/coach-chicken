package com.coachchicken

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.coachchicken.core.CalendarDay
import com.coachchicken.core.Cue
import com.coachchicken.core.PlankTimeline
import com.coachchicken.core.SessionRecord
import com.coachchicken.core.buildCalendar
import com.coachchicken.core.completedSessionTimes
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class Phase { IDLE, RUNNING, DONE }

data class UiState(
    val phase: Phase = Phase.IDLE,
    val remainingMs: Long = PlankTimeline.DEFAULT_DURATION_MS,
    val calendar: List<List<CalendarDay>> = emptyList(),
)

class CoachViewModel(app: Application) : AndroidViewModel(app) {
    private val timeline = PlankTimeline()
    private val store = SessionStore(app)
    private val sounds = SoundPlayer(app)

    private val _state = MutableStateFlow(UiState(calendar = loadCalendar()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var ticker: Job? = null
    private var startedAt: Instant = Instant.now()
    private var startedRealtime = 0L

    private fun loadCalendar(): List<List<CalendarDay>> {
        val zone = ZoneId.systemDefault()
        return buildCalendar(LocalDate.now(zone), completedSessionTimes(store.readAll(), zone))
    }

    fun start() {
        if (ticker != null) return
        startedAt = Instant.now()
        startedRealtime = SystemClock.elapsedRealtime()
        _state.value = _state.value.copy(phase = Phase.RUNNING, remainingMs = timeline.durationMs)
        sounds.startQuietLoop()
        ticker = viewModelScope.launch {
            var prev = 0L
            while (isActive) {
                delay(TICK_MS)
                val elapsed = SystemClock.elapsedRealtime() - startedRealtime
                for (cue in timeline.cuesBetween(prev, elapsed)) when (cue) {
                    Cue.LOUD_CLUCK -> sounds.loudCluck()
                    Cue.SQUAWK -> sounds.squawk()
                }
                prev = elapsed
                if (timeline.isComplete(elapsed)) {
                    finish(completed = true, elapsedMs = timeline.durationMs)
                    break
                }
                _state.value = _state.value.copy(remainingMs = timeline.remainingMs(elapsed))
            }
        }
    }

    /** Stop early (button) or because the app left the foreground. Logged as incomplete. */
    fun cancel() {
        if (ticker == null) return
        val elapsed = SystemClock.elapsedRealtime() - startedRealtime
        ticker?.cancel()
        finish(completed = false, elapsedMs = elapsed, goTo = Phase.IDLE)
    }

    fun dismissDone() {
        _state.value = _state.value.copy(phase = Phase.IDLE, remainingMs = timeline.durationMs)
    }

    private fun finish(completed: Boolean, elapsedMs: Long, goTo: Phase = Phase.DONE) {
        ticker = null
        sounds.stopQuietLoop()
        store.append(SessionRecord("plank", startedAt, elapsedMs, completed))
        _state.value = UiState(phase = goTo, remainingMs = timeline.durationMs, calendar = loadCalendar())
    }

    override fun onCleared() {
        sounds.release()
    }

    private companion object {
        const val TICK_MS = 100L
    }
}

package com.coachchicken

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.coachchicken.core.CalendarDay
import com.coachchicken.core.Landmark
import com.coachchicken.core.PlankTracker
import com.coachchicken.core.PoseLog
import com.coachchicken.core.PoseReading
import com.coachchicken.core.readPlank
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class Phase { IDLE, RUNNING, DONE }

/** The latest camera reading, for drawing the skeleton and the status line. */
data class PoseUi(
    val landmarks: List<Landmark>,
    val aspect: Float,
    val reading: PoseReading,
    val holdProgress: Float,
)

data class UiState(
    val phase: Phase = Phase.IDLE,
    val remainingMs: Long = PlankTimeline.DEFAULT_DURATION_MS,
    val calendar: List<List<CalendarDay>> = emptyList(),
    val cameraMode: Boolean = false,
    val frontCamera: Boolean = true,
    val pose: PoseUi? = null,
)

class CoachViewModel(app: Application) : AndroidViewModel(app) {
    private val timeline = PlankTimeline()
    private val store = SessionStore(app)
    private val sounds = SoundPlayer(app)
    private val tracker = PlankTracker()
    private val poseLog = PoseLog()
    private var cameraStartedAt = 0L

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
        poseLog.addEvent(cameraMs(), "SESSION_START")
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

    /**
     * Stop early (button), because the app left the foreground, or because the camera saw you get up.
     * Logged as incomplete. Leaves camera mode unless [keepCamera] (used when the camera ended it).
     */
    fun cancel(keepCamera: Boolean = false, endedAtRealtime: Long? = null) {
        if (ticker == null) {
            if (!keepCamera) exitCamera()
            return
        }
        val elapsed = ((endedAtRealtime ?: SystemClock.elapsedRealtime()) - startedRealtime).coerceAtLeast(0)
        ticker?.cancel()
        finish(completed = false, elapsedMs = elapsed, goTo = Phase.IDLE, keepCamera = keepCamera)
    }

    private fun cameraMs() = SystemClock.elapsedRealtime() - cameraStartedAt

    /** Everything the camera saw since camera mode was last entered, ready to paste. */
    fun poseLogText(): String = poseLog.render()

    fun enterCamera() {
        tracker.reset()
        cameraStartedAt = SystemClock.elapsedRealtime()
        poseLog.clear("camera=${if (_state.value.frontCamera) "front" else "back"} app=${BuildConfig.VERSION_NAME}")
        _state.update { it.copy(cameraMode = true, pose = null) }
    }

    fun exitCamera() {
        _state.update { it.copy(cameraMode = false, pose = null) }
    }

    fun flipCamera() {
        poseLog.addEvent(cameraMs(), "FLIP_CAMERA now=${if (_state.value.frontCamera) "back" else "front"}")
        _state.update { it.copy(frontCamera = !it.frontCamera, pose = null) }
    }

    /** Called from the camera analysis thread for every frame; hops to the main thread. */
    fun onPose(landmarks: List<Landmark>?, aspect: Float) {
        viewModelScope.launch {
            if (!_state.value.cameraMode) return@launch
            val now = SystemClock.elapsedRealtime()
            val reading = if (landmarks == null) PoseReading.NO_BODY else readPlank(landmarks, aspect)
            poseLog.addFrame(cameraMs(), _state.value.phase.name, landmarks, aspect, reading)
            when (tracker.update(now, reading)) {
                PlankTracker.Event.STARTED -> {
                    poseLog.addEvent(cameraMs(), "TRACKER_STARTED")
                    if (_state.value.phase == Phase.IDLE) start()
                }
                PlankTracker.Event.ENDED -> {
                    poseLog.addEvent(cameraMs(), "TRACKER_ENDED")
                    // The plank really ended when you first lost it, not when the grace period ran out.
                    if (_state.value.phase == Phase.RUNNING) cancel(keepCamera = true, endedAtRealtime = tracker.lastEndedLostAtMs)
                }
                null -> Unit
            }
            _state.update { it.copy(pose = PoseUi(landmarks.orEmpty(), aspect, reading, tracker.holdProgress(now))) }
        }
    }

    fun dismissDone() {
        _state.value = _state.value.copy(phase = Phase.IDLE, remainingMs = timeline.durationMs)
    }

    private fun finish(completed: Boolean, elapsedMs: Long, goTo: Phase = Phase.DONE, keepCamera: Boolean = false) {
        ticker = null
        sounds.stopQuietLoop()
        store.append(SessionRecord("plank", startedAt, elapsedMs, completed))
        poseLog.addEvent(cameraMs(), "SESSION_END completed=$completed elapsedMs=$elapsedMs")
        _state.value = UiState(
            phase = goTo,
            remainingMs = timeline.durationMs,
            calendar = loadCalendar(),
            cameraMode = keepCamera,
            frontCamera = _state.value.frontCamera,
        )
    }

    override fun onCleared() {
        sounds.release()
    }

    private companion object {
        const val TICK_MS = 100L
    }
}

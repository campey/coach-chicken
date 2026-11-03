package com.coachchicken

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.coachchicken.core.CalendarDay
import com.coachchicken.core.Cue
import com.coachchicken.core.Landmark
import com.coachchicken.core.PlankTimeline
import com.coachchicken.core.PlankTracker
import com.coachchicken.core.PoseLog
import com.coachchicken.core.PoseReading
import com.coachchicken.core.SessionRecord
import com.coachchicken.core.buildCalendar
import com.coachchicken.core.completedSessionTimes
import com.coachchicken.core.readPlank
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

/** HOME: calendar and Start session. READY: in a session, waiting for the plank. PLANKING: timing. DONE: reached the goal. */
enum class Phase { HOME, READY, PLANKING, DONE }

/** The latest camera reading, for drawing the skeleton and the status line. */
data class PoseUi(
    val landmarks: List<Landmark>,
    val aspect: Float,
    val reading: PoseReading,
    val holdProgress: Float,
)

data class UiState(
    val phase: Phase = Phase.HOME,
    /** Plank time so far (while PLANKING) or the length of the plank just finished (DONE). */
    val elapsedMs: Long = 0,
    val goalMs: Long = PlankTimeline.DEFAULT_DURATION_MS,
    val calendar: List<List<CalendarDay>> = emptyList(),
    val cameraOn: Boolean = false,
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

    private val _state = MutableStateFlow(UiState(goalMs = timeline.durationMs, calendar = loadCalendar()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var ticker: Job? = null
    private var startedAt: Instant = Instant.now()
    private var startedRealtime = 0L

    private fun loadCalendar(): List<List<CalendarDay>> {
        val zone = ZoneId.systemDefault()
        return buildCalendar(LocalDate.now(zone), completedSessionTimes(store.readAll(), zone))
    }

    private fun cameraMs() = SystemClock.elapsedRealtime() - cameraStartedAt

    /** Everything the camera saw in the latest session, ready to paste. */
    fun poseLogText(): String = poseLog.render()

    /** True once the camera has recorded anything this run, so the log can be copied after the session ends. */
    fun hasPoseLog(): Boolean = poseLog.size > 0

    // --- session -------------------------------------------------------------------------

    /** Home -> session. [camera] false when camera permission was refused: the buttons still work. */
    fun openSession(camera: Boolean) {
        if (_state.value.phase != Phase.HOME) return
        tracker.reset()
        cameraStartedAt = SystemClock.elapsedRealtime()
        poseLog.clear("camera=${if (_state.value.frontCamera) "front" else "back"} on=$camera app=${BuildConfig.VERSION_NAME}")
        _state.update { it.copy(phase = Phase.READY, elapsedMs = 0, cameraOn = camera, pose = null) }
    }

    /** Leave the session without planking. */
    fun closeSession() {
        if (_state.value.phase != Phase.READY) return
        tracker.reset()
        _state.update { it.copy(phase = Phase.HOME, cameraOn = false, pose = null) }
    }

    fun flipCamera() {
        poseLog.addEvent(cameraMs(), "FLIP_CAMERA now=${if (_state.value.frontCamera) "back" else "front"}")
        _state.update { it.copy(frontCamera = !it.frontCamera, pose = null) }
    }

    /** Start timing: the button, or the camera seeing a held plank. */
    fun startPlank() {
        if (_state.value.phase != Phase.READY) return
        startedAt = Instant.now()
        startedRealtime = SystemClock.elapsedRealtime()
        poseLog.addEvent(cameraMs(), "PLANK_START")
        // Button start while the camera is on: let the tracker know, so getting up is still noticed.
        if (_state.value.cameraOn && !tracker.active) tracker.startedExternally()
        sounds.startQuietLoop()
        _state.update { it.copy(phase = Phase.PLANKING, elapsedMs = 0) }
        ticker = viewModelScope.launch {
            var prev = 0L
            while (isActive) {
                delay(TICK_MS)
                val elapsed = SystemClock.elapsedRealtime() - startedRealtime
                for (cue in timeline.cuesBetween(prev, elapsed)) when (cue) {
                    Cue.LOUD_CLUCK -> sounds.loudCluck()
                    Cue.SQUAWK -> {
                        sounds.stopQuietLoop() // goal reached; the plank carries on in silence
                        sounds.squawk()
                    }
                }
                prev = elapsed
                _state.update { it.copy(elapsedMs = elapsed) }
            }
        }
    }

    /**
     * End the plank: the Stop/Done button, the camera seeing you get up, or the app leaving the foreground.
     * Reaching the goal counts (-> DONE); ending earlier is logged as incomplete and returns to READY.
     * [endedAtRealtime] back-dates the end to when the plank was last held (camera-detected end).
     */
    fun endPlank(endedAtRealtime: Long? = null) {
        if (_state.value.phase != Phase.PLANKING) return
        ticker?.cancel()
        ticker = null
        sounds.stopQuietLoop()
        val now = SystemClock.elapsedRealtime()
        val elapsed = ((endedAtRealtime ?: now) - startedRealtime).coerceIn(0, now - startedRealtime)
        val completed = timeline.isComplete(elapsed)
        store.append(SessionRecord("plank", startedAt, elapsed, completed))
        poseLog.addEvent(cameraMs(), "PLANK_END completed=$completed elapsedMs=$elapsed")
        tracker.reset()
        _state.update {
            it.copy(
                phase = if (completed) Phase.DONE else Phase.READY,
                elapsedMs = elapsed,
                calendar = loadCalendar(),
                cameraOn = it.cameraOn && !completed,
                pose = null,
            )
        }
    }

    fun dismissDone() {
        if (_state.value.phase == Phase.DONE) _state.update { it.copy(phase = Phase.HOME, elapsedMs = 0) }
    }

    /** The app left the foreground: there is no foreground service yet, so wind the session up. */
    fun onAppStopped() {
        endPlank()
        closeSession()
    }

    // --- camera --------------------------------------------------------------------------

    /** Called from the camera analysis thread for every frame; hops to the main thread. */
    fun onPose(landmarks: List<Landmark>?, aspect: Float) {
        viewModelScope.launch {
            val before = _state.value
            if (!before.cameraOn || (before.phase != Phase.READY && before.phase != Phase.PLANKING)) return@launch
            val now = SystemClock.elapsedRealtime()
            val reading = if (landmarks == null) PoseReading.NO_BODY else readPlank(landmarks, aspect)
            poseLog.addFrame(cameraMs(), before.phase.name, landmarks, aspect, reading)
            when (tracker.update(now, reading)) {
                PlankTracker.Event.STARTED -> {
                    poseLog.addEvent(cameraMs(), "TRACKER_STARTED")
                    if (before.phase == Phase.READY) startPlank()
                }
                PlankTracker.Event.ENDED -> {
                    poseLog.addEvent(cameraMs(), "TRACKER_ENDED")
                    // The plank really ended when you first lost it, not when the grace period ran out.
                    if (before.phase == Phase.PLANKING) endPlank(tracker.lastEndedLostAtMs)
                }
                null -> Unit
            }
            val after = _state.value
            if (after.cameraOn && (after.phase == Phase.READY || after.phase == Phase.PLANKING)) {
                _state.update { it.copy(pose = PoseUi(landmarks.orEmpty(), aspect, reading, tracker.holdProgress(now))) }
            }
        }
    }

    override fun onCleared() {
        sounds.release()
    }

    private companion object {
        const val TICK_MS = 100L
    }
}

package com.coachchicken

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.coachchicken.core.AuraMeter
import com.coachchicken.core.CalendarDay
import com.coachchicken.core.Cue
import com.coachchicken.core.Landmark
import com.coachchicken.core.PlankGrade
import com.coachchicken.core.PlankRecorder
import com.coachchicken.core.PlankRecording
import com.coachchicken.core.jointsOf
import com.coachchicken.core.PlankTarget
import com.coachchicken.core.PlankTimeline
import com.coachchicken.core.PlankTracker
import com.coachchicken.core.PoseLog
import com.coachchicken.core.PoseReading
import com.coachchicken.core.SessionRecord
import com.coachchicken.core.buildCalendar
import com.coachchicken.core.completedSessionTimes
import com.coachchicken.core.gradePlank
import com.coachchicken.core.headOutOfFrame
import com.coachchicken.core.lockTarget
import com.coachchicken.core.plankHip
import com.coachchicken.core.plankMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** HOME: calendar and Start session. READY: in a session, waiting for the plank. PLANKING: timing. DONE: reached the goal. */
enum class Phase { HOME, READY, PLANKING, DONE }

/** The latest camera reading, for drawing the skeleton, the goal zone and the status line. */
data class PoseUi(
    val landmarks: List<Landmark>,
    val aspect: Float,
    val grade: PlankGrade,
    val holdProgress: Float,
    /** While the plank is lapsing and has been warned about: how long until it ends, ms. Otherwise null. */
    val endsInMs: Long? = null,
    /** The camera-session clock, the same as the log's t_ms (shown on screen so a screen recording lines up). */
    val logMs: Long = 0,
    /** 0..1 gold glow: builds within seconds while the plank is perfect. */
    val aura: Float = 0f,
    /** 0..1 how long the perfect form has been held (slow): drives the glow size and the sparkle count. */
    val streak: Float = 0f,
    /** 'L' or 'R': the side of the body the grading and the green zone are judged on; null if no body. */
    val judgedSide: Char? = null,
    /** The green goal zone, once a plank has been seen. */
    val target: PlankTarget? = null,
    /** Whether the hip is inside [target] (null when there's no target or no body). */
    val hipInZone: Boolean? = null,
    /** The head is off the edge of the image: the phone is too close. */
    val headOutOfFrame: Boolean = false,
)

/** Everything one summary card needs: the logged session, and its replay if one was recorded. */
data class CardData(val record: SessionRecord, val recording: PlankRecording?)

/** The cards of one past day, opened from the calendar. */
data class DayView(val date: LocalDate, val cards: List<CardData>)

data class UiState(
    val phase: Phase = Phase.HOME,
    /** Plank time so far (while PLANKING) or the length of the plank just finished (DONE). */
    val elapsedMs: Long = 0,
    val goalMs: Long = PlankTimeline.DEFAULT_DURATION_MS,
    val calendar: List<List<CalendarDay>> = emptyList(),
    val cameraOn: Boolean = false,
    val frontCamera: Boolean = true,
    val pose: PoseUi? = null,
    /** The card of the plank just finished (shown on the DONE screen). */
    val card: CardData? = null,
    /** A past day opened from the calendar, or null. */
    val day: DayView? = null,
)

class CoachViewModel(app: Application) : AndroidViewModel(app) {
    private val timeline = PlankTimeline()
    private val store = SessionStore(app)
    private val plankStore = PlankStore(app)
    private val recorder = PlankRecorder()
    private var plankStartCameraMs = 0L
    private var plankTarget: PlankTarget? = null
    private var lastAspect = 1f
    private val sounds = SoundPlayer(app)
    private val tracker = PlankTracker()
    private val poseLog = PoseLog()
    private val aura = AuraMeter()
    private var target: PlankTarget? = null
    private var targetFrozen = false
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
        resetGoal()
        recorder.clear()
        cameraStartedAt = SystemClock.elapsedRealtime()
        poseLog.clear("camera=${if (_state.value.frontCamera) "front" else "back"} on=$camera app=${BuildConfig.VERSION_NAME} (t_ms is the on-screen t= clock)")
        _state.update { it.copy(phase = Phase.READY, elapsedMs = 0, cameraOn = camera, pose = null) }
    }

    private fun resetGoal() {
        aura.reset()
        target = null
        targetFrozen = false
    }

    /** Leave the session without planking. */
    fun closeSession() {
        if (_state.value.phase != Phase.READY) return
        tracker.reset()
        resetGoal()
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
        plankStartCameraMs = cameraMs()
        plankTarget = target
        targetFrozen = target != null // the green zone stops following you once the timer runs
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
        // A camera-detected end lets the warning finish on its own; any other end silences it.
        if (endedAtRealtime == null) sounds.stopWarning()
        val now = SystemClock.elapsedRealtime()
        val elapsed = ((endedAtRealtime ?: now) - startedRealtime).coerceIn(0, now - startedRealtime)
        val completed = timeline.isComplete(elapsed)
        store.append(SessionRecord("plank", startedAt, elapsed, completed))
        poseLog.addEvent(cameraMs(), "PLANK_END completed=$completed elapsedMs=$elapsed")
        val record = SessionRecord("plank", startedAt, elapsed, completed)
        val recording = if (_state.value.cameraOn && recorder.size > 0) {
            PlankRecording(
                startedAt = startedAt, aspect = lastAspect, mirror = _state.value.frontCamera, goalMs = timeline.durationMs,
                durationMs = elapsed, completed = completed, frames = recorder.framesFor(plankStartCameraMs, cameraMs()),
                target = plankTarget,
            )
        } else null
        if (recording != null) runCatching { plankStore.save(recording) } // a failed save must not lose the session
        tracker.reset()
        resetGoal()
        _state.update {
            it.copy(
                phase = if (completed) Phase.DONE else Phase.READY,
                elapsedMs = elapsed,
                calendar = loadCalendar(),
                cameraOn = it.cameraOn && !completed,
                pose = null,
                card = if (completed) CardData(record, recording) else null,
            )
        }
    }

    fun dismissDone() {
        if (_state.value.phase == Phase.DONE) _state.update { it.copy(phase = Phase.HOME, elapsedMs = 0, card = null) }
    }

    /** Open the cards of a past day (its completed planks, oldest first). Planks from before replays were saved have none. */
    fun openDay(date: LocalDate) {
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val cards = withContext(Dispatchers.IO) {
                store.readAll().filter { it.completed && it.startedAt.atZone(zone).toLocalDate() == date }
                    .sortedBy { it.startedAt }
                    .map { CardData(it, runCatching { plankStore.load(it.startedAt) }.getOrNull()) }
            }
            if (cards.isNotEmpty()) _state.update { it.copy(day = DayView(date, cards)) }
        }
    }

    fun closeDay() = _state.update { it.copy(day = null) }

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
            val grade = if (landmarks == null) PlankGrade.NO_BODY else gradePlank(landmarks, aspect, target = target)
            val isPlank = grade.reading == PoseReading.PLANK
            val event = tracker.update(now, grade.reading)
            val auraNow = aura.update(now, grade == PlankGrade.PERFECT)

            // The green goal zone follows you while you settle into position, then freezes once the timer runs.
            if (landmarks != null && isPlank && !targetFrozen) {
                target = lockTarget(landmarks, aspect)
                if (before.phase == Phase.PLANKING && target != null) targetFrozen = true
            } else if (!isPlank && !targetFrozen && tracker.smoothed < 0.3f) {
                target = null
            }
            val hip = landmarks?.let { plankHip(it, side = target?.side) }
            val offset = if (target != null && hip != null) target!!.offset(hip, aspect) else null

            poseLog.addFrame(cameraMs(), before.phase.name, landmarks, aspect, grade, tracker.smoothed, auraNow, offset)
            lastAspect = aspect
            val hipDeg = landmarks?.let { plankMetrics(it, aspect) }?.takeIf { it.lower != 'H' }?.hipAngleDeg?.toFloat()
            recorder.add(cameraMs(), grade.ordinal, auraNow, hipDeg, if (grade == PlankGrade.NO_BODY) null else landmarks?.let { jointsOf(it) }) // no body: whatever the model guessed is noise
            when (event) {
                PlankTracker.Event.STARTED -> {
                    poseLog.addEvent(cameraMs(), "TRACKER_STARTED")
                    if (before.phase == Phase.READY) startPlank()
                }
                PlankTracker.Event.WARNING -> {
                    poseLog.addEvent(cameraMs(), "TRACKER_WARNING")
                    if (before.phase == Phase.PLANKING) sounds.startWarning()
                }
                PlankTracker.Event.RECOVERED -> {
                    poseLog.addEvent(cameraMs(), "TRACKER_RECOVERED")
                    sounds.stopWarning()
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
                val endsIn = if (tracker.isWarning) (tracker.endGraceMs - tracker.lapseMs(now)).coerceAtLeast(0) else null
                _state.update {
                    it.copy(
                        pose = PoseUi(
                            landmarks.orEmpty(), aspect, grade, tracker.holdProgress(now), endsIn, cameraMs(),
                            aura = auraNow,
                            streak = aura.streak,
                            judgedSide = landmarks?.let { plankMetrics(it, aspect)?.side },
                            target = target,
                            hipInZone = if (target != null && hip != null) target!!.contains(hip, aspect) else null,
                            headOutOfFrame = landmarks != null && grade != PlankGrade.NO_BODY && headOutOfFrame(landmarks),
                        ),
                    )
                }
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

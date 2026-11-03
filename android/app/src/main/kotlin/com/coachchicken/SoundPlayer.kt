package com.coachchicken

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * Plays the chicken sounds. If anything fails to load or play, it silently does nothing:
 * the timer must keep working without sound.
 */
class SoundPlayer(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val quiet = pool.load(context, R.raw.cluck_quiet_loop, 1)
    private val loud = pool.load(context, R.raw.cluck_loud, 1)
    private val squawk = pool.load(context, R.raw.squawk, 1)
    private val warning = pool.load(context, R.raw.bwaaak, 1)
    private var loopStream = 0
    private var warningStream = 0

    fun startQuietLoop() = runCatching {
        stopQuietLoop()
        loopStream = pool.play(quiet, QUIET_LOOP_VOLUME, QUIET_LOOP_VOLUME, 0, -1, 1f)
    }

    fun stopQuietLoop() {
        runCatching { if (loopStream != 0) pool.stop(loopStream) }
        loopStream = 0
    }

    fun loudCluck() = runCatching { pool.play(loud, 1f, 1f, 1, 0, 1f) }

    fun squawk() = runCatching { pool.play(squawk, 1f, 1f, 2, 0, 1f) }

    /** The long building "bwaaaak!" played through the grace period; cut it off if you get back into plank. */
    fun startWarning() = runCatching {
        stopWarning()
        warningStream = pool.play(warning, 1f, 1f, 3, 0, 1f)
    }

    fun stopWarning() {
        runCatching { if (warningStream != 0) pool.stop(warningStream) }
        warningStream = 0
    }

    fun release() = pool.release()

    private companion object {
        /** The murmur stays well under the loud cluck, but is clearly audible (was 0.25, which was inaudible). */
        const val QUIET_LOOP_VOLUME = 0.5f
    }
}

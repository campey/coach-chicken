package com.coachchicken

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.coachchicken.core.ReminderCopy
import com.coachchicken.core.completedSessionTimes
import com.coachchicken.core.currentStreak
import com.coachchicken.core.nextFireAt
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** The reminder choice, kept in the app's private preferences. Off until the person turns it on. */
class ReminderSettings(context: Context) {
    private val prefs = context.getSharedPreferences("reminder", Context.MODE_PRIVATE)
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(v) = prefs.edit().putBoolean("enabled", v).apply()
    var hour: Int
        get() = prefs.getInt("hour", 18)
        set(v) = prefs.edit().putInt("hour", v).apply()
    var minute: Int
        get() = prefs.getInt("minute", 0)
        set(v) = prefs.edit().putInt("minute", v).apply()
}

object Reminders {
    private const val WORK_NAME = "daily-reminder"
    const val CHANNEL_ID = "daily_cluck"

    fun notificationsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /**
     * Queues the next reminder at the chosen time. [policy]: REPLACE when the time changed, KEEP at app start (an entry
     * already waiting stays), APPEND_OR_REPLACE from the worker itself, which is the entry still running.
     */
    fun schedule(context: Context, policy: ExistingWorkPolicy) {
        val s = ReminderSettings(context)
        val wm = WorkManager.getInstance(context)
        if (!s.enabled) { wm.cancelUniqueWork(WORK_NAME); return }
        val now = ZonedDateTime.now()
        val delay = Duration.between(now, nextFireAt(now, s.hour, s.minute))
        val request = OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS).build()
        wm.enqueueUniqueWork(WORK_NAME, policy, request)
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Daily cluck", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A daily nudge to do your plank"
            })
        }
    }

    internal fun chickenBitmap(context: Context): Bitmap? {
        val d = ContextCompat.getDrawable(context, R.drawable.ic_chicken) ?: return null
        val h = (128 * context.resources.displayMetrics.density).toInt().coerceAtMost(256)
        val w = h * 56 / 110
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, w, h)
        d.draw(Canvas(bmp))
        return bmp
    }
}

/**
 * Fires at the reminder time. Says something plucky unless today's plank is already done, then queues tomorrow's.
 * It runs in the background with the app closed, so it reads the saved sessions itself.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!ReminderSettings(ctx).enabled) return Result.success()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val streak = currentStreak(completedSessionTimes(SessionStore(ctx).readAll(), zone).keys, today)
        if (!streak.doneToday && Reminders.notificationsAllowed(ctx)) {
            Reminders.ensureChannel(ctx)
            val text = ReminderCopy.pick(streak, today)
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(ctx, Reminders.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(Reminders.chickenBitmap(ctx))
                .setContentTitle(text.title)
                .setContentText(text.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text.body))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            ctx.getSystemService(NotificationManager::class.java).notify(1, n)
        }
        Reminders.schedule(ctx, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }
}

package com.coachchicken

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.coachchicken.core.ReminderCopy
import com.coachchicken.core.ReminderSlot
import com.coachchicken.core.completedSessionTimes
import com.coachchicken.core.currentStreak
import com.coachchicken.core.nextSlot
import com.coachchicken.core.shouldSend
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Whether reminders are on, kept in the app's private preferences. Off until the person turns them on. */
class ReminderSettings(context: Context) {
    private val prefs = context.getSharedPreferences("reminder", Context.MODE_PRIVATE)
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(v) = prefs.edit().putBoolean("enabled", v).apply()
}

/**
 * The daily nudges, set with the system alarm clock service rather than a background job: an alarm is allowed to go off
 * while the phone is idle (a background job can be held back for hours until the app is opened).
 */
object Reminders {
    const val CHANNEL_ID = "daily_cluck"
    private const val REQ_NEXT = 100
    private const val REQ_TEST = 101

    fun notificationsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun pending(context: Context, request: Int, slot: ReminderSlot, force: Boolean): PendingIntent =
        PendingIntent.getBroadcast(
            context, request, Intent(context, ReminderReceiver::class.java).putExtra("slot", slot.name).putExtra("force", force),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Sets the alarm for the next nudge (morning, midday or streak saver, whichever comes next), or cancels it when reminders are off. */
    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        if (!ReminderSettings(context).enabled) { am.cancel(pending(context, REQ_NEXT, ReminderSlot.MORNING, false)); return }
        val (slot, at) = nextSlot(ZonedDateTime.now())
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), pending(context, REQ_NEXT, slot, false))
    }

    /** A test: one nudge in a minute, whatever today's state, to check that it arrives with the phone locked. */
    fun scheduleTest(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 60_000, pending(context, REQ_TEST, ReminderSlot.MIDDAY, true))
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

    /** Shows the nudge for [slot] unless today's plank is done (or the streak saver has no streak to save); [force] skips that check. */
    fun fire(context: Context, slot: ReminderSlot, force: Boolean) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val streak = currentStreak(completedSessionTimes(SessionStore(context).readAll(), zone).keys, today)
        if (!force && !shouldSend(slot, streak)) return
        if (!notificationsAllowed(context)) return
        ensureChannel(context)
        val text = ReminderCopy.pick(slot, streak, today)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(chickenBitmap(context))
            .setContentTitle(text.title)
            .setContentText(text.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(slot.ordinal + 1, n)
    }

    private fun chickenBitmap(context: Context): Bitmap? {
        val d = ContextCompat.getDrawable(context, R.drawable.ic_chicken) ?: return null
        val h = (128 * context.resources.displayMetrics.density).toInt().coerceAtMost(256)
        val w = h * 56 / 110
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, w, h)
        d.draw(Canvas(bmp))
        return bmp
    }
}

/** The alarm going off: show the nudge, then set the next one. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val slot = runCatching { ReminderSlot.valueOf(intent.getStringExtra("slot") ?: "") }.getOrDefault(ReminderSlot.MORNING)
        val force = intent.getBooleanExtra("force", false)
        if (ReminderSettings(context).enabled || force) Reminders.fire(context, slot, force)
        if (!force) Reminders.schedule(context)
    }
}

/** Alarms are forgotten when the phone restarts, the app updates or the clock changes: set the next one again. */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Reminders.schedule(context)
}

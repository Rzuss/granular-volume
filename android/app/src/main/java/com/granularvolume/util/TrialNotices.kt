package com.granularvolume.util

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.granularvolume.InfoSheetActivity
import com.granularvolume.R
import com.granularvolume.receiver.TrialNoticeReceiver

/**
 * The two informative notices of the free week (1.6.2), and nothing else.
 *
 * Why they exist: the sheets only ever answer a person's own action (a start, a touch of the
 * dial). Someone who set a level and tucked the dial away never takes that action, so before
 * 1.6.2 they had no warning on the last day, and when their phone restarted after the week the
 * control simply did not come back, with no word about why. That is the most engaged user of
 * the whole week meeting the lock as "it stopped working".
 *
 * The contract, and it is deliberately narrow:
 *  - at most ONE last-day notice and ONE ended notice per install, ever. Never repeated,
 *    never re-armed, never a series.
 *  - only for a user whose access rests on the trial alone. Grandfathered users, buyers, key
 *    owners and the F-Droid build (always entitled) can never reach either one.
 *  - silent (low importance): it waits in the shade, it does not ring or pop over anything.
 *  - informational, with one tap to the "Your access" sheet. No price in the notice itself.
 *  - a notice is skipped when the person has already been told the same thing in the app
 *    (the last-day sheet, or the locked sheet), so nobody is told twice.
 *  - notifications denied on Android 13+: nothing is shown, nothing breaks.
 */
object TrialNotices {

    private const val TAG = "GranularVolume:Notices"
    private const val CHANNEL_ID = "gv_free_week"
    /** One id for both notices, so the ended notice replaces a last-day notice still in the shade. */
    private const val NOTICE_ID = 1101

    /** Last-day notice: the trial is inside its final 24 hours and nothing has warned yet. */
    fun maybePostLastDay(context: Context): Boolean {
        if (!ProAccess.isOnTrial(context)) return false
        if (Entitlement.millisLeftInTrial(context) > DAY_MS) return false
        if (Prefs.wasLastDayNudgeShown(context) || Prefs.wasLastDayNoticePosted(context)) return false
        // 1.6.4: the once-per-install flag is spent only when the notice really went up. It used
        // to be set first, so a person with notifications off at that moment lost the notice for good.
        val posted = post(
            context,
            context.getString(R.string.gv_notice_last_day_title),
            context.getString(R.string.gv_notice_last_day_body),
            "last-day"
        )
        if (posted) Prefs.setLastDayNoticePosted(context)
        return posted
    }

    /**
     * Ended notice. [stillRunning] = the week ran out while the dial is on (1.7.0: the level
     * stays as it is until the control is stopped or the device restarts, and changing it
     * needs the unlock, so the text says exactly that); false = the control is paused (a
     * restart, or the system bringing the service back on its own).
     */
    fun maybePostEnded(context: Context, stillRunning: Boolean): Boolean {
        if (!ProAccess.isTrialExpired(context)) return false
        if (Prefs.wasEndedNoticePosted(context)) return false
        val posted = post(
            context,
            context.getString(R.string.gv_notice_ended_title),
            context.getString(
                if (stillRunning) R.string.gv_notice_ended_running_body
                else R.string.gv_notice_ended_paused_body
            ),
            if (stillRunning) "ended-running" else "ended-paused"
        )
        if (posted) Prefs.setEndedNoticePosted(context)   // 1.6.4: spent only on success, see above
        return posted
    }

    /** The person has now seen the locked sheet in the app: the ended notice has nothing to add. */
    fun markEndedToldInApp(context: Context) {
        if (ProAccess.isTrialExpired(context) && !Prefs.wasEndedNoticePosted(context)) {
            Prefs.setEndedNoticePosted(context)
            Log.i(TAG, "Ended notice retired: the locked sheet was shown in the app")
        }
    }

    /**
     * Arms the two moments with the system's alarm service (inexact, allowed while idle, no
     * exact-alarm permission). A plain Handler delay counts uptime, which stops while the phone
     * sleeps, so a 24-hour-before notice could land a night late. Called on every service start
     * while the trial runs; re-arming replaces the previous alarms (same request codes). Alarms
     * do not survive a reboot, and the boot path re-arms through the service start or, when the
     * week is over, posts the ended notice itself.
     */
    fun arm(context: Context) {
        if (!ProAccess.isOnTrial(context)) return
        val left = Entitlement.millisLeftInTrial(context)
        if (left <= 0L) return
        val now = System.currentTimeMillis()
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        if (left > DAY_MS && !Prefs.wasLastDayNoticePosted(context)) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, now + (left - DAY_MS) + SLACK_MS, alarm(context, ACTION_LAST_DAY, 11))
        }
        if (!Prefs.wasEndedNoticePosted(context)) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, now + left + SLACK_MS, alarm(context, ACTION_ENDED, 12))
        }
        Log.i(TAG, "Armed: last day in ${(left - DAY_MS) / 60000} min, end in ${left / 60000} min")
    }

    private fun alarm(context: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, code,
            Intent(context, TrialNoticeReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /** Access opened (purchase, restore, key): a notice still waiting in the shade is now wrong. */
    fun cancel(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTICE_ID) }
    }

    private fun post(context: Context, title: String, body: String, kind: String): Boolean {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) {
            Log.i(TAG, "Notice '$kind' not shown: notifications are off for the app")
            return false
        }
        ensureChannel(context)
        val tap = PendingIntent.getActivity(
            context, 2,
            Intent(context, InfoSheetActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_volume_slider)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        return try {
            nm.notify(NOTICE_ID, notification)
            Log.i(TAG, "Notice '$kind' posted")
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Notice '$kind' refused by the system: ${e.message}")
            false
        }
    }

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.gv_notice_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.gv_notice_channel_desc)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    const val DAY_MS = 24L * 60L * 60L * 1000L
    /** Lands just inside the window, never a moment before it (rounding in daysLeftInTrial). */
    private const val SLACK_MS = 5_000L
    const val ACTION_LAST_DAY = "com.granularvolume.NOTICE_LAST_DAY"
    const val ACTION_ENDED = "com.granularvolume.NOTICE_ENDED"
}

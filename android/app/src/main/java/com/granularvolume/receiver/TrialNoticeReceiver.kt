package com.granularvolume.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.granularvolume.util.Prefs
import com.granularvolume.util.TrialNotices

/**
 * 1.6.2: receives the two alarms armed by [TrialNotices.arm]. Not exported; only this app's own
 * PendingIntents reach it. Every check (still on trial, not yet told, notifications allowed) lives
 * in TrialNotices, so a stale alarm after a purchase or a restore simply does nothing.
 */
class TrialNoticeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            TrialNotices.ACTION_LAST_DAY -> TrialNotices.maybePostLastDay(context)
            TrialNotices.ACTION_ENDED -> {
                // Only someone who keeps the control on is told at the moment the week ends; the
                // session stays open until the next start, which is what the notice says. A person
                // who had already switched the control off meets the locked sheet on their next start.
                if (Prefs.wasServiceRunning(context)) TrialNotices.maybePostEnded(context, stillRunning = true)
                else Log.i("GranularVolume:Notices", "Week ended with the control off: no notice")
            }
        }
    }
}

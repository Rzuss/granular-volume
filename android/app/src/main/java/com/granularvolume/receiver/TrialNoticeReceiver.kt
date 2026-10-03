package com.granularvolume.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.granularvolume.service.VolumeControlService
import com.granularvolume.util.ControlLive
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
                // level stays as it is until the control is next stopped, which is what the notice
                // says. A person who had already switched the control off meets the locked sheet on
                // their next start.
                // 1.7.0: read from the live flag. The stored "was running" stays true after the
                // system kills the control, so the notice could describe a dial that was not there.
                if (ControlLive.running && ControlLive.sessionOpen) {
                    TrialNotices.maybePostEnded(context, stillRunning = true)
                    // The dial and the shade were drawn for an open week; repaint them now.
                    runCatching {
                        context.startService(
                            Intent(context, VolumeControlService::class.java)
                                .setAction(VolumeControlService.ACTION_ACCESS_CHANGED)
                        )
                    }
                } else Log.i("GranularVolume:Notices", "Week ended with the control off: no notice")
            }
        }
    }
}

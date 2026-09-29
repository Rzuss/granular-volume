package com.granularvolume.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.granularvolume.service.VolumeControlService
import com.granularvolume.util.Prefs
import com.granularvolume.util.ProAccess
import com.granularvolume.util.TrialNotices

/**
 * Receives BOOT_COMPLETED and restarts the service if it was running before shutdown,
 * unless the dial is locked: a locked control has nothing to apply.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON") return

        // The one-time grandfather decision runs before the lock is read: after an update
        // from 1.4.x this receiver can be the first entry point, and a long-time user must
        // not look locked only because nothing has decided yet. It writes no prefs first.
        ProAccess.evaluateGrandfather(context)
        // A locked control does not come back on its own after a restart; the user reopens
        // it and meets the purchase sheet then.
        if (Prefs.wasServiceRunning(context) && !ProAccess.isTrialExpired(context)) {
            Log.i("GranularVolume:Boot", "Restarting VolumeControlService after boot")
            // 1.5.3: a boot broadcast delivered while the system does not grant the
            // background-start exemption (seen when BOOT_COMPLETED is re-delivered to a package
            // after a force-stop) made startForegroundService throw
            // ForegroundServiceStartNotAllowedException, and an uncaught throw in a receiver
            // crashes the app. A refused restart now just logs: the control stays off and
            // comes back the next time the user opens it, exactly like a locked one.
            try {
                context.startForegroundService(
                    Intent(context, VolumeControlService::class.java)
                        // Marks the start as machine-originated, so the service's purchase
                        // sheet stays quiet: a boot is not the user opening the control.
                        .putExtra(VolumeControlService.EXTRA_FROM_BOOT, true)
                )
            } catch (e: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException (API 31+) extends IllegalStateException.
                Log.w("GranularVolume:Boot", "Restart after boot refused by the system: ${e.message}")
            }
        } else if (Prefs.wasServiceRunning(context)) {
            Log.i("GranularVolume:Boot", "Not restarting after boot: the dial is locked")
            // 1.6.2: the control that was on before the restart does not come back, so say why,
            // once. Before this the quiet level simply vanished after the first locked restart,
            // which reads as "it stopped working". No sheet: a boot is not the user's action.
            TrialNotices.maybePostEnded(context, stillRunning = false)
        }
    }
}

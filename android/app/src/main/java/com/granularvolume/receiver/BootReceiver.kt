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
 *
 * 1.6.4: also receives MY_PACKAGE_REPLACED, under exactly the same rules. An app update kills
 * the process and Android does not bring a sticky service back after a package replace
 * (measured 2026-10-01: service and effect gone 90 s after `install -r`, pref still "running").
 * The quiet level vanished with every update and nothing said why. An update is the machine's
 * doing, like a boot: no sheet, no tour, and a control the person stopped stays stopped.
 *
 * 1.7.0: an update and a boot now differ in ONE case. A control that was on when the free week
 * ended keeps holding its level until it is next stopped. A restart of the device ends that
 * session, as before. Our own update must not: it is our doing, not the user's, and a release
 * that makes somebody's sound louder by itself is the one thing an update may never do. So
 * after an update that session comes back as it was, level held, dial asking for the unlock on
 * the next adjustment. A session that started locked comes back as nothing, as before.
 *
 * 1.7.0: the two free-week notices are armed here as well. Alarms do not survive a restart,
 * and until now they were re-armed only by a service start, so someone whose control was off
 * across a reboot lost the last-day note.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val afterUpdate = intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (!afterUpdate &&
            intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON") return
        val why = if (afterUpdate) "an app update" else "boot"

        // The one-time grandfather decision runs before the lock is read: after an update
        // from 1.4.x this receiver can be the first entry point, and a long-time user must
        // not look locked only because nothing has decided yet. It writes no prefs first.
        ProAccess.evaluateGrandfather(context)
        TrialNotices.arm(context)
        val expired = ProAccess.isTrialExpired(context)
        // Read BEFORE anything can rewrite it: was the session that the update just killed open?
        val carry = afterUpdate && expired && Prefs.wasSessionOpenThisBoot(context)
        // A locked control does not come back on its own after a restart; the user reopens
        // it and meets the purchase sheet then.
        if (Prefs.wasServiceRunning(context) && (!expired || carry)) {
            Log.i("GranularVolume:Boot", "Restarting VolumeControlService after $why" +
                if (carry) " (free week over, session carried)" else "")
            Prefs.setSessionCarry(context, carry)
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
                Log.w("GranularVolume:Boot", "Restart after $why refused by the system: ${e.message}")
                Prefs.setSessionCarry(context, false)
            }
        } else if (Prefs.wasServiceRunning(context)) {
            Log.i("GranularVolume:Boot", "Not restarting after $why: the dial is locked")
            // The session that was holding a level ended with this restart; nothing may carry it later.
            Prefs.setSessionOpen(context, false)
            // 1.6.2: the control that was on before the restart does not come back, so say why,
            // once. Before this the quiet level simply vanished after the first locked restart,
            // which reads as "it stopped working". No sheet: a boot is not the user's action.
            TrialNotices.maybePostEnded(context, stillRunning = false)
        }
    }
}

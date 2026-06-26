package com.granularvolume.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.granularvolume.service.VolumeControlService
import com.granularvolume.util.Prefs

/**
 * Receives BOOT_COMPLETED and restarts the service if it was running before shutdown.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON") return

        if (Prefs.wasServiceRunning(context)) {
            Log.i("GranularVolume:Boot", "Restarting VolumeControlService after boot")
            context.startForegroundService(
                Intent(context, VolumeControlService::class.java)
            )
        }
    }
}

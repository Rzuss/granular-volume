package com.granularvolume.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.granularvolume.MainActivity
import com.granularvolume.util.PermissionHelper
import com.granularvolume.util.Prefs
import com.granularvolume.util.ReviewHelper

/**
 * Quick Settings tile that toggles the VolumeControlService on/off.
 *
 * The tile reflects the current service state on every panel open (onStartListening)
 * and updates optimistically on tap so the user sees immediate feedback.
 *
 * If the overlay permission has not been granted, tapping the tile opens MainActivity
 * so the user can complete the setup, instead of starting a service that cannot show
 * its overlay.
 */
class GranularVolumeTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        syncTile()
    }

    override fun onClick() {
        super.onClick()

        if (!PermissionHelper.canDrawOverlays(applicationContext)) {
            launchMainActivity()
            return
        }

        val running = Prefs.wasServiceRunning(applicationContext)
        if (running) {
            stopService(Intent(this, VolumeControlService::class.java))
            syncTile(active = false)
        } else {
            startForegroundService(Intent(this, VolumeControlService::class.java))
            syncTile(active = true)
            // The control is now running — the real "moment of value" for tile-only
            // users. After enough activations this offers the in-app review once
            // (Play flavor); reviewIntentIfDue returns null until then, and always
            // on F-Droid, so nothing here disturbs the user in the normal case.
            ReviewHelper.reviewIntentIfDue(applicationContext)?.let { startActivityAndCollapseCompat(it) }
        }
    }

    private fun syncTile(active: Boolean = Prefs.wasServiceRunning(applicationContext)) {
        val tile = qsTile ?: return
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Granular Volume"
        tile.updateTile()
    }

    private fun launchMainActivity() {
        startActivityAndCollapseCompat(
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /**
     * Starts an activity from the tile the sanctioned way. On Android 14+ the
     * PendingIntent overload is required; older versions take the Intent directly.
     * Both collapse the Quick Settings panel so the launched activity comes forward.
     */
    private fun startActivityAndCollapseCompat(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}

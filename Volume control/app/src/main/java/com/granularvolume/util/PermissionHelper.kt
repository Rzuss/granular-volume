package com.granularvolume.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Checks and requests special permissions that cannot use the standard
 * ActivityCompat.requestPermissions() flow.
 */
object PermissionHelper {

    /** Returns true if the app can draw system overlays. */
    fun canDrawOverlays(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    /** Returns an Intent that opens the overlay permission settings for this app. */
    fun overlayPermissionIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )

    /** Returns true if MODIFY_AUDIO_SETTINGS is granted (normal permission, auto-granted on API 28+). */
    fun hasModifyAudioSettings(context: Context): Boolean {
        // MODIFY_AUDIO_SETTINGS is a normal permission — auto-granted at install time.
        // We check it explicitly to guard against unusual OEM restrictions.
        return context.checkSelfPermission(android.Manifest.permission.MODIFY_AUDIO_SETTINGS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}

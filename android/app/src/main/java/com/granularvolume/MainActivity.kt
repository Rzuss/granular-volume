package com.granularvolume

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.granularvolume.service.GranularVolumeTileService
import com.granularvolume.service.VolumeControlService
import com.granularvolume.util.PermissionHelper
import com.granularvolume.util.Prefs
import com.granularvolume.util.ReviewHelper

/**
 * Single-screen activity: guides the user through permission grants,
 * then starts the foreground service and finishes (the overlay IS the UI).
 *
 * First-run sequence on Android 13+:
 *   1. Overlay permission (manual — Android OS requirement)
 *   2. POST_NOTIFICATIONS runtime request
 *   3. Start VolumeControlService
 *   4. requestAddTileService dialog — one-tap "Add" to Quick Settings (offered once only)
 *   5. finish()
 */
class MainActivity : AppCompatActivity() {

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (PermissionHelper.canDrawOverlays(this)) {
            launchService()
        } else {
            updateStatus()
            Toast.makeText(this, "Overlay permission required", Toast.LENGTH_SHORT).show()
        }
    }

    // Best-effort: the service works whether or not the user grants notifications.
    private val notificationPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> startServiceAndOfferTile() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (PermissionHelper.canDrawOverlays(this) &&
            PermissionHelper.hasModifyAudioSettings(this)) {
            // A return visit — the app is already set up and working. This is the
            // right moment to (rarely, at most once) ask for a review, before the
            // usual auto-launch-and-finish flow continues exactly as before.
            ReviewHelper.maybeRequestReview(this) { launchService() }
            return
        }

        updateStatus()

        findViewById<Button>(R.id.btn_grant_overlay).setOnClickListener {
            overlayPermissionLauncher.launch(PermissionHelper.overlayPermissionIntent(this))
        }

        findViewById<Button>(R.id.btn_start_service).setOnClickListener {
            if (!PermissionHelper.canDrawOverlays(this)) {
                Toast.makeText(this, "Please grant overlay access first", Toast.LENGTH_SHORT).show()
            } else {
                launchService()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    // -------------------------------------------------------------------------
    // Launch sequence
    // -------------------------------------------------------------------------

    private fun launchService() {
        // On Android 13+: request notification permission so the FGS notification
        // is visible immediately. The service runs regardless of the user's choice.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startServiceAndOfferTile()
        }
    }

    private fun startServiceAndOfferTile() {
        startForegroundService(Intent(this, VolumeControlService::class.java))
        Toast.makeText(this, "Volume control started", Toast.LENGTH_SHORT).show()
        // On Android 13+: show the system "Add to Quick Settings" dialog once.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Prefs.wasQsTileOffered(this)) {
            offerQsTile()
        } else {
            finish()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun offerQsTile() {
        Prefs.setQsTileOffered(this, true)
        getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(this, GranularVolumeTileService::class.java),
            getString(R.string.app_name),
            Icon.createWithResource(this, R.drawable.ic_qs_tile),
            mainExecutor
        ) { finish() }
    }

    // -------------------------------------------------------------------------
    // UI
    // -------------------------------------------------------------------------

    private fun updateStatus() {
        val overlayOk = PermissionHelper.canDrawOverlays(this)
        val audioOk   = PermissionHelper.hasModifyAudioSettings(this)

        renderStatePill(findViewById(R.id.tv_overlay_state), overlayOk)
        renderStatePill(findViewById(R.id.tv_audio_state), audioOk)

        findViewById<TextView>(R.id.tv_hint).text =
            getString(if (overlayOk) R.string.gv_hint_ready else R.string.gv_hint_grant_first)

        findViewById<Button>(R.id.btn_grant_overlay).isEnabled = !overlayOk
        findViewById<Button>(R.id.btn_start_service).isEnabled = overlayOk
    }

    private fun renderStatePill(pill: TextView, granted: Boolean) {
        val textRes = if (granted) R.string.gv_state_granted else R.string.gv_state_needed
        val fg = if (granted) R.color.gv_success else R.color.gv_warning
        val bg = if (granted) R.color.gv_success_dim else R.color.gv_warning_dim
        pill.text = getString(textRes)
        pill.setTextColor(ContextCompat.getColor(this, fg))
        pill.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, bg))
    }
}

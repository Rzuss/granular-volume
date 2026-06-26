package com.granularvolume

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.granularvolume.service.VolumeControlService
import com.granularvolume.util.PermissionHelper

/**
 * Single-screen activity: guides the user through permission grants,
 * then starts the foreground service and finishes (the overlay IS the UI).
 *
 * The screen shows a live status card (overlay / audio access) plus a primary
 * CTA and a secondary "start" action whose enabled state tracks the grants.
 */
class MainActivity : AppCompatActivity() {

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Check again after returning from settings
        if (PermissionHelper.canDrawOverlays(this)) {
            startServiceAndFinish()
        } else {
            updateStatus()
            Toast.makeText(this, "Overlay permission required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // If both permissions are already granted, skip the UI and go
        if (PermissionHelper.canDrawOverlays(this) &&
            PermissionHelper.hasModifyAudioSettings(this)) {
            startServiceAndFinish()
            return
        }

        updateStatus()

        findViewById<Button>(R.id.btn_grant_overlay).setOnClickListener {
            overlayPermissionLauncher.launch(PermissionHelper.overlayPermissionIntent(this))
        }

        findViewById<Button>(R.id.btn_start_service).setOnClickListener {
            when {
                !PermissionHelper.canDrawOverlays(this) ->
                    Toast.makeText(this, "Please grant overlay access first", Toast.LENGTH_SHORT).show()
                else -> startServiceAndFinish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

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

    /** Paint a status pill as granted (green) or needed (amber). */
    private fun renderStatePill(pill: TextView, granted: Boolean) {
        val textRes = if (granted) R.string.gv_state_granted else R.string.gv_state_needed
        val fg = if (granted) R.color.gv_success else R.color.gv_warning
        val bg = if (granted) R.color.gv_success_dim else R.color.gv_warning_dim
        pill.text = getString(textRes)
        pill.setTextColor(ContextCompat.getColor(this, fg))
        pill.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, bg))
    }

    private fun startServiceAndFinish() {
        startForegroundService(Intent(this, VolumeControlService::class.java))
        Toast.makeText(this, "Volume control started", Toast.LENGTH_SHORT).show()
        finish() // The overlay is the UI — no need for this activity to stay open
    }
}

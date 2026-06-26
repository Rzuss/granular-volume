package com.granularvolume.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.granularvolume.MainActivity
import com.granularvolume.R
import com.granularvolume.audio.AudioController
import com.granularvolume.overlay.OverlayManager
import com.granularvolume.util.Prefs
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the AudioController and OverlayManager lifecycle.
 *
 * Lifecycle:
 *   onCreate() -> initialize audio + overlay
 *   onDestroy() -> release audio + hide overlay + cancel coroutines
 *
 * This service is START_STICKY — the OS will restart it if killed.
 */
class VolumeControlService : Service() {

    private val tag = "GranularVolume:Service"

    companion object {
        private const val CHANNEL_ID   = "gv_volume_control"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.granularvolume.ACTION_STOP"
    }

    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineName("VolumeControlService")
    )

    private lateinit var audioController: AudioController
    private lateinit var overlayManager: OverlayManager

    override fun onCreate() {
        super.onCreate()
        Log.i(tag, "Service starting")

        createNotificationChannel()
        // API 29+: must pass foregroundServiceType explicitly or the OS throws on some devices.
        // Hardened: never let an FGS-start exception kill the service before the overlay shows.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(0f),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification(0f))
            }
        } catch (e: Exception) {
            Log.e(tag, "startForeground failed: ${e.message}", e)
        }

        audioController = AudioController(applicationContext)
        overlayManager  = OverlayManager(
            context         = applicationContext,
            audioController = audioController,
            scope           = serviceScope,
            onDismiss       = { stopSelf() }
        )

        serviceScope.launch(Dispatchers.Default) {
            audioController.initialize()
            if (!audioController.isEffectAvailable) {
                Log.e(tag, "No audio effect available — service will run without audio attenuation")
            }
        }

        try {
            overlayManager.show()
        } catch (e: Exception) {
            // Surface the real reason on-device instead of failing silently.
            Log.e(tag, "Failed to show overlay: ${e.message}", e)
            toast("Couldn't show the control: ${e.message}. Check 'Display over other apps'.")
        }
        Prefs.setServiceWasRunning(applicationContext, true)

        // Update notification when attenuation changes
        audioController.attenuationDb
            .onEach { dB -> updateNotification(dB) }
            .launchIn(serviceScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.i(tag, "Stop action received")
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(tag, "Service stopping")
        overlayManager.hide()
        audioController.release()
        serviceScope.cancel()
        Prefs.setServiceWasRunning(applicationContext, false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Show a toast from any thread (service callbacks may run off the main thread). */
    private fun toast(msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Volume Control",
            NotificationManager.IMPORTANCE_LOW   // No sound, no popup
        ).apply {
            description = "Granular sub-volume control overlay"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(dB: Float): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, VolumeControlService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        val dbText = if (dB == 0f) "Pass-through" else "%.0f dB".format(dB)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_volume_slider)
            .setContentTitle("Sub-Volume Control")
            .setContentText(dbText)
            .setContentIntent(tapIntent)
            .addAction(R.drawable.ic_close, "Stop", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(dB: Float) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(dB))
    }
}

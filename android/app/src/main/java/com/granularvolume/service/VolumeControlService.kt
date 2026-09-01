package com.granularvolume.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
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
import com.granularvolume.audio.FullRangeCoordinator
import com.granularvolume.audio.StreamVolumeController
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

        // Hidden-but-stable system broadcast + extras (no public constants exist for these).
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        private const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
        private const val EXTRA_VOLUME_STREAM_VALUE = "android.media.EXTRA_VOLUME_STREAM_VALUE"
        private const val EXTRA_PREV_VOLUME_STREAM_VALUE = "android.media.EXTRA_PREV_VOLUME_STREAM_VALUE"
    }

    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineName("VolumeControlService")
    )

    private lateinit var audioController: AudioController
    private lateinit var streamVolumeController: StreamVolumeController
    private lateinit var coordinator: FullRangeCoordinator
    private lateinit var overlayManager: OverlayManager

    /**
     * VOLUME_CHANGED_ACTION is undocumented but long-stable and the standard listening
     * mechanism for volume apps — spec accepts it with a real-hardware verification gate.
     * Feeds the coordinator's absorb policy; our own writes are filtered there.
     */
    private val volumeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != VOLUME_CHANGED_ACTION) return
            val stream = intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1)
            if (stream < 0) return
            val to = intent.getIntExtra(EXTRA_VOLUME_STREAM_VALUE, -1)
            val from = intent.getIntExtra(EXTRA_PREV_VOLUME_STREAM_VALUE, to)
            if (to < 0) return
            coordinator.onExternalVolumeChange(stream, from, to)
        }
    }

    /**
     * The volume curve differs per output route — re-read it on every route change (spec).
     * 1.4.6: a route change during a call also moves the voice audio to a different output,
     * so the coordinator must re-place the effect chain there too (onRouteChanged is a
     * no-op outside calls; media effects follow the music output by policy on their own).
     */
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) {
            coordinator.refreshCurve()
            coordinator.onRouteChanged()
        }
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) {
            coordinator.refreshCurve()
            coordinator.onRouteChanged()
        }
    }

    // 1.4.2: the AudioPlaybackCallback that re-rendered the pill on playback start/stop is
    // gone WITH its reason: the dial no longer flips between media and ring (it always drives
    // media, matching AOSP's own no-playback default), so playback changes affect nothing the
    // overlay renders.

    /**
     * 1.4.3: in a call the upper zone drives the voice-call stream, so the ladder must
     * re-render when a call starts or ends. Control correctness never depends on this —
     * activeStream() is evaluated live per use — this is display freshness only. The
     * listener API exists from 31; on 28-30 the display catches up on the next volume
     * broadcast or touch, which in practice is the moment the call audio starts.
     */
    private val modeListener =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            AudioManager.OnModeChangedListener { coordinator.onAudioModeChanged() }
        else null

    /**
     * True only when the user explicitly asked to stop (notification Stop action or
     * overlay dismiss). onDestroy also runs on device shutdown and OS kills, and those
     * must NOT clear the boot-restore flag — otherwise BootReceiver always sees false
     * and the control never comes back after a reboot.
     */
    @Volatile
    private var stopRequestedByUser = false

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
        streamVolumeController = StreamVolumeController(applicationContext)
        coordinator = FullRangeCoordinator(applicationContext, audioController, streamVolumeController)
        coordinator.onQuietUnavailable = {
            // One short line instead of a bar that would move without the sound moving.
            // Routed through the existing toast() helper, which already hops to the main
            // thread — this fires from the overlay's touch handler.
            Log.i(tag, "Quiet step refused: cellular call carries no effect chain")
            toast(getString(R.string.gv_quiet_unavailable_in_call))
        }
        overlayManager  = OverlayManager(
            context         = applicationContext,
            audioController = audioController,
            coordinator     = coordinator,
            scope           = serviceScope,
            onDismiss       = {
                stopRequestedByUser = true
                stopSelf()
            }
        )

        serviceScope.launch(Dispatchers.Default) {
            audioController.initialize()
            if (!audioController.isEffectAvailable) {
                Log.e(tag, "No audio effect available — service will run without audio attenuation")
            }
            // Read the device's volume curve AFTER the effect is up (update semantics:
            // reads only, writes nothing until the user touches the slider).
            coordinator.refreshCurve()
        }

        // API 34+ requires an explicit export flag on context-registered receivers.
        // NOT_EXPORTED still receives system broadcasts (they come from the system UID).
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            volumeChangeReceiver,
            IntentFilter(VOLUME_CHANGED_ACTION),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val mainHandler = Handler(Looper.getMainLooper())
        am.registerAudioDeviceCallback(deviceCallback, mainHandler)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && modeListener != null) {
            am.addOnModeChangedListener({ r -> mainHandler.post(r) }, modeListener)
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
            stopRequestedByUser = true
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(tag, "Service stopping (userRequested=$stopRequestedByUser)")
        runCatching { unregisterReceiver(volumeChangeReceiver) }
        runCatching {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.unregisterAudioDeviceCallback(deviceCallback)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && modeListener != null) {
                am.removeOnModeChangedListener(modeListener)
            }
        }
        overlayManager.hide()
        audioController.release()
        serviceScope.cancel()
        // Only a user-intended stop clears the boot-restore flag. A system-initiated
        // destroy (device shutdown, OS kill) leaves it set, so BootReceiver restores
        // the control after the next boot.
        if (stopRequestedByUser) {
            Prefs.setServiceWasRunning(applicationContext, false)
        }
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

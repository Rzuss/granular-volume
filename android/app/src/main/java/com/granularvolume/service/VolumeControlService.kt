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
import android.os.PatternMatcher
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.granularvolume.MainActivity
import com.granularvolume.R
import com.granularvolume.InfoSheetActivity
import com.granularvolume.PaywallActivity
import com.granularvolume.audio.AudioController
import com.granularvolume.util.ProAccess
import com.granularvolume.audio.FullRangeCoordinator
import com.granularvolume.audio.StreamVolumeController
import com.granularvolume.overlay.OverlayManager
import com.granularvolume.util.Entitlement
import com.granularvolume.util.KeyCheck
import com.granularvolume.util.Prefs
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.math.abs

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

        /**
         * The key app just appeared. Sent by the paywall sheet when it returns from the
         * store and finds the key installed, so ownership takes effect in that second
         * rather than at the next service start.
         * Since 2026-09-10 this is the FALLBACK door: the service hears the install itself
         * (see keyInstalledReceiver), whatever screen the buyer is on.
         */
        const val ACTION_KEY_INSTALLED = "com.granularvolume.ACTION_KEY_INSTALLED"
        /** 1.5.1: replay the feature tour, sent by the info sheet's "Show the tour" link. */
        const val ACTION_SHOW_TOUR = "com.granularvolume.ACTION_SHOW_TOUR"
        const val ACTION_PREVIEW_BLUETOOTH_FLOOR = "com.granularvolume.ACTION_PREVIEW_BLUETOOTH_FLOOR"

        /**
         * Boot-restore starts carry this so the purchase sheet stays closed. A boot is
         * the MACHINE resuming, not the user opening the control, and a sales sheet
         * over the launcher seconds after power-on is the exact adware gesture this
         * app must never make.
         */
        const val EXTRA_FROM_BOOT = "com.granularvolume.EXTRA_FROM_BOOT"

        /** The paid key app. Must match KeyCheck and the play manifest's <queries> entry. */
        private const val KEY_APP_PACKAGE = "com.granularvolume.key"

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

    /** Latched full-range verdict for this session. See [unlockedThisSession]. */
    /**
     * The quiet step a locked user reached for, kept only until the store round trip
     * ends. In memory on purpose: the foreground service outlives the trip, and this
     * must never outlive it, survive a restart, or travel in a backup.
     */
    private var pendingQuietStepDb: Float? = null

    private var sessionUnlocked = false

    /**
     * Whether this session has already answered the key. Seeded at start from what is
     * installed, so a key that was there before the control started is never announced again
     * (the cold return has its own one-time card in MainActivity, harness A27).
     */
    private var keyWelcomed = false
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
     * The key app was installed. Listened for HERE, by the service that owns the dial, because
     * the dial is what the buyer is looking at.
     *
     * Until 2026-09-10 the only signal was ACTION_KEY_INSTALLED, sent by the paywall or the
     * info sheet when one of them RESUMED after the store. A buyer who pressed Home after the
     * install, or tapped Open on the key, resumed neither, and kept looking at the dim ladder
     * the lock had drawn until they happened to touch it. Found on the owner's own phone. The
     * package broadcast arrives when the install completes, whatever the buyer does next. It
     * needs the play manifest's <queries> entry for the key, the same one KeyCheck relies on.
     */
    private val keyInstalledReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_PACKAGE_ADDED) return
            if (intent.data?.schemeSpecificPart != KEY_APP_PACKAGE) return
            mainHandler.post { onKeyArrived("package-added") }
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

    private val mainHandler = Handler(Looper.getMainLooper())

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

        // Full-range gate (1.5.0): decide grandfathering once, then latch the verdict for
        // this session. Must precede initialize(), which routes the boot-restore level
        // through the gate.
        ProAccess.evaluateGrandfather(applicationContext)
        sessionUnlocked = ProAccess.isPro(applicationContext)
        keyWelcomed = KeyCheck.isKeyInstalled(applicationContext)
        audioController.proProvider = ::unlockedThisSession

        streamVolumeController = StreamVolumeController(applicationContext)
        coordinator = FullRangeCoordinator(applicationContext, audioController, streamVolumeController)
        coordinator.lockedProvider = { !unlockedThisSession() }
        // Repaints must never open the latch (see lockedDisplayProvider). The same read-only
        // rule the notification uses.
        coordinator.lockedDisplayProvider = { !sessionUnlocked && !ProAccess.isPro(applicationContext) }
        coordinator.onLockedInteraction = { pendingStep ->
            mainHandler.post {
                pendingQuietStepDb = pendingStep
                // Observable refusal: this is now the ONLY way a user gesture reaches the
                // paywall, so the harness asserts on it instead of on the gate's clamp.
                Log.i(tag, "Locked gesture refused (pendingQuietStep=$pendingStep), opening paywall")
                openPaywall()
            }
        }
        coordinator.onQuietUnavailable = {
            mainHandler.post {
                // One short line instead of a bar that would move without the sound moving.
                Log.i(tag, "Quiet step refused: cellular call carries no effect chain")
                Toast.makeText(
                    applicationContext,
                    getString(R.string.gv_quiet_unavailable_in_call),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        // The dial is the only surface a set-up user still sees, so it carries the one
        // route to status, purchase and the legal texts. NEW_TASK because the caller is a
        // service, exactly as with the paywall sheet.
        overlayManager  = OverlayManager(
            context         = applicationContext,
            audioController = audioController,
            coordinator     = coordinator,
            scope           = serviceScope,
            onDismiss       = {
                stopRequestedByUser = true
                stopSelf()
            },
            onInfo          = { openInfoSheet() }
        )
        // Wired HERE, after construction, never inside a callback. It sat inside the onInfo
        // lambda until 2026-09-09, which compiled and meant it was only ever assigned after
        // the user tapped the info button: the "session already running when the last day
        // begins" warning (see onOpenedByUser) was dead for anyone driving the app from the
        // dial. The harness was green over it because A17 reads the countdown by tapping
        // info, the one action that armed the broken assignment. A21 now covers this path.
        overlayManager.onEngaged = { mainHandler.post { maybeLastDayNudge() } }

        serviceScope.launch(Dispatchers.Default) {
            audioController.initialize()
            // initialize() re-applies the persisted level THROUGH the gate, so a locked
            // device with a stale deep level is now at 0 dB while the coordinator still
            // believes it is in the quiet zone (it read the pre-clamp value when it was
            // constructed). Without this the dial would open showing a step it is not
            // applying, on every single start.
            coordinator.syncZoneToAppliedGain()
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
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            keyInstalledReceiver,
            IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply {
                addDataScheme("package")
                addDataSchemeSpecificPart(KEY_APP_PACKAGE, PatternMatcher.PATTERN_LITERAL)
            },
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
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
        when (intent?.action) {
            ACTION_STOP -> {
                Log.i(tag, "Stop action received")
                stopRequestedByUser = true
                stopSelf()
            }
            ACTION_KEY_INSTALLED -> onKeyArrived("activity")
            ACTION_SHOW_TOUR -> overlayManager.startTourOnRequest()
            ACTION_PREVIEW_BLUETOOTH_FLOOR -> coordinator.previewBluetoothFloor()
            // A plain start with a real Intent is a person or the boot receiver turning
            // the control on; a null Intent is only ever the system resurrecting a
            // killed sticky service, which no one asked for and no sheet may answer.
            null -> if (intent != null) {
                onOpenedByUser(fromBoot = intent.getBooleanExtra(EXTRA_FROM_BOOT, false))
            }
        }
        return START_STICKY
    }

    /**
     * Whether the full range is open for THIS session.
     *
     * Latched, and it only ever opens: the snapshot is taken at service start, and after
     * that the only thing that can change the answer is the key arriving, which must take
     * effect at once. A trial that runs out while the control is live is deliberately
     * ignored until the next start. Nothing may get louder on its own while someone is on a
     * call at -30 dB, and finding the app dead the next time you start it is a far kinder
     * failure than the phone shouting mid-sentence.
     */
    private fun unlockedThisSession(): Boolean {
        if (sessionUnlocked) return true
        if (!ProAccess.isPro(applicationContext)) return false
        sessionUnlocked = true
        Log.i(tag, "Full range opened mid-session (key installed)")
        // A gesture got here before the package broadcast was handled. The gesture's own level
        // stands; the welcome still has to happen, so schedule it (it finds the session open and
        // skips the landing). Skipped when onKeyArrived is the caller: it is already here.
        if (!keyWelcomed) mainHandler.post { onKeyArrived("gesture") }
        return true
    }

    /**
     * The commercial voice of the app, and ALL of it. Two sentences of policy:
     *
     *  - Expired: every user-originated start answers with the "Your access" sheet,
     *    because starting a control that can no longer do anything deserves an
     *    explanation and the one-tap route to fixing it, every time, uncapped.
     *  - Final trial day: the sheet interrupts ONCE, to warn that tomorrow it locks.
     *    Both here (covers a fresh start that day) and from onEngaged (covers a
     *    session already running when the last day begins).
     *
     * Nothing here ever fires on a timer or a boot. A prompt with no user action
     * behind it is spam, reads as adware in reviews, and risks the Play policy on
     * interruptive monetization; the daily cadence the model needs is carried by
     * whichever comes first that day: a start (sheet) or a locked gesture (paywall).
     */
    private fun onOpenedByUser(fromBoot: Boolean) {
        if (fromBoot) return
        if (ProAccess.isTrialExpired(applicationContext)) { openInfoSheet(); return }
        // 1.5.1: the feature tour, once per install or update, only on a start a person made,
        // and never over the last-day sheet: one thing at a time on a start.
        if (!maybeLastDayNudge()) overlayManager.maybeStartTour()
    }

    /** Once, on the trial's final day: cheapest check first, so the everyday cost is one boolean read. */
    private fun maybeLastDayNudge(): Boolean {
        if (Prefs.wasLastDayNudgeShown(applicationContext)) return false
        if (!ProAccess.isOnTrial(applicationContext)) return false
        if (Entitlement.daysLeftInTrial(applicationContext) > 1) return false
        Prefs.setLastDayNudgeShown(applicationContext)
        openInfoSheet()
        return true
    }

    private fun openInfoSheet() = startSheet(InfoSheetActivity::class.java)

    private fun openPaywall() = startSheet(PaywallActivity::class.java)

    /**
     * 1.5.3: every screen the service opens goes through here. Android may refuse an activity
     * start from a service (background-start limits, a start that races the boot sequence), and
     * the refusal arrives as a RuntimeException ("Activity could not be started"). Thrown from
     * onStartCommand, it killed the whole service: the control vanished because a sheet could
     * not open. Found in the emulator's dropbox during the 1.5.3 lifecycle run, on the locked
     * start path (onOpenedByUser -> openInfoSheet). A refused sheet is now logged and skipped;
     * the dial keeps running, and the user meets the sheet on their next tap.
     */
    private fun startSheet(activity: Class<*>) {
        try {
            startActivity(
                Intent(applicationContext, activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: RuntimeException) {
            Log.w(tag, "Could not open ${activity.simpleName}: ${e.message}")
        }
    }

    /**
     * The key has arrived, through one of three doors: the package broadcast (the normal one,
     * see [keyInstalledReceiver]), the paywall or info sheet resuming after the store, or a
     * gesture that found the key already installed. Whichever comes first does the work; the
     * others find it done.
     *
     *  1. LANDING, only if the session was still locked. The gate held the gain at 0 dB for the
     *     whole locked session, so the purchase by itself changes nothing audible, and "I paid
     *     and nothing happened" is the worst possible first second of ownership. See
     *     [landAfterUnlock]: the refused step if there was one, otherwise the stored place, and
     *     never louder than the locked state it replaces. Every public surface promises "every
     *     step comes back exactly where you left it"; until 2026-09-10 that only happened at the
     *     next service start.
     *  2. WELCOME for a buyer: one toast and the dial lighting up. Not for a grandfathered user
     *     buying the key as support, for whom nothing was unlocked. The live welcome also counts
     *     as the purchase acknowledgement, so MainActivity's one-time card does not repeat it.
     *  3. The notification is repainted: it said "Locked" a second ago.
     */
    private fun onKeyArrived(source: String) {
        if (keyWelcomed) return
        if (!KeyCheck.isKeyInstalled(applicationContext)) {
            // A key signed by anyone else. KeyCheck has already logged the rejection.
            Log.w(tag, "Key signal ($source), but no valid key installed: ignoring")
            return
        }
        val wasLocked = !sessionUnlocked
        keyWelcomed = true
        unlockedThisSession()
        Log.i(tag, "Key arrived ($source), session was ${if (wasLocked) "locked" else "open"}")
        if (wasLocked) landAfterUnlock() else pendingQuietStepDb = null
        if (!Entitlement.isGrandfathered(applicationContext)) {
            Prefs.setUnlockAcknowledged(applicationContext)
            Toast.makeText(applicationContext, R.string.gv_paywall_unlocked, Toast.LENGTH_LONG).show()
            overlayManager.celebrateUnlock()
        }
        updateNotification(audioController.attenuationDb.value)
    }

    /**
     * Puts the audio where the buyer asked, or where they left it. Never louder.
     *
     * Never louder, because: while locked the gain sat at 0 dB and the hardware wherever the
     * buyer's own keys had left it. The landing goes through [FullRangeCoordinator.applyQuiet],
     * which only ever LOWERS the hardware (lowerTo) and applies a gain at or below 0 dB. The two
     * exits below are the only ways that could break: a muted stream (applyQuiet cancels mute
     * first, restoring the pre-mute level, so a landing would UNMUTE the buyer) and a cellular
     * call (applyQuiet refuses there and says so; the place comes back after the call). A locked
     * session cannot be muted today, since a locked mute is refused and mute is not persisted, so
     * that exit guards a future path rather than a tested one.
     *
     * Only a real quiet step counts as a place. The stored level also holds upper-zone curve
     * remainders, a few dB of fine-tuning between hardware rungs, and applying one as a quiet
     * step would drop the buyer to the floor for a fraction of a step, which is not where they
     * were. Those buyers, and anyone who never used the quiet zone, stay exactly where they are.
     */
    private fun landAfterUnlock() {
        val pending = pendingQuietStepDb
        pendingQuietStepDb = null
        if (coordinator.isMuted) { Log.i(tag, "Key landing skipped: muted"); return }
        if (coordinator.uiState().quietUnavailable) { Log.i(tag, "Key landing skipped: cellular call"); return }
        val stored = Prefs.getAttenuation(applicationContext)
        val place = stored.takeIf { s -> OverlayManager.STEP_DB.any { it < 0f && abs(it - s) < 0.01f } }
        val target = pending ?: place
        if (target == null) {
            Log.i(tag, "Key landing: no quiet place to return to (stored ${stored}dB), staying put")
            coordinator.syncZoneToAppliedGain()
            return
        }
        Log.i(tag, "Key landing on ${if (pending != null) "the refused step" else "the stored place"} (${target}dB)")
        coordinator.applyQuiet(target)
    }

    override fun onDestroy() {
        Log.i(tag, "Service stopping (userRequested=$stopRequestedByUser)")
        runCatching { unregisterReceiver(volumeChangeReceiver) }
        runCatching { unregisterReceiver(keyInstalledReceiver) }
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
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, VolumeControlService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        // A locked control saying "Pass-through" would be the shade lying about why
        // nothing works. Locked gets the honest line and a tap that opens the sheet:
        // the notification is the one surface the user sees every single day, so it
        // carries the standing, silent version of the daily reminder.
        // Same truth the audio gate uses: once this session is open it stays open until the
        // next start, so a trial that runs out mid-session must not have the shade calling
        // the control "Locked" while the dial still works. Read-only on purpose (no latch
        // mutation, no "opened mid-session" log from a notification repaint).
        val locked = !sessionUnlocked && !ProAccess.isPro(applicationContext)
        val tapTarget = if (locked) InfoSheetActivity::class.java else MainActivity::class.java
        val tapIntent = PendingIntent.getActivity(
            this, 1, Intent(this, tapTarget), PendingIntent.FLAG_IMMUTABLE
        )
        val dbText =
            if (locked) getString(R.string.gv_notif_locked)
            else if (dB == 0f) "Pass-through" else "%.0f dB".format(dB)

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

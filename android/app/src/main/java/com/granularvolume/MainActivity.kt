package com.granularvolume

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.granularvolume.service.GranularVolumeTileService
import com.granularvolume.service.VolumeControlService
import com.granularvolume.util.AccessState
import com.granularvolume.util.BillingManager
import com.granularvolume.util.ControlLive
import com.granularvolume.util.Entitlement
import com.granularvolume.util.Links
import com.granularvolume.util.PermissionHelper
import com.granularvolume.util.ProAccess
import com.granularvolume.util.PurchaseFlow
import com.granularvolume.util.Prefs
import com.granularvolume.util.ReviewHelper
import com.granularvolume.util.TrialNotices

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
 *
 * 1.7.0, the launcher icon as a door. Once the app is set up this screen used to answer every
 * tap on the icon the same way: start the control and close, with a toast that said "started"
 * even when it had been running for days. Status, price and purchase were reachable only
 * through the small i on the dial. Now a tap on the icon while the control is RUNNING opens
 * "Your access"; when it is not running, it starts it, as before.
 */
class MainActivity : AppCompatActivity() {

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (PermissionHelper.canDrawOverlays(this)) {
            launchService()
        } else {
            updateStatus()
            Toast.makeText(this, R.string.gv_toast_overlay_required, Toast.LENGTH_SHORT).show()
        }
    }

    // Best-effort: the service works whether or not the user grants notifications.
    private val notificationPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> startServiceAndOfferTile() }

    companion object {
        private const val STATE_TRIAL_CARD = "trial_card_showing"

        /**
         * Version of the Terms this build presents. Bump ONLY on a material change to the
         * Terms, which re-prompts every existing user. Cosmetic edits must not bump it.
         */
        /**
         * Version 2, bumped for the 1.5.0 Terms.
         *
         * The earlier reasoning for NOT bumping was that the new sections governed only the
         * optional purchase and took nothing from anyone. Re-reading the finished text, that
         * is no longer true: section 14 takes a licence to a user's feedback, section 16 asks
         * for an export and sanctions representation, and section 7 gained an age and capacity
         * representation. Those bind every user, buyer or not, and none of them existed in the
         * version people actually accepted.
         *
         * A bump costs one screen on the next launch and does not touch a running service, so
         * the people most likely to interact, and to buy, will have actively accepted the text
         * that governs them.
         *
         * Version 3, bumped for the trial model. Section 5 previously promised a free tier
         * "at no charge, permanently". That promise is gone for new installs, replaced by
         * [Entitlement.TRIAL_DAYS] days of the complete app. Nobody may be moved onto those
         * terms without being shown them, and existing users are grandfathered in code, not
         * merely in copy, so what they re-accept still describes what they have.
         */
        const val TERMS_VERSION = 3

        /** The countdown card starts appearing this many days before the trial ends. */
        private const val TRIAL_CARD_FROM_DAYS = 3

        private const val URL_TERMS = "https://rzuss.github.io/granular-volume-privacy/terms-of-use.html"
        private const val URL_PRIVACY = "https://rzuss.github.io/granular-volume-privacy/"
    }

    /** The countdown card is on screen now / was on screen when this screen was rebuilt. */
    private var trialCardShowing = false
    private var trialCardRestored = false

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_TRIAL_CARD, trialCardShowing)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Full-range gate (1.5.0): the grandfather verdict must be taken before
        // this session writes any prefs, or a fresh install could read its own
        // fresh writes as prior use.
        ProAccess.evaluateGrandfather(this)
        // 1.7.0: the two free-week notices are armed from here as well. Until now only a
        // service start armed them, so someone who opened the app and never started the
        // control, or whose alarms were lost to a restart, was never told on the last day.
        TrialNotices.arm(this)

        setupConsentGate()
        findViewById<TextView>(R.id.tv_link_licenses).setOnClickListener {
            startActivity(Intent(this, LicensesActivity::class.java))
        }
        findViewById<TextView>(R.id.tv_link_help).setOnClickListener { Links.open(this, Links.HELP) }

        // 1.7.0: set up AND running. The icon is a door to "Your access", not a second start.
        // Decided before the cards, so the countdown card's once-a-day turn is not spent on a
        // screen nobody will see: the sheet shows the same days and the same button.
        if (isSetUp() && ControlLive.running) {
            ReviewHelper.maybeRequestReview(this) { openAccessAndClose() }
            return
        }

        // 1.7.1: a rotation (or any rebuild of this screen) while the countdown card is up keeps
        // the card. Its once-a-day turn is recorded when it is drawn, so the rebuilt screen saw
        // "already shown today", started the control and closed itself under the reader.
        trialCardRestored = savedInstanceState?.getBoolean(STATE_TRIAL_CARD, false) == true
        val cardShowing = setupTipjarCard()

        if (isSetUp() && !cardShowing) {
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
                Toast.makeText(this, R.string.gv_toast_grant_first, Toast.LENGTH_SHORT).show()
            } else {
                launchService()
            }
        }
    }

    private fun isSetUp(): Boolean =
        PermissionHelper.canDrawOverlays(this) &&
            PermissionHelper.hasModifyAudioSettings(this) &&
            hasAcceptedTerms()

    /** Opens "Your access" over whatever is behind, and leaves. Safe after the screen is gone. */
    private fun openAccessAndClose() {
        runCatching {
            applicationContext.startActivity(
                Intent(applicationContext, InfoSheetActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { Log.w("GranularVolume", "open access: ${it.message}") }
        if (!isFinishing && !isDestroyed) finish()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        recheckOwnership()
    }

    /**
     * 1.7.0: asks Google Play for this account's purchase record every time the screen comes
     * forward, and redraws when the answer changes where the user stands. Until now only a
     * service start asked, so a buyer who reinstalled, or bought on another device, was shown
     * the pay screen until they happened to find the Restore link.
     */
    private fun recheckOwnership() {
        val before = AccessState.of(this)
        BillingManager.verifyOwnership(applicationContext) { _ ->
            if (isFinishing || isDestroyed) return@verifyOwnership
            val after = AccessState.of(this)
            if (after != before) {
                Log.i("GranularVolume", "Access changed on resume: $before -> $after")
                if (after == AccessState.UNLOCKED && ControlLive.running) PurchaseFlow.notifyService(this)
                setupTipjarCard()
                updateStatus()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Grandfather tip-jar card (1.5.0) — play flavor only: the CTA points at
    // Google Play, which means nothing to an F-Droid user.
    // -------------------------------------------------------------------------

    /**
     * @return true while the card is due, which also holds the auto-launch-and-finish
     * fast path open for one visit — otherwise the activity closes itself before the
     * grandfathered user could ever see the card.
     */
    private fun setupTipjarCard(): Boolean {
        // FIRST, unconditionally: this function has early returns for the grandfathered
        // and just-bought paths, and a line left over from the trial would greet a buyer
        // with a promise about a week they have already finished paying to end.
        setupTrialOrientation()
        val card = findViewById<View>(R.id.gv_tipjar_card)

        // A buyer, a grandfathered user and someone on the trial are mutually exclusive, so
        // one card slot serves all three. The purchase confirmation takes priority: it is
        // the one a person paid for.
        //
        // The condition is a PAID unlock (the in-app purchase or the key), never
        // [ProAccess.isPro] — that is also true during the trial, and thanking someone for a
        // purchase they have not made is the fastest way to lose the sale that was still coming.
        if (BuildConfig.FLAVOR == "play" && !Entitlement.isGrandfathered(this) &&
            !Prefs.wasUnlockAcknowledged(this) && ProAccess.hasPaidUnlock(this)
        ) {
            card.visibility = View.VISIBLE
            findViewById<TextView>(R.id.tv_card_title).setText(R.string.gv_unlocked_title)
            findViewById<TextView>(R.id.tv_card_body).setText(R.string.gv_unlocked_body)
            findViewById<TextView>(R.id.btn_tipjar_support).visibility = View.GONE
            findViewById<View>(R.id.btn_card_restore).visibility = View.GONE
            findViewById<TextView>(R.id.btn_tipjar_dismiss).apply {
                visibility = View.VISIBLE
                setText(R.string.gv_unlocked_dismiss)
                setOnClickListener {
                    Prefs.setUnlockAcknowledged(this@MainActivity)
                    card.visibility = View.GONE
                }
            }
            return true
        }

        // 1.7.0: the support card for people who keep everything free moved to "Your access"
        // (InfoSheetActivity), which the icon and the notification now open. One invitation in
        // one place; this screen no longer holds a long-time user back on its way to the dial.
        return setupTrialCard(card)
    }

    /**
     * The quiet half of the trial story: one line, no buttons, shown on the days the
     * countdown card stays silent.
     *
     * It exists because the ONLY places a new user was previously told about the week
     * were the store listing and the Terms, and nobody reads the Terms. Finding a locked
     * dial on day eight with no prior notice is the review that costs the most, and it is
     * free to prevent here: MainActivity is already open and being read while permissions
     * are granted, so this interrupts nothing.
     *
     * Deliberately NOT an offer. No price, no CTA, nothing to tap. The days that need to
     * sell are 3, 2 and 1, and the card handles those.
     */
    private fun setupTrialOrientation() {
        val line = findViewById<TextView>(R.id.tv_trial_orientation) ?: return
        // 1.7.0: the whole deal in one sentence, with the real number of days: everything is
        // open, then the dial LOCKS, then one payment unlocks it for good. The old line said
        // "one payment keeps it" and never said what happens without it. No price here, by the
        // owner's decision: the amount is one tap away in "Your access".
        // Shown on every day of the free week (it used to stop three days before the end,
        // exactly when it matters most); the countdown card adds the button on those days.
        if (!AccessState.of(this).onTrial) { line.visibility = View.GONE; return }
        line.text = if (Entitlement.isInGrace(this)) {
            // A first open that found the week already over: 24 hours, once, said plainly.
            // This screen IS the last-day warning for that person, so the sheet that would
            // repeat it on their very first start (in place of the tour) and the last-day
            // note are both marked as said.
            if (!Prefs.wasLastDayNudgeShown(this)) Prefs.setLastDayNudgeShown(this)
            getString(R.string.gv_trial_grace)
        } else {
            val days = Entitlement.daysLeftInTrial(this)
            resources.getQuantityString(R.plurals.gv_trial_orientation_days, days, days)
        }
        line.visibility = View.VISIBLE
    }


    /**
     * The trial's own card, in the same slot. Two states, and the honest thing in both is to
     * say where the person stands before they discover it by tapping something that no longer
     * works.
     *
     *  RUNNING — shown in the last [TRIAL_CARD_FROM_DAYS] days only, once per day. Earlier
     *  than that it is a countdown nobody asked for on an app they are still deciding about.
     *
     *  OVER — shown every launch, with no dismiss. The app genuinely does nothing at this
     *  point, so a screen that quietly returned to the dial would read as a broken app rather
     *  than a finished trial.
     *
     * @return true while the card holds the auto-launch-and-finish fast path open.
     */
    private fun setupTrialCard(card: View): Boolean {
        if (BuildConfig.FLAVOR != "play") { card.visibility = View.GONE; return false }

        val title   = findViewById<TextView>(R.id.tv_card_title)
        val body    = findViewById<TextView>(R.id.tv_card_body)
        val support = findViewById<TextView>(R.id.btn_tipjar_support)
        val dismiss = findViewById<TextView>(R.id.btn_tipjar_dismiss)
        val restore = findViewById<TextView>(R.id.btn_card_restore)
        restore.visibility = View.GONE
        // 1.7.0: a payment Google Play is still confirming. The buy button would invite a
        // second order for something already ordered, so it steps aside for the one sentence.
        val pending = Entitlement.isPurchasePending(this)

        // Both questions are asked through ProAccess, which excludes a grandfathered user and
        // a buyer by construction. Asking Entitlement directly would tell a grandfathered
        // user their trial is over, which is both false and alarming.
        if (ProAccess.isOnTrial(this)) {
            val daysLeft = Entitlement.daysLeftInTrial(this)
            // 1.7.0: never on the very first screen. Before the Terms are accepted the line under
            // the title already states the whole arrangement, and a countdown card with a buy
            // button there pushed "Agree and continue" off the screen (seen on a late first open,
            // which starts with one day). The card's daily turn is not spent either.
            if (!hasAcceptedTerms()) { card.visibility = View.GONE; return false }
            val shownToday = Prefs.getTrialCardShownForDay(this) == daysLeft &&
                !trialCardRestored && !trialCardShowing
            if (daysLeft > TRIAL_CARD_FROM_DAYS || shownToday) {
                card.visibility = View.GONE
                return false
            }
            Prefs.setTrialCardShownForDay(this, daysLeft)
            trialCardShowing = true
            card.visibility = View.VISIBLE
            title.text = resources.getQuantityString(R.plurals.gv_trial_days_left, daysLeft, daysLeft)
            body.setText(if (pending) R.string.gv_purchase_pending else R.string.gv_trial_body)
            wireBuyButton(support, pending)
            dismiss.visibility = View.VISIBLE
            dismiss.setText(R.string.gv_trial_dismiss)
            // 1.7.0: "Later" means "not now, take me to the dial". It used to hide the card and
            // leave the person on the setup screen, one more tap away from what they came for.
            dismiss.setOnClickListener {
                trialCardShowing = false
                card.visibility = View.GONE
                if (isSetUp()) launchService()
            }
            return true
        }

        if (!ProAccess.isTrialExpired(this)) { card.visibility = View.GONE; return false }

        card.visibility = View.VISIBLE
        title.setText(R.string.gv_trial_over_title)
        body.setText(if (pending) R.string.gv_purchase_pending else R.string.gv_trial_over_body)
        wireBuyButton(support, pending)
        dismiss.visibility = View.GONE
        // 1.7.0: the locked screen offered a purchase and no way to say "I already paid".
        restore.visibility = View.VISIBLE
        restore.setOnClickListener { PurchaseFlow.restore(this) { setupTipjarCard(); updateStatus() } }
        return true
    }

    /**
     * The card's buy button (1.7.0): hidden while a payment is pending, and while Google Play
     * is opening it says so and takes no second tap. Every outcome gives it back.
     */
    private fun wireBuyButton(button: TextView, pending: Boolean) {
        if (pending) { button.visibility = View.GONE; return }
        button.visibility = View.VISIBLE
        // 1.6.4: the button says what it does and, once Play has answered, what it costs
        // ("See the price" made the reader open a purchase window to learn a number).
        button.text = PurchaseFlow.ctaLabel(this)
        button.isEnabled = !PurchaseFlow.isInFlight()
        refreshCardPrice(button)
        button.setOnClickListener {
            if (PurchaseFlow.isInFlight()) return@setOnClickListener
            PurchaseFlow.start(
                this,
                onSettled = {
                    if (!isFinishing && !isDestroyed) {
                        button.isEnabled = true
                        button.text = PurchaseFlow.ctaLabel(this)
                    }
                },
                onUnlocked = { if (!isFinishing && !isDestroyed) { setupTipjarCard(); updateStatus() } }
            )
            button.isEnabled = false
            button.text = PurchaseFlow.ctaLabel(this)
        }
    }

    /**
     * 1.6.4: asks Play for the price and relabels the card's button when it arrives. Only the
     * label changes; re-running the card setup would spend the once-per-day guard of the
     * trial card and hide it.
     */
    private fun refreshCardPrice(button: TextView) {
        BillingManager.refreshPrice(this) {
            if (!isFinishing && !isDestroyed) button.text = PurchaseFlow.ctaLabel(this)
        }
    }

    // -------------------------------------------------------------------------
    // Consent gate (clickwrap) — mandatory legal component, see A2b/A2c
    // -------------------------------------------------------------------------

    private fun hasAcceptedTerms(): Boolean =
        Prefs.getTermsAcceptedVersion(this) >= TERMS_VERSION

    /**
     * Wires the consent block. Until the current Terms version is accepted, the permission
     * CTAs stay disabled, so nothing is granted and no service starts without an active
     * agreement. Once accepted, the block disappears and the flow is exactly as before.
     */
    private fun setupConsentGate() {
        val block = findViewById<View>(R.id.gv_consent_block)
        if (hasAcceptedTerms()) {
            block.visibility = View.GONE
            return
        }
        block.visibility = View.VISIBLE

        findViewById<TextView>(R.id.tv_link_terms).setOnClickListener { openUrl(URL_TERMS) }
        findViewById<TextView>(R.id.tv_link_privacy).setOnClickListener { openUrl(URL_PRIVACY) }

        findViewById<Button>(R.id.btn_accept_terms).setOnClickListener {
            Prefs.setTermsAcceptedVersion(this, TERMS_VERSION)
            block.visibility = View.GONE
            updateStatus()
        }
    }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(this, url, Toast.LENGTH_LONG).show() }
    }

    // -------------------------------------------------------------------------
    // Launch sequence
    // -------------------------------------------------------------------------

    private fun launchService() {
        // 1.5.3: this also runs as the in-app review's completion callback, which Play can
        // deliver after the user has already left and the activity is destroyed. The result
        // launcher is unregistered by then, and launch() threw IllegalStateException (2 users
        // in 28 days, every 1.4.x/1.5.x version). A destroyed screen can show no dialog, so the
        // control is started directly and the permission question waits for the next visit.
        if (isFinishing || isDestroyed) {
            runCatching {
                applicationContext.startForegroundService(Intent(applicationContext, VolumeControlService::class.java))
            }.onFailure { Log.w("GranularVolume", "launchService after destroy: ${it.message}") }
            return
        }
        // On Android 13+: request notification permission so the FGS notification
        // is visible immediately. The service runs regardless of the user's choice.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            try {
                notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } catch (e: IllegalStateException) {
                // Belt and braces for the same race: never crash the screen over a permission prompt.
                Log.w("GranularVolume", "notification permission prompt unavailable: ${e.message}")
                startServiceAndOfferTile()
            }
        } else {
            startServiceAndOfferTile()
        }
    }

    private fun startServiceAndOfferTile() {
        // On Android 13+: show the system "Add to Quick Settings" dialog once.
        val offerTile = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Prefs.wasQsTileOffered(this)
        // 1.7.0: when that dialog is about to open, the service is told to hold the tour. It
        // used to start 700 ms after the dial appeared, underneath the system dialog, and its
        // first card was over before anyone could read it. The tour is released below, when
        // the dialog has been answered.
        startForegroundService(
            Intent(this, VolumeControlService::class.java)
                .putExtra(VolumeControlService.EXTRA_HOLD_TOUR, offerTile)
        )
        Toast.makeText(this, R.string.gv_toast_started, Toast.LENGTH_SHORT).show()
        if (offerTile) offerQsTile() else finish()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun offerQsTile() {
        Prefs.setQsTileOffered(this, true)
        val released = java.util.concurrent.atomic.AtomicBoolean(false)
        val release = {
            if (released.compareAndSet(false, true)) {
                runCatching {
                    applicationContext.startService(
                        Intent(applicationContext, VolumeControlService::class.java)
                            .setAction(VolumeControlService.ACTION_TOUR_IF_DUE)
                    )
                }
                if (!isFinishing && !isDestroyed) finish()
            }
        }
        try {
            getSystemService(StatusBarManager::class.java).requestAddTileService(
                ComponentName(this, GranularVolumeTileService::class.java),
                getString(R.string.app_name),
                Icon.createWithResource(this, R.drawable.ic_qs_tile),
                mainExecutor
            ) { release() }
        } catch (e: RuntimeException) {
            // No status bar service to ask (some tablets and TV builds): nothing to wait for.
            Log.w("GranularVolume", "Add-tile dialog unavailable: ${e.message}")
            release()
        }
    }

    // -------------------------------------------------------------------------
    // UI
    // -------------------------------------------------------------------------

    private fun updateStatus() {
        val overlayOk = PermissionHelper.canDrawOverlays(this)
        val audioOk   = PermissionHelper.hasModifyAudioSettings(this)
        val consented = hasAcceptedTerms()

        renderStatePill(findViewById(R.id.tv_overlay_state), overlayOk)
        renderStatePill(findViewById(R.id.tv_audio_state), audioOk)
        renderNotificationRow()
        renderBatteryHint()

        // 1.7.0: the hint follows what is actually in the way, in order. Before consent both
        // buttons are disabled and the old hint talked about the overlay; when the dial is
        // locked the old hint said "You're all set, start the control", which starts nothing
        // the reader can use.
        val state = AccessState.of(this)
        findViewById<TextView>(R.id.tv_hint).text = getString(
            when {
                !consented -> R.string.gv_hint_consent
                !overlayOk -> R.string.gv_hint_grant_first
                state == AccessState.LOCKED -> R.string.gv_hint_locked
                else -> R.string.gv_hint_ready
            }
        )

        // Both CTAs stay disabled until the Terms are actively accepted (clickwrap).
        // 1.7.0: one button at a time, the one for the step the user is on. Showing both, one
        // of them always disabled, pushed "Start" below the fold once the screen gained a row.
        findViewById<Button>(R.id.btn_grant_overlay).apply {
            visibility = if (overlayOk) View.GONE else View.VISIBLE
            isEnabled = consented && !overlayOk
        }
        findViewById<Button>(R.id.btn_start_service).apply {
            visibility = if (overlayOk) View.VISIBLE else View.GONE
            isEnabled = consented && overlayOk
        }
    }

    /**
     * The third row (1.7.0). Optional, and worded as optional: the control works without it.
     * The pill is neutral when off, never the amber "Needed" of the two real requirements.
     */
    private fun renderNotificationRow() {
        val on = NotificationManagerCompat.from(this).areNotificationsEnabled()
        findViewById<TextView>(R.id.tv_notif_desc).setText(
            if (AccessState.of(this).onTrial) R.string.gv_perm_notif_desc_trial
            else R.string.gv_perm_notif_desc
        )
        val pill = findViewById<TextView>(R.id.tv_notif_state)
        pill.setText(if (on) R.string.gv_state_on else R.string.gv_state_off)
        pill.setTextColor(ContextCompat.getColor(this, if (on) R.color.gv_success else R.color.gv_text_secondary))
        pill.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, if (on) R.color.gv_success_dim else R.color.gv_surface_stroke)
        )
    }

    /** Shown only while the system may put the app to sleep (1.7.0). A statement and a Help link. */
    private fun renderBatteryHint() {
        val exempt = runCatching {
            getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        }.getOrDefault(true)
        findViewById<View>(R.id.tv_battery_hint).visibility = if (exempt) View.GONE else View.VISIBLE
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

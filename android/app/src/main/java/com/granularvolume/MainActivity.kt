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
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.granularvolume.service.GranularVolumeTileService
import com.granularvolume.service.VolumeControlService
import com.granularvolume.util.BillingManager
import com.granularvolume.util.Entitlement
import com.granularvolume.util.PermissionHelper
import com.granularvolume.util.ProAccess
import com.granularvolume.util.PurchaseFlow
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

    companion object {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Full-range gate (1.5.0): the grandfather verdict must be taken before
        // this session writes any prefs, or a fresh install could read its own
        // fresh writes as prior use.
        ProAccess.evaluateGrandfather(this)

        setupConsentGate()
        findViewById<TextView>(R.id.tv_link_licenses).setOnClickListener {
            startActivity(Intent(this, LicensesActivity::class.java))
        }
        val tipjarShowing = setupTipjarCard()

        if (PermissionHelper.canDrawOverlays(this) &&
            PermissionHelper.hasModifyAudioSettings(this) &&
            hasAcceptedTerms() && !tipjarShowing) {
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
            findViewById<TextView>(R.id.btn_tipjar_dismiss).apply {
                setText(R.string.gv_unlocked_dismiss)
                setOnClickListener {
                    Prefs.setUnlockAcknowledged(this@MainActivity)
                    card.visibility = View.GONE
                }
            }
            return true
        }

        val show = BuildConfig.FLAVOR == "play" &&
            Entitlement.isGrandfathered(this) && !Prefs.wasTipjarCardShown(this)
        if (show) {
            card.visibility = View.VISIBLE
            findViewById<TextView>(R.id.btn_tipjar_dismiss).setOnClickListener {
                Prefs.setTipjarCardShown(this)
                card.visibility = View.GONE
            }
            findViewById<TextView>(R.id.btn_tipjar_support).setOnClickListener {
                Prefs.setTipjarCardShown(this)
                card.visibility = View.GONE
                PurchaseFlow.start(this) { }
            }
            return true
        }

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
        val show = BuildConfig.FLAVOR == "play" &&
            ProAccess.isOnTrial(this) &&
            Entitlement.daysLeftInTrial(this) > TRIAL_CARD_FROM_DAYS
        line.visibility = if (show) View.VISIBLE else View.GONE
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

        // Both questions are asked through ProAccess, which excludes a grandfathered user and
        // a buyer by construction. Asking Entitlement directly would tell a grandfathered
        // user their trial is over, which is both false and alarming.
        if (ProAccess.isOnTrial(this)) {
            val daysLeft = Entitlement.daysLeftInTrial(this)
            if (daysLeft > TRIAL_CARD_FROM_DAYS || Prefs.getTrialCardShownForDay(this) == daysLeft) {
                card.visibility = View.GONE
                return false
            }
            Prefs.setTrialCardShownForDay(this, daysLeft)
            card.visibility = View.VISIBLE
            title.text = resources.getQuantityString(R.plurals.gv_trial_days_left, daysLeft, daysLeft)
            body.setText(R.string.gv_trial_body)
            support.visibility = View.VISIBLE
            // 1.6.4: the button says what it does and, once Play has answered, what it costs
            // ("See the price" made the reader open a purchase window to learn a number).
            support.setText(PurchaseFlow.ctaLabel(this))
            refreshCardPrice(support)
            support.setOnClickListener { PurchaseFlow.start(this) { setupTipjarCard() } }
            dismiss.setText(R.string.gv_trial_dismiss)
            dismiss.setOnClickListener { card.visibility = View.GONE }
            return true
        }

        if (!ProAccess.isTrialExpired(this)) { card.visibility = View.GONE; return false }

        card.visibility = View.VISIBLE
        title.setText(R.string.gv_trial_over_title)
        body.setText(R.string.gv_trial_over_body)
        support.visibility = View.VISIBLE
        support.setText(PurchaseFlow.ctaLabel(this))
        refreshCardPrice(support)
        support.setOnClickListener { PurchaseFlow.start(this) { setupTipjarCard() } }
        dismiss.visibility = View.GONE
        return true
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
        val consented = hasAcceptedTerms()

        renderStatePill(findViewById(R.id.tv_overlay_state), overlayOk)
        renderStatePill(findViewById(R.id.tv_audio_state), audioOk)

        findViewById<TextView>(R.id.tv_hint).text =
            getString(if (overlayOk) R.string.gv_hint_ready else R.string.gv_hint_grant_first)

        // Both CTAs stay disabled until the Terms are actively accepted (clickwrap).
        findViewById<Button>(R.id.btn_grant_overlay).isEnabled = consented && !overlayOk
        findViewById<Button>(R.id.btn_start_service).isEnabled = consented && overlayOk
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

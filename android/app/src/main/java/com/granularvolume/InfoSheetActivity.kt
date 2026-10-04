package com.granularvolume

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.granularvolume.service.VolumeControlService
import com.granularvolume.util.AccessState
import com.granularvolume.util.BillingManager
import com.granularvolume.util.ControlLive
import com.granularvolume.util.Entitlement
import com.granularvolume.util.KeyCheck
import com.granularvolume.util.Links
import com.granularvolume.util.Prefs
import com.granularvolume.util.ProAccess
import com.granularvolume.util.TrialNotices
import com.granularvolume.util.PurchaseFlow
import com.granularvolume.util.StatusHeader
import com.granularvolume.util.UpdateCheck

/**
 * "Your access": the sheet behind the dial's info button.
 *
 * Why a sheet and not a screen. The dial exists for late-night listening next to someone
 * asleep. A full, bright screen taking over the display at 2am is the opposite of what
 * this app promises, so this slides up over whatever is running and closes on a tap
 * outside. Same component and palette as the paywall sheet, so the two moments a user
 * meets our commercial side look like one product rather than two.
 *
 * It is also the ONLY place several things can be reached at all:
 *  - during the first four days of a trial nothing is locked, so the paywall never opens
 *    and there was otherwise no way for a convinced user to pay us
 *  - a long-time user had no route to supporting us
 *  - the legal texts were reachable only from a screen that closes itself once set up
 *
 * 1.7.0: three doors now lead here, not one: the dial's info button, a tap on the control's
 * notification, and the launcher icon while the control is running. It reads the single
 * [AccessState] every other surface reads, so a control that is still holding its level
 * after the free week is told exactly that, and never "locked".
 *
 * Read-only by design: it reports state and offers Play. It never writes entitlement
 * itself; 1.6.0's purchase route ([PurchaseFlow]) opens Google Play's own sheet over this
 * one, and the cache it writes belongs to [com.granularvolume.util.BillingManager].
 */
class InfoSheetActivity : AppCompatActivity() {

    private var dialog: BottomSheetDialog? = null

    /** The state this sheet last rendered, so a resume can tell a purchase from a mere return. */
    private var lastState: AccessState? = null

    /** Everything the last render depended on; a re-check redraws only when this changes. */
    private var lastSignature: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The sheet can be the first entry point of the process (a tap on a notice).
        ProAccess.evaluateGrandfather(this)
        dialog = BottomSheetDialog(this).apply {
            setContentView(buildSheet())
            setOnCancelListener { finish() }
            // 1.6.3: open fully in every orientation (same reason as PaywallActivity): in landscape
            // the automatic peek was 64dp, a title strip with the offer hidden below the taskbar.
            behavior.skipCollapsed = true
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            show()
        }
        syncNotices()
        refreshPriceLabel()
    }

    /** The price label this sheet last drew on its buy button (null: no number yet). */
    private var shownPrice: String? = null

    /**
     * 1.6.4: asks Play for the price as the sheet opens and redraws when the answer differs
     * from what the button says. A locked start opens this sheet before the service has
     * fetched anything, so the button used to carry no number at all.
     */
    private fun refreshPriceLabel() {
        BillingManager.refreshPrice(this) { price ->
            if (price != shownPrice) rerender()
        }
    }

    /**
     * 1.6.2: keeps the free-week notices honest with what this sheet just showed. Week over:
     * the person has now been told in the app, so the one-time ended notice has nothing to add.
     * Unlocked: a notice still waiting in the shade would now be wrong.
     */
    private fun syncNotices() {
        when (lastState) {
            AccessState.LOCKED, AccessState.WEEK_ENDED_RUNNING -> TrialNotices.markEndedToldInApp(this)
            AccessState.UNLOCKED -> TrialNotices.cancel(this)
            else -> Unit
        }
    }

    /**
     * Returning from Play with the key installed: re-render rather than show stale state, AND
     * tell the service. This sheet is the only purchase route on trial days 7 to 4 and the
     * route every locked notification opens, and until 2026-09-09 a purchase made from here
     * told the service nothing: the audio latch would open on the next dial touch anyway, but
     * the shade kept saying "Locked" and the buyer got no acknowledgement at all. Same signal
     * PaywallActivity sends, so the two purchase routes now end identically.
     */
    override fun onResume() {
        super.onResume()
        leavingForUpdate = false
        leavingForLink = false
        linkReturn.removeCallbacks(closeUnreturned)
        val before = lastState
        dialog?.setContentView(buildSheet())
        val after = lastState
        syncNotices()
        if (after == AccessState.UNLOCKED && before != null && before != AccessState.UNLOCKED) {
            startService(
                Intent(this, VolumeControlService::class.java)
                    .setAction(VolumeControlService.ACTION_KEY_INSTALLED)
            )
            // No toast: the service announces the purchase itself (see PaywallActivity.onResume).
        }
        recheck()
    }

    /**
     * 1.7.0: every time the sheet comes forward it asks Google Play for this account's purchase
     * record, and the Play Store for a newer version. A buyer on a new device, or one whose
     * payment was confirmed since the last start, is no longer shown a buy button until they
     * find Restore; a refund is no longer shown as "unlocked" until the next service start.
     */
    private fun recheck() {
        BillingManager.verifyOwnership(applicationContext) { _ ->
            if (isFinishing || isDestroyed) return@verifyOwnership
            if (signature() != lastSignature) {
                rerender()
                syncNotices()
                tellService()
            }
        }
        val knownBefore = UpdateCheck.isKnownAvailable(this)
        UpdateCheck.checkNow(applicationContext) { available ->
            // The dial marks its info button from the same answer; a running dial is told now,
            // not at its next start (found on the owner's phone, 2026-10-04).
            if (available != knownBefore) tellService()
            if (!isFinishing && !isDestroyed && signature() != lastSignature) rerender()
        }
    }

    /** The running control repaints its dial and its notification, and opens if a purchase arrived. */
    private fun tellService() {
        if (!ControlLive.running) return
        runCatching {
            startService(
                Intent(this, VolumeControlService::class.java)
                    .setAction(VolumeControlService.ACTION_ACCESS_CHANGED)
            )
        }
    }

    private fun signature(): String =
        "${AccessState.of(this)}|${ProAccess.hasPaidUnlock(this)}|${Entitlement.isPurchasePending(this)}|" +
            "${UpdateCheck.isKnownAvailable(this)}|${ControlLive.effect}|${Prefs.wasSupportCardDone(this)}"

    /** After an in-app purchase or restore: the service was already told by PurchaseFlow; just re-render. */
    private fun rerender() {
        if (isFinishing || isDestroyed) return
        dialog?.setContentView(buildSheet())
    }

    /**
     * 1.7.0: a sheet that has left the screen is closed, not kept.
     *
     * It is a translucent activity in the app's own task. Pressing Home with the sheet open
     * used to leave it there, stopped and invisible (it is excluded from recents). The next
     * tap on the launcher icon put the setup screen on top of it, the setup screen started
     * the control and closed itself, and the old sheet reappeared underneath, unasked. Found
     * by the 1.7.0 regression run: once the icon became a door to this sheet, stale copies of
     * it surfaced on plain starts.
     *
     * Not closed in three cases: a rotation (the activity is being rebuilt), a purchase in
     * flight (Google Play may cover the screen for a moment and must find the sheet on the
     * way back), and the update screen the reader just asked for.
     */
    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations || PurchaseFlow.isInFlight() || leavingForUpdate || leavingForLink) return
        if (!isFinishing) finish()
    }

    /** Set when Update is pressed, cleared when the sheet is back in front. */
    private var leavingForUpdate = false

    /**
     * 1.7.1: set when the reader opens one of this sheet's own links (Terms, Privacy, Help,
     * licences), cleared when the sheet is back in front. Since 1.7.0 the sheet closes when it
     * leaves the screen, which also closed it under the person who went to read the Terms
     * before paying and then pressed Back. Bounded: a sheet nobody came back to still closes.
     */
    private var leavingForLink = false
    private val linkReturn = android.os.Handler(android.os.Looper.getMainLooper())
    private val closeUnreturned = Runnable { if (!isFinishing && !isDestroyed) finish() }

    private fun leaveForLink() {
        leavingForLink = true
        linkReturn.removeCallbacks(closeUnreturned)
        linkReturn.postDelayed(closeUnreturned, LINK_RETURN_MS)
    }

    override fun onDestroy() {
        linkReturn.removeCallbacks(closeUnreturned)
        dialog?.setOnCancelListener(null)
        dialog?.dismiss()
        dialog = null
        super.onDestroy()
    }

    // -- sheet ----------------------------------------------------------------

    /**
     * 1.4.4: the sheet's content is built as a plain LinearLayout, so at a large system font
     * scale it grew past the sheet and the actions at the bottom were simply unreachable.
     * A NestedScrollView keeps the bottom-sheet drag working while letting the content scroll.
     */
    private fun View.inScroller(): View = NestedScrollView(this@InfoSheetActivity).apply {
        isFillViewport = true
        addView(
            this@inScroller,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun buildSheet(): View {
        // The F-Droid check inside AccessState is a FLAVOR check, never a KeyCheck one: the
        // F-Droid KeyCheck stub answers true by design, and reading it here would tell every
        // F-Droid user "Unlocked with the Full Range Key. Thank you." -- gratitude for a
        // purchase that never happened, on the one surface whose audience checks.
        val st = AccessState.of(this)
        lastState = st
        lastSignature = signature()
        val paid = ProAccess.hasPaidUnlock(this)
        val pending = Entitlement.isPurchasePending(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(context, R.color.gv_surface))
            val p = dp(24)
            setPadding(p, p, p, dp(28))
        }

        // 1.7.0: a newer version exists. One quiet row, first thing on the sheet, and nothing
        // happens until the reader presses Update.
        if (UpdateCheck.isSupported && UpdateCheck.isKnownAvailable(this)) root.addView(updateRow())

        val headline = when (st) {
            AccessState.FREE_BUILD -> getString(R.string.gv_info_state_fdroid)
            AccessState.FREE_FOR_GOOD -> getString(R.string.gv_info_state_grandfathered)
            // A key owner is thanked for the key; an in-app buyer for the account the purchase lives on.
            AccessState.UNLOCKED -> getString(
                if (KeyCheck.isKeyInstalled(this)) R.string.gv_info_state_unlocked
                else R.string.gv_info_state_purchased
            )
            // One headline for both: the week has ended. What follows from it differs, and the
            // body says which: a dial that is locked, or a level that is still being held.
            AccessState.LOCKED, AccessState.WEEK_ENDED_RUNNING -> getString(R.string.gv_info_state_locked)
            AccessState.FREE_WEEK, AccessState.LAST_DAY -> {
                val d = Entitlement.daysLeftInTrial(this)
                resources.getQuantityString(R.plurals.gv_trial_days_left, d, d)
            }
        }
        // 1.6.0: a status badge beside the headline (owner's request), so the state reads at a glance.
        val kind = when (st) {
            AccessState.LOCKED, AccessState.WEEK_ENDED_RUNNING -> StatusHeader.Kind.LOCKED
            AccessState.FREE_WEEK, AccessState.LAST_DAY -> StatusHeader.Kind.TRIAL
            AccessState.UNLOCKED, AccessState.FREE_FOR_GOOD, AccessState.FREE_BUILD -> StatusHeader.Kind.OPEN
        }
        root.addView(
            StatusHeader.build(this, text(headline, 19f, bold = true, colorRes = R.color.gv_text_primary), kind)
        )

        // 1.7.0: the support card for people who keep everything free. It is the body of their
        // sheet until they answer it, once; afterwards the sheet goes back to the plain
        // statement with a quiet link, and it never asks again.
        val supportCard = st == AccessState.FREE_FOR_GOOD && !paid && !Prefs.wasSupportCardDone(this)

        val body = when (st) {
            AccessState.FREE_BUILD -> R.string.gv_info_body_fdroid
            AccessState.FREE_FOR_GOOD -> when {
                paid -> R.string.gv_support_thanks
                supportCard -> R.string.gv_support_card_body
                else -> R.string.gv_info_body_grandfathered
            }
            AccessState.UNLOCKED -> R.string.gv_info_body_unlocked
            // On the final day the generic trial body would bury the one fact that matters.
            AccessState.LAST_DAY -> R.string.gv_info_body_trial_last_day
            AccessState.FREE_WEEK -> R.string.gv_info_body_trial
            AccessState.LOCKED -> R.string.gv_info_body_locked
            AccessState.WEEK_ENDED_RUNNING -> R.string.gv_info_body_held
        }
        root.addView(text(getString(body), 13f, colorRes = R.color.gv_text_secondary).topPad(8))

        // 1.7.0: when the device is the reason the quiet steps do nothing, say so, here, for as
        // long as it is true. Only a running control knows; a stopped one says nothing.
        if (ControlLive.running) {
            val effectLine = when (ControlLive.effect) {
                ControlLive.Effect.NONE -> R.string.gv_effect_none
                ControlLive.Effect.FALLBACK -> R.string.gv_effect_fallback
                else -> 0
            }
            if (effectLine != 0) {
                root.addView(text(getString(effectLine), 13f, colorRes = R.color.gv_warning).topPad(10))
            }
        }

        // A buyer is offered nothing: they already paid, and a live "buy" button would read
        // as a second charge. F-Droid is offered nothing either: that build has no key and
        // pointing its users at Google Play would betray the promise the listing makes.
        // 1.6.4: a payment Play is still confirming (cash, bank transfer) is said on the sheet
        // itself; until now only a toast said so, once, and the sheet went on reading "locked".
        // 1.7.0: and while it is pending the buy button is gone, so nobody orders twice.
        val offers = st != AccessState.UNLOCKED && st != AccessState.FREE_BUILD && !paid
        if (offers && pending) {
            root.addView(text(getString(R.string.gv_purchase_pending), 13f, colorRes = R.color.gv_text_primary).topPad(10))
        }
        shownPrice = BillingManager.priceOrNull(this)
        when {
            !offers -> Unit
            supportCard -> root.addView(supportButtons(pending).topPad(18, fill = true))
            st == AccessState.FREE_FOR_GOOD ->
                // Answered before: no card, no button, one quiet line that keeps the route open.
                if (!pending) root.addView(link(getString(R.string.gv_info_cta_support)) { buy() }
                    .apply { gravity = Gravity.CENTER }.topPad(8, fill = true))
            else -> {
                if (!pending) root.addView(buyButton(PurchaseFlow.ctaLabel(this)).topPad(18, fill = true))
                root.addView(link(getString(R.string.gv_purchase_restore_link)) {
                    PurchaseFlow.restore(this) { rerender() }
                }.apply { gravity = Gravity.CENTER }.topPad(2, fill = true))
            }
        }

        // The restore note must describe the reader's OWN restore. A grandfathered user
        // never bought a key, so "install the key again, it is never charged twice" would
        // be false for them -- their access travels with Android backup instead. F-Droid
        // has neither key nor backup story worth a line here, so it gets none.
        when (st) {
            AccessState.FREE_BUILD -> Unit
            AccessState.FREE_FOR_GOOD -> root.addView(
                text(getString(R.string.gv_info_restore_grandfathered), 12f, colorRes = R.color.gv_text_muted)
                    .topPad(14)
            )
            // A key owner restores by reinstalling the key; everyone else by signing in to Play.
            else -> root.addView(
                text(
                    getString(
                        if (KeyCheck.isKeyInstalled(this)) R.string.gv_info_restore
                        else R.string.gv_info_restore_iap
                    ),
                    12f, colorRes = R.color.gv_text_muted
                ).topPad(if (st == AccessState.UNLOCKED) 18 else 14)
            )
        }

        root.addView(linksBlock().topPad(14, fill = true))
        return root.inScroller()
    }

    // -- purchase -------------------------------------------------------------

    /**
     * One attempt at a time (1.7.0). The tap redraws the sheet at once, so the button reads
     * "Opening Google Play..." and is disabled until the attempt has an outcome; whatever the
     * outcome, the sheet is redrawn and the button is back.
     */
    private fun buy() {
        if (PurchaseFlow.isInFlight()) return
        PurchaseFlow.start(this, onSettled = { rerender() }, onUnlocked = { rerender() })
        rerender()
    }

    private fun buyButton(label: String): Button = Button(this).apply {
        text = label
        isAllCaps = false
        isEnabled = !PurchaseFlow.isInFlight()
        setTextColor(ContextCompat.getColor(context, R.color.gv_on_accent))
        setBackgroundColor(ContextCompat.getColor(context, R.color.gv_accent))
        alpha = if (isEnabled) 1f else 0.6f
        setOnClickListener { buy() }
    }

    /**
     * The support card's two answers, side by side and the same size: "Not now" is as easy to
     * reach as "Support". No countdown, no repeat, nothing held back. "Not now" retires the
     * card for good; a purchase replaces the whole block with a thank-you.
     */
    private fun supportButtons(pending: Boolean): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val half = { LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        // One look for both answers: same size, same outline, same text colour. A filled
        // "Support" beside an outlined "Not now" would be the app leaning on the reader, on the
        // one card whose whole point is that nothing is asked of them.
        fun answer(label: String, onClick: () -> Unit): Button = Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(ContextCompat.getColor(context, R.color.gv_text_primary))
            background = GradientDrawable().apply {
                cornerRadius = dp(4).toFloat()
                setColor(ContextCompat.getColor(context, R.color.gv_surface))
                setStroke(dp(1), ContextCompat.getColor(context, R.color.gv_accent_text))
            }
            setOnClickListener { onClick() }
        }
        row.addView(answer(getString(R.string.gv_paywall_not_now)) {
            Prefs.setSupportCardDone(this@InfoSheetActivity)
            rerender()
        }, half().apply { marginEnd = dp(6) })
        if (!pending) {
            val price = BillingManager.priceOrNull(this)
            val label = when {
                PurchaseFlow.isInFlight() -> getString(R.string.gv_purchase_opening)
                price != null -> getString(R.string.gv_support_cta_price, price)
                else -> getString(R.string.gv_support_cta)
            }
            // Opening Google Play and closing it again without paying is not an answer: the
            // card stays until the reader says "Not now" or pays.
            row.addView(answer(label) { buy() }.apply {
                isEnabled = !PurchaseFlow.isInFlight()
                alpha = if (isEnabled) 1f else 0.6f
            }, half().apply { marginStart = dp(6) })
        }
        return row
    }

    // -- update ---------------------------------------------------------------

    private fun updateRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(ContextCompat.getColor(context, R.color.gv_success_dim))
            }
            setPadding(dp(14), dp(4), dp(6), dp(4))
        }
        row.addView(
            text(getString(R.string.gv_update_ready), 13f, colorRes = R.color.gv_text_primary),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        row.addView(link(getString(R.string.gv_update_cta)) {
            leavingForUpdate = true
            UpdateCheck.startUpdate(this)
        }.apply {
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(ContextCompat.getColor(context, R.color.gv_success))
        })
        row.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(16) }
        return row
    }

    // -- links ----------------------------------------------------------------

    /**
     * Two rows (1.7.0). Four links on one line were 12sp text with 8dp of padding: small
     * targets, and on a narrow screen or a large font the last one fell off the edge. Help
     * and the tour sit together above the three legal texts, and every link is a 48dp target
     * announced as a button.
     */
    private fun linksBlock(): View {
        val block = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val first = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        // 1.5.1: replay the feature tour. The service opens the dial if it is docked, then
        // runs the four callouts; the sheet closes so the dial is unobstructed.
        first.addView(link(getString(R.string.gv_tour_replay)) {
            startService(
                Intent(this, VolumeControlService::class.java)
                    .apply { action = VolumeControlService.ACTION_SHOW_TOUR }
            )
            finish()
        })
        first.addView(link(getString(R.string.gv_help)) { leaveForLink(); Links.open(this, Links.HELP) })
        val second = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        second.addView(link(getString(R.string.gv_info_terms)) { openUrl(URL_TERMS) })
        second.addView(link(getString(R.string.gv_info_privacy)) { openUrl(URL_PRIVACY) })
        second.addView(link(getString(R.string.gv_licenses_title)) {
            leaveForLink()
            startActivity(
                Intent(this, LicensesActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        })
        block.addView(first)
        block.addView(second)
        return block
    }

    private fun link(label: String, action: () -> Unit): TextView = TextView(this).apply {
        text = label
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(ContextCompat.getColor(context, R.color.gv_accent_text))
        gravity = Gravity.CENTER
        minHeight = dp(48)
        minWidth = dp(48)
        setPadding(dp(10), 0, dp(10), 0)
        setOnClickListener { action() }
        // A TextView with a click listener is announced as plain text; a reader is never told
        // it can be activated. Every link on this sheet carries the Button role.
        isFocusable = true
        accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = Button::class.java.name
            }
        }
    }

    private fun openUrl(url: String) {
        leaveForLink()
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    private fun text(value: String, sizeSp: Float, bold: Boolean = false, colorRes: Int): TextView =
        TextView(this).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            setTextColor(ContextCompat.getColor(context, colorRes))
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun <T : View> T.topPad(topDp: Int, fill: Boolean = false): T {
        layoutParams = LinearLayout.LayoutParams(
            if (fill) LinearLayout.LayoutParams.MATCH_PARENT else LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(topDp) }
        return this
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private companion object {
        const val URL_TERMS = "https://rzuss.github.io/granular-volume-privacy/terms-of-use.html"
        const val URL_PRIVACY = "https://rzuss.github.io/granular-volume-privacy/"
        /** How long a sheet waits for its reader to come back from one of its links. */
        const val LINK_RETURN_MS = 10 * 60_000L
    }
}

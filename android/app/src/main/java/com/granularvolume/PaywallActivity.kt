package com.granularvolume

import android.content.Intent
import android.graphics.Typeface
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
import com.granularvolume.util.Entitlement
import com.granularvolume.util.TrialNotices
import com.granularvolume.util.ProAccess
import com.granularvolume.util.PurchaseFlow
import com.granularvolume.util.StatusHeader

/**
 * Full-range upgrade sheet (1.5.0). Launched by the coordinator the moment a locked
 * gesture is refused: a quiet step, an upper-zone move, or mute.
 *
 * Nothing is playing behind this sheet. The device is held at 0 dB and stays there;
 * the reader has already spent seven days with the full range and needs no reminder
 * of what it sounds like, only a way to get it back.
 *
 * 1.7.0: it also opens over a control that is still holding its level after the free week
 * (the gesture that would change the level is what gets refused). There the level is NOT at
 * 0 dB and does not move; the sheet says so in one line, and never calls that dial locked.
 *
 * Lifecycle contract with the service:
 *  - Dismissed without buying (Not now, tap outside, back, swipe): nothing to undo.
 *  - CTA opens Google Play's purchase sheet over this activity (1.6.0); a completed
 *    purchase closes it through closeUnlocked(). onResume still re-checks ProAccess and
 *    sends ACTION_KEY_INSTALLED, which covers a key app that was installed earlier by
 *    someone who already owns it; the app itself no longer points anyone at the key app.
 *
 * 1.6.0: the CTA opens Google Play's own purchase sheet over this one ([PurchaseFlow]);
 * nothing leaves the app. The price on the button is the one Play reported for this
 * account and region, so it cannot go stale; until Play has answered, the button carries
 * no number. Since 1.6.1 the key app is not offered as a fallback; a key owner is unlocked
 * exactly as before.
 */
class PaywallActivity : AppCompatActivity() {

    private var dialog: BottomSheetDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dialog = BottomSheetDialog(this).apply {
            setContentView(buildSheet())
            setOnCancelListener { finish() }
            // 1.6.3: open fully in every orientation. On a wide, short screen (a tablet or a phone
            // held sideways) Material's automatic peek height is its 64dp minimum, so the sheet
            // opened as a title strip and the price and the buy button sat below the taskbar
            // until the reader thought to drag it up. Swipe down still closes it.
            behavior.skipCollapsed = true
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            show()
        }
        // 1.6.2: the paywall only opens on a locked gesture, so the person now knows the week is
        // over; the one-time ended notice has nothing left to say.
        TrialNotices.markEndedToldInApp(this)
        // 1.6.4: the price is asked for as the sheet opens; the button is redrawn when the
        // answer differs from what it says (it opened with no number on a cold process).
        BillingManager.refreshPrice(this) { price ->
            if (price != shownPrice && !isFinishing && !isDestroyed) dialog?.setContentView(buildSheet())
        }
    }

    /** The price label this sheet last drew on its buy button (null: no number yet). */
    private var shownPrice: String? = null

    /**
     * Re-checked on EVERY resume, not only on the first return from the store.
     *
     * Until 2026-09-09 this was gated on a one-shot "leavingForStore" flag that was cleared
     * before the check. A buyer who came back while the key was still downloading (the
     * ordinary case on mobile data) consumed the flag, saw a sheet still reading "Your seven
     * days are over", and never got the confirmation or the replay of the refused step. The
     * gate was designed for the moment it failed in. Checking unconditionally costs two pref
     * reads and one PackageManager call per resume, and cannot misfire: this sheet only ever
     * opens on a refused gesture, which requires the range to be locked at that instant.
     */
    override fun onResume() {
        super.onResume()
        if (ProAccess.isPro(this)) { closeUnlocked(); return }
        // 1.7.0: ask Google Play too. A buyer whose purchase this device has not heard of yet
        // (new device, reinstall, a payment confirmed since the last start) is let in here,
        // instead of being shown the price of something they own.
        BillingManager.verifyOwnership(applicationContext) { _ ->
            if (isFinishing || isDestroyed) return@verifyOwnership
            if (ProAccess.isPro(this)) closeUnlocked()
            else if (Entitlement.isPurchasePending(this) != shownPending) redraw()
        }
    }

    /** Whether the sheet as drawn says "payment pending" (and so has no buy button). */
    private var shownPending = false

    private fun redraw() {
        if (!isFinishing && !isDestroyed) dialog?.setContentView(buildSheet())
    }

    /** One attempt at a time (1.7.0): the button says "Opening Google Play..." until it has an outcome. */
    private fun buy() {
        if (PurchaseFlow.isInFlight()) return
        PurchaseFlow.start(this, onSettled = { redraw() }, onUnlocked = { closeUnlocked() })
        redraw()
    }

    /**
     * The range is open (in-app purchase, restore, or the key app): tell the service and leave.
     * Since 2026-09-10 the service learns of the key from the package broadcast the moment its
     * install completes, and announces the purchase itself (one toast, the dial lights up, the
     * refused step lands). This signal is the fallback door and a no-op when that already
     * happened. No toast here, or a buyer would get two.
     */
    private fun closeUnlocked() {
        serviceAction(VolumeControlService.ACTION_KEY_INSTALLED)
        dialog?.setOnCancelListener(null)
        dialog?.dismiss()
        finish()
    }

    /**
     * 1.7.0: closed when it leaves the screen, for the reason given at InfoSheetActivity.onStop:
     * a sheet left behind by the Home key came back, unasked, under the next tap on the app
     * icon. Kept through a rotation and while a purchase is in flight.
     */
    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations || PurchaseFlow.isInFlight()) return
        if (!isFinishing) finish()
    }

    override fun onDestroy() {
        dialog?.setOnCancelListener(null)
        dialog?.dismiss()
        dialog = null
        super.onDestroy()
    }

    private fun serviceAction(action: String) {
        startService(Intent(this, VolumeControlService::class.java).setAction(action))
    }

    // ── sheet UI, programmatic — matches the app palette, adds no layout file ──

    /**
     * 1.4.4: the sheet's content is built as a plain LinearLayout, so at a large system font
     * scale it grew past the sheet and the actions at the bottom were simply unreachable.
     * A NestedScrollView keeps the bottom-sheet drag working while letting the content scroll.
     */
    private fun View.inScroller(): View = NestedScrollView(this@PaywallActivity).apply {
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
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(context, R.color.gv_surface))
            val p = dp(24)
            setPadding(p, p, p, dp(28))
        }

        // 1.6.0: the same locked badge the info sheet shows, so both sheets say "locked" the same way.
        root.addView(
            StatusHeader.build(
                this,
                text(R.string.gv_paywall_title, 19f, bold = true, colorRes = R.color.gv_text_primary),
                StatusHeader.Kind.LOCKED
            )
        )
        // 1.7.0: the control is still on and still holding its level. Say that first: the
        // reader's first question, with a sheet suddenly over their video, is "did it change?".
        if (AccessState.of(this) == AccessState.WEEK_ENDED_RUNNING) {
            root.addView(text(R.string.gv_paywall_held, 14f, bold = true, colorRes = R.color.gv_text_primary).topPad(8))
        }
        root.addView(text(R.string.gv_paywall_depth, 14f, colorRes = R.color.gv_text_secondary).topPad(6))
        root.addView(text(R.string.gv_paywall_body, 13f, colorRes = R.color.gv_text_secondary).topPad(12))
        root.addView(text(R.string.gv_paywall_expectation_iap, 12f, colorRes = R.color.gv_text_muted).topPad(8))

        // 1.6.4: a payment Play is still confirming is said here, not only in a passing toast.
        // 1.7.0: and while it is pending the buy button is gone, so nobody orders twice.
        shownPending = Entitlement.isPurchasePending(this)
        if (shownPending) {
            root.addView(text(R.string.gv_purchase_pending, 13f, colorRes = R.color.gv_text_primary).topPad(10))
        }
        shownPrice = BillingManager.priceOrNull(this)
        if (!shownPending) root.addView(Button(this).apply {
            text = PurchaseFlow.ctaLabel(context)
            isAllCaps = false
            isEnabled = !PurchaseFlow.isInFlight()
            alpha = if (isEnabled) 1f else 0.6f
            setTextColor(ContextCompat.getColor(context, R.color.gv_on_accent))
            setBackgroundColor(ContextCompat.getColor(context, R.color.gv_accent))
            setOnClickListener { buy() }
        }.topPad(18, fill = true))
        root.addView(text(R.string.gv_purchase_restore_link, 13f, colorRes = R.color.gv_accent_text).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(4))
            asButtonForAccessibility()
            setOnClickListener { PurchaseFlow.restore(this@PaywallActivity) { closeUnlocked() } }
        }.topPad(4, fill = true))

        root.addView(text(R.string.gv_paywall_terms, 13f, colorRes = R.color.gv_accent_text).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(10))
            asButtonForAccessibility()
            setOnClickListener { openUrl(URL_TERMS) }
        }.topPad(4, fill = true))

        root.addView(TextView(this).apply {
            text = getString(R.string.gv_paywall_not_now)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.gv_text_secondary))
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
            asButtonForAccessibility()
            setOnClickListener { finish() }
        }.topPad(6, fill = true))

        return root.inScroller()
    }

    /**
     * A TextView with a click listener is announced by TalkBack as plain text, so a reader is
     * never told it can be activated. Both of this sheet's text actions carry the Button role.
     */
    private fun View.asButtonForAccessibility() {
        isFocusable = true
        accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = Button::class.java.name
            }
        }
    }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    private fun text(res: Int, sizeSp: Float, bold: Boolean = false, colorRes: Int): TextView =
        TextView(this).apply {
            text = getString(res)
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
        // Same document the consent gate links to (MainActivity.URL_TERMS).
        const val URL_TERMS = "https://rzuss.github.io/granular-volume-privacy/terms-of-use.html"
    }
}

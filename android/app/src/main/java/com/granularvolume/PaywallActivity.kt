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
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.granularvolume.service.VolumeControlService
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
            show()
        }
    }

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
        if (ProAccess.isPro(this)) closeUnlocked()
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
        root.addView(text(R.string.gv_paywall_depth, 14f, colorRes = R.color.gv_text_secondary).topPad(6))
        root.addView(text(R.string.gv_paywall_body, 13f, colorRes = R.color.gv_text_secondary).topPad(12))
        root.addView(text(R.string.gv_paywall_expectation_iap, 12f, colorRes = R.color.gv_text_muted).topPad(8))

        root.addView(Button(this).apply {
            text = PurchaseFlow.ctaLabel(context)
            isAllCaps = false
            setTextColor(ContextCompat.getColor(context, R.color.gv_on_accent))
            setBackgroundColor(ContextCompat.getColor(context, R.color.gv_accent))
            setOnClickListener { PurchaseFlow.start(this@PaywallActivity) { closeUnlocked() } }
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

package com.granularvolume.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.granularvolume.R
import com.granularvolume.service.VolumeControlService

/**
 * The one purchase route every sheet and card uses (1.6.0). It turns a [PurchaseOutcome]
 * into exactly what the person should see, and tells the service when the range opened,
 * so the paywall, the info sheet and the main screen all end a purchase identically.
 *
 * Nothing here decides entitlement: [BillingManager] caches it, [ProAccess] reads it.
 *
 * 1.7.0: one attempt at a time. A second tap while Google Play is opening used to start a
 * second purchase flow, whose listener replaced the first one's: the first attempt then ended
 * with nobody listening. [isInFlight] lets every buy button say "Opening Google Play..." and
 * ignore taps until the attempt has an outcome, and every outcome gives the button back.
 */
object PurchaseFlow {

    private const val TAG = "GranularVolume:Purchase"
    /** BillingClient.BillingResponseCode values; literals because this file is flavor-neutral. */
    private const val BILLING_UNAVAILABLE = 3
    private const val ITEM_UNAVAILABLE = 4

    /**
     * How often the watchdog looks. It gives the button back only after seeing the host
     * screen in front twice in a row with no outcome, which a healthy flow cannot produce:
     * while Google Play's sheet is up the host is paused, and when the sheet closes the
     * outcome arrives within the same second.
     */
    private const val WATCHDOG_MS = 12_000L

    private val main = Handler(Looper.getMainLooper())
    private var inFlight = false
    private var attempt = 0

    /** True from a tap on a buy button until that attempt has an outcome. */
    fun isInFlight(): Boolean = inFlight

    /**
     * Button label for where the user stands (1.7.0): during the free week everything is
     * already open, so the button keeps it; once the week is over it unlocks. The live price
     * follows when Play has told us; until then the label carries no number.
     */
    fun ctaLabel(context: Context): String {
        if (inFlight) return context.getString(R.string.gv_purchase_opening)
        val price = BillingManager.priceOrNull(context)
        val keep = AccessState.of(context).onTrial
        return when {
            keep && price != null -> context.getString(R.string.gv_purchase_cta_keep_price, price)
            keep -> context.getString(R.string.gv_purchase_cta_keep)
            price != null -> context.getString(R.string.gv_purchase_cta_price, price)
            else -> context.getString(R.string.gv_purchase_cta)
        }
    }

    /**
     * Opens Google Play's purchase sheet and handles every way it can end.
     * [onUnlocked] runs on the main thread after a purchase or a successful restore, once the
     * service has been told; the caller re-renders or closes.
     * [onSettled] runs on the main thread when the attempt has ended in ANY way, before
     * [onUnlocked] or a dialog: the caller redraws its button.
     */
    fun start(activity: Activity, onSettled: () -> Unit = {}, onUnlocked: () -> Unit) {
        if (inFlight) {
            Log.i(TAG, "Tap ignored: a purchase attempt is already opening")
            return
        }
        inFlight = true
        val mine = ++attempt
        watch(activity, mine, seenInFront = false, onSettled = onSettled, onUnlocked = onUnlocked)
        BillingManager.launchPurchase(activity) { outcome ->
            Log.i(TAG, "Outcome: ${outcome::class.simpleName}")
            val live = inFlight && mine == attempt
            if (live) {
                inFlight = false
                onSettled()
            }
            when (outcome) {
                // A purchase is honoured even when it arrives after the watchdog gave up.
                PurchaseOutcome.Purchased, PurchaseOutcome.AlreadyOwned -> {
                    notifyService(activity)
                    onUnlocked()
                }
                PurchaseOutcome.Pending ->
                    if (live) Toast.makeText(activity, R.string.gv_purchase_pending, Toast.LENGTH_LONG).show()
                PurchaseOutcome.Canceled -> Unit
                is PurchaseOutcome.Unavailable ->
                    if (live) showUnavailable(activity, onSettled, onUnlocked, outcome.code)
            }
        }
    }

    private fun watch(
        activity: Activity, mine: Int, seenInFront: Boolean,
        onSettled: () -> Unit, onUnlocked: () -> Unit
    ) {
        main.postDelayed({
            if (!inFlight || mine != attempt) return@postDelayed
            if (activity.isFinishing || activity.isDestroyed) { inFlight = false; return@postDelayed }
            val inFront = (activity as? LifecycleOwner)?.lifecycle?.currentState
                ?.isAtLeast(Lifecycle.State.RESUMED) ?: true
            if (inFront && seenInFront) {
                Log.w(TAG, "No outcome and no Google Play sheet: giving the button back")
                inFlight = false
                onSettled()
                showUnavailable(activity, onSettled, onUnlocked)
            } else {
                watch(activity, mine, inFront, onSettled, onUnlocked)
            }
        }, WATCHDOG_MS)
    }

    /** "Already bought it? Restore": Play's own record for this account decides. */
    fun restore(activity: Activity, onUnlocked: () -> Unit) {
        BillingManager.restore(activity) { owned ->
            when (owned) {
                true -> {
                    Toast.makeText(activity, R.string.gv_purchase_restored, Toast.LENGTH_LONG).show()
                    notifyService(activity)
                    onUnlocked()
                }
                false -> showNotFound(activity, onUnlocked)
                null -> showUnavailable(activity, {}, onUnlocked)
            }
        }
    }

    /**
     * 1.7.0: a restore that finds nothing says why that usually happens. The old toast ("No
     * purchase found on this Google account") left a buyer who is signed in to Google Play
     * with a different account with nothing to do next.
     */
    private fun showNotFound(activity: Activity, onUnlocked: () -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) return
        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.gv_purchase_not_found)
            .setMessage(R.string.gv_purchase_not_found_hint)
            .setPositiveButton(R.string.gv_purchase_retry) { _, _ -> restore(activity, onUnlocked) }
            .setNegativeButton(R.string.gv_dialog_close, null)
            .show()
        sentenceCase(dialog)
    }

    /**
     * Play could not run the purchase here. One honest line and a retry. Since 1.6.1 the app
     * never points anyone at the separate key app: the in-app purchase is the only route
     * offered, so nobody is sent to buy a second app (owner's decision, 2026-09-28). A key that
     * is already installed still unlocks, see [ProAccess.hasPaidUnlock].
     */
    private fun showUnavailable(activity: Activity, onSettled: () -> Unit, onUnlocked: () -> Unit, code: Int = -1) {
        if (activity.isFinishing || activity.isDestroyed) return
        val builder = AlertDialog.Builder(activity)
        if (code == ITEM_UNAVAILABLE) {
            // 1.7.0: Play has nothing to sell this account here. Retrying cannot change that,
            // and the reader's first worry is the money.
            builder.setTitle(R.string.gv_purchase_not_for_sale_title)
                .setMessage(R.string.gv_purchase_not_for_sale)
                .setPositiveButton(R.string.gv_dialog_close, null)
        } else {
            builder.setTitle(R.string.gv_purchase_unavailable)
                // 1.6.4: Play saying "billing is not available here" (3) is not a connection problem,
                // and telling that person to check the connection sent them the wrong way.
                .setMessage(
                    if (code == BILLING_UNAVAILABLE) R.string.gv_purchase_unavailable_billing_hint
                    else R.string.gv_purchase_unavailable_hint
                )
                .setPositiveButton(R.string.gv_purchase_retry) { _, _ -> start(activity, onSettled, onUnlocked) }
                .setNegativeButton(R.string.gv_paywall_not_now, null)
        }
        sentenceCase(builder.show())
    }

    /** Sentence case, like every other button in the app; the theme's default shouts. */
    private fun sentenceCase(dialog: AlertDialog) {
        for (which in intArrayOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE)) {
            dialog.getButton(which)?.isAllCaps = false
        }
    }

    /** The service re-applies the refused step, plays the unlock wave and repaints the shade. */
    fun notifyService(context: Context) {
        TrialNotices.cancel(context)   // 1.6.2: a free-week notice still in the shade is now wrong
        runCatching {
            context.startService(
                Intent(context, VolumeControlService::class.java)
                    .setAction(VolumeControlService.ACTION_KEY_INSTALLED)
            )
        }
    }
}

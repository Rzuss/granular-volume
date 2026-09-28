package com.granularvolume.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.granularvolume.R
import com.granularvolume.service.VolumeControlService

/**
 * The one purchase route every sheet and card uses (1.6.0). It turns a [PurchaseOutcome]
 * into exactly what the person should see, and tells the service when the range opened,
 * so the paywall, the info sheet and the main screen all end a purchase identically.
 *
 * Nothing here decides entitlement: [BillingManager] caches it, [ProAccess] reads it.
 */
object PurchaseFlow {

    private const val TAG = "GranularVolume:Purchase"

    /** Button label: the live price when Play has told us, a plain label until then. */
    fun ctaLabel(context: Context): String {
        val price = BillingManager.priceOrNull(context)
        return if (price != null) context.getString(R.string.gv_purchase_cta_price, price)
        else context.getString(R.string.gv_purchase_cta)
    }

    /**
     * Opens Google Play's purchase sheet and handles every way it can end.
     * [onUnlocked] runs on the main thread after a purchase or a successful restore, once the
     * service has been told; the caller re-renders or closes.
     */
    fun start(activity: Activity, onUnlocked: () -> Unit) {
        BillingManager.launchPurchase(activity) { outcome ->
            Log.i(TAG, "Outcome: ${outcome::class.simpleName}")
            when (outcome) {
                PurchaseOutcome.Purchased, PurchaseOutcome.AlreadyOwned -> {
                    notifyService(activity)
                    onUnlocked()
                }
                PurchaseOutcome.Pending ->
                    Toast.makeText(activity, R.string.gv_purchase_pending, Toast.LENGTH_LONG).show()
                PurchaseOutcome.Canceled -> Unit
                is PurchaseOutcome.Unavailable -> showUnavailable(activity, onUnlocked)
            }
        }
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
                false -> Toast.makeText(activity, R.string.gv_purchase_not_found, Toast.LENGTH_LONG).show()
                null -> showUnavailable(activity, onUnlocked)
            }
        }
    }

    /**
     * Play could not run the purchase here. One honest line and a retry. Since 1.6.1 the app
     * never points anyone at the separate key app: the in-app purchase is the only route
     * offered, so nobody is sent to buy a second app (owner's decision, 2026-09-28). A key that
     * is already installed still unlocks, see [ProAccess.hasPaidUnlock].
     */
    private fun showUnavailable(activity: Activity, onUnlocked: () -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) return
        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.gv_purchase_unavailable)
            .setMessage(R.string.gv_purchase_unavailable_hint)
            .setPositiveButton(R.string.gv_purchase_retry) { _, _ -> start(activity, onUnlocked) }
            .setNegativeButton(R.string.gv_paywall_not_now, null)
            .show()
        // Sentence case, like every other button in the app; the theme's default shouts.
        for (which in intArrayOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE)) {
            dialog.getButton(which)?.isAllCaps = false
        }
    }

    /** The service re-applies the refused step, plays the unlock wave and repaints the shade. */
    private fun notifyService(context: Context) {
        context.startService(
            Intent(context, VolumeControlService::class.java)
                .setAction(VolumeControlService.ACTION_KEY_INSTALLED)
        )
    }
}

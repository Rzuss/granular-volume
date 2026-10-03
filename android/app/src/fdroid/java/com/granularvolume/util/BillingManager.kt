package com.granularvolume.util

import android.app.Activity
import android.content.Context

/**
 * F-Droid flavor: there is nothing to buy. The full range is free and complete, and this
 * build carries no Google code at all. Same signatures as the play-flavor [BillingManager],
 * the same pattern as the flavor-split [ReviewHelper] and [KeyCheck]; every call is a no-op
 * that answers "unavailable". No sheet on this flavor ever reaches these calls, because
 * [KeyCheck] answers true here and the sheets offer nothing to an unlocked device.
 */
object BillingManager {

    const val PRODUCT_ID = "full_range_unlock"

    const val isAvailable = false

    @Suppress("UNUSED_PARAMETER")
    fun priceOrNull(context: Context): String? = null

    @Suppress("UNUSED_PARAMETER")
    fun prefetch(context: Context, onOwnership: ((Boolean?) -> Unit)? = null) = Unit

    @Suppress("UNUSED_PARAMETER")
    fun refreshPrice(context: Context, onPrice: (String?) -> Unit) = onPrice(null)

    @Suppress("UNUSED_PARAMETER")
    fun verifyOwnership(context: Context, onResult: ((Boolean?) -> Unit)?) {
        onResult?.invoke(null)
    }

    @Suppress("UNUSED_PARAMETER")
    fun launchPurchase(activity: Activity, listener: (PurchaseOutcome) -> Unit) =
        listener(PurchaseOutcome.Unavailable(3, "no billing on this build"))

    @Suppress("UNUSED_PARAMETER")
    fun restore(context: Context, onResult: (Boolean?) -> Unit) = onResult(null)
}

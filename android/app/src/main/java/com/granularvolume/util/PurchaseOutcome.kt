package com.granularvolume.util

/**
 * What a purchase attempt or a restore came back with. Shared by both flavors so the
 * activities can react to it; only the play flavor's [BillingManager] ever produces
 * anything other than [Unavailable].
 */
sealed class PurchaseOutcome {
    /** Paid and acknowledged. The entitlement is already cached when this is delivered. */
    object Purchased : PurchaseOutcome()

    /** Google Play accepted the order but the payment is not complete yet (cash, bank transfer). */
    object Pending : PurchaseOutcome()

    /** The user closed Google Play's sheet. Nothing to say. */
    object Canceled : PurchaseOutcome()

    /** Google Play reports the item as already owned on this account; the entitlement is cached. */
    object AlreadyOwned : PurchaseOutcome()

    /**
     * Google Play could not run the purchase: no billing on this device, no Play account,
     * no connection, or the product is not on sale. [code] is Google's response code, kept
     * for the log only.
     */
    data class Unavailable(val code: Int, val message: String) : PurchaseOutcome()
}

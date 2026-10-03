package com.granularvolume.util

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * Play flavor: the in-app purchase of the full range, through Google Play's own sheet.
 *
 * The app never talks to a server. Every call here is IPC to the Play Store app on the
 * device, which is why this works with no INTERNET permission: Play carries the order,
 * the receipt and the refund; we read the result. Google's billing telemetry, the one
 * part of the library that would phone home, is excluded from the build
 * (see build.gradle.kts), and the library tolerates its absence.
 *
 * What is cached, and why it is safe:
 *  - [Entitlement.isPurchased] is written only from a PURCHASED, acknowledged purchase, or
 *    from a query that found one. It is read by [ProAccess.isPro] so the dial does not wait
 *    for Play on every gesture.
 *  - [verifyOwnership] runs on every service start. A query that answers OK with no purchase
 *    clears the cache (a refund, a chargeback, or a restored backup on another account). A
 *    query that cannot answer (no Play, no account) leaves the cache alone: an outage must
 *    never lock a buyer out.
 *  - Acknowledgement is retried on every query: Google refunds a purchase that is not
 *    acknowledged within three days. 1.7.0: a failed acknowledgement is also retried by
 *    itself a few times, spaced out, instead of waiting for the next service start.
 *
 * The separate Full Range Key app stays a valid unlock forever ([KeyCheck]). Since 1.6.1 it is
 * no longer offered anywhere in the app, not even when Play cannot run a purchase here.
 *
 * Threading: nothing here runs on the main thread except the one call Play requires there,
 * [BillingClient.launchBillingFlow]. Building the client and opening the connection do
 * synchronous package-manager work, and the 2026-09-27 pilot run caught that work inside
 * the service's onCreate on a loaded emulator, in the ANR trace of the activity waiting for
 * it. Every entry point hops to a private worker thread first; results reach callers on the
 * main thread.
 */
object BillingManager {

    const val PRODUCT_ID = "full_range_unlock"

    /** True on this flavor: the sheets may offer an in-app purchase. */
    const val isAvailable = true

    private const val TAG = "GranularVolume:Billing"

    private val main = Handler(Looper.getMainLooper())
    private val worker: Handler by lazy {
        Handler(HandlerThread("GranularVolume:Billing").apply { start() }.looper)
    }

    /** Runs [block] on the billing worker; immediately if already there. */
    private fun onWorker(block: () -> Unit) {
        if (Looper.myLooper() == worker.looper) block() else worker.post(block)
    }
    private var client: BillingClient? = null
    private var appContext: Context? = null
    private var details: ProductDetails? = null
    private var activeListener: ((PurchaseOutcome) -> Unit)? = null
    private val connectWaiters = ArrayList<(BillingResult) -> Unit>()
    private var connecting = false

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        val ctx = appContext ?: return@PurchasesUpdatedListener
        val outcome: PurchaseOutcome = when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val ours = purchases.orEmpty().filter { PRODUCT_ID in it.products }
                val state = absorb(ctx, ours)
                when (state) {
                    Purchase.PurchaseState.PURCHASED -> PurchaseOutcome.Purchased
                    Purchase.PurchaseState.PENDING -> PurchaseOutcome.Pending
                    else -> PurchaseOutcome.Unavailable(result.responseCode, "no purchase in the result")
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> PurchaseOutcome.Canceled
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // Play knows better than our cache: re-read and unlock.
                verifyOwnership(ctx) { owned ->
                    deliver(if (owned == true) PurchaseOutcome.AlreadyOwned
                            else PurchaseOutcome.Unavailable(result.responseCode, result.debugMessage))
                }
                return@PurchasesUpdatedListener
            }
            else -> PurchaseOutcome.Unavailable(result.responseCode, result.debugMessage)
        }
        Log.i(TAG, "Purchase flow result: code=${result.responseCode} -> ${outcome::class.simpleName}")
        deliver(outcome)
    }

    private fun deliver(outcome: PurchaseOutcome) {
        val l = activeListener ?: return
        activeListener = null
        main.post { l(outcome) }
    }

    private fun client(context: Context): BillingClient {
        client?.let { return it }
        appContext = context.applicationContext
        return BillingClient.newBuilder(context.applicationContext)
            .setListener(purchasesListener)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
            .also { client = it }
    }

    /** Runs [cb] with the connection result, connecting first if needed. Callbacks may arrive off the main thread. */
    private fun ensureConnected(context: Context, cb: (BillingResult) -> Unit) {
        val c = client(context)
        if (c.isReady) {
            cb(BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.OK).build())
            return
        }
        synchronized(connectWaiters) {
            connectWaiters.add(cb)
            if (connecting) return
            connecting = true
        }
        try {
            c.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                        Log.w(TAG, "Billing unavailable (code ${result.responseCode}): ${result.debugMessage}")
                    }
                    flushWaiters(result)
                }

                override fun onBillingServiceDisconnected() {
                    // Auto-reconnection is enabled; nothing to do until the next call.
                }
            })
        } catch (t: Throwable) {
            // A broken Play Store must read as "unavailable", never as a crash in our process.
            Log.w(TAG, "startConnection threw: ${t.message}")
            flushWaiters(BillingResult.newBuilder()
                .setResponseCode(BillingClient.BillingResponseCode.BILLING_UNAVAILABLE)
                .setDebugMessage(t.message ?: "").build())
        }
    }

    private fun flushWaiters(result: BillingResult) {
        val waiters: List<(BillingResult) -> Unit>
        synchronized(connectWaiters) {
            waiters = ArrayList(connectWaiters)
            connectWaiters.clear()
            connecting = false
        }
        waiters.forEach { it(result) }
    }

    /** The localized price Play reported, once [prefetch] or a purchase attempt has fetched it. */
    fun priceOrNull(@Suppress("UNUSED_PARAMETER") context: Context): String? =
        details?.oneTimePurchaseOfferDetails?.formattedPrice

    /**
     * Warms the connection, fetches the price for the sheets and re-checks ownership.
     * Called from the service on start; every step is asynchronous and failure-tolerant.
     * 1.7.0: [onOwnership] gets Play's answer on the main thread (true = owned, false = not
     * owned, null = no answer), so a purchase this session did not know about opens the dial
     * now and not at the next start.
     */
    fun prefetch(context: Context, onOwnership: ((Boolean?) -> Unit)? = null) = onWorker {
        ensureConnected(context) { r ->
            if (r.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.i(TAG, "Purchase verify: unavailable (code ${r.responseCode}), cache kept")
                onOwnership?.let { main.post { it(null) } }
                return@ensureConnected
            }
            fetchDetails(context) { _, _ -> }
            verifyOwnership(context, onOwnership)
        }
    }

    /**
     * 1.6.4: asks Play for the price NOW and answers on the main thread with the label, or
     * null when Play cannot say. Every surface with a buy button calls this as it opens.
     * Until this existed the price was fetched only by the service, 1.5 s after it started,
     * and kept for the life of the process: a locked start opened its sheet before the answer
     * (a button with no number), and a long-lived process kept showing a price that had since
     * changed in the Console.
     */
    fun refreshPrice(context: Context, onPrice: (String?) -> Unit) {
        val ctx = context.applicationContext
        onWorker {
            ensureConnected(ctx) { r ->
                if (r.responseCode != BillingClient.BillingResponseCode.OK) {
                    main.post { onPrice(priceOrNull(ctx)) }
                    return@ensureConnected
                }
                fetchDetails(ctx) { _, _ -> main.post { onPrice(priceOrNull(ctx)) } }
            }
        }
    }

    /**
     * Always asks Play (1.6.4): a ProductDetails kept from an earlier query carries the price
     * of that moment. When the query finds nothing, the last good answer is still used, so an
     * outage never takes a working buy button away.
     */
    private fun fetchDetails(context: Context, cb: (ProductDetails?, Int) -> Unit) {
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )
        ).build()
        client(context).queryProductDetailsAsync(params) { r, result ->
            val found = result.productDetailsList.firstOrNull { it.productId == PRODUCT_ID }
            if (found != null) details = found
            // Why a product is missing is only in the unfetched list (Billing 8+): 3 = not found or not yet
            // propagated, 4 = found but no purchase option eligible for this user or region.
            val unfetched = result.unfetchedProductList.joinToString { "${it.productId}:${it.statusCode}" }
            Log.i(TAG, "Product details: code=${r.responseCode} found=${found != null} price=${found?.oneTimePurchaseOfferDetails?.formattedPrice} unfetched=[$unfetched]")
            // The response code travels with the answer (1.7.0): "Play answered and has no such
            // product for this account" and "Play did not answer" need different words.
            cb(found ?: details, r.responseCode)
        }
    }

    /**
     * Reads Play's record for this account and updates the cache.
     * [onResult]: true = owned, false = Play answered and it is not owned, null = no answer.
     */
    fun verifyOwnership(context: Context, onResult: ((Boolean?) -> Unit)?) = onWorker {
        ensureConnected(context) { r ->
            if (r.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.i(TAG, "Purchase verify: unavailable (code ${r.responseCode}), cache kept")
                onResult?.let { main.post { it(null) } }
                return@ensureConnected
            }
            client(context).queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
            ) { qr, list ->
                if (qr.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.i(TAG, "Purchase verify: query failed (code ${qr.responseCode}), cache kept")
                    onResult?.let { main.post { it(null) } }
                    return@queryPurchasesAsync
                }
                val ours = list.filter { PRODUCT_ID in it.products }
                val state = absorb(context, ours)
                val owned = state == Purchase.PurchaseState.PURCHASED
                if (!owned && Entitlement.isPurchased(context)) {
                    if (KeyCheck.isKeyInstalled(context)) {
                        // The key app is a separate, valid receipt; only the in-app cache is stale.
                        Entitlement.setPurchased(context, false)
                        Log.i(TAG, "Purchase cache cleared; the key app still unlocks this device")
                    } else {
                        Entitlement.setPurchased(context, false)
                        Log.i(TAG, "Purchase revoked: Play reports no purchase on this account")
                    }
                }
                Log.i(TAG, "Purchase verify: owned=$owned pending=${state == Purchase.PurchaseState.PENDING}")
                onResult?.let { main.post { it(owned) } }
            }
        }
    }

    /**
     * Records what Play reported for our product and acknowledges anything unacknowledged.
     * Returns the strongest state seen: PURCHASED beats PENDING beats nothing.
     */
    private fun absorb(context: Context, ours: List<Purchase>): Int {
        var strongest = Purchase.PurchaseState.UNSPECIFIED_STATE
        for (p in ours) {
            when (p.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    if (!p.isAcknowledged) acknowledge(context, p)
                    if (!Entitlement.isPurchased(context)) Log.i(TAG, "Purchase state: PURCHASED, entitlement cached")
                    Entitlement.setPurchased(context, true)
                    Entitlement.setPurchasePending(context, false)
                    strongest = Purchase.PurchaseState.PURCHASED
                }
                Purchase.PurchaseState.PENDING -> {
                    if (strongest != Purchase.PurchaseState.PURCHASED) strongest = Purchase.PurchaseState.PENDING
                    Entitlement.setPurchasePending(context, true)
                    Log.i(TAG, "Purchase state: PENDING")
                }
                else -> Unit
            }
        }
        if (strongest != Purchase.PurchaseState.PENDING && Entitlement.isPurchasePending(context)) {
            Entitlement.setPurchasePending(context, false)
        }
        return strongest
    }

    /**
     * 1.7.0: retried with a growing pause. Until now a failed acknowledgement waited for the
     * next service start, and a buyer who paid and then left the control alone for three days
     * would have been refunded by Google and locked again, without a word from us.
     */
    private fun acknowledge(context: Context, p: Purchase, attempt: Int = 0) {
        val token = p.purchaseToken
        if (attempt == 0 && !acknowledging.add(token)) return   // already on its way
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()
        try {
            client(context).acknowledgePurchase(params) { r ->
                Log.i(TAG, "Acknowledge: code=${r.responseCode} attempt=${attempt + 1}")
                if (r.responseCode == BillingClient.BillingResponseCode.OK || attempt >= ACK_RETRY_MS.size) {
                    acknowledging.remove(token)
                } else {
                    worker.postDelayed({ acknowledge(context, p, attempt + 1) }, ACK_RETRY_MS[attempt])
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Acknowledge threw: ${t.message}")
            acknowledging.remove(token)
        }
    }

    private val acknowledging: MutableSet<String> =
        java.util.Collections.synchronizedSet(HashSet<String>())
    private val ACK_RETRY_MS = longArrayOf(3_000L, 15_000L, 60_000L)

    /**
     * Opens Google Play's purchase sheet over [activity]. Exactly one [listener] is delivered,
     * on the main thread, when the sheet closes or the attempt could not start.
     */
    fun launchPurchase(activity: Activity, listener: (PurchaseOutcome) -> Unit) {
        val ctx = activity.applicationContext
        activeListener = listener
        onWorker { ensureConnected(ctx) { r ->
            if (r.responseCode != BillingClient.BillingResponseCode.OK) {
                deliver(PurchaseOutcome.Unavailable(r.responseCode, r.debugMessage))
                return@ensureConnected
            }
            fetchDetails(ctx) { pd, code ->
                if (pd == null) {
                    // Only when Play ANSWERED and has nothing to sell this account here (not
                    // offered in its country, or not eligible) is it "not for sale". A query
                    // that failed (no Store, the Store busy) stays the generic "could not
                    // complete this right now", with its retry.
                    val notForSale = code == BillingClient.BillingResponseCode.OK
                    deliver(PurchaseOutcome.Unavailable(
                        if (notForSale) BillingClient.BillingResponseCode.ITEM_UNAVAILABLE else code,
                        if (notForSale) "product not offered to this account" else "product query failed"
                    ))
                    return@fetchDetails
                }
                val flow = BillingFlowParams.newBuilder().setProductDetailsParamsList(
                    listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(pd).build())
                ).build()
                main.post {
                    if (activity.isFinishing || activity.isDestroyed) {
                        deliver(PurchaseOutcome.Canceled); return@post
                    }
                    val lr = client(ctx).launchBillingFlow(activity, flow)
                    Log.i(TAG, "Launch flow: code=${lr.responseCode}")
                    if (lr.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                        verifyOwnership(ctx) { owned ->
                            deliver(if (owned == true) PurchaseOutcome.AlreadyOwned
                                    else PurchaseOutcome.Unavailable(lr.responseCode, lr.debugMessage))
                        }
                    } else if (lr.responseCode != BillingClient.BillingResponseCode.OK) {
                        deliver(PurchaseOutcome.Unavailable(lr.responseCode, lr.debugMessage))
                    }
                    // OK: the sheet is up; purchasesListener delivers the outcome.
                }
            }
        } }
    }

    /** Manual restore: the same query the service runs on start, with a result for the sheet. */
    fun restore(context: Context, onResult: (Boolean?) -> Unit) = verifyOwnership(context, onResult)
}

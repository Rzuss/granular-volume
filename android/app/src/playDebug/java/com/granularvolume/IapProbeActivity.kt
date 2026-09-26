package com.granularvolume

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * EXPERIMENT exp/iap-proof, debug only. Exercises every Play Billing call the real feature would
 * need, with no network permission in the app, and logs each result under the tag "IapProbe":
 * connect, product details, owned purchases, and (with --ez buy true) the purchase sheet.
 *
 * adb shell am start -n granularvolume.com.debug/com.granularvolume.IapProbeActivity [--ez buy true]
 */
class IapProbeActivity : Activity() {

    private lateinit var client: BillingClient
    private lateinit var out: TextView

    private fun log(msg: String) {
        Log.i(TAG, msg)
        runOnUiThread { out.append(msg + "\n") }
    }

    private fun describe(r: BillingResult) = "code=${r.responseCode} msg='${r.debugMessage}'"

    private val purchasesListener = PurchasesUpdatedListener { r, purchases ->
        log("PURCHASES_UPDATED ${describe(r)} count=${purchases?.size ?: 0}")
        purchases?.forEach { log("  purchase products=${it.products} state=${it.purchaseState} ack=${it.isAcknowledged}") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        out = TextView(this).apply { setPadding(32, 32, 32, 32) }
        setContentView(out)
        val buy = intent.getBooleanExtra("buy", false)
        log("probe start pkg=$packageName buy=$buy")
        client = BillingClient.newBuilder(this)
            .setListener(purchasesListener)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(r: BillingResult) {
                log("SETUP_FINISHED ${describe(r)} ready=${client.isReady}")
                if (r.responseCode != BillingClient.BillingResponseCode.OK) return
                queryProduct(buy)
                client.queryPurchasesAsync(
                    QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
                ) { pr, list ->
                    log("QUERY_PURCHASES ${describe(pr)} owned=${list.size}")
                    list.forEach { log("  owned products=${it.products} state=${it.purchaseState} ack=${it.isAcknowledged}") }
                }
            }

            override fun onBillingServiceDisconnected() = log("SERVICE_DISCONNECTED")
        })
    }

    private fun queryProduct(buy: Boolean) {
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )
        ).build()
        client.queryProductDetailsAsync(params) { r, result ->
            val found: List<ProductDetails> = result.productDetailsList
            log("PRODUCT_DETAILS ${describe(r)} found=${found.size} unfetched=${result.unfetchedProductList.size}")
            found.forEach { log("  ${it.productId} '${it.name}' price=${it.oneTimePurchaseOfferDetails?.formattedPrice}") }
            if (buy && found.isNotEmpty()) {
                val flow = BillingFlowParams.newBuilder().setProductDetailsParamsList(
                    listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(found[0]).build())
                ).build()
                runOnUiThread { log("LAUNCH_FLOW ${describe(client.launchBillingFlow(this, flow))}") }
            }
        }
    }

    override fun onDestroy() {
        if (::client.isInitialized) client.endConnection()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "IapProbe"
        const val PRODUCT_ID = "full_range_unlock"
    }
}

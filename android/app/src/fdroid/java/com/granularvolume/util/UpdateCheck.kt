package com.granularvolume.util

import android.app.Activity
import android.content.Context

/**
 * F-Droid flavor: nothing to ask and nobody to ask it of. Updates are the F-Droid client's
 * job, and this build carries no Google code at all. Same signatures as the play-flavor
 * [UpdateCheck], the same pattern as the flavor-split [BillingManager]; every call is a no-op
 * that answers "no update".
 */
object UpdateCheck {

    const val isSupported = false

    @Suppress("UNUSED_PARAMETER")
    fun isKnownAvailable(context: Context): Boolean = false

    @Suppress("UNUSED_PARAMETER")
    fun checkDaily(context: Context, onResult: (Boolean) -> Unit) = onResult(false)

    @Suppress("UNUSED_PARAMETER")
    fun checkNow(context: Context, onResult: (Boolean) -> Unit) = onResult(false)

    @Suppress("UNUSED_PARAMETER")
    fun startUpdate(activity: Activity) = Unit
}

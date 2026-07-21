package com.granularvolume.util

import android.app.Activity
import android.content.Context
import android.content.Intent

/**
 * F-Droid build carries no proprietary review dependency (see issue #1). This
 * stub preserves every call site and control-flow contract of the play-flavor
 * [ReviewHelper] exactly, as pure no-ops:
 *
 *  - [maybeRequestReview] always proceeds immediately.
 *  - [reviewIntentIfDue] never has anything to launch, so it always returns null
 *    and the shared Quick Settings tile code does nothing extra.
 *
 * There is deliberately no ReviewActivity and no launchFlow here, keeping the
 * F-Droid build free of any Play-review surface.
 */
object ReviewHelper {

    fun maybeRequestReview(activity: Activity, onDone: () -> Unit) = onDone()

    @Suppress("UNUSED_PARAMETER")
    fun reviewIntentIfDue(context: Context): Intent? = null
}

package com.granularvolume.util

import android.app.Activity

/**
 * F-Droid build carries no proprietary review dependency (see issue #1). This
 * stub preserves [MainActivity]'s call site and control flow exactly — it is
 * a no-op that always proceeds immediately.
 */
object ReviewHelper {
    fun maybeRequestReview(activity: Activity, onDone: () -> Unit) = onDone()
}

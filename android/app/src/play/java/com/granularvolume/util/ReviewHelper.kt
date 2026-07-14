package com.granularvolume.util

import android.app.Activity
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Wraps Google Play's in-app review flow. Asks at most once per install, only
 * on a return visit after the app is already configured and working (never on
 * first-time setup). Google's own quota decides whether the dialog is actually
 * shown to the user; this class only decides whether it is a reasonable moment
 * to ask at all.
 *
 * Fails silently on any error (no crash, no user-visible effect, no network
 * permission required — verified: the library binds to the Play Store app via
 * a local service connection, it does not open a socket from this process).
 * A missing or unavailable Play Store must never block the app's real function,
 * so [onDone] always fires, success or failure.
 */
object ReviewHelper {

    private const val TAG = "ReviewHelper"
    private const val MIN_LAUNCHES_BEFORE_ASK = 3

    /**
     * Requests an in-app review if this looks like a good moment, then invokes
     * [onDone] once it is safe to continue (finish the activity, etc.) whether
     * or not a review was actually shown.
     */
    fun maybeRequestReview(activity: Activity, onDone: () -> Unit) {
        val launches = Prefs.incrementAndGetLaunchCount(activity)
        if (Prefs.wasReviewFlowRequested(activity) || launches < MIN_LAUNCHES_BEFORE_ASK) {
            onDone()
            return
        }

        try {
            val manager = ReviewManagerFactory.create(activity)
            manager.requestReviewFlow().addOnCompleteListener { request ->
                // One attempt per install, matching how often Google's own quota
                // would realistically allow the dialog to show anyway.
                Prefs.setReviewFlowRequested(activity, true)
                if (request.isSuccessful) {
                    manager.launchReviewFlow(activity, request.result)
                        .addOnCompleteListener { onDone() }
                } else {
                    Log.d(TAG, "In-app review unavailable, skipping: ${request.exception?.message}")
                    onDone()
                }
            }
        } catch (t: Throwable) {
            Log.d(TAG, "In-app review threw, skipping: ${t.message}")
            onDone()
        }
    }
}

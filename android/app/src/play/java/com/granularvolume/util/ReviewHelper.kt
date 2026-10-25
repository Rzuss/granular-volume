package com.granularvolume.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory
import com.granularvolume.ReviewActivity

/**
 * Wraps Google Play's in-app review flow and decides when it is a fair moment to offer it.
 *
 * The moments (1.7.6 added the last two):
 *  1. A return visit to [com.granularvolume.MainActivity] after the app is already
 *     configured (see [maybeRequestReview]).
 *  2. After the user has used the Quick Settings tile several times (see [reviewIntentIfDue]).
 *  3. Right after a purchase completes (see [afterPurchase]), the most satisfied moment there is.
 *  4. For people who only ever use the floating dial: after the level has been changed on a few
 *     separate days, when they park the dial in its edge tab (see [dialMomentIntent]).
 *
 * How often (1.7.6, see [canAskNow]): at most twice per install. The first ask may come from any
 * moment. A second ask is only for someone who now has the full app for good (bought it, or a
 * long-time user) and comes at least 90 days after the first, or 14 days after it when the
 * occasion is the purchase itself. Until 1.7.6 the prompt was offered exactly once, and most
 * people, who drive the app from the dial, never reached either of the first two moments, so the
 * people asked were few and the ones who rated on their own were mostly unhappy ones.
 *
 * Google's own quota then decides whether a card is actually shown; this class only decides
 * whether it is a reasonable moment to ask. Nothing here asks the person a question first, and no
 * button calls the flow: both are against Google's guidelines for this API.
 *
 * Fails silently on any error (no crash, no user-visible effect, no network permission
 * required: the library binds to the Play Store app via a local service connection). A missing
 * or unavailable Play Store must never block the app's real function, so [onDone] always fires.
 */
object ReviewHelper {

    private const val TAG = "ReviewHelper"

    /** Return visits to MainActivity required before that path may ask. */
    private const val MIN_LAUNCHES_BEFORE_ASK = 3

    /** QS-tile "turn on" taps required before the tile path may ask. */
    private const val MIN_TILE_ACTIVATIONS_BEFORE_ASK = 4

    /** Separate days with a level change on the dial before the dial path may ask. */
    private const val MIN_USE_DAYS_BEFORE_ASK = 3

    /** Never more than this many review flows per install. */
    private const val MAX_ASKS = 2

    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val REASK_AFTER_MS = 90 * DAY_MS
    private const val REASK_AFTER_PURCHASE_MS = 14 * DAY_MS

    /** Lets the unlock wave on the dial finish before Google's card appears over it. */
    private const val AFTER_PURCHASE_DELAY_MS = 2_500L

    private val main = Handler(Looper.getMainLooper())

    /**
     * Whether this is a fair moment to ask, on top of the usage thresholds.
     *
     * Added 2026-09-09. Until then neither path consulted entitlement at all, so the two
     * worst possible moments to ask were both reachable:
     *
     *  - **Locked.** Someone whose trial has run out opens the app to find out why the dial
     *    stopped working, and on the third such visit is asked to rate us. A "rate this app"
     *    card shown to a person who has just lost access is the most reliable way to
     *    manufacture the one-star review this whole design exists to avoid, and the rating
     *    is what Google Play's Explore surface feeds on.
     *  - **The final trial day.** Asking today and locking tomorrow reads as a trick, even
     *    though the warning itself is honest.
     *
     * Everyone else is fair game: a grandfathered user, a buyer, and a trial user with days
     * still ahead are all being asked while the app is doing exactly what they expect.
     */
    private fun isFairMomentToAsk(context: Context): Boolean {
        if (ProAccess.isTrialExpired(context)) return false
        if (ProAccess.isOnTrial(context) && Entitlement.daysLeftInTrial(context) <= 1) return false
        return true
    }

    /** The full app for good: bought (in the app or by the key app) or a long-time user. */
    private fun ownsFullApp(context: Context): Boolean =
        Entitlement.isGrandfathered(context) || ProAccess.hasPaidUnlock(context)

    /**
     * 1.7.6: the frequency rule. [afterPurchase] is true only for the purchase moment, which
     * may follow an ask made during the trial sooner than the general 90 days.
     */
    private fun canAskNow(context: Context, afterPurchase: Boolean = false): Boolean {
        if (!isFairMomentToAsk(context)) return false
        val asks = Prefs.reviewAskCount(context)
        if (asks == 0) return true
        if (asks >= MAX_ASKS || !ownsFullApp(context)) return false
        val since = System.currentTimeMillis() - Prefs.reviewLastAskMs(context)
        return since >= if (afterPurchase) REASK_AFTER_PURCHASE_MS else REASK_AFTER_MS
    }

    /**
     * MainActivity path. Requests a review if this looks like a good moment, then
     * invokes [onDone] once it is safe to continue (finish the activity, etc.)
     * whether or not a review was actually shown.
     */
    fun maybeRequestReview(activity: Activity, onDone: () -> Unit) {
        // The count is incremented first and unconditionally: it is also one of the prior-use
        // traces the grandfather decision reads, so it must keep rising even when we choose
        // not to ask.
        val launches = Prefs.incrementAndGetLaunchCount(activity)
        if (launches < MIN_LAUNCHES_BEFORE_ASK || !canAskNow(activity)) {
            onDone()
            return
        }
        launchFlow(activity, onDone)
    }

    /**
     * Tile path. Called on every QS-tile "turn on" tap. Counts the activation and,
     * once the threshold is reached and it is a fair moment, returns an [Intent] for the
     * transparent [ReviewActivity] that will host the review card. Returns null when it is
     * not (yet) time to ask, so the caller does nothing and never disturbs the user.
     *
     * The ask is recorded only when the flow actually runs (in [launchFlow]), so a race that
     * fails to start the activity simply tries again next time rather than silently burning it.
     */
    fun reviewIntentIfDue(context: Context): Intent? {
        // Same rule as the activity path: count the activation regardless (it is a prior-use
        // trace), but never turn it into a prompt at a moment the person is being locked out.
        val activations = Prefs.incrementAndGetTileActivations(context)
        if (activations < MIN_TILE_ACTIVATIONS_BEFORE_ASK) return null
        if (!canAskNow(context)) return null
        return reviewActivityIntent(context)
    }

    /**
     * 1.7.6 dial path. Called when the person parks the dial in its edge tab: a calm moment,
     * the level is set and they are going back to what they were doing. Never on close, which
     * is someone leaving. Returns null until the level has been changed on enough separate days.
     */
    fun dialMomentIntent(context: Context): Intent? {
        if (Prefs.useDayCount(context) < MIN_USE_DAYS_BEFORE_ASK) return null
        if (!canAskNow(context)) return null
        return reviewActivityIntent(context)
    }

    /**
     * 1.7.6 purchase path. Called once a purchase has completed (not a restore, not "already
     * owned"). The card is shown a moment later over whatever is in front, so the unlock wave
     * on the dial plays first. Starting it can be refused (no visible window of ours); then
     * nothing happens and the next natural moment asks instead.
     */
    fun afterPurchase(activity: Activity) {
        val app = activity.applicationContext
        main.postDelayed({
            if (!canAskNow(app, afterPurchase = true)) return@postDelayed
            val intent = reviewActivityIntent(app)
            val host = activity.takeIf { !it.isFinishing && !it.isDestroyed }
            runCatching { (host ?: app).startActivity(intent) }
                .onFailure { Log.d(TAG, "Review after purchase not started: ${it.message}") }
        }, AFTER_PURCHASE_DELAY_MS)
    }

    private fun reviewActivityIntent(context: Context): Intent =
        Intent(context, ReviewActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Runs the raw review flow with no gating (the caller has already decided it is
     * time), records the ask, and calls [onDone] when the flow settles, success or failure.
     */
    fun launchFlow(activity: Activity, onDone: () -> Unit) {
        // Recorded before launching so a thrown/failed flow still counts as an ask and
        // never loops.
        Prefs.recordReviewAsk(activity)
        try {
            val manager = ReviewManagerFactory.create(activity)
            manager.requestReviewFlow().addOnCompleteListener { request ->
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

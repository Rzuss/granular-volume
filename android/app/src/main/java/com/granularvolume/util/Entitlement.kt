package com.granularvolume.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Everything that decides whether the full range is open, kept in its OWN prefs file.
 *
 * Why a separate file and not a few more keys in [Prefs]: Android's backup rules work at file
 * granularity, never per key. The trial has to survive an uninstall and reinstall, which means
 * this data must be in the system backup, but the user's dial position, overlay coordinates and
 * general settings must NOT be. Splitting the file is the only way to back up exactly the four
 * values below and nothing else, which is what keeps the privacy policy narrow and true.
 *
 * Contents, and the whole of it:
 *   trial_start_at    when the 7 day trial began, epoch millis
 *   last_seen_at      the latest wall clock we have ever observed, for tamper detection
 *   grandfathered_pro sticky verdict for users who had the app before the gate existed
 *   grandfather_evaluated  so the verdict is taken once and never re-litigated
 *   purchased         1.6.0: the in-app purchase, cached from Google Play's own record and
 *                     re-verified on every service start (see BillingManager)
 *   purchase_pending  1.6.0: Play accepted an order whose payment is not complete yet
 *
 * 1.7.0: the 24-hour allowance of a late first open is deliberately NOT kept here. The Privacy
 * Policy lists the values of this backed-up file one by one, so nothing may be added to it
 * without changing that text; the allowance lives in the ordinary settings file ([Prefs]),
 * which never leaves the device.
 */
object Entitlement {

    private const val FILE_NAME = "gv_entitlement"

    private const val KEY_TRIAL_START   = "trial_start_at"
    private const val KEY_LAST_SEEN     = "last_seen_at"
    private const val KEY_GRANDFATHERED = "grandfathered_pro"
    private const val KEY_EVALUATED     = "grandfather_evaluated"
    private const val KEY_PURCHASED     = "purchased"
    private const val KEY_PURCHASE_PENDING = "purchase_pending"

    const val TRIAL_DAYS = 7L
    const val TRIAL_MS = TRIAL_DAYS * 24L * 60L * 60L * 1000L

    /** The one-time allowance for a first open that finds the week already over (1.7.0). */
    const val GRACE_MS = 24L * 60L * 60L * 1000L

    /** A backwards clock jump larger than this is treated as tampering, not as drift. */
    private const val CLOCK_SLACK_MS = 24L * 60L * 60L * 1000L

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    // ── grandfathering ──────────────────────────────────────────────────────

    fun isGrandfathered(context: Context): Boolean =
        prefs(context).getBoolean(KEY_GRANDFATHERED, false)

    fun setGrandfathered(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_GRANDFATHERED, value) }
    }

    fun wasGrandfatherEvaluated(context: Context): Boolean =
        prefs(context).getBoolean(KEY_EVALUATED, false)

    fun setGrandfatherEvaluated(context: Context) {
        prefs(context).edit { putBoolean(KEY_EVALUATED, true) }
    }

    // ── in-app purchase (1.6.0) ─────────────────────────────────────────────

    /** Cached from Play's record; never the last word, see [BillingManager.verifyOwnership]. */
    fun isPurchased(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PURCHASED, false)

    fun setPurchased(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_PURCHASED, value) }
    }

    fun isPurchasePending(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PURCHASE_PENDING, false)

    fun setPurchasePending(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_PURCHASE_PENDING, value) }
    }

    // ── trial ───────────────────────────────────────────────────────────────

    /**
     * When the trial started.
     *
     * The stored timestamp, once written, is the answer. It is written the first time we are
     * ever asked, from a floor the package manager keeps for us: the LATER of
     * [firstInstallTime] and [lastUpdateTime]. Both survive Clear Data, so clearing app data
     * still cannot buy a fresh week (the floor is unchanged and is re-read into the store).
     *
     * Why the later of the two and not the earlier (which is what shipped in the internal
     * builds up to 2026-09-09): about half of all installs are never opened. An install from
     * months ago that is opened for the first time AFTER the gated version arrives has no
     * prior-use trace, so it is not grandfathered, and anchoring on the original install
     * date handed it "your seven days are over" on its first ever launch. Anchoring on the
     * update that brought the gate gives that device the same seven days as anyone else.
     *
     * Why the stored value is trusted once present: a stored anchor is always at or after
     * the floor, so re-taking the minimum could only ever pull an updated install backwards
     * to its original install date, which is the exact failure above, every time it ran.
     *
     * The late first open (1.7.0). A fresh install that sat unopened for more than seven days
     * used to meet "your seven days are over" on its very first launch, having seen nothing.
     * It now gets [GRACE_MS] of the complete app, once, and the first screen says so. The
     * allowance is written only in the branch that stores the anchor for the first time, so it
     * can only ever be decided at the first evaluation: an install whose anchor is already
     * stored never reaches it.
     *
     * Clear Data wipes the anchor too, so it would look like a first open and buy another day
     * each time. [graceAllowed] closes that: the allowance is given only while the app has
     * never been allowed to draw over other apps, a system setting that Clear Data does not
     * reset and that every install that has ever shown its dial has granted. Measured
     * 2026-10-03 on API 36 and API 34 images: `pm clear` leaves the SYSTEM_ALERT_WINDOW app-op
     * exactly as it was, in both directions (allow stays allow, default stays default). A
     * device that used the app and cleared it gets no second day.
     */
    private fun trialStart(context: Context): Long {
        val stored = prefs(context).getLong(KEY_TRIAL_START, 0L)
        if (stored != 0L) return stored
        val floor = try {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            maxOf(pi.firstInstallTime, pi.lastUpdateTime)
        } catch (_: Exception) {
            0L
        }
        val now = System.currentTimeMillis()
        val anchor = if (floor != 0L) floor else now
        val late = now - anchor >= TRIAL_MS && graceAllowed(context)
        prefs(context).edit { putLong(KEY_TRIAL_START, anchor) }
        if (late) Prefs.setGraceUntil(context, now + GRACE_MS)
        return anchor
    }

    /** See [trialStart]: only an install that never had the overlay permission is a first open. */
    private fun graceAllowed(context: Context): Boolean = try {
        !android.provider.Settings.canDrawOverlays(context)
    } catch (_: Exception) {
        false
    }

    /**
     * Monotonic-ish "now".
     *
     * Winding the device clock backwards is the obvious way to stretch a trial, so we remember
     * the latest time we have ever seen. If the clock has moved back by more than a day we do
     * not trust it and keep using the high-water mark, which freezes the trial rather than
     * extending it. Small backwards moves (a few hours of NTP correction or a timezone edit)
     * pass through untouched.
     */
    private fun trustedNow(context: Context): Long {
        val now = System.currentTimeMillis()
        val lastSeen = prefs(context).getLong(KEY_LAST_SEEN, 0L)
        val trusted = if (lastSeen > 0L && now < lastSeen - CLOCK_SLACK_MS) lastSeen else now
        if (trusted > lastSeen) prefs(context).edit { putLong(KEY_LAST_SEEN, trusted) }
        return trusted
    }

    fun isTrialActive(context: Context): Boolean = millisLeftInTrial(context) > 0L

    fun millisLeftInTrial(context: Context): Long {
        val start = trialStart(context)   // first: it is what may write the allowance below
        val now = trustedNow(context)
        val week = TRIAL_MS - (now - start)
        val grace = Prefs.getGraceUntil(context) - now
        // The allowance can never be longer than it was written, whatever the clock says now.
        return maxOf(week, grace.coerceAtMost(GRACE_MS)).coerceAtLeast(0L)
    }

    /** True while the only thing keeping the range open is the late-first-open allowance (1.7.0). */
    fun isInGrace(context: Context): Boolean {
        val start = trialStart(context)
        val now = trustedNow(context)
        return TRIAL_MS - (now - start) <= 0L && Prefs.getGraceUntil(context) - now > 0L
    }

    /** Whole days left, rounded up, so the last part-day still reads as "1 day left". */
    fun daysLeftInTrial(context: Context): Int {
        val left = millisLeftInTrial(context)
        if (left <= 0L) return 0
        return ((left + 86_399_999L) / 86_400_000L).toInt()
    }

    /**
     * One-time move of the grandfather verdict out of the old prefs file.
     * Installs that were already grandfathered under 1.5.0-internal keep their status.
     */
    fun migrateFromLegacyPrefsIfNeeded(context: Context, legacyEvaluated: Boolean, legacyGrandfathered: Boolean) {
        if (wasGrandfatherEvaluated(context)) return
        if (!legacyEvaluated) return
        setGrandfathered(context, legacyGrandfathered)
        setGrandfatherEvaluated(context)
    }
}

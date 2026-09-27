package com.granularvolume.util

import android.content.Context
import com.granularvolume.BuildConfig

/**
 * Single source of truth for "does this device have the full quiet range".
 *
 * Three independent ways in, any one is enough:
 *
 *  1. **Grandfathered** — the app was installed or used before the gated version arrived.
 *     Decided ONCE, on the first run of the gated version, from prior-use traces in
 *     prefs ([Prefs.hasAnyPriorUse]) or from the install having come from an earlier
 *     version ([installedBeforeGate]), then stored sticky in [Entitlement]. Existing
 *     users keep the full range forever; the promise that made them install is never
 *     withdrawn.
 *
 *  2. **Trial** — every new install gets [Entitlement.TRIAL_DAYS] days of the complete
 *     app, gate included, so the decision to buy is made after living with the thing
 *     rather than after reading a description of it.
 *
 *  3. **Paid** — either the in-app purchase is cached from Google Play's own record
 *     ([Entitlement.isPurchased], 1.6.0, re-verified on every service start by
 *     [BillingManager]), or the separate unlock-key app is present and its signing
 *     certificate matches the pinned set ([KeyCheck], flavor-split: the F-Droid
 *     flavor is fully unlocked by a stub, keeping that build free and complete).
 *     Both stay valid forever; the key app is also the fallback when Play cannot run
 *     a purchase on a device.
 *
 * This object lives in src/main and is GPL-published like everything else: the
 * check is possession of the key app, not a secret. Nothing here needs hiding.
 */
object ProAccess {

    /**
     * Runs the one-time grandfather decision. Call early on EVERY entry point
     * (service create, MainActivity create) — whichever runs first decides, the
     * rest are no-ops. Never call after writing new prefs in the same session,
     * or a fresh install could look like prior use.
     *
     * Also carries the verdict over from the old single-prefs-file layout, so an
     * install that was already grandfathered before the split keeps its status.
     */
    fun evaluateGrandfather(context: Context) {
        Entitlement.migrateFromLegacyPrefsIfNeeded(
            context,
            legacyEvaluated = Prefs.wasGrandfatherEvaluated(context),
            legacyGrandfathered = Prefs.isGrandfathered(context),
        )
        if (Entitlement.wasGrandfatherEvaluated(context)) return
        // Prior use is read first, before anything below can touch prefs.
        val priorUse = Prefs.hasAnyPriorUse(context)
        val installedBefore = installedBeforeGate(context) // null: PackageManager failed
        // Never turn a failed lookup into a permanent lock: decide at the next entry point.
        if (!priorUse && installedBefore == null) return
        Entitlement.setGrandfathered(context, priorUse || installedBefore == true)
        Entitlement.setGrandfatherEvaluated(context)
    }

    /**
     * True when this installation came from an earlier version (an update, not a fresh
     * install) AND its first install predates a deliberately late cutoff. The promise is
     * "installed before 1.5.0", so an error here may grandfather a few extra installs and
     * must never lock out one that was promised.
     */
    private fun installedBeforeGate(context: Context): Boolean? = try {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        pi.firstInstallTime < pi.lastUpdateTime &&
            pi.firstInstallTime < BuildConfig.GATE_CUTOFF_MS
    } catch (_: Exception) {
        null
    }

    /**
     * Live answer, cheap enough to call on every gate decision: two prefs reads
     * short-circuit before the PackageManager lookup.
     *
     * Order matters only for cost, not correctness. Grandfather first because it is a
     * single boolean; trial second because it is arithmetic on two longs; the key check
     * last because it is the only one that touches the package manager.
     */
    fun isPro(context: Context): Boolean =
        Entitlement.isGrandfathered(context) ||
            Entitlement.isTrialActive(context) ||
            hasPaidUnlock(context)

    /**
     * A buyer, by either receipt. The cached in-app purchase is a prefs read; the key app
     * is the package-manager lookup, so it goes second.
     */
    fun hasPaidUnlock(context: Context): Boolean =
        Entitlement.isPurchased(context) || KeyCheck.isKeyInstalled(context)

    /**
     * True when the ONLY reason the range is open is the trial still running.
     * Used to decide whether to show the countdown; a grandfathered user or a buyer
     * must never be told about a trial that does not apply to them.
     */
    fun isOnTrial(context: Context): Boolean =
        !Entitlement.isGrandfathered(context) &&
            !hasPaidUnlock(context) &&
            Entitlement.isTrialActive(context)

    /** True once a trial has run out and nothing else has opened the range. */
    fun isTrialExpired(context: Context): Boolean =
        !Entitlement.isGrandfathered(context) &&
            !hasPaidUnlock(context) &&
            !Entitlement.isTrialActive(context)
}

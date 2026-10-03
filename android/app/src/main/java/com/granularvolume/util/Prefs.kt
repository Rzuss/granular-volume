package com.granularvolume.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Type-safe SharedPreferences wrapper.
 * All keys are constants — no magic strings outside this class.
 */
object Prefs {

    private const val FILE_NAME = "gv_prefs"

    private const val KEY_ATTENUATION_DB   = "attenuation_db"
    private const val KEY_OVERLAY_X        = "overlay_x"
    private const val KEY_OVERLAY_Y        = "overlay_y"
    private const val KEY_OVERLAY_LANDSCAPE = "overlay_saved_landscape"
    private const val KEY_SERVICE_WAS_RUNNING = "service_was_running"
    private const val KEY_COLLAPSED        = "overlay_collapsed"
    private const val KEY_BLADE_Y          = "blade_y"
    private const val KEY_BLADE_RIGHT      = "blade_right"
    private const val KEY_BLADE_TIP_SHOWN  = "blade_tip_shown"
    private const val KEY_TOUR_SHOWN_VERSION = "tour_shown_version"
    private const val KEY_QS_TILE_OFFERED  = "qs_tile_offered"
    private const val KEY_LAUNCH_COUNT     = "launch_count"
    private const val KEY_TILE_ACTIVATIONS = "tile_activations"
    private const val KEY_REVIEW_REQUESTED = "review_flow_requested"
    private const val KEY_LINE_TOOLTIP_SHOWN = "line_tooltip_shown"
    private const val KEY_TERMS_ACCEPTED_VERSION = "terms_accepted_version"

    // ── Pro / full-range gate (1.5.0) ───────────────────────────────
    private const val KEY_GRANDFATHERED_PRO      = "grandfathered_pro"
    private const val KEY_GRANDFATHER_EVALUATED  = "grandfather_evaluated"
    private const val KEY_TIPJAR_CARD_SHOWN      = "tipjar_card_shown"
    private const val KEY_UNLOCK_ACKNOWLEDGED    = "unlock_acknowledged"
    private const val KEY_TRIAL_CARD_DAY         = "trial_card_shown_for_day"
    private const val KEY_LAST_DAY_NUDGE_SHOWN   = "last_day_nudge_shown"
    // 1.6.2: the two one-time notices of the free week (see TrialNotices)
    private const val KEY_LAST_DAY_NOTICE_POSTED = "last_day_notice_posted"
    private const val KEY_ENDED_NOTICE_POSTED    = "ended_notice_posted"
    // 1.7.0
    private const val KEY_SESSION_OPEN           = "session_open"
    private const val KEY_SESSION_OPEN_BOOT      = "session_open_boot_count"
    private const val KEY_SESSION_CARRY_UNTIL    = "session_carry_until"
    private const val KEY_NO_EFFECT_TOLD         = "no_effect_told"
    private const val KEY_SUPPORT_CARD_DONE      = "support_card_done"
    private const val KEY_UPDATE_CHECKED_AT      = "update_checked_at"
    private const val KEY_UPDATE_AVAILABLE_FOR   = "update_available_for_vc"
    private const val KEY_GRACE_UNTIL            = "grace_until"

    /** How long the receiver's "carry this session" note stays valid (1.7.0). */
    private const val SESSION_CARRY_WINDOW_MS = 60_000L

    /** Current attenuation in dB (0.0 = none, -30.0 = near-silent) */
    const val ATTENUATION_DEFAULT = 0f
    const val ATTENUATION_MIN     = -30f
    const val ATTENUATION_MAX     = 0f

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun getAttenuation(context: Context): Float =
        prefs(context).getFloat(KEY_ATTENUATION_DB, ATTENUATION_DEFAULT)

    fun setAttenuation(context: Context, dB: Float) {
        prefs(context).edit { putFloat(KEY_ATTENUATION_DB, dB.coerceIn(ATTENUATION_MIN, ATTENUATION_MAX)) }
    }

    fun getOverlayX(context: Context, default: Int): Int =
        prefs(context).getInt(KEY_OVERLAY_X, default)

    fun getOverlayY(context: Context, default: Int): Int =
        prefs(context).getInt(KEY_OVERLAY_Y, default)

    fun setOverlayPosition(context: Context, x: Int, y: Int, landscape: Boolean = false) {
        prefs(context).edit {
            putInt(KEY_OVERLAY_X, x)
            putInt(KEY_OVERLAY_Y, y)
            putBoolean(KEY_OVERLAY_LANDSCAPE, landscape)
        }
    }

    /**
     * 1.7.0: which way up the screen was when the dial's position was last chosen. Absent on
     * every earlier install, which reads as portrait: where nearly every dial was placed.
     */
    fun wasOverlaySavedInLandscape(context: Context): Boolean =
        prefs(context).getBoolean(KEY_OVERLAY_LANDSCAPE, false)

    fun setServiceWasRunning(context: Context, running: Boolean) {
        prefs(context).edit { putBoolean(KEY_SERVICE_WAS_RUNNING, running) }
    }

    fun wasServiceRunning(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SERVICE_WAS_RUNNING, false)

    fun isCollapsed(context: Context): Boolean =
        prefs(context).getBoolean(KEY_COLLAPSED, false)

    fun setCollapsed(context: Context, collapsed: Boolean) {
        prefs(context).edit { putBoolean(KEY_COLLAPSED, collapsed) }
    }

    // 1.5.1 Quiet Blade. The dial's own (x, y) above is deliberately NOT overwritten while
    // collapsed: it is the "home" the blade returns to, exactly as the user left it. The blade
    // keeps its own vertical position and the edge it hugs.
    fun getBladeY(context: Context, default: Int): Int =
        prefs(context).getInt(KEY_BLADE_Y, default)

    /** True = right edge, false = left edge. */
    fun isBladeOnRight(context: Context, default: Boolean): Boolean =
        prefs(context).getBoolean(KEY_BLADE_RIGHT, default)

    fun setBladePlacement(context: Context, y: Int, onRight: Boolean) {
        prefs(context).edit {
            putInt(KEY_BLADE_Y, y)
            putBoolean(KEY_BLADE_RIGHT, onRight)
        }
    }

    /**
     * 1.5.1: the versionCode whose feature tour has been shown. The tour runs once per
     * install AND once per update (the owner's call: every installer and updater sees it),
     * so the gate is a version number, not a boolean.
     */
    fun getTourShownVersion(context: Context): Int =
        prefs(context).getInt(KEY_TOUR_SHOWN_VERSION, 0)

    fun setTourShownVersion(context: Context, versionCode: Int) {
        prefs(context).edit { putInt(KEY_TOUR_SHOWN_VERSION, versionCode) }
    }

    fun wasBladeTipShown(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BLADE_TIP_SHOWN, false)

    fun setBladeTipShown(context: Context) {
        prefs(context).edit { putBoolean(KEY_BLADE_TIP_SHOWN, true) }
    }

    fun wasQsTileOffered(context: Context): Boolean =
        prefs(context).getBoolean(KEY_QS_TILE_OFFERED, false)

    fun setQsTileOffered(context: Context, offered: Boolean) {
        prefs(context).edit { putBoolean(KEY_QS_TILE_OFFERED, offered) }
    }

    /** Counts MainActivity opens where the app was already fully set up (a "return visit"). */
    fun incrementAndGetLaunchCount(context: Context): Int {
        val next = prefs(context).getInt(KEY_LAUNCH_COUNT, 0) + 1
        prefs(context).edit { putInt(KEY_LAUNCH_COUNT, next) }
        return next
    }

    /**
     * Counts Quick Settings tile "turn on" taps. This is the real usage signal for
     * tile-driven users who rarely reopen [com.granularvolume.MainActivity], and is
     * used to decide when to (once ever) offer the in-app review prompt.
     */
    fun incrementAndGetTileActivations(context: Context): Int {
        val next = prefs(context).getInt(KEY_TILE_ACTIVATIONS, 0) + 1
        prefs(context).edit { putInt(KEY_TILE_ACTIVATIONS, next) }
        return next
    }

    fun wasReviewFlowRequested(context: Context): Boolean =
        prefs(context).getBoolean(KEY_REVIEW_REQUESTED, false)

    fun setReviewFlowRequested(context: Context, requested: Boolean) {
        prefs(context).edit { putBoolean(KEY_REVIEW_REQUESTED, requested) }
    }

    /** One-time "drag below the line" tooltip on the first overlay display (full-range spec). */
    fun wasLineTooltipShown(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LINE_TOOLTIP_SHOWN, false)

    fun setLineTooltipShown(context: Context) {
        prefs(context).edit { putBoolean(KEY_LINE_TOOLTIP_SHOWN, true) }
    }

    /**
     * Version number of the Terms the user actively accepted via the consent gate (clickwrap).
     * 0 = never accepted. Versioned (not boolean) so a future material Terms change can
     * re-prompt — locked design, see the Pro plan's A2b item.
     */
    fun getTermsAcceptedVersion(context: Context): Int =
        prefs(context).getInt(KEY_TERMS_ACCEPTED_VERSION, 0)

    fun setTermsAcceptedVersion(context: Context, version: Int) {
        prefs(context).edit { putInt(KEY_TERMS_ACCEPTED_VERSION, version) }
    }

    // ── Pro / full-range gate (1.5.0) ───────────────────────────────

    /**
     * True iff any trace of pre-gate use exists. Several independent keys are
     * checked because no single one covers every user: tile-driven users never
     * increment the launch count, dial-only users may never touch the tile, and
     * a user who granted overlay but never moved the dial still placed it once.
     * Consulted exactly once, by ProAccess.evaluateGrandfather.
     */
    fun hasAnyPriorUse(context: Context): Boolean {
        val p = prefs(context)
        return p.getInt(KEY_LAUNCH_COUNT, 0) > 0 ||
            p.getInt(KEY_TILE_ACTIVATIONS, 0) > 0 ||
            p.getFloat(KEY_ATTENUATION_DB, ATTENUATION_DEFAULT) != ATTENUATION_DEFAULT ||
            p.contains(KEY_OVERLAY_X) || p.contains(KEY_OVERLAY_Y) ||
            p.getInt(KEY_TERMS_ACCEPTED_VERSION, 0) > 0 ||
            // Every key below is written by ordinary 1.4.x use and by nothing else. Presence
            // is the proof, not the value: service_was_running is false after a user stop,
            // and that user still used the app. Added 2026-09-09 after an audit found a
            // tile-driven user who never returned to MainActivity, never moved the dial and
            // predates the consent gate would fail every check above and be locked on day
            // one with no trial. Note attenuation_db is written as 0.0 on every service
            // start, and 0.0 == ATTENUATION_DEFAULT, so that clause alone never covered them.
            p.contains(KEY_SERVICE_WAS_RUNNING) ||
            p.contains(KEY_QS_TILE_OFFERED) ||
            p.contains(KEY_LINE_TOOLTIP_SHOWN) ||
            p.contains(KEY_REVIEW_REQUESTED) ||
            p.contains(KEY_COLLAPSED)
    }

    /** Sticky grandfather verdict. Written once; never flips back to false. */
    fun isGrandfathered(context: Context): Boolean =
        prefs(context).getBoolean(KEY_GRANDFATHERED_PRO, false)

    fun setGrandfathered(context: Context, grandfathered: Boolean) {
        prefs(context).edit { putBoolean(KEY_GRANDFATHERED_PRO, grandfathered) }
    }

    fun wasGrandfatherEvaluated(context: Context): Boolean =
        prefs(context).getBoolean(KEY_GRANDFATHER_EVALUATED, false)

    fun setGrandfatherEvaluated(context: Context) {
        prefs(context).edit { putBoolean(KEY_GRANDFATHER_EVALUATED, true) }
    }

    /**
     * The one 24-hours-left warning. A boolean, not a day key: a trial has exactly one
     * final day per install, and the flag lives in gv_prefs on purpose, OUTSIDE the
     * backup, so it can never travel to a device it does not describe.
     */
    fun wasLastDayNudgeShown(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LAST_DAY_NUDGE_SHOWN, false)

    fun setLastDayNudgeShown(context: Context) {
        prefs(context).edit { putBoolean(KEY_LAST_DAY_NUDGE_SHOWN, true) }
    }

    /** 1.6.2: the last-day notice went to the shade (once per install, never re-armed). */
    fun wasLastDayNoticePosted(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LAST_DAY_NOTICE_POSTED, false)

    fun setLastDayNoticePosted(context: Context) {
        prefs(context).edit { putBoolean(KEY_LAST_DAY_NOTICE_POSTED, true) }
    }

    /** 1.6.2: the ended notice was posted, or made redundant by the locked sheet (once per install). */
    fun wasEndedNoticePosted(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENDED_NOTICE_POSTED, false)

    fun setEndedNoticePosted(context: Context) {
        prefs(context).edit { putBoolean(KEY_ENDED_NOTICE_POSTED, true) }
    }

    // ── 1.7.0: the session across our own update ────────────────────────────

    /**
     * Whether the running session's audio gate is open, written by the service whenever it
     * decides or changes it. Read by exactly one caller: the update receiver, to bring a
     * control back as it was. The boot count is stored beside it, so a note left before a
     * restart of the DEVICE can never be read as a session that is still alive.
     */
    fun setSessionOpen(context: Context, open: Boolean) {
        prefs(context).edit {
            putBoolean(KEY_SESSION_OPEN, open)
            putInt(KEY_SESSION_OPEN_BOOT, bootCount(context))
        }
    }

    fun wasSessionOpenThisBoot(context: Context): Boolean {
        val p = prefs(context)
        return p.getBoolean(KEY_SESSION_OPEN, false) &&
            p.getInt(KEY_SESSION_OPEN_BOOT, -1) == bootCount(context)
    }

    /** -2 when the system will not say, which never equals a stored count (-1 or a real one). */
    private fun bootCount(context: Context): Int = try {
        android.provider.Settings.Global.getInt(
            context.contentResolver, android.provider.Settings.Global.BOOT_COUNT
        )
    } catch (_: Exception) {
        -2
    }

    /**
     * Left by the update receiver just before it restarts a control whose free week is over
     * but whose session was open: "open this one session as it was". Valid for a minute and
     * consumed by the first service start, so it can never open a later, unrelated start.
     */
    fun setSessionCarry(context: Context, carry: Boolean) {
        prefs(context).edit {
            putLong(KEY_SESSION_CARRY_UNTIL, if (carry) System.currentTimeMillis() + SESSION_CARRY_WINDOW_MS else 0L)
        }
    }

    fun consumeSessionCarry(context: Context): Boolean {
        val until = prefs(context).getLong(KEY_SESSION_CARRY_UNTIL, 0L)
        if (until == 0L) return false
        prefs(context).edit { putLong(KEY_SESSION_CARRY_UNTIL, 0L) }
        val now = System.currentTimeMillis()
        // Inside the window, and not a clock that jumped far back to stretch it.
        return now <= until && until - now <= SESSION_CARRY_WINDOW_MS
    }

    /**
     * 1.7.0: until when a late first open keeps everything open (see Entitlement.trialStart).
     * Here, in the settings file, and not in the backed-up entitlement file, whose contents
     * the Privacy Policy lists exactly. 0 = no allowance.
     */
    fun getGraceUntil(context: Context): Long =
        prefs(context).getLong(KEY_GRACE_UNTIL, 0L)

    fun setGraceUntil(context: Context, untilMs: Long) {
        prefs(context).edit { putLong(KEY_GRACE_UNTIL, untilMs) }
    }

    /** 1.7.0: the one-time "this device did not attach the quiet steps" message was shown. */
    fun wasNoEffectTold(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NO_EFFECT_TOLD, false)

    fun setNoEffectTold(context: Context) {
        prefs(context).edit { putBoolean(KEY_NO_EFFECT_TOLD, true) }
    }

    /**
     * 1.7.0: the support card in "Your access" (free-for-good users) was answered: dismissed,
     * or followed to Google Play. Either way it never comes back.
     */
    fun wasSupportCardDone(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SUPPORT_CARD_DONE, false)

    fun setSupportCardDone(context: Context) {
        prefs(context).edit { putBoolean(KEY_SUPPORT_CARD_DONE, true) }
    }

    // ── 1.7.0: "a newer version is ready" ───────────────────────────────────

    fun getUpdateCheckedAt(context: Context): Long =
        prefs(context).getLong(KEY_UPDATE_CHECKED_AT, 0L)

    fun setUpdateCheckedAt(context: Context, atMs: Long) {
        prefs(context).edit { putLong(KEY_UPDATE_CHECKED_AT, atMs) }
    }

    /** The versionCode Play offered at the last check, or 0 when it offered none. */
    fun getUpdateAvailableVersion(context: Context): Int =
        prefs(context).getInt(KEY_UPDATE_AVAILABLE_FOR, 0)

    fun setUpdateAvailableVersion(context: Context, versionCode: Int) {
        prefs(context).edit { putInt(KEY_UPDATE_AVAILABLE_FOR, versionCode) }
    }

    /** One-time grandfather tip-jar card in MainActivity: shown once, never again. */
    fun wasTipjarCardShown(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TIPJAR_CARD_SHOWN, false)

    fun setTipjarCardShown(context: Context) {
        prefs(context).edit { putBoolean(KEY_TIPJAR_CARD_SHOWN, true) }
    }

    /**
     * Which "days left" value the trial card was last shown for, so the countdown appears
     * once a day rather than on every launch. Deliberately keyed on the number and not on a
     * date: the number is what the card says, so it is exactly what must not repeat. -1 means
     * never shown.
     *
     * Lives here, in the settings file, and is therefore NOT part of the backup set. A
     * restored device re-showing one countdown card is the right side of that trade.
     */
    fun getTrialCardShownForDay(context: Context): Int =
        prefs(context).getInt(KEY_TRIAL_CARD_DAY, -1)

    fun setTrialCardShownForDay(context: Context, daysLeft: Int) {
        prefs(context).edit { putInt(KEY_TRIAL_CARD_DAY, daysLeft) }
    }

    /**
     * One-time purchase confirmation. A buyer can return from the store by several paths,
     * and only one of them keeps the paywall alive to say thank you. This makes the
     * acknowledgement independent of the path taken.
     */
    fun wasUnlockAcknowledged(context: Context): Boolean =
        prefs(context).getBoolean(KEY_UNLOCK_ACKNOWLEDGED, false)

    fun setUnlockAcknowledged(context: Context) {
        prefs(context).edit { putBoolean(KEY_UNLOCK_ACKNOWLEDGED, true) }
    }
}

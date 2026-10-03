package com.granularvolume.util

import android.content.Context
import com.granularvolume.BuildConfig

/**
 * Where the user stands, as ONE answer that every surface reads (1.7.0).
 *
 * Before this each surface asked its own question: the main screen and the service start tested
 * "has the trial expired", the notification tested the session latch, the access sheet tested a
 * third thing. A control that was still running after the week ended was therefore called
 * "locked" by one screen and shown working by another. The main screen, the access sheet, the
 * paywall, the notification, the tile and the notices now all come here.
 */
enum class AccessState {
    /** The F-Droid build: everything, free, nothing to buy. Not one of the six commercial states. */
    FREE_BUILD,

    /** 1. The free week is running, more than a day left. */
    FREE_WEEK,

    /** 2. The free week is inside its final 24 hours. */
    LAST_DAY,

    /**
     * 3. The week ended while the control was on. The level that is applied stays exactly as it
     * is until the control is next stopped; changing it asks for the unlock. Never worded "locked".
     */
    WEEK_ENDED_RUNNING,

    /** 4. The week is over and nothing is carrying a level: the dial is locked. */
    LOCKED,

    /** 5. Bought in the app, or the key app is installed. */
    UNLOCKED,

    /** 6. Installed before the paid model: everything stays free, for good. */
    FREE_FOR_GOOD;

    /** The free week is the only thing keeping the range open. */
    val onTrial: Boolean get() = this == FREE_WEEK || this == LAST_DAY

    /** The free week has run out and nothing else opened the range. */
    val weekOver: Boolean get() = this == WEEK_ENDED_RUNNING || this == LOCKED

    companion object {
        /**
         * Read-only: it never opens the session latch and never writes entitlement. The order
         * mirrors [ProAccess.isPro]: a grandfathered device that also paid is reported as free
         * for good, because that is the promise it was given.
         */
        fun of(context: Context): AccessState = when {
            BuildConfig.FLAVOR != "play" -> FREE_BUILD
            Entitlement.isGrandfathered(context) -> FREE_FOR_GOOD
            ProAccess.hasPaidUnlock(context) -> UNLOCKED
            Entitlement.isTrialActive(context) ->
                if (Entitlement.millisLeftInTrial(context) <= TrialNotices.DAY_MS) LAST_DAY else FREE_WEEK
            ControlLive.running && ControlLive.sessionOpen -> WEEK_ENDED_RUNNING
            else -> LOCKED
        }
    }
}

package com.granularvolume.util

/**
 * What the running control looks like RIGHT NOW, for every surface outside the service (1.7.0).
 *
 * Process memory on purpose, never a pref. [Prefs.wasServiceRunning] answers a different
 * question ("should the control come back after a restart") and stays true after the system
 * kills the service, so the launcher icon, the tile and the free-week notice were reading a
 * control that was not there. These fields are written only by the service's own onCreate and
 * onDestroy, and a dead process reads as "not running" by construction.
 */
object ControlLive {

    /** True between the service's onCreate and onDestroy, and only when the dial could be shown. */
    @Volatile
    var running = false

    /**
     * True while this session's audio gate is open: decided at service start, and afterwards
     * only ever opened (a purchase). A session that was open when the free week ended keeps
     * its level until it is next stopped; that is the "week ended, still running" state.
     */
    @Volatile
    var sessionOpen = false

    /** How the quiet steps are attached on this device, as far as the service has seen. */
    enum class Effect { UNKNOWN, PREFERRED, FALLBACK, NONE }

    @Volatile
    var effect = Effect.UNKNOWN

    /** 1.8.0: another app holds the control of the effect right now, so the steps may do nothing. */
    @Volatile
    var effectTaken = false

    /**
     * 1.8.0: Google's review card may not have anything drawn over or around it. Whoever shows the
     * card says so here, true before and false after; the running service hides the dial meanwhile.
     */
    @Volatile
    var onReviewCard: ((showing: Boolean) -> Unit)? = null

    fun reset() {
        running = false
        sessionOpen = false
        effect = Effect.UNKNOWN
        effectTaken = false
        onReviewCard = null
    }
}

package com.granularvolume.audio

import android.content.Context
import android.util.Log
import com.granularvolume.util.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Central audio controller. Manages strategy selection and exposes a StateFlow
 * for the current attenuation level so the UI can react reactively.
 *
 * Strategy selection order:
 *   1. DynamicsProcessingStrategy (preferred — clean, flat-spectrum)
 *   2. LoudnessEnhancerStrategy (fallback — OEM-dependent behavior)
 *   3. null (no effect available — notify user)
 */
class AudioController(private val context: Context) {

    /**
     * Where a locked device is held: 0 dB, no attenuation at all.
     *
     * Under the trial model there is no permanent free tier to fall back to. Every install
     * gets the complete app for [com.granularvolume.util.Entitlement.TRIAL_DAYS] days, and
     * after that the range itself is what is being sold. Devices that used the app before
     * the trial existed are grandfathered and never reach this floor.
     */
    val lockedFloorDb = 0f

    private val tag = "GranularVolume:AudioCtrl"

    @Volatile
    private var strategy: AudioEffectStrategy? = null

    /**
     * Set by [release]. 1.7.1: until then nothing stopped a late [initialize] or [reattach]
     * (the start runs on a background thread, and the coordinator posts re-attach passes up
     * to a few seconds ahead) from building a fresh effect on the output mix after the
     * control had stopped: sound held down with no dial and no notification.
     */
    @Volatile
    private var released = false

    // ── Full-range gate (1.5.0) ─────────────────────────────────────
    /**
     * Answers "is the full quiet range unlocked on this device". Wired by the
     * service to ProAccess. Defaults to open on purpose: if a future entry point
     * forgets to wire it, the failure mode is a free full range, never a paying
     * or grandfathered user losing depth.
     */
    var proProvider: () -> Boolean = { true }

    /**
     * True while a level change is written to storage, which is the same condition
     * [setAttenuation] uses. The coordinator stores the zone under this rule too, so the
     * stored level and the stored zone always describe the same moment.
     */
    fun keepsLevel(): Boolean = proProvider()

    /** Emits current attenuation in dB. UI observes this. */
    private val _attenuationDb = MutableStateFlow(Prefs.getAttenuation(context))
    val attenuationDb: StateFlow<Float> = _attenuationDb.asStateFlow()

    /** True if a working audio effect strategy was found. */
    var isEffectAvailable: Boolean = false
        private set

    /**
     * 1.7.0: told after every [initialize], including the ones [reattach] runs: whether any
     * effect is attached, and whether it is the preferred one. The service publishes it so
     * "Your access" can say when this device is the reason the quiet steps do nothing.
     */
    var onEffectState: ((available: Boolean, preferred: Boolean) -> Unit)? = null

    /**
     * True while the preferred DynamicsProcessing strategy holds the effect. False means
     * either no strategy at all or the LoudnessEnhancer fallback, whose negative-gain
     * support is OEM-dependent — both states that a caller may want to retry out of.
     */
    val usingPreferredStrategy: Boolean
        get() = strategy is DynamicsProcessingStrategy

    /**
     * Initializes the best available AudioEffect strategy.
     * Call this from Service.onCreate() — never from UI thread.
     * @return true if any strategy initialized successfully
     */
    @Synchronized
    fun initialize(): Boolean {
        if (released) return false
        val strategies: List<AudioEffectStrategy> = listOf(
            DynamicsProcessingStrategy(),
            LoudnessEnhancerStrategy()
        )

        for (s in strategies) {
            if (s.initialize()) {
                strategy = s
                isEffectAvailable = true
                Log.i(tag, "Using strategy: ${s::class.simpleName}")
                // Apply persisted attenuation immediately, THROUGH the gate: a stale
                // deep level from a refunded or pre-gate state re-clamps at service start.
                setAttenuation(_attenuationDb.value, GainSource.SYSTEM)
                onEffectState?.invoke(true, usingPreferredStrategy)
                return true
            }
        }

        Log.e(tag, "No AudioEffect strategy available on this device")
        isEffectAvailable = false
        onEffectState?.invoke(false, false)
        return false
    }

    /**
     * Where a gain request comes from — the gate treats each origin differently.
     *
     * The one exemption that makes this enum necessary: the full-range curve
     * routes rung remainders through this same method, and a remainder is
     * bounded by the LOCAL hardware step gap, which on coarse OEM volume curves
     * can exceed 5 dB. A blind clamp would corrupt the FREE upper zone on
     * exactly those devices.
     */
    enum class GainSource {
        /** A quiet-zone step the user picked. Gated, and clamped to the locked floor. */
        QUIET_STEP,
        /**
         * Upper-zone curve remainder. Gated silently: a locked device never reaches the
         * upper zone either, because [FullRangeCoordinator] refuses that gesture first and
         * shows the paywall itself. If one arrives anyway, holding it at 0 dB is correct.
         */
        CURVE_REMAINDER,
        /** Restores and internal writes (boot restore, mute cancel, absorb easing). Gated silently. */
        SYSTEM,
        /** The mute convenience. Gated silently, for the same reason as the remainder. */
        MUTE,
    }

    /**
     * Sets attenuation level. Persists to prefs and updates StateFlow.
     * Thread-safe: AudioEffect API is thread-safe internally.
     * @param dB range [Prefs.ATTENUATION_MIN, Prefs.ATTENUATION_MAX]
     * @param source who is asking — decides whether the free-floor gate applies
     */
    fun setAttenuation(dB: Float, source: GainSource = GainSource.SYSTEM) {
        val requested = dB.coerceIn(Prefs.ATTENUATION_MIN, Prefs.ATTENUATION_MAX)
        val clamped = if (proProvider()) requested else {
            val limited = requested.coerceAtLeast(lockedFloorDb)
            if (limited != requested) {
                Log.i(tag, "Gate: ${requested}dB requested, held at ${limited}dB (source=$source)")
            }
            limited
        }
        strategy?.setAttenuation(clamped)
        _attenuationDb.value = clamped
        // Persist ONLY what the user is entitled to keep. The stored level is the place
        // the dial returns to, and every public surface promises a buyer "every step comes
        // back exactly where you left it" -- so a locked session must never overwrite it.
        // Concretely: while entitled (grandfathered, trial, key) every change persists as
        // before; while locked nothing does, so the level from the last entitled day
        // survives the clamp untouched and the next entitled start re-applies it through
        // initialize(). That is the entire restore path, with no separate bookkeeping.
        if (proProvider()) Prefs.setAttenuation(context, clamped)
        Log.d(tag, "Attenuation set to ${clamped}dB (source=$source)")
    }

    /**
     * Convenience: mute immediately (max attenuation). Gated like everything else once the
     * trial is over: mute IS the deepest step, so leaving it open would be the whole product
     * behind a different button.
     */
    fun mute() = setAttenuation(Prefs.ATTENUATION_MIN, GainSource.MUTE)

    /**
     * Convenience: pass-through (no attenuation).
     */
    fun passThrough() = setAttenuation(Prefs.ATTENUATION_MAX)

    /**
     * Tears the effect down and rebuilds it, restoring the current attenuation.
     *
     * Why this exists (in-call fix, 2026-08-16): a session-0 effect chain lives on ONE
     * output thread, chosen by audio policy at effect creation time following the MUSIC
     * strategy. Call audio (cellular downlink, VoIP playout) is routed to a different
     * output on many devices, so the running effect never touches it. Re-creating the
     * effect while the call is active gives policy the chance to attach the chain to the
     * output that is actually carrying sound right now. Field-measured: quiet zone dead
     * in calls on two devices while media attenuation worked on both.
     *
     * Cheap and safe by construction: [initialize] re-applies the persisted attenuation,
     * so the audible state is preserved across the swap; on devices where policy re-picks
     * the same output this is a harmless no-op glitch of a few ms.
     */
    @Synchronized
    fun reattach() {
        if (released) return
        Log.i(tag, "Reattaching audio effect (attenuation=${_attenuationDb.value}dB)")
        strategy?.release()
        strategy = null
        isEffectAvailable = false
        initialize()
    }

    /**
     * Releases the underlying AudioEffect. Must be called in Service.onDestroy().
     * After this call, this instance should not be used.
     */
    @Synchronized
    fun release() {
        released = true
        strategy?.release()
        strategy = null
        Log.i(tag, "AudioController released")
    }

}

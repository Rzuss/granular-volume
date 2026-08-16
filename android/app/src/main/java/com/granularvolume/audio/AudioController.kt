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

    private val tag = "GranularVolume:AudioCtrl"

    private var strategy: AudioEffectStrategy? = null

    /** Emits current attenuation in dB. UI observes this. */
    private val _attenuationDb = MutableStateFlow(Prefs.getAttenuation(context))
    val attenuationDb: StateFlow<Float> = _attenuationDb.asStateFlow()

    /** True if a working audio effect strategy was found. */
    var isEffectAvailable: Boolean = false
        private set

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
    fun initialize(): Boolean {
        val strategies: List<AudioEffectStrategy> = listOf(
            DynamicsProcessingStrategy(),
            LoudnessEnhancerStrategy()
        )

        for (s in strategies) {
            if (s.initialize()) {
                strategy = s
                isEffectAvailable = true
                Log.i(tag, "Using strategy: ${s::class.simpleName}")
                // Apply persisted attenuation immediately
                applyAttenuation(_attenuationDb.value)
                return true
            }
        }

        Log.e(tag, "No AudioEffect strategy available on this device")
        isEffectAvailable = false
        return false
    }

    /**
     * Sets attenuation level. Persists to prefs and updates StateFlow.
     * Thread-safe: AudioEffect API is thread-safe internally.
     * @param dB range [Prefs.ATTENUATION_MIN, Prefs.ATTENUATION_MAX]
     */
    fun setAttenuation(dB: Float) {
        val clamped = dB.coerceIn(Prefs.ATTENUATION_MIN, Prefs.ATTENUATION_MAX)
        strategy?.setAttenuation(clamped)
        _attenuationDb.value = clamped
        Prefs.setAttenuation(context, clamped)
        Log.d(tag, "Attenuation set to ${clamped}dB")
    }

    /**
     * Convenience: mute immediately (max attenuation).
     */
    fun mute() = setAttenuation(Prefs.ATTENUATION_MIN)

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
    fun reattach() {
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
    fun release() {
        strategy?.release()
        strategy = null
        Log.i(tag, "AudioController released")
    }

    private fun applyAttenuation(dB: Float) {
        strategy?.setAttenuation(dB)
    }
}

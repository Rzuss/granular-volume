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

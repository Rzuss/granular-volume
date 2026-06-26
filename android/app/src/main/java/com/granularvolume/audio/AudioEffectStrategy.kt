package com.granularvolume.audio

/**
 * Abstraction over different AudioEffect implementations.
 * Allows runtime strategy selection based on device capability.
 */
interface AudioEffectStrategy {
    /** @return true if the effect was successfully initialized */
    fun initialize(): Boolean

    /**
     * Sets the attenuation level.
     * @param dB value in decibels, range [ATTENUATION_MIN, 0.0]
     *            0.0 = no attenuation (pass-through)
     *            -30.0 = near-silent
     */
    fun setAttenuation(dB: Float)

    /** Release all audio resources. Must be called on Service destroy. */
    fun release()

    companion object {
        const val GLOBAL_SESSION_ID = 0
        const val EFFECT_PRIORITY   = 0
    }
}

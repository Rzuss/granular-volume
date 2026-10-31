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

    /**
     * 1.8.0: whether this app's effect instance is the one setting the sound. Android gives the
     * control of an effect on a session to its newest owner of the highest priority, so another
     * app opening the same effect on the output mix at the same or a higher priority takes it (read
     * on GV_API36, 2026-10-31: priority 0 takes it from us, -1 does not), and our writes do nothing.
     */
    fun hasControl(): Boolean

    /** 1.8.0: told when [hasControl] changes. Called on the main thread by the audio framework. */
    var onControlChanged: ((granted: Boolean) -> Unit)?

    /** Release all audio resources. Must be called on Service destroy. */
    fun release()

    companion object {
        const val GLOBAL_SESSION_ID = 0
        const val EFFECT_PRIORITY   = 0
    }
}

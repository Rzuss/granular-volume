package com.granularvolume.audio

import android.media.audiofx.LoudnessEnhancer
import android.util.Log

/**
 * Fallback AudioEffect strategy using LoudnessEnhancer.
 * NOTE: Negative gain support is OEM-dependent. This is a best-effort fallback.
 * Target gain is in millibels (mB): 1 dB = 100 mB, -10 dB = -1000 mB
 */
class LoudnessEnhancerStrategy : AudioEffectStrategy {

    private val tag = "GranularVolume:LE"
    private var enhancer: LoudnessEnhancer? = null

    override fun initialize(): Boolean {
        return try {
            enhancer = LoudnessEnhancer(AudioEffectStrategy.GLOBAL_SESSION_ID).also { le ->
                le.setTargetGain(0) // Start neutral
                le.enabled = true
                Log.i(tag, "LoudnessEnhancer initialized (fallback mode)")
            }
            true
        } catch (e: RuntimeException) {
            Log.e(tag, "LoudnessEnhancer initialization failed: ${e.message}")
            false
        }
    }

    override fun setAttenuation(dB: Float) {
        val le = enhancer ?: return
        try {
            // Convert dB to millibels: 1 dB = 100 mB
            val mB = (dB * MILLIBELS_PER_DB).toInt()
            le.setTargetGain(mB)
        } catch (e: RuntimeException) {
            Log.e(tag, "setTargetGain failed: ${e.message}")
        }
    }

    override fun release() {
        enhancer?.run {
            enabled = false
            release()
        }
        enhancer = null
        Log.i(tag, "LoudnessEnhancer released")
    }

    private companion object {
        const val MILLIBELS_PER_DB = 100
    }
}

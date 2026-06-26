package com.granularvolume.audio

import android.media.audiofx.DynamicsProcessing
import android.util.Log

/**
 * AudioEffect strategy using DynamicsProcessing (API 28+).
 * Applies clean, flat-spectrum output gain — no tonal coloring.
 *
 * Advantage: The output gain stage is a linear scaler applied after all other
 * processing, giving us precise dB control without EQ artifacts.
 */
class DynamicsProcessingStrategy : AudioEffectStrategy {

    private val tag = "GranularVolume:DynProc"
    private var dp: DynamicsProcessing? = null

    override fun initialize(): Boolean {
        return try {
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                /* channelCount */ CHANNEL_COUNT,
                /* preEqInUse */ false, /* preEqBandCount */ 0,
                /* mbcInUse */ false, /* mbcBandCount */ 0,
                /* postEqInUse */ false, /* postEqBandCount */ 0,
                /* limiterInUse */ false
            ).build()

            dp = DynamicsProcessing(
                AudioEffectStrategy.EFFECT_PRIORITY,
                AudioEffectStrategy.GLOBAL_SESSION_ID,
                config
            ).also { effect ->
                effect.enabled = true
                Log.i(tag, "DynamicsProcessing initialized successfully")
            }
            true
        } catch (e: RuntimeException) {
            Log.e(tag, "DynamicsProcessing initialization failed: ${e.message}")
            false
        }
    }

    override fun setAttenuation(dB: Float) {
        val effect = dp ?: return
        try {
            // DEVIATION FROM SPEC: CLAUDE.md referenced dp.setOutputGain(channel, dB),
            // which is NOT a real Android API. DynamicsProcessing exposes no global
            // output-gain setter. The input-gain stage is the correct equivalent: it is
            // a flat, frequency-independent dB scaler applied across all channels — giving
            // exactly the clean, no-coloring attenuation the design calls for.
            effect.setInputGainAllChannelsTo(dB)
        } catch (e: RuntimeException) {
            Log.e(tag, "setAttenuation failed: ${e.message}")
        }
    }

    override fun release() {
        dp?.run {
            enabled = false
            release()
        }
        dp = null
        Log.i(tag, "DynamicsProcessing released")
    }

    private companion object {
        // Stereo output mix — two channels on the global session.
        const val CHANNEL_COUNT = 2
    }
}

package com.granularvolume.audio

import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.Log
import kotlin.math.abs

/**
 * A device's real volume curve for one stream on one output route, read at runtime via
 * [AudioManager.getStreamVolumeDb] (API 28 — equals minSdk, no compat path needed).
 *
 * Why this exists: hardware volume steps are OEM-defined and NON-uniform (typically coarser
 * near the bottom of the range). The full-range slider promises a uniform 5 dB ladder, which
 * is only possible by knowing how many dB each hardware index is actually worth and letting
 * the DynamicsProcessing gain fill the remainder. This class is that knowledge.
 *
 * All dB values are RELATIVE to the maximum index (rel(max) == 0), because the absolute
 * reference AudioFlinger reports is meaningless to the user — only distances matter.
 *
 * Safety property relied on elsewhere: a rung's [Rung.remainderDb] is bounded by the LOCAL
 * hardware gap around it, so if the audio effect ever dies, the loudness jump equals at most
 * one hardware step — the same jump a physical button press produces.
 */
class VolumeCurve private constructor(
    val streamType: Int,
    /** dB relative to max index, one entry per hardware index 0..maxIndex. Monotone rising. */
    val relDb: FloatArray,
    /** Lowest hardware index that still produces sound (the "floor" the quiet zone pins). */
    val minAudibleIndex: Int
) {

    /**
     * One 5 dB rung of the upper-zone ladder.
     * @param hardwareIndex nearest hardware index AT OR ABOVE the rung's target loudness
     * @param remainderDb   the correction our gain applies on top (always <= 0, media mode only)
     * @param totalDb       the rung's target, relative to max volume (0, -5, -10, ...)
     */
    data class Rung(val hardwareIndex: Int, val remainderDb: Float, val totalDb: Float)

    /** Uniform 5 dB ladder from max volume (rung 0) down to the floor. Never empty. */
    val rungs: List<Rung> = buildRungs()

    val maxIndex: Int get() = relDb.lastIndex
    val floorDb: Float get() = relDb[minAudibleIndex]

    private fun buildRungs(): List<Rung> {
        val out = ArrayList<Rung>()
        var target = 0f
        val floor = relDb[minAudibleIndex]
        while (target >= floor) {
            // Smallest index whose loudness is >= target ("nearest from above"). Always exists:
            // rel(maxIndex) == 0 >= target.
            var idx = maxIndex
            for (i in minAudibleIndex..maxIndex) {
                if (relDb[i] >= target) { idx = i; break }
            }
            out.add(Rung(idx, target - relDb[idx], target))
            target -= RUNG_DB
        }

        // SEAM FIX (2026-08-09): anchor the last rung EXACTLY on the device floor.
        // Without this the ladder stops at the last 5 dB multiple above the floor, so crossing
        // the line jumps by (floor - lastRung) — anywhere from 0 to 5 dB — at precisely the one
        // place the product promises continuity. Measured on the API 36 emulator: last rung -50,
        // floor -53.26, a 3.26 dB step at the seam.
        // The cost is one shorter step at the very bottom of the upper zone, which is far less
        // noticeable than a discontinuity mid-slider. This final rung is the SAME loudness as the
        // quiet zone's 0 dB step (hardware at floor, no gain), and the chevron logic already
        // skips that duplicate when crossing.
        val last = out.lastOrNull()
        if (last == null || last.totalDb - floor > SEAM_EPSILON_DB) {
            out.add(Rung(minAudibleIndex, 0f, floor))
        }
        return out
    }

    /** The rung whose total loudness sits closest to [db] (relative to max). */
    fun nearestRung(db: Float): Int =
        rungs.indices.minByOrNull { abs(rungs[it].totalDb - db) } ?: 0

    companion object {
        private const val TAG = "GranularVolume:Curve"

        /** Ladder spacing — matches STEP_DB spacing in the quiet zone so the scale reads as one. */
        const val RUNG_DB = 5f

        /**
         * If the last 5 dB rung already sits within this of the floor, do not add a floor rung —
         * it would be an inaudible duplicate step.
         */
        private const val SEAM_EPSILON_DB = 0.5f

        // Sanity bounds: a curve whose full range is outside these is OEM nonsense — reject it
        // and let the caller fall back to plain hardware-index behaviour (spec decision).
        private const val MIN_PLAUSIBLE_RANGE_DB = 3f
        private const val MAX_PLAUSIBLE_RANGE_DB = 100f

        /**
         * Reads the curve for [streamType] on the current output route.
         * @return the curve, or null if the device returns implausible values — callers must
         *         treat null as "no ladder, use raw hardware indices".
         */
        fun read(am: AudioManager, streamType: Int): VolumeCurve? {
            val deviceType = currentOutputDeviceType(am)
            val max = am.getStreamMaxVolume(streamType)
            val min = am.getStreamMinVolume(streamType)
            if (max < 1) return null

            val raw = FloatArray(max + 1)
            try {
                for (i in 0..max) raw[i] = am.getStreamVolumeDb(streamType, i, deviceType)
            } catch (e: Exception) {
                Log.w(TAG, "getStreamVolumeDb failed: ${e.message}")
                return null
            }

            // Relative to max; index 0 is often -inf (mute) — clamp it below the floor.
            val top = raw[max]
            val rel = FloatArray(max + 1) { i ->
                val r = raw[i] - top
                if (r.isFinite()) r else -MAX_PLAUSIBLE_RANGE_DB
            }

            // Sanity: monotone non-decreasing and a plausible audible range.
            for (i in 1..max) {
                if (rel[i] < rel[i - 1]) {
                    Log.w(TAG, "Non-monotone curve at index $i — rejecting")
                    return null
                }
            }
            val minAudible = maxOf(1, min)
            if (minAudible > max) return null
            val range = -rel[minAudible]
            if (range < MIN_PLAUSIBLE_RANGE_DB || range > MAX_PLAUSIBLE_RANGE_DB) {
                Log.w(TAG, "Implausible curve range ${range}dB — rejecting")
                return null
            }

            Log.i(TAG, "Curve stream=$streamType device=$deviceType max=$max floor=${rel[minAudible]}dB")
            return VolumeCurve(streamType, rel, minAudible)
        }

        /**
         * Best guess at the active output device type, preferring the route audio actually
         * takes when several are connected: Bluetooth A2DP > wired > USB > built-in speaker.
         * The curve differs per route (spec: re-read on every route change).
         */
        private fun currentOutputDeviceType(am: AudioManager): Int {
            val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val priority = listOf(
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            )
            for (wanted in priority) {
                if (devices.any { it.type == wanted }) return wanted
            }
            return AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        }
    }
}

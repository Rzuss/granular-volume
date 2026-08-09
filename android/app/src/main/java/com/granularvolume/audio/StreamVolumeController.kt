package com.granularvolume.audio

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log

/**
 * Hardware volume control for the full-range upper zone.
 *
 * Responsibilities, per the locked spec:
 *  - Drive whatever stream the physical buttons would drive: MEDIA while audio plays,
 *    RING otherwise ([activeStream]) — restoring lost button behaviour, not inventing new.
 *  - Floor the ring stream at index 1, NEVER 0: driving ring to zero toggles silent/DND mode,
 *    which requires ACCESS_NOTIFICATION_POLICY. Staying above zero keeps us at zero permissions.
 *  - Mark every write we make ([wasSelfChange]) so the volume-change receiver can tell our own
 *    writes from external ones and never loops.
 *  - THE INVARIANT (spec, 2026-08-09): corrections issued through [lowerTo] may only ever
 *    LOWER hardware volume. Raising it requires an explicit user action routed via [setIndex].
 */
class StreamVolumeController(context: Context) {

    private val tag = "GranularVolume:StreamVol"
    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** Timestamp of our last write, per stream — the self-change flag. */
    private val lastSelfChangeMs = HashMap<Int, Long>()

    /** Stream the physical buttons would currently drive. */
    fun activeStream(): Int =
        if (am.isMusicActive) AudioManager.STREAM_MUSIC else AudioManager.STREAM_RING

    fun index(stream: Int): Int = am.getStreamVolume(stream)
    fun maxIndex(stream: Int): Int = am.getStreamMaxVolume(stream)

    /** Lowest index that still produces sound; ring additionally floored at 1 (DND rule above). */
    fun minAudibleIndex(stream: Int): Int = maxOf(1, am.getStreamMinVolume(stream))

    /**
     * Explicit user-initiated set (slider touch). May move in either direction.
     * Clamped to [minAudibleIndex, max] — the slider never produces hardware mute; true mute
     * is only the mute button's job.
     */
    fun setIndex(stream: Int, index: Int) {
        val clamped = index.coerceIn(minAudibleIndex(stream), maxIndex(stream))
        write(stream, clamped)
    }

    /**
     * Correction path used by the absorb policy. Enforces the lower-only invariant:
     * if [index] is not strictly below the current index, this is a no-op.
     * @return true if a write happened
     */
    fun lowerTo(stream: Int, index: Int): Boolean {
        val current = index(stream)
        val clamped = index.coerceIn(minAudibleIndex(stream), maxIndex(stream))
        if (clamped >= current) return false
        write(stream, clamped)
        return true
    }

    /**
     * True if a volume change observed "now-ish" on [stream] was caused by our own write.
     * The window is deliberately short: external changes arriving later must not be eaten.
     */
    fun wasSelfChange(stream: Int): Boolean {
        val t = lastSelfChangeMs[stream] ?: return false
        return SystemClock.elapsedRealtime() - t <= SELF_CHANGE_WINDOW_MS
    }

    /**
     * True media mute (index 0). Deliberately bypasses the minAudible clamp — this is the ONE
     * sanctioned path to hardware silence, reachable only from the explicit mute button.
     * Media-stream mute needs no DND permission (that rule applies to the ring stream).
     * @return the index that was live before muting, for exact restore
     */
    fun muteMedia(): Int {
        val previous = index(AudioManager.STREAM_MUSIC)
        write(AudioManager.STREAM_MUSIC, 0)
        return previous
    }

    /** Restores the media stream to [index] after an unmute. */
    fun restoreMedia(index: Int) {
        write(AudioManager.STREAM_MUSIC, index.coerceIn(0, maxIndex(AudioManager.STREAM_MUSIC)))
    }

    private fun write(stream: Int, index: Int) {
        lastSelfChangeMs[stream] = SystemClock.elapsedRealtime()
        try {
            // FLAG_0: no system volume UI — the overlay IS the UI.
            am.setStreamVolume(stream, index, 0)
        } catch (e: SecurityException) {
            // Defensive: should be unreachable given the >=1 ring floor, but an OEM surprise
            // here must degrade to "no hardware write", never crash the service.
            Log.e(tag, "setStreamVolume rejected: ${e.message}")
        }
        Log.d(tag, "stream=$stream -> index=$index")
    }

    companion object {
        private const val SELF_CHANGE_WINDOW_MS = 500L
    }
}

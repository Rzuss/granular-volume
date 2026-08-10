package com.granularvolume.audio

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log

/**
 * Hardware volume control for the full-range upper zone.
 *
 * Responsibilities, per the locked spec:
 *  - Drive the MEDIA stream, always ([activeStream]). The 1.4.0/1.4.1 rule "media while audio
 *    plays, ring otherwise" was built on a false premise: AOSP's own
 *    AudioService.getActiveStreamType falls through to DEFAULT_VOL_STREAM_NO_PLAYBACK ==
 *    STREAM_MUSIC when nothing is playing (frameworks/base, line 733), so the physical keys
 *    drive MEDIA in the idle case too. Our ring branch made the dial freeze on the home screen
 *    and silently wrote the ringer (field report + AOSP source, 2026-08-10). Never touching
 *    the ring stream also retires the silent/DND permission concern entirely.
 *  - Mark every write we make ([wasSelfChange]) so the volume-change receiver can tell our own
 *    writes from external ones and never loops.
 *  - THE INVARIANT (spec, 2026-08-09): corrections issued through [lowerTo] may only ever
 *    LOWER hardware volume. Raising it requires an explicit user action routed via [setIndex].
 */
class StreamVolumeController(context: Context) {

    private val tag = "GranularVolume:StreamVol"
    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** Our last write per stream: the value we wrote and when. */
    private data class SelfWrite(val index: Int, val atMs: Long)
    private val lastSelfWrite = HashMap<Int, SelfWrite>()

    /**
     * Stream the dial drives. MEDIA in every ordinary state — matching AOSP's no-playback
     * default (see class KDoc) — and the VOICE CALL stream while a call is in progress,
     * matching what the physical keys do in a call (AOSP getActiveStreamType's
     * isInCommunication branch). Evaluated LIVE on every use, exactly like AOSP evaluates
     * it per keypress, so no phone-state permission and no listener is needed for control
     * correctness. MODE_RINGTONE (incoming ring, not yet answered) deliberately stays on
     * MEDIA: the ringer is the one stream this app never touches (1.4.2 decision).
     */
    fun activeStream(): Int = when (am.mode) {
        AudioManager.MODE_IN_CALL,
        AudioManager.MODE_IN_COMMUNICATION -> AudioManager.STREAM_VOICE_CALL
        else -> AudioManager.STREAM_MUSIC
    }

    /** True while a cellular or VoIP call is in progress (same condition as [activeStream]). */
    fun inCall(): Boolean = activeStream() == AudioManager.STREAM_VOICE_CALL

    fun index(stream: Int): Int = am.getStreamVolume(stream)
    fun maxIndex(stream: Int): Int = am.getStreamMaxVolume(stream)

    /**
     * Lowest index that still produces sound, floored at 1: the slider must never produce
     * hardware mute (index 0) — true silence is exclusively the mute button's job.
     */
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
     * True if the volume change observed on [stream] landing at [observedIndex] was our own.
     *
     * Matches the VALUE we wrote as well as the time. A time-only window raced with system
     * broadcast delivery: a late broadcast for our own write was read as an external change,
     * which fired the absorb policy and moved the dial by itself. Requiring the value to match
     * removes that race, so the window can be generous without ever eating a real user press
     * (a real press lands on a different index than the one we just wrote).
     */
    fun wasSelfChange(stream: Int, observedIndex: Int): Boolean {
        val w = lastSelfWrite[stream] ?: return false
        return w.index == observedIndex &&
                SystemClock.elapsedRealtime() - w.atMs <= SELF_CHANGE_WINDOW_MS
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
        lastSelfWrite[stream] = SelfWrite(index, SystemClock.elapsedRealtime())
        try {
            // FLAG_0: no system volume UI — the overlay IS the UI.
            am.setStreamVolume(stream, index, 0)
        } catch (e: SecurityException) {
            // Defensive: media-stream writes need no special permission, but an OEM surprise
            // here must degrade to "no hardware write", never crash the service.
            Log.e(tag, "setStreamVolume rejected: ${e.message}")
        }
        Log.d(tag, "stream=$stream -> index=$index")
    }

    companion object {
        // Generous, because the value must also match — see wasSelfChange.
        private const val SELF_CHANGE_WINDOW_MS = 1_500L
    }
}

package com.granularvolume.audio

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The brain of full-range mode: one logical scale built from two mechanisms.
 *
 *  UPPER ZONE (above the device-minimum line)
 *    Media mode: uniform 5 dB rungs from [VolumeCurve] — hardware index does the coarse work,
 *    the DynamicsProcessing gain fills the sub-5 dB remainder. If the curve was rejected
 *    (OEM nonsense), falls back to raw hardware indices with zero gain.
 *    Ring mode: raw hardware indices ONLY, gain pinned to 0 — the gain is global (session 0),
 *    so a remainder tuned for the ring stream would distort media that starts later
 *    (final-audit decision #1, 2026-08-09).
 *
 *  QUIET ZONE (below the line)
 *    Exactly today's product: hardware pinned at the floor, gain runs 0..-30 dB in 5 dB steps.
 *
 *  MUTE
 *    Media-stream mute (index 0), NOT a global-gain mute, so alarms keep ringing. Reversible:
 *    unmute restores the exact previous index. Slider movement while muted cancels mute first.
 *
 *  ABSORB POLICY (external volume changes while in the quiet zone)
 *    THE INVARIANT: corrections only ever LOWER hardware volume.
 *    - our own writes: ignored (self-change flag)
 *    - single-step button press: absorbed — hardware back to floor, attenuation moves 5 dB
 *    - large upward jump: hardware back to floor, attenuation kept (defend the quiet)
 *    - any downward change: never fought
 *    - fight-loop breaker: too many corrections in a short window → surrender, sync display.
 */
class FullRangeCoordinator(
    context: Context,
    private val audioController: AudioController,
    private val streamVol: StreamVolumeController
) {

    private val tag = "GranularVolume:FullRange"
    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** Media curve for the current output route; null = fallback to raw indices. */
    @Volatile
    var mediaCurve: VolumeCurve? = null
        private set

    /** Bumps every time external state changed in a way the overlay should re-render. */
    private val _uiRevision = MutableStateFlow(0)
    val uiRevision: StateFlow<Int> = _uiRevision.asStateFlow()

    /** True while the media stream is muted by our mute button. */
    @Volatile
    var isMuted: Boolean = false
        private set
    private var preMuteIndex: Int = -1
    private var preMuteAttenuation: Float = 0f

    /**
     * Which zone the user is logically in. Needed because the gain alone is ambiguous:
     * a small negative gain can be either a quiet-zone step or an upper-zone remainder.
     * Initialised from the persisted attenuation so the 110 existing users land exactly
     * where they left off (zero-write migration, final-audit decision #2).
     */
    @Volatile
    var zoneQuiet: Boolean = audioController.attenuationDb.value < 0f
        private set

    /** Snapshot the overlay renders from. One source of truth, computed on demand. */
    data class UiState(
        val muted: Boolean,
        val zoneQuiet: Boolean,
        val quietDb: Float,
        val upperPos: Int,
        val upperCount: Int,
        val percent: Int,
        val ringMode: Boolean
    )

    fun uiState(): UiState {
        val stream = streamVol.activeStream()
        val ringMode = stream != AudioManager.STREAM_MUSIC
        val idx = streamVol.index(stream)
        val max = streamVol.maxIndex(stream)
        val percent = if (max > 0) (idx * 100) / max else 0
        return UiState(
            muted = isMuted,
            zoneQuiet = zoneQuiet,
            quietDb = audioController.attenuationDb.value,
            upperPos = currentUpperPos(stream, idx),
            upperCount = upperPositionCount(),
            percent = percent,
            ringMode = ringMode
        )
    }

    /** Position (0 = loudest) that best matches the CURRENT hardware state. */
    private fun currentUpperPos(stream: Int, idx: Int): Int {
        val curve = mediaCurve
        return if (stream == AudioManager.STREAM_MUSIC && curve != null) {
            val safeIdx = idx.coerceIn(0, curve.maxIndex)
            val totalDb = curve.relDb[safeIdx] + audioController.attenuationDb.value
            curve.nearestRung(totalDb)
        } else {
            (streamVol.maxIndex(stream) - idx).coerceAtLeast(0)
        }
    }

    // Fight-loop breaker state.
    private var correctionTimesMs = ArrayDeque<Long>()
    @Volatile
    var surrendered: Boolean = false
        private set

    /**
     * Playback started or stopped, so [StreamVolumeController.activeStream] may have flipped
     * between media and ring. The ladder and the label both depend on it, so re-render.
     * Cheap and idempotent: a no-op if the active stream did not actually change.
     */
    fun onActiveStreamMayHaveChanged() {
        val stream = streamVol.activeStream()
        if (stream == lastActiveStream) return
        lastActiveStream = stream
        Log.i(tag, "Active stream -> ${if (stream == AudioManager.STREAM_MUSIC) "MEDIA" else "RING"}")
        notifyUi()
    }

    private var lastActiveStream: Int = -1

    /** (Re)read the media curve. Call on service start and on every output-route change. */
    fun refreshCurve() {
        mediaCurve = VolumeCurve.read(am, AudioManager.STREAM_MUSIC)
        surrendered = false
        notifyUi()
    }

    fun inQuietZone(): Boolean = audioController.attenuationDb.value < 0f

    /**
     * Number of selectable positions in the upper zone for the CURRENT mode.
     * Media with a valid curve: the 5 dB rung count. Otherwise: raw hardware indices.
     */
    fun upperPositionCount(): Int {
        val stream = streamVol.activeStream()
        val curve = mediaCurve
        return if (stream == AudioManager.STREAM_MUSIC && curve != null) {
            curve.rungs.size
        } else {
            streamVol.maxIndex(stream) - streamVol.minAudibleIndex(stream) + 1
        }
    }

    /**
     * User selected upper-zone position [pos] (0 = loudest). Clears any quiet-zone attenuation
     * except the remainder, cancels mute if active.
     */
    fun applyUpper(pos: Int) {
        if (isMuted) cancelMute()
        val stream = streamVol.activeStream()
        val curve = mediaCurve
        if (stream == AudioManager.STREAM_MUSIC && curve != null) {
            val rung = curve.rungs[pos.coerceIn(0, curve.rungs.lastIndex)]
            streamVol.setIndex(stream, rung.hardwareIndex)
            audioController.setAttenuation(rung.remainderDb)
        } else {
            // Ring mode or fallback: raw indices, gain MUST stay 0 (see class KDoc).
            val top = streamVol.maxIndex(stream)
            streamVol.setIndex(stream, (top - pos).coerceAtLeast(streamVol.minAudibleIndex(stream)))
            audioController.setAttenuation(0f)
        }
        zoneQuiet = false
        notifyUi()
    }

    /**
     * User selected quiet-zone step [stepDb] (one of OverlayManager.STEP_DB). Pins the media
     * stream at its floor and hands the rest to the gain — exactly today's behaviour.
     */
    fun applyQuiet(stepDb: Float) {
        if (isMuted) cancelMute()
        val media = AudioManager.STREAM_MUSIC
        streamVol.lowerTo(media, streamVol.minAudibleIndex(media))
        audioController.setAttenuation(stepDb)
        zoneQuiet = true
        notifyUi()
    }

    // ────────────────────────────────────────────────────────────────
    // Mute (media stream only — alarms survive by design)
    // ────────────────────────────────────────────────────────────────

    fun toggleMute() {
        if (isMuted) cancelMute() else {
            preMuteIndex = streamVol.index(AudioManager.STREAM_MUSIC)
            preMuteAttenuation = audioController.attenuationDb.value
            streamVol.muteMedia()
            isMuted = true
            notifyUi()
        }
    }

    private fun cancelMute() {
        if (!isMuted) return
        streamVol.restoreMedia(preMuteIndex)
        audioController.setAttenuation(preMuteAttenuation)
        isMuted = false
        notifyUi()
    }

    // ────────────────────────────────────────────────────────────────
    // Absorb policy — called by the service's VOLUME_CHANGED receiver
    // ────────────────────────────────────────────────────────────────

    /**
     * An external volume change was observed on [stream] (old [from] → new [to]).
     * Applies the locked policy. Never raises hardware volume.
     */
    fun onExternalVolumeChange(stream: Int, from: Int, to: Int) {
        if (streamVol.wasSelfChange(stream)) return
        if (!inQuietZone() || stream != AudioManager.STREAM_MUSIC) {
            // Upper zone / other stream: display sync only.
            notifyUi()
            return
        }
        if (isMuted) { notifyUi(); return }
        if (to <= from) {
            // Downward external change (safe-volume, user intent to be quieter): never fought.
            notifyUi()
            return
        }
        if (surrendered) { notifyUi(); return }
        if (!registerCorrection()) {
            Log.w(tag, "Fight-loop breaker tripped — surrendering, display sync only")
            surrendered = true
            notifyUi()
            return
        }

        val floor = streamVol.minAudibleIndex(stream)
        val current = audioController.attenuationDb.value
        if (to - from == 1) {
            // Single button step up: absorb — back to floor, attenuation eases 5 dB.
            streamVol.lowerTo(stream, floor)
            audioController.setAttenuation((current + VolumeCurve.RUNG_DB).coerceAtMost(0f))
            Log.i(tag, "Absorbed +1 step: attenuation ${current} -> ${current + VolumeCurve.RUNG_DB}")
        } else {
            // Large jump (an app set 70%): defend the quiet — floor restored, attenuation kept.
            streamVol.lowerTo(stream, floor)
            Log.i(tag, "Defended quiet zone against jump $from -> $to")
        }
        notifyUi()
    }

    /** Sliding window rate limit. @return false when the fight-loop breaker should trip. */
    private fun registerCorrection(): Boolean {
        val now = SystemClock.elapsedRealtime()
        correctionTimesMs.addLast(now)
        while (correctionTimesMs.isNotEmpty() && now - correctionTimesMs.first() > FIGHT_WINDOW_MS) {
            correctionTimesMs.removeFirst()
        }
        return correctionTimesMs.size <= MAX_CORRECTIONS_IN_WINDOW
    }

    private fun notifyUi() {
        _uiRevision.value = _uiRevision.value + 1
    }

    companion object {
        // Tuned on real hardware during Round A; spec placeholders until then.
        private const val FIGHT_WINDOW_MS = 5_000L
        private const val MAX_CORRECTIONS_IN_WINDOW = 3
    }
}

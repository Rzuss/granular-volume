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
 *    Always the MEDIA stream (1.4.2 — see [StreamVolumeController] KDoc for the AOSP proof
 *    that the physical keys drive media even when nothing plays). Uniform 5 dB rungs from
 *    [VolumeCurve] — hardware index does the coarse work, the DynamicsProcessing gain fills
 *    the sub-5 dB remainder. If the curve was rejected (OEM nonsense) or the route is
 *    wireless with Absolute Volume, falls back to raw hardware indices with zero gain.
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
    private val appContext = context.applicationContext
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
        val percent: Int
    )

    fun uiState(): UiState {
        val stream = streamVol.activeStream()
        val idx = streamVol.index(stream)
        val max = streamVol.maxIndex(stream)
        val percent = if (max > 0) (idx * 100) / max else 0
        return UiState(
            muted = isMuted,
            zoneQuiet = zoneQuiet,
            quietDb = audioController.attenuationDb.value,
            upperPos = currentUpperPos(stream, idx),
            upperCount = upperPositionCount(),
            percent = percent
        )
    }

    /** Position (0 = loudest) that best matches the CURRENT hardware state. */
    private fun currentUpperPos(stream: Int, idx: Int): Int {
        val curve = mediaCurve
        return if (curve != null) {
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

    /** (Re)read the media curve. Call on service start and on every output-route change. */
    fun refreshCurve() {
        // Wireless routes with Bluetooth Absolute Volume active (the modern default): the
        // phone forwards the index and the HEADSET applies its own loudness curve, so the
        // table getStreamVolumeDb reports is not what plays. Building the 5 dB ladder from
        // it painted live bars over headset silence (field report, 2026-08-10). Raw indices
        // are the honest scale there: every bar is a real hardware step, exactly what the
        // volume keys do, so nothing on screen can be a step the ear never hears.
        // If the user disabled Absolute Volume in developer options, the phone-side curve
        // is back in charge and the uniform ladder is trustworthy again.
        val wireless = VolumeCurve.isWirelessRoute(am)
        mediaCurve = if (wireless && !absoluteVolumeDisabled()) {
            Log.i(tag, "Wireless route with Absolute Volume: raw-index upper zone")
            null
        } else {
            VolumeCurve.read(am, AudioManager.STREAM_MUSIC)
        }
        if (isMuted) {
            // The saved pre-mute level belonged to the previous route, and indices are per
            // route: restoring a headset level onto the speaker would be a loud surprise.
            // Re-anchor to whatever the new route is already set to; if the mute itself
            // carried over as zero, fall back to the minimum audible step instead of
            // "unmute to silence".
            val current = streamVol.index(AudioManager.STREAM_MUSIC)
            preMuteIndex = if (current > 0) current
                           else streamVol.minAudibleIndex(AudioManager.STREAM_MUSIC)
        }
        surrendered = false
        notifyUi()
    }

    /**
     * The developer option "Disable absolute volume". The settings key string is stable
     * AOSP since Android 8. Absent or unreadable means the feature is in its default
     * state, which is ON, so we return false and stay conservative.
     */
    private fun absoluteVolumeDisabled(): Boolean = try {
        android.provider.Settings.Global.getInt(
            appContext.contentResolver, "bluetooth_disable_absolute_volume", 0
        ) == 1
    } catch (e: Exception) {
        false
    }

    /**
     * Which zone the user is in. MUST read the authoritative flag, never the gain's sign:
     * our gain is also active ABOVE the line (it carries the sub-rung remainder that makes the
     * 5 dB ladder uniform), so a negative gain does NOT imply the quiet zone.
     *
     * Getting this wrong caused a real fault, reported from hardware 2026-08-09: on the second
     * rung from the bottom — the first one with a non-zero remainder — the absorb policy
     * believed it was defending the quiet zone, saw the volume rise, and "corrected" it back to
     * the floor. The dial snapped down a rung on its own, intermittently.
     */
    fun inQuietZone(): Boolean = zoneQuiet

    /**
     * Number of selectable positions in the upper zone.
     * With a valid curve: the 5 dB rung count. Otherwise: raw hardware indices.
     */
    fun upperPositionCount(): Int {
        val stream = streamVol.activeStream()
        val curve = mediaCurve
        return if (curve != null) {
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
        if (curve != null) {
            val rung = curve.rungs[pos.coerceIn(0, curve.rungs.lastIndex)]
            streamVol.setIndex(stream, rung.hardwareIndex)
            audioController.setAttenuation(rung.remainderDb)
        } else {
            // Raw-index fallback (rejected curve, or wireless Absolute Volume): gain MUST
            // stay 0 — every position is a real hardware step, nothing to fill in.
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
        if (streamVol.wasSelfChange(stream, to)) return
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

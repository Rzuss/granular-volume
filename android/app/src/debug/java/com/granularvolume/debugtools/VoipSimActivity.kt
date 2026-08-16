package com.granularvolume.debugtools

import android.app.Activity
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.util.Log
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin

/**
 * DEBUG-ONLY diagnostic instrument. Simulates the audio conditions of a VoIP call so the
 * engine-layer placement of our session-0 effect can be read from `dumpsys media.audio_flinger`
 * on an emulator, where no real telephony audio exists.
 *
 * Drive it from adb:
 *   am start -n granularvolume.com.debug/com.granularvolume.debugtools.VoipSimActivity \
 *       --es sim_mode in_communication      # or: in_call | normal
 *   am start ... --es sim_mode stop         # stop playback and restore NORMAL
 *
 * It sets the requested audio mode and loops a 440 Hz tone on an AudioTrack whose
 * AudioAttributes are USAGE_VOICE_COMMUNICATION + CONTENT_TYPE_SPEECH — the exact attributes
 * a VoIP app's downlink playout uses, which is what routes it to the communication output.
 */
class VoipSimActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when (intent.getStringExtra("sim_mode") ?: "in_communication") {
            "stop" -> stopSim(am)
            "in_call" -> startSim(am, AudioManager.MODE_IN_CALL)
            "normal" -> startSim(am, AudioManager.MODE_NORMAL)
            else -> startSim(am, AudioManager.MODE_IN_COMMUNICATION)
        }
        finish()
    }

    private fun startSim(am: AudioManager, mode: Int) {
        stopSim(am)
        am.mode = mode
        Log.i(TAG, "audio mode set to $mode (now=${am.mode})")

        val sampleRate = 48000
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack(attrs, format, minBuf * 4, AudioTrack.MODE_STREAM, 0)
        activeTrack = track
        running = true
        track.play()
        Log.i(TAG, "VOICE_COMMUNICATION track playing, session=${track.audioSessionId}")

        thread(name = "VoipSimTone") {
            val buf = ShortArray(minBuf)
            var phase = 0.0
            val step = 2.0 * PI * 440.0 / sampleRate
            while (running) {
                for (i in buf.indices) {
                    buf[i] = (sin(phase) * 8000).toInt().toShort()
                    phase += step
                }
                val w = track.write(buf, 0, buf.size)
                if (w < 0) break
            }
        }
    }

    private fun stopSim(am: AudioManager) {
        running = false
        activeTrack?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
            Log.i(TAG, "sim track stopped")
        }
        activeTrack = null
        am.mode = AudioManager.MODE_NORMAL
    }

    private companion object {
        const val TAG = "GranularVolume:VoipSim"
        // Static so a second launch with sim_mode=stop can reach the running track:
        // the activity instance finishes immediately, the playback thread does not.
        @Volatile var running = false
        @Volatile var activeTrack: AudioTrack? = null
    }
}

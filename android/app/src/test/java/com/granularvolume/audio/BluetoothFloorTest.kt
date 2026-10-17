package com.granularvolume.audio

import android.content.Context
import android.content.ContextWrapper
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.provider.Settings
import android.os.Build
import com.granularvolume.util.Prefs
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BluetoothFloorTest {
    private lateinit var am: AudioManager
    private lateinit var context: Context
    private lateinit var audio: AudioController
    private lateinit var volume: StreamVolumeController
    private lateinit var coordinator: FullRangeCoordinator
    private var index = 1
    // Record gain at the moment of each hardware write, so ordering is observable.
    private val writes = mutableListOf<Pair<Int, Float>>()

    @Before fun setup() {
        am = mock(AudioManager::class.java)
        context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getApplicationContext(): Context = this
            override fun getSystemService(name: String): Any? =
                if (name == Context.AUDIO_SERVICE) am else super.getSystemService(name)
        }
        context.getSharedPreferences("gv_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        Settings.Global.putInt(context.contentResolver, "bluetooth_disable_absolute_volume", 0)
        `when`(am.getStreamMinVolume(anyInt())).thenReturn(0)
        `when`(am.getStreamMaxVolume(anyInt())).thenReturn(15)
        `when`(am.getStreamVolume(anyInt())).thenAnswer { index }
        `when`(am.mode).thenReturn(AudioManager.MODE_NORMAL)
        `when`(am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)).thenReturn(emptyArray())
        if (Build.VERSION.SDK_INT >= 33)
            `when`(am.getAudioDevicesForAttributes(any(AudioAttributes::class.java))).thenReturn(emptyList())
        audio = AudioController(context)
        doAnswer {
            index = it.getArgument(1)
            writes.add(index to audio.attenuationDb.value)
            null
        }.`when`(am).setStreamVolume(anyInt(), anyInt(), anyInt())
        volume = StreamVolumeController(context)
        coordinator = FullRangeCoordinator(context, audio, volume)
    }

    private fun route(type: Int, name: String = "Headset") {
        val device = mock(AudioDeviceInfo::class.java)
        `when`(device.type).thenReturn(type)
        `when`(device.productName).thenReturn(name)
        if (Build.VERSION.SDK_INT >= 33)
            `when`(am.getAudioDevicesForAttributes(any(AudioAttributes::class.java))).thenReturn(listOf(device))
        `when`(am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)).thenReturn(arrayOf(device))
    }

    private fun calibratedQuiet(db: Float = -20f) {
        route(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        Prefs.setBluetoothFloor(context, "Headset", 2)
        coordinator.applyQuiet(db)
        writes.clear()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
    }

    @Test fun uncalibratedVolumeHandlingNeverQueriesMediaRoute() {
        route(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        repeat(3) { assertEquals(1, volume.minAudibleIndex(AudioManager.STREAM_MUSIC)) }
        coordinator.applyQuiet(-20f)
        index = 2
        coordinator.onExternalVolumeChange(AudioManager.STREAM_MUSIC, 1, 2)
        verify(am, never()).getAudioDevicesForAttributes(any(AudioAttributes::class.java))
        assertEquals(1, index)
        assertEquals(-15f, audio.attenuationDb.value)
    }

    @Test fun firstCalibrationEnablesRouteLookupAfterUncalibratedFastPath() {
        route(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        assertEquals(1, volume.minAudibleIndex(AudioManager.STREAM_MUSIC))
        Prefs.setBluetoothFloor(context, "Headset", 2)
        assertEquals(2, volume.minAudibleIndex(AudioManager.STREAM_MUSIC))
    }

    @Test fun legacyCalibrationIsDetectedWithoutPresenceFlag() {
        route(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        context.getSharedPreferences("gv_prefs", Context.MODE_PRIVATE).edit()
            .putInt("bluetooth_floor_Headset", 2).commit()
        assertEquals(2, volume.minAudibleIndex(AudioManager.STREAM_MUSIC))
        assertTrue(Prefs.hasBluetoothCalibration(context))
    }

    @Test fun calibrationCommandCannotPreviewAboveFourStepsOverMinimum() {
        route(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        coordinator.calibrateBluetoothFloor("Headset", 15)
        assertEquals(listOf(5 to 0f), writes)
        assertEquals(5, Prefs.getBluetoothFloor(context, "Headset"))
    }

    @Test fun calibrationCapRespectsStreamMaximumAndNonzeroSystemMinimum() {
        `when`(am.getStreamMinVolume(AudioManager.STREAM_MUSIC)).thenReturn(2)
        assertEquals(6, volume.calibrationMaxIndex())
        `when`(am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).thenReturn(3)
        assertEquals(3, volume.calibrationMaxIndex())
    }

    @Test fun enteringQuietFromSilentHeadsetIndexAppliesGainBeforeRaisingHardware() {
        route(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        Prefs.setBluetoothFloor(context, "Headset", 2)
        coordinator.applyQuiet(-20f)
        assertEquals(listOf(2 to -20f), writes)
        assertTrue(coordinator.zoneQuiet)
    }

    @Test fun speakerEntryFromMuteRetainsUpstreamLowerOnlyBehavior() {
        route(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
        index = 0
        coordinator.applyQuiet(-20f)
        assertTrue(writes.isEmpty())
        assertEquals(0, index)
    }

    @Test fun defaultFloorStillAbsorbsFirstVolumeUpAsBefore() {
        route(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
        index = 0
        coordinator.applyQuiet(-20f)
        index = 1
        coordinator.onExternalVolumeChange(AudioManager.STREAM_MUSIC, 0, 1)
        assertEquals(-15f, audio.attenuationDb.value)
    }

    @Test fun storedFloorIsClampedToCurrentDeviceRange() {
        route(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        Prefs.setBluetoothFloor(context, "Headset", 100)
        assertEquals(5, volume.minAudibleIndex(AudioManager.STREAM_MUSIC))
        Prefs.setBluetoothFloor(context, "Headset", -1)
        assertEquals(1, volume.minAudibleIndex(AudioManager.STREAM_MUSIC))
    }

    @Test fun repeatedVolumeUpUsesCalibratedFloorThenExitsQuietZone() {
        calibratedQuiet(-30f)
        repeat(6) {
            index = 3
            coordinator.onExternalVolumeChange(AudioManager.STREAM_MUSIC, 2, 3)
            assertEquals(2, index)
            ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        }
        assertEquals(0f, audio.attenuationDb.value)
        assertFalse(coordinator.zoneQuiet)
        writes.clear()
        index = 3
        coordinator.onExternalVolumeChange(AudioManager.STREAM_MUSIC, 2, 3)
        assertTrue(writes.isEmpty())
        assertEquals(3, index)
    }

    @Test fun risingFromSilentIndexToFloorDoesNotAlsoEaseGain() {
        calibratedQuiet()
        index = 2
        coordinator.onExternalVolumeChange(AudioManager.STREAM_MUSIC, 1, 2)
        assertTrue(writes.isEmpty())
        assertEquals(-20f, audio.attenuationDb.value)
    }

    @Test fun downwardExternalChangeIsNeverFought() {
        calibratedQuiet()
        index = 1
        coordinator.onExternalVolumeChange(AudioManager.STREAM_MUSIC, 2, 1)
        assertTrue(writes.isEmpty())
        assertEquals(-20f, audio.attenuationDb.value)
    }

    @Test fun calibrationLowersHardwareBeforeClearingGainAndRebuildsCurve() {
        route(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        Settings.Global.putInt(context.contentResolver, "bluetooth_disable_absolute_volume", 1)
        `when`(am.getStreamVolumeDb(anyInt(), anyInt(), anyInt())).thenAnswer {
            -60f + 4f * it.getArgument<Int>(1)
        }
        audio.setAttenuation(-20f)
        index = 10
        coordinator.calibrateBluetoothFloor("Headset", 2)
        assertEquals(listOf(2 to -20f), writes)
        assertEquals(0f, audio.attenuationDb.value)
        assertEquals(2, coordinator.mediaCurve?.minAudibleIndex)
        assertFalse(coordinator.zoneQuiet)
        assertFalse(Prefs.isLevelInQuietZone(context))
    }

    @Test fun calibrationFromMuteDoesNotRestoreOldHighVolume() {
        route(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        index = 10
        coordinator.toggleMute()
        writes.clear()
        coordinator.calibrateBluetoothFloor("Headset", 2)
        assertEquals(listOf(2 to 0f), writes)
        assertFalse(coordinator.isMuted)
    }

    @Test fun lockedCalibrationCannotChangePreferenceGainOrVolume() {
        calibratedQuiet()
        coordinator.lockedProvider = { true }
        coordinator.calibrateBluetoothFloor("Headset", 3)
        assertTrue(writes.isEmpty())
        assertEquals(2, Prefs.getBluetoothFloor(context, "Headset"))
        assertEquals(-20f, audio.attenuationDb.value)
    }

    @Test fun calibrationCannotChangeCallAudio() {
        route(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        for (mode in listOf(AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_COMMUNICATION)) {
            `when`(am.mode).thenReturn(mode)
            coordinator.calibrateBluetoothFloor("Headset", 2)
        }
        assertTrue(writes.isEmpty())
        assertEquals(1, Prefs.getBluetoothFloor(context, "Headset"))
    }

    @Test fun routeChangedSinceTapCannotCalibrateAnotherOutput() {
        route(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "Other headset")
        coordinator.calibrateBluetoothFloor("Headset", 2)
        assertTrue(writes.isEmpty())
        assertEquals(1, Prefs.getBluetoothFloor(context, "Headset"))
        assertEquals(1, Prefs.getBluetoothFloor(context, "Other headset"))
    }

    @Test fun connectedBluetoothDoesNotApplyItsFloorToSpeakerMediaRoute() {
        route(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        Prefs.setBluetoothFloor(context, "Headset", 2)
        val speaker = mock(AudioDeviceInfo::class.java)
        `when`(speaker.type).thenReturn(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
        `when`(am.getAudioDevicesForAttributes(any(AudioAttributes::class.java))).thenReturn(listOf(speaker))
        assertNull(volume.activeBluetoothName())
        assertEquals(1, volume.minAudibleIndex(AudioManager.STREAM_MUSIC))
    }

    @Test @Config(sdk = [28]) fun olderAndroidKeepsDefaultFloorWithoutGuessingActiveRoute() {
        route(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        Prefs.setBluetoothFloor(context, "Headset", 2)
        assertNull(volume.activeBluetoothName())
        assertEquals(1, volume.minAudibleIndex(AudioManager.STREAM_MUSIC))
    }

    @Test fun routeQueryFailureKeepsDefaultFloor() {
        Prefs.setBluetoothFloor(context, "Headset", 2)
        `when`(am.getAudioDevicesForAttributes(any(AudioAttributes::class.java)))
            .thenThrow(IllegalStateException("Audio service unavailable"))
        assertEquals(1, volume.minAudibleIndex(AudioManager.STREAM_MUSIC))
    }
}

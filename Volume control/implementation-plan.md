# Implementation Plan — Granular Sub-Volume Controller
## Android App | Production-Ready | Claude Code Execution Guide

> **How to use this file**: Open this project folder in Claude Code and say:
> *"Read CLAUDE.md and implementation-plan.md, then execute Phase 1."*
> Work one phase at a time. Each phase ends with a verification step before proceeding.

---

## Project Metadata

| Field | Value |
|---|---|
| App Name | Granular Volume Control |
| Package | `com.granularvolume` |
| Min SDK | 28 (Android 9.0 Pie) |
| Target SDK | 34 (Android 14) |
| Language | Kotlin 1.9.22 |
| Build System | Gradle (Kotlin DSL) |
| Architecture | Service + Custom View + AudioEffect |
| Estimated LOC | ~900 lines |

---

## Phase 0 — Android Studio Project Scaffold

### Action
Create a new Android project (Empty Activity, Kotlin, SDK 28):

```
Project name: GranularVolume
Package name: com.granularvolume
Save location: [current folder]
Language: Kotlin
Minimum SDK: API 28 (Android 9.0)
```

### File: `settings.gradle.kts`
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "GranularVolume"
include(":app")
```

### File: `build.gradle.kts` (root)
```kotlin
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}
```

### File: `app/build.gradle.kts`
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.granularvolume"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.granularvolume"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

### File: `app/proguard-rules.pro`
```
-keep class android.media.audiofx.** { *; }
-keepclassmembers class com.granularvolume.** { *; }
-dontwarn android.media.audiofx.**
```

### ✅ Phase 0 Verification
```bash
./gradlew assembleDebug
# Must compile with 0 errors
```

---

## Phase 1 — AndroidManifest.xml

### File: `app/src/main/AndroidManifest.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Audio attenuation via AudioEffect global session -->
    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
    <!-- Floating overlay -->
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <!-- Foreground service (required Android 8+) -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
    <!-- Auto-start on boot -->
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <!-- Post notification (Android 13+) -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.GranularVolume">

        <!-- Main entry point — permission setup only, minimal UI -->
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTask"
            android:screenOrientation="unspecified">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Foreground service for audio effect + overlay -->
        <service
            android:name=".service.VolumeControlService"
            android:exported="false"
            android:foregroundServiceType="mediaPlayback"
            android:stopWithTask="false" />

        <!-- Auto-start after device reboot -->
        <receiver
            android:name=".receiver.BootReceiver"
            android:exported="true"
            android:enabled="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.QUICKBOOT_POWERON" />
            </intent-filter>
        </receiver>

    </application>
</manifest>
```

---

## Phase 2 — Utilities & Foundation

### File: `util/Prefs.kt`
```kotlin
package com.granularvolume.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Type-safe SharedPreferences wrapper.
 * All keys are constants — no magic strings outside this class.
 */
object Prefs {

    private const val FILE_NAME = "gv_prefs"

    private const val KEY_ATTENUATION_DB   = "attenuation_db"
    private const val KEY_OVERLAY_X        = "overlay_x"
    private const val KEY_OVERLAY_Y        = "overlay_y"
    private const val KEY_SERVICE_WAS_RUNNING = "service_was_running"
    private const val KEY_COLLAPSED        = "overlay_collapsed"

    /** Current attenuation in dB (0.0 = none, -30.0 = near-silent) */
    const val ATTENUATION_DEFAULT = 0f
    const val ATTENUATION_MIN     = -30f
    const val ATTENUATION_MAX     = 0f

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun getAttenuation(context: Context): Float =
        prefs(context).getFloat(KEY_ATTENUATION_DB, ATTENUATION_DEFAULT)

    fun setAttenuation(context: Context, dB: Float) {
        prefs(context).edit { putFloat(KEY_ATTENUATION_DB, dB.coerceIn(ATTENUATION_MIN, ATTENUATION_MAX)) }
    }

    fun getOverlayX(context: Context, default: Int): Int =
        prefs(context).getInt(KEY_OVERLAY_X, default)

    fun getOverlayY(context: Context, default: Int): Int =
        prefs(context).getInt(KEY_OVERLAY_Y, default)

    fun setOverlayPosition(context: Context, x: Int, y: Int) {
        prefs(context).edit {
            putInt(KEY_OVERLAY_X, x)
            putInt(KEY_OVERLAY_Y, y)
        }
    }

    fun setServiceWasRunning(context: Context, running: Boolean) {
        prefs(context).edit { putBoolean(KEY_SERVICE_WAS_RUNNING, running) }
    }

    fun wasServiceRunning(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SERVICE_WAS_RUNNING, false)

    fun isCollapsed(context: Context): Boolean =
        prefs(context).getBoolean(KEY_COLLAPSED, false)

    fun setCollapsed(context: Context, collapsed: Boolean) {
        prefs(context).edit { putBoolean(KEY_COLLAPSED, collapsed) }
    }
}
```

### File: `util/PermissionHelper.kt`
```kotlin
package com.granularvolume.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Checks and requests special permissions that cannot use the standard
 * ActivityCompat.requestPermissions() flow.
 */
object PermissionHelper {

    /** Returns true if the app can draw system overlays. */
    fun canDrawOverlays(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    /** Returns an Intent that opens the overlay permission settings for this app. */
    fun overlayPermissionIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )

    /** Returns true if MODIFY_AUDIO_SETTINGS is a normal permission (always granted on API 28+). */
    fun hasModifyAudioSettings(context: Context): Boolean {
        // MODIFY_AUDIO_SETTINGS is a normal permission — auto-granted at install time.
        // We check it explicitly to guard against unusual OEM restrictions.
        return context.checkSelfPermission(android.Manifest.permission.MODIFY_AUDIO_SETTINGS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
```

---

## Phase 3 — Audio Engine

### File: `audio/AudioEffectStrategy.kt`
```kotlin
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

    /** Release all audio resources. Must be called on Service destroy. */
    fun release()

    companion object {
        const val GLOBAL_SESSION_ID = 0
        const val EFFECT_PRIORITY   = 0
    }
}
```

### File: `audio/DynamicsProcessingStrategy.kt`
```kotlin
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
                /* channelCount */ 2,
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
            // Apply gain to both channels (stereo)
            for (channel in 0 until 2) {
                effect.setOutputGain(channel, dB)
            }
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
}
```

### File: `audio/LoudnessEnhancerStrategy.kt`
```kotlin
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
            val mB = (dB * 100).toInt()
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
}
```

### File: `audio/AudioController.kt`
```kotlin
package com.granularvolume.audio

import android.content.Context
import android.util.Log
import com.granularvolume.util.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Central audio controller. Manages strategy selection and exposes a StateFlow
 * for the current attenuation level so the UI can react reactively.
 *
 * Strategy selection order:
 *   1. DynamicsProcessingStrategy (preferred — clean, flat-spectrum)
 *   2. LoudnessEnhancerStrategy (fallback — OEM-dependent behavior)
 *   3. null (no effect available — notify user)
 */
class AudioController(private val context: Context) {

    private val tag = "GranularVolume:AudioCtrl"

    private var strategy: AudioEffectStrategy? = null

    /** Emits current attenuation in dB. UI observes this. */
    private val _attenuationDb = MutableStateFlow(Prefs.getAttenuation(context))
    val attenuationDb: StateFlow<Float> = _attenuationDb.asStateFlow()

    /** True if a working audio effect strategy was found. */
    var isEffectAvailable: Boolean = false
        private set

    /**
     * Initializes the best available AudioEffect strategy.
     * Call this from Service.onCreate() — never from UI thread.
     * @return true if any strategy initialized successfully
     */
    fun initialize(): Boolean {
        val strategies: List<AudioEffectStrategy> = listOf(
            DynamicsProcessingStrategy(),
            LoudnessEnhancerStrategy()
        )

        for (s in strategies) {
            if (s.initialize()) {
                strategy = s
                isEffectAvailable = true
                Log.i(tag, "Using strategy: ${s::class.simpleName}")
                // Apply persisted attenuation immediately
                applyAttenuation(_attenuationDb.value)
                return true
            }
        }

        Log.e(tag, "No AudioEffect strategy available on this device")
        isEffectAvailable = false
        return false
    }

    /**
     * Sets attenuation level. Persists to prefs and updates StateFlow.
     * Thread-safe: AudioEffect API is thread-safe internally.
     * @param dB range [Prefs.ATTENUATION_MIN, Prefs.ATTENUATION_MAX]
     */
    fun setAttenuation(dB: Float) {
        val clamped = dB.coerceIn(Prefs.ATTENUATION_MIN, Prefs.ATTENUATION_MAX)
        strategy?.setAttenuation(clamped)
        _attenuationDb.value = clamped
        Prefs.setAttenuation(context, clamped)
        Log.d(tag, "Attenuation set to ${clamped}dB")
    }

    /**
     * Convenience: mute immediately (max attenuation).
     */
    fun mute() = setAttenuation(Prefs.ATTENUATION_MIN)

    /**
     * Convenience: pass-through (no attenuation).
     */
    fun passThrough() = setAttenuation(Prefs.ATTENUATION_MAX)

    /**
     * Releases the underlying AudioEffect. Must be called in Service.onDestroy().
     * After this call, this instance should not be used.
     */
    fun release() {
        strategy?.release()
        strategy = null
        Log.i(tag, "AudioController released")
    }

    private fun applyAttenuation(dB: Float) {
        strategy?.setAttenuation(dB)
    }
}
```

### ✅ Phase 3 Verification
```kotlin
// In a temporary test: instantiate AudioController, call initialize(), 
// setAttenuation(-15f), verify no crash, call release()
```

---

## Phase 4 — Overlay UI

### File: `res/drawable/bg_pill_overlay.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#CC1A1A2E" />
    <corners android:radius="24dp" />
</shape>
```

### File: `res/layout/overlay_slider.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/gv_overlay_root"
    android:layout_width="64dp"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:gravity="center_horizontal"
    android:padding="8dp"
    android:background="@drawable/bg_pill_overlay"
    android:elevation="8dp">

    <!-- Speaker / Volume icon (top — tap = pass-through) -->
    <ImageButton
        android:id="@+id/gv_btn_max"
        android:layout_width="36dp"
        android:layout_height="36dp"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:contentDescription="@string/gv_pass_through"
        android:src="@drawable/ic_volume_up"
        android:tint="#FFFFFF"
        android:padding="4dp"/>

    <!-- Vertical SeekBar — rotated 270° so top = loud, bottom = quiet -->
    <SeekBar
        android:id="@+id/gv_slider"
        android:layout_width="140dp"
        android:layout_height="32dp"
        android:layout_marginTop="4dp"
        android:layout_marginBottom="4dp"
        android:rotation="270"
        android:max="100"
        android:progress="100"
        android:progressTint="#66FFFFFF"
        android:thumbTint="#FFFFFF"
        android:splitTrack="false"/>

    <!-- Mute icon (bottom — tap = max attenuation) -->
    <ImageButton
        android:id="@+id/gv_btn_mute"
        android:layout_width="36dp"
        android:layout_height="36dp"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:contentDescription="@string/gv_mute"
        android:src="@drawable/ic_volume_off"
        android:tint="#AAFFFFFF"
        android:padding="4dp"/>

    <!-- dB label — updates with slider -->
    <TextView
        android:id="@+id/gv_label_db"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="0 dB"
        android:textColor="#AAFFFFFF"
        android:textSize="10sp"
        android:fontFamily="monospace"
        android:layout_marginTop="2dp"/>

    <!-- Dismiss button — hidden by default, shown on long press -->
    <ImageButton
        android:id="@+id/gv_btn_dismiss"
        android:layout_width="28dp"
        android:layout_height="28dp"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:contentDescription="@string/gv_dismiss"
        android:src="@drawable/ic_close"
        android:tint="#FFAAAAAA"
        android:padding="2dp"
        android:layout_marginTop="4dp"
        android:visibility="gone"/>

</LinearLayout>
```

### File: `overlay/OverlayManager.kt`
```kotlin
package com.granularvolume.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import com.granularvolume.R
import com.granularvolume.audio.AudioController
import com.granularvolume.util.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Manages the floating overlay window lifecycle.
 *
 * Responsibilities:
 *  - Add/remove view from WindowManager
 *  - Handle drag-to-reposition touch events
 *  - Sync SeekBar with AudioController.attenuationDb StateFlow
 *  - Handle mute/pass-through button taps
 *  - Persist overlay position to Prefs
 */
class OverlayManager(
    private val context: Context,
    private val audioController: AudioController,
    private val scope: CoroutineScope,
    private val onDismiss: () -> Unit
) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var flowJob: Job? = null

    // Layout params for the overlay window
    private val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = Prefs.getOverlayX(context, 40)
        y = Prefs.getOverlayY(context, 300)
    }

    /** Inflate and attach the overlay to the WindowManager. */
    fun show() {
        if (overlayView != null) return

        val view = LayoutInflater.from(context).inflate(R.layout.overlay_slider, null)
        overlayView = view
        setupView(view)
        wm.addView(view, layoutParams)

        // Observe audio controller state and update slider accordingly
        flowJob = audioController.attenuationDb
            .onEach { dB -> updateSliderFromDb(view, dB) }
            .launchIn(scope)
    }

    /** Remove overlay from WindowManager. */
    fun hide() {
        flowJob?.cancel()
        flowJob = null
        overlayView?.let {
            wm.removeView(it)
            overlayView = null
        }
    }

    private fun setupView(view: View) {
        val slider    = view.findViewById<SeekBar>(R.id.gv_slider)
        val labelDb   = view.findViewById<TextView>(R.id.gv_label_db)
        val btnMax    = view.findViewById<ImageButton>(R.id.gv_btn_max)
        val btnMute   = view.findViewById<ImageButton>(R.id.gv_btn_mute)
        val btnDismiss = view.findViewById<ImageButton>(R.id.gv_btn_dismiss)

        // Slider: progress 100 = 0dB (loud), progress 0 = -30dB (quiet)
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val dB = progressToDb(progress)
                audioController.setAttenuation(dB)
                labelDb.text = if (dB == 0f) "0 dB" else "%.0f dB".format(dB)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        btnMax.setOnClickListener { audioController.passThrough() }
        btnMute.setOnClickListener { audioController.mute() }
        btnDismiss.setOnClickListener { onDismiss() }

        // Long press to reveal dismiss button
        view.setOnLongClickListener {
            btnDismiss.visibility = View.VISIBLE
            // Auto-hide after 3 seconds
            view.postDelayed({ btnDismiss.visibility = View.GONE }, 3000)
            true
        }

        // Drag-to-reposition
        setupDragListener(view)
    }

    private fun setupDragListener(view: View) {
        var initialX = 0; var initialY = 0
        var touchX = 0f; var touchY = 0f
        var isDragging = false

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    touchX = event.rawX
                    touchY = event.rawY
                    isDragging = false
                    false // Pass through — allow child views to receive clicks
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (!isDragging && (Math.abs(dx) > 10 || Math.abs(dy) > 10)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        layoutParams.x = initialX + dx
                        layoutParams.y = initialY + dy
                        wm.updateViewLayout(view, layoutParams)
                    }
                    isDragging
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        Prefs.setOverlayPosition(view.context, layoutParams.x, layoutParams.y)
                    }
                    isDragging = false
                    false
                }
                else -> false
            }
        }
    }

    private fun updateSliderFromDb(view: View, dB: Float) {
        val slider  = view.findViewById<SeekBar>(R.id.gv_slider)
        val labelDb = view.findViewById<TextView>(R.id.gv_label_db)
        slider.progress = dbToProgress(dB)
        labelDb.text = if (dB == 0f) "0 dB" else "%.0f dB".format(dB)
    }

    /** Map dB [-30, 0] → progress [0, 100] */
    private fun dbToProgress(dB: Float): Int =
        ((dB - Prefs.ATTENUATION_MIN) / (Prefs.ATTENUATION_MAX - Prefs.ATTENUATION_MIN) * 100).toInt()

    /** Map progress [0, 100] → dB [-30, 0] */
    private fun progressToDb(progress: Int): Float =
        Prefs.ATTENUATION_MIN + (progress / 100f) * (Prefs.ATTENUATION_MAX - Prefs.ATTENUATION_MIN)
}
```

---

## Phase 5 — Foreground Service

### File: `service/VolumeControlService.kt`
```kotlin
package com.granularvolume.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.granularvolume.MainActivity
import com.granularvolume.R
import com.granularvolume.audio.AudioController
import com.granularvolume.overlay.OverlayManager
import com.granularvolume.util.Prefs
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the AudioController and OverlayManager lifecycle.
 *
 * Lifecycle:
 *   onCreate() → initialize audio + overlay
 *   onDestroy() → release audio + hide overlay + cancel coroutines
 *
 * This service is START_STICKY — the OS will restart it if killed.
 */
class VolumeControlService : Service() {

    private val tag = "GranularVolume:Service"

    companion object {
        private const val CHANNEL_ID   = "gv_volume_control"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.granularvolume.ACTION_STOP"
    }

    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineName("VolumeControlService")
    )

    private lateinit var audioController: AudioController
    private lateinit var overlayManager: OverlayManager

    override fun onCreate() {
        super.onCreate()
        Log.i(tag, "Service starting")

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(0f))

        audioController = AudioController(applicationContext)
        overlayManager  = OverlayManager(
            context         = applicationContext,
            audioController = audioController,
            scope           = serviceScope,
            onDismiss       = { stopSelf() }
        )

        serviceScope.launch(Dispatchers.Default) {
            audioController.initialize()
            if (!audioController.isEffectAvailable) {
                Log.e(tag, "No audio effect available — service will run without audio attenuation")
            }
        }

        overlayManager.show()
        Prefs.setServiceWasRunning(applicationContext, true)

        // Update notification when attenuation changes
        audioController.attenuationDb
            .onEach { dB -> updateNotification(dB) }
            .launchIn(serviceScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.i(tag, "Stop action received")
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(tag, "Service stopping")
        overlayManager.hide()
        audioController.release()
        serviceScope.cancel()
        Prefs.setServiceWasRunning(applicationContext, false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Volume Control",
            NotificationManager.IMPORTANCE_LOW   // No sound, no popup
        ).apply {
            description = "Granular sub-volume control overlay"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(dB: Float): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, VolumeControlService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        val dbText = if (dB == 0f) "Pass-through" else "%.0f dB".format(dB)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_volume_slider)
            .setContentTitle("Sub-Volume Control")
            .setContentText(dbText)
            .setContentIntent(tapIntent)
            .addAction(R.drawable.ic_close, "Stop", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(dB: Float) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(dB))
    }
}
```

---

## Phase 6 — Receivers & MainActivity

### File: `receiver/BootReceiver.kt`
```kotlin
package com.granularvolume.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.granularvolume.service.VolumeControlService
import com.granularvolume.util.Prefs

/**
 * Receives BOOT_COMPLETED and restarts the service if it was running before shutdown.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON") return

        if (Prefs.wasServiceRunning(context)) {
            Log.i("GranularVolume:Boot", "Restarting VolumeControlService after boot")
            context.startForegroundService(
                Intent(context, VolumeControlService::class.java)
            )
        }
    }
}
```

### File: `MainActivity.kt`
```kotlin
package com.granularvolume

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.granularvolume.service.VolumeControlService
import com.granularvolume.util.PermissionHelper

/**
 * Single-screen activity: guides the user through permission grants,
 * then starts the foreground service and finishes (the overlay IS the UI).
 *
 * Screen is intentionally minimal — users won't spend time here.
 */
class MainActivity : AppCompatActivity() {

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Check again after returning from settings
        if (PermissionHelper.canDrawOverlays(this)) {
            startServiceAndFinish()
        } else {
            updateStatusText()
            Toast.makeText(this, "Overlay permission required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // If both permissions are already granted, skip the UI and go
        if (PermissionHelper.canDrawOverlays(this) &&
            PermissionHelper.hasModifyAudioSettings(this)) {
            startServiceAndFinish()
            return
        }

        updateStatusText()

        findViewById<Button>(R.id.btn_grant_overlay).setOnClickListener {
            overlayPermissionLauncher.launch(PermissionHelper.overlayPermissionIntent(this))
        }

        findViewById<Button>(R.id.btn_start_service).setOnClickListener {
            when {
                !PermissionHelper.canDrawOverlays(this) ->
                    Toast.makeText(this, "Please grant overlay permission first", Toast.LENGTH_SHORT).show()
                else -> startServiceAndFinish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatusText()
    }

    private fun updateStatusText() {
        val statusOverlay = if (PermissionHelper.canDrawOverlays(this)) "✓" else "✗"
        val statusAudio   = if (PermissionHelper.hasModifyAudioSettings(this)) "✓" else "✗"
        findViewById<TextView>(R.id.tv_status).text =
            "Overlay: $statusOverlay   Audio: $statusAudio"
        findViewById<Button>(R.id.btn_grant_overlay).isEnabled =
            !PermissionHelper.canDrawOverlays(this)
        findViewById<Button>(R.id.btn_start_service).isEnabled =
            PermissionHelper.canDrawOverlays(this)
    }

    private fun startServiceAndFinish() {
        startForegroundService(Intent(this, VolumeControlService::class.java))
        finish() // The overlay is the UI — no need for this activity to stay open
    }
}
```

### File: `res/layout/activity_main.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:gravity="center"
    android:padding="32dp"
    android:background="@color/gv_background">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/app_name"
        android:textSize="24sp"
        android:textColor="@color/gv_text_primary"
        android:textStyle="bold"
        android:layout_marginBottom="8dp"/>

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/gv_subtitle"
        android:textSize="14sp"
        android:textColor="@color/gv_text_secondary"
        android:layout_marginBottom="32dp"/>

    <TextView
        android:id="@+id/tv_status"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textSize="13sp"
        android:textColor="@color/gv_text_secondary"
        android:fontFamily="monospace"
        android:layout_marginBottom="24dp"/>

    <Button
        android:id="@+id/btn_grant_overlay"
        android:layout_width="220dp"
        android:layout_height="48dp"
        android:text="@string/gv_grant_overlay"
        android:layout_marginBottom="12dp"/>

    <Button
        android:id="@+id/btn_start_service"
        android:layout_width="220dp"
        android:layout_height="48dp"
        android:text="@string/gv_start_service"/>

</LinearLayout>
```

---

## Phase 7 — Resources

### File: `res/values/strings.xml`
```xml
<resources>
    <string name="app_name">Volume Control</string>
    <string name="gv_subtitle">Granular sub-volume for quiet environments</string>
    <string name="gv_grant_overlay">Grant Overlay Permission</string>
    <string name="gv_start_service">Start Overlay</string>
    <string name="gv_pass_through">Full volume (pass-through)</string>
    <string name="gv_mute">Minimum volume</string>
    <string name="gv_dismiss">Dismiss overlay</string>
</resources>
```

### File: `res/values/colors.xml`
```xml
<resources>
    <color name="gv_background">#111827</color>
    <color name="gv_surface">#1A1A2E</color>
    <color name="gv_overlay_bg">#CC1A1A2E</color>
    <color name="gv_text_primary">#FFFFFF</color>
    <color name="gv_text_secondary">#AAAAAA</color>
    <color name="gv_accent">#6C63FF</color>
</resources>
```

### Drawable icons needed (vector XML)
Create these 4 vector drawables from Material Icons or built-in Android:
- `ic_volume_up.xml` — speaker with sound waves
- `ic_volume_off.xml` — speaker with X / muted
- `ic_volume_slider.xml` — notification small icon (24×24dp)
- `ic_close.xml` — ✕ close icon

Use `File → New → Vector Asset` in Android Studio, or copy from AOSP Material Icons.

---

## Phase 8 — Final Polish & QA Checklist

### ProGuard verify
```bash
./gradlew assembleRelease
# Check that DynamicsProcessing and LoudnessEnhancer classes are not stripped
```

### Manual QA steps (run in order)
1. Fresh install → MainActivity opens → permission flow works
2. Grant overlay → service starts → floating widget appears
3. Drag widget to new position → force-close app → reopen → widget at same position ✓
4. Slider to 50% → audio perceptibly quieter ✓
5. Tap mute icon → audio near-silent ✓
6. Tap speaker icon → audio back to normal ✓
7. Reboot device → widget reappears automatically ✓
8. Notification shows correct dB level ✓
9. Tap "Stop" in notification → widget disappears, service stops ✓
10. No crash when slider adjusted rapidly ✓

---

## Execution Commands for Claude Code

```bash
# Phase 0 — scaffold (run in project root after Android Studio creates project)
./gradlew assembleDebug

# Install on connected device/emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Watch logcat filtered to our tag
adb logcat -s "GranularVolume:*"

# Check memory (run after 30 min of usage)
adb shell dumpsys meminfo com.granularvolume

# Check battery impact
adb shell dumpsys batterystats com.granularvolume

# Release build
./gradlew assembleRelease
```

---

## Hand-off Notes

When you hand this to Claude Code, say:

> "Read CLAUDE.md and implementation-plan.md in this folder.
> Create a new Android Studio project with the structure defined there.
> Start with Phase 0 (Gradle setup) and work through each phase sequentially.
> After each phase, run `./gradlew assembleDebug` to verify compilation before proceeding.
> Do not use Jetpack Compose — the service-hosted custom View approach is intentional."

---

*Plan version: 1.0 | 2026-06-20*

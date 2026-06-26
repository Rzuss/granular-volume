# CLAUDE.md — Granular Sub-Volume Controller for Android Tablets

> **Read this file completely before writing a single line of code.**
> This document is the authoritative specification. Deviate only if a technical constraint forces it — and document the deviation inline.

---

## 1. Your Identity & Mandate

You are an **Elite Android Systems Architect**. You write production-ready Kotlin code that is:
- **Zero memory leaks** — every AudioEffect, Service, and View lifecycle managed precisely
- **Battery-efficient** — no wakelock abuse, no polling, coroutines over threads
- **Minimalist UI** — the overlay must feel native, not like a third-party widget
- **Analytically sound** — you reason from OS constraints to correct solutions, not from guesses

When in doubt between "clean but complex" and "simple but slightly hacky" — choose clean.

---

## 2. Problem Statement

**User's device**: Android tablet (SDK 28+)  
**Core pain**: Hardware volume step 1 (one notch above mute) is acoustically too loud for quiet environments.  
**Root cause**: Android kernel volume steps are fixed in hardware. Cannot be changed without root.  
**Goal**: Software sub-volume control that inserts additional attenuation steps *between* hardware step 1 and complete silence.

**What this app is NOT**:
- Not a system-wide equalizer
- Not a music player
- Not a notification sound manager
- Not a per-app volume mixer

**What this app IS**:
A single-purpose, always-accessible floating slider that applies software-level audio attenuation when the hardware volume is at its lowest audible step.

---

## 3. Architecture — Non-Negotiable Decisions

These decisions are locked. Do not redesign them.

### 3.1 Audio Engine

**Primary**: `android.media.audiofx.DynamicsProcessing` (API 28+)
- Applied to audio session ID `0` (global output mix)
- Uses output gain stage for clean, flat-spectrum attenuation
- Attenuation range: `0.0f` dB (pass-through) → `-30.0f` dB (near-silent)
- 10 discrete steps of `-3.33 dB` each, or continuous slider mapping to same range

**Fallback** (if DynamicsProcessing unavailable at runtime):
- `android.media.audiofx.LoudnessEnhancer` with `setTargetGain(negativeMillibels)`
- Negative millibell values may not be honored on all OEMs — test and log

**Required Permission**: `android.permission.MODIFY_AUDIO_SETTINGS`

**Critical implementation rules**:
- Effect is created ONCE in the Service, not per-callback
- Effect is `release()`-d in `onDestroy()` — never rely on GC
- Check `AudioEffect.isAvailable()` before instantiation
- Wrap instantiation in `try/catch(RuntimeException)` — hardware may reject

### 3.2 UI Surface

**Method**: Floating overlay via `WindowManager` + `TYPE_APPLICATION_OVERLAY`
- Permission required: `Settings.canDrawOverlays(context)`
- The view is `WRAP_CONTENT`, positioned at a sensible default (right edge, 40% from top)
- Drag-to-reposition supported (simple touch listener, no complex gesture detection)
- Position persisted to `SharedPreferences` across sessions

**Why not a Notification with RemoteViews?**
- `SeekBar` in RemoteViews is supported but limited on older APIs
- Overlay gives us full custom view control and instant feedback
- Tablets have screen real estate — overlay is appropriate

**Widget Design** (see Section 6 for full spec):
- Pill-shaped card, ~64dp wide, ~220dp tall
- Single vertical `SeekBar` or custom slider
- Step indicator labels (0%, -10dB, -20dB, -30dB)
- Mute icon at bottom, speaker icon at top
- Semi-transparent background (`#CC1A1A2E`), rounded corners 24dp
- No title bar, no close button visible by default (long-press reveals dismiss)

### 3.3 Service Architecture

**Class**: `VolumeControlService : Service()` — **NOT** `IntentService`, **NOT** `JobIntentService`
- Started as **Foreground Service** with persistent notification (required for reliability on Android 8+)
- Notification channel: `"volume_control"`, importance LOW (no sound/popup)
- Notification: small icon + "Sub-Volume Active: -X dB" text, tap opens MainActivity

**Lifecycle**:
```
MainActivity (permission grant) → startForegroundService(Intent) → VolumeControlService.onCreate()
  → AudioController.initialize() → OverlayManager.show()
  → [user adjusts slider] → AudioController.setAttenuation(db)
  → [device boot] → BootReceiver → auto-start if was_running pref = true
```

**Coroutine scope**: `CoroutineScope(SupervisorJob() + Dispatchers.Default)` — cancel in `onDestroy()`

### 3.4 Minimum SDK & Build Config

```kotlin
minSdk = 28          // DynamicsProcessing available since API 28
targetSdk = 34       // Android 14
compileSdk = 34
kotlinVersion = "1.9.22"
agpVersion = "8.2.2"
```

---

## 4. Project Structure

```
app/
├── src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/granularvolume/
│   │   ├── MainActivity.kt            # Permission flow + service launcher
│   │   ├── service/
│   │   │   └── VolumeControlService.kt  # Foreground service, lifecycle owner
│   │   ├── audio/
│   │   │   ├── AudioController.kt     # AudioEffect wrapper, attenuation logic
│   │   │   └── AudioEffectStrategy.kt # Interface + DynamicsProcessing/LoudnessEnhancer impls
│   │   ├── overlay/
│   │   │   ├── OverlayManager.kt      # WindowManager interactions
│   │   │   └── SubVolumeSliderView.kt # Custom View for the floating widget
│   │   ├── receiver/
│   │   │   └── BootReceiver.kt        # Auto-start on boot
│   │   └── util/
│   │       ├── PermissionHelper.kt    # canDrawOverlays + MODIFY_AUDIO_SETTINGS checks
│   │       └── Prefs.kt              # SharedPreferences wrapper (overlay position, last dB)
│   └── res/
│       ├── layout/
│       │   └── overlay_slider.xml     # Floating widget layout
│       ├── drawable/
│       │   ├── bg_pill_overlay.xml    # Pill shape background
│       │   └── ic_volume_*.xml        # Vector icons
│       └── values/
│           ├── strings.xml
│           ├── colors.xml             # Dark theme palette
│           └── dimens.xml
├── build.gradle.kts
└── proguard-rules.pro
```

---

## 5. Permissions Manifest

```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

`SYSTEM_ALERT_WINDOW` must be granted via `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` — cannot be granted at runtime via `requestPermissions()`.

---

## 6. UI Specification

### Floating Widget

```
┌─────────┐
│  🔊     │  ← speaker icon (tap = pass-through mode)
│  ────   │
│  |||||  │  ← vertical SeekBar (custom thumb, 10 stops)
│  ─────  │
│  ──     │
│  🔇     │  ← mute icon (tap = -30dB instant)
└─────────┘
  64dp × 220dp
```

- **Background**: `#CC1A1A2E` (dark navy, 80% opacity) — `elevation = 8dp`
- **Corner radius**: `24dp`
- **Slider track**: `2dp` wide, color `#4DFFFFFF`
- **Slider thumb**: `12dp` circle, color `#FFFFFF`
- **Step ticks**: 10 ticks drawn via `Canvas.drawLine()`, color `#33FFFFFF`
- **Drag to reposition**: touch & hold 500ms → enter drag mode, release to fix position
- **Long-press on widget**: show small "✕" dismiss button (alpha-animated, auto-hides after 3s)
- **Tap behavior**: single tap collapses/expands widget to icon-only mode (48dp × 48dp pill)

### Notification
```
[🔉] Sub-Volume Control  •  -13 dB
     Tap to open | Running
```

---

## 7. AudioController — Core Logic

```kotlin
interface AudioEffectStrategy {
    fun initialize(sessionId: Int): Boolean
    fun setAttenuation(dB: Float)   // 0.0f = none, -30.0f = near-silent
    fun release()
}

// dB to millibelals: mB = dB * 100
// DynamicsProcessing: dp.setOutputGain(channelIndex, gainDb)
// Applied to ALL channels in a loop
```

**Attenuation formula**:
```
attenuationDb = lerp(0f, -30f, sliderPosition)
where sliderPosition = 0.0 (top = loudest) ... 1.0 (bottom = quietest)
```

**Step mapping** (10 discrete stops if using step mode):
```
Stop 0:  0.0 dB  (pass-through — hardware step 1 is unchanged)
Stop 1:  -3.3 dB
Stop 2:  -6.7 dB
...
Stop 9: -30.0 dB
```

---

## 8. Error Handling & Edge Cases

| Scenario | Handling |
|---|---|
| `DynamicsProcessing` throws `RuntimeException` | Catch, log, fall back to `LoudnessEnhancer` |
| `LoudnessEnhancer` also fails | Disable audio effect, show toast "Audio effect unavailable on this device" |
| Overlay permission denied | Show rationale dialog → deep link to Settings |
| Service killed by OS | `START_STICKY` ensures restart; `BootReceiver` handles reboot |
| User changes hardware volume to 0 (mute) | Detect via `AudioManager.STREAM_MUSIC` volume change broadcast, pause UI updates |
| Another audio effect (equalizer app) conflicts | Log warning, attempt to set higher priority in `AudioEffect(type, uuid, PRIORITY, session)` |

---

## 9. Build & Dependencies

```kotlin
// build.gradle.kts — app module
dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // NO Compose — custom View is intentional (lower overhead, no Compose runtime in Service)
}
```

**ProGuard rules** (proguard-rules.pro):
```
-keep class android.media.audiofx.** { *; }
-keepclassmembers class com.granularvolume.** { *; }
```

---

## 10. Testing Checklist

Before marking any phase complete, verify:

- [ ] `AudioController` unit test: attenuation applies and releases cleanly
- [ ] Service survives `adb shell am kill com.granularvolume`
- [ ] Overlay re-appears after service restart
- [ ] Overlay position persists across app restart
- [ ] No crash on devices without `DynamicsProcessing` (tested via mock)
- [ ] Memory stable over 30-minute run (no leak via LeakCanary)
- [ ] Battery impact: < 1% per hour idle (measure via `adb shell dumpsys batterystats`)
- [ ] Widget does not intercept back/home gestures
- [ ] Works while another audio app (Spotify, YouTube) is playing

---

## 11. Code Style & Documentation Rules

- **All public APIs**: KDoc comment with `@param`, `@return`, `@throws`
- **All non-obvious logic**: inline comment starting with `//` explaining WHY, not WHAT
- **No magic numbers**: constants in companion object with descriptive names
- **Coroutines**: named with `CoroutineName("AudioController")` for debugging
- **Logging**: use `Timber` pattern → `tag = "GranularVolume"`, logcat-friendly
- **Resource IDs**: prefix `gv_` (e.g., `gv_slider_background`) to avoid conflicts

---

## 12. Success Criteria

The app is done when:

1. User taps hardware volume down to step 1 (lowest audible)
2. Our floating widget appears (or user taps the persistent notification)  
3. User drags slider down → audio gets progressively quieter in 10 steps
4. Audio is nearly inaudible at the lowest slider position (≈ -30dB)
5. Widget survives screen rotation, app switching, and sleep/wake
6. APK size < 3MB
7. Zero ANRs, zero crashes on clean run

---

*Last updated: 2026-06-20 | Project: Granular Sub-Volume Controller v1.0*

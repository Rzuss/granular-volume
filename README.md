<div align="center">

<img src="store-assets/play_icon_512.png" width="120" alt="Granular Volume icon">

# Granular Volume

**Volume below Android's minimum. A floating control that stays above any app.**

*Listed on Google Play as "Volume Control: Quiet Dial". Same app, same developer.*

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android-green.svg)](#)
[![Min SDK](https://img.shields.io/badge/minSdk-28-orange.svg)](#)
[![Target SDK](https://img.shields.io/badge/targetSdk-35-orange.svg)](#)
[![Language](https://img.shields.io/badge/Kotlin-100%25-7F52FF.svg)](#)

</div>

---

## The problem

On a lot of phones and tablets, the lowest non-mute volume step is still too loud. Not "a little loud": genuinely uncomfortable, next to a sleeping baby, through sensitive in-ear monitors, or in a quiet room at night. Android's own Settings app has no control for this. The volume slider stops where it stops, and there is nothing below it.

This isn't a bug. It's a structural limit in how Android's volume system is built, and it means the fix has to live outside the volume slider entirely, which is what this app does.

## How Android volume actually works

Android does not represent volume as a continuous, calibrated loudness scale. Each audio stream (`STREAM_MUSIC`, `STREAM_RING`, `STREAM_NOTIFICATION`, and so on) has a fixed number of discrete steps, set by `AudioManager.getStreamMaxVolume()`, commonly somewhere around 15 steps for media, though this varies by OEM and device. The physical volume buttons and the on-screen slider move you between these steps by calling `AudioManager.setStreamVolume()` with an index, not a decibel value.

Critically, step 1 (the lowest non-mute step) is not a fixed loudness. It is whatever fraction of the device's maximum output that OEM's volume curve assigns to index 1. That curve is set by the phone or tablet manufacturer, not by Android itself, and not by the app playing audio. A phone with a louder maximum output will usually also have a louder first step, even though both devices report "step 1 of 15" identically to any app that asks.

This is why the same complaint shows up across completely different phones, tablets, and Android versions: **the floor is proportional to the ceiling, not fixed to a comfortable loudness.**

## Why the minimum step exists (and why it's still too loud)

The stepped design exists for a real reason: discrete steps are predictable and easy to reason about, both for users pressing a volume button and for developers integrating with `AudioManager`. A continuous slider sounds nice in theory but is fiddly to hit an exact level with a physical button, and inconsistent across the huge range of speaker and amplifier hardware Android runs on.

The problem is that "predictable steps" and "quiet enough" are different goals, and Android's volume system was only ever designed to solve the first one. Manufacturers set the step curve based on the device's overall loudness range, not based on any accessibility or comfort floor. There is no requirement that step 1 be quiet in any absolute sense, only that it be quieter than step 2.

So: sensitive IEMs, bone-conduction headphones, tablet speakers (which often have an even higher minimum than phone speakers, because tablets are usually driven louder to fill more room), a sleeping baby a few feet away, tinnitus, a library, 2 a.m.: all of these need something below step 1, and Android's own settings simply do not offer it.

## What Granular Volume does

Granular Volume adds real attenuation **below** the hardware's step 1, not more steps squeezed into the existing range (which is what most "precise volume" apps actually do; they subdivide the same floor into finer clicks, which doesn't make the floor itself any quieter), but genuine additional headroom underneath it.

It's a small floating dial that sits over any app:

- **Drag it anywhere** on screen and it stays there across restarts.
- **Tuck it into a corner** and it turns semi-transparent, staying out of the way without fully disappearing.
- **Close it with one tap**, or reopen it from a **Quick Settings tile** without leaving the app you're in.
- **Doesn't override your physical volume buttons** or replace the system volume panel. It sits on top and does one additional thing, nothing else.

No ads. No tracking. No account. No network permission requested at all. The app has no way to send or receive data even if it wanted to.

## Features

- **Fine grained attenuation** from 0 dB (pass through, i.e. Android's own step 1) down to about -30 dB in steps, applied to the global output mix.
- **Floating overlay** that sits above any app, draggable to any position and persisted across restarts.
- **Quick Settings tile**: toggle the overlay on or off from the notification shade without opening the app.
- **One tap dismiss** and a clean, native feeling control surface.
- **Auto start on boot** so the control is ready when you need it.
- **Lightweight**, single purpose, written in pure Kotlin with no Compose runtime.

## Use cases

- **A sleeping baby or partner nearby**: white noise or a lullaby that needs to be barely audible, not just "quiet."
- **Sensitive in-ear monitors or bone-conduction headphones**: where even a couple of percent of the device's output is already too much.
- **Tablet speakers**: tablets frequently ship with an even louder minimum step than phones, since they're built to fill a bigger room.
- **Late-night podcasts or audiobooks**: falling asleep to something without it waking anyone else.
- **Quiet rooms**: a library, a shared office, meditation, or anywhere that needs genuine calm rather than "quieter than before."
- **Sound sensitivity and tinnitus**: situations where the standard volume floor is simply above a comfortable threshold.

## How this compares to a typical "volume booster" or "precise volume" app

Most apps in this space solve the opposite problem, or a different problem that looks similar on the surface:

| | Granular Volume | Typical volume app |
|---|---|---|
| Direction | Goes **quieter** than the hardware minimum | Usually makes things **louder**, or adds more clicks within the *existing* range |
| Mechanism | Independent output-gain effect, applied underneath the existing volume steps | Often remaps or subdivides the same step range you already have |
| Touches volume buttons? | No, buttons and system panel work exactly as before | Sometimes overrides or intercepts them |
| Setup | One dial, drag it, done | Frequently an equalizer, presets, or an account |
| Cost model | Free, open source, no ads, no tracking | Often paid, ad-supported, or both |

The distinction matters because "more steps" and "a lower floor" are not the same fix. An app that adds 30 finer clicks between mute and your phone's existing step 1 still bottoms out at exactly the same loudness step 1 always had. It just takes more taps to get there. Only attenuation applied *underneath* that floor actually changes the quietest sound the device can produce.

## Known limitations

In the interest of being straightforward about what this app does and doesn't do:

- It attenuates the **global** output mix, not a specific app's stream in isolation. If two apps are playing audio simultaneously, both are affected together.
- Very old or unusual OEM audio stacks that don't support `DynamicsProcessing` fall back to `LoudnessEnhancer`, which is a coarser tool not originally designed for this direction of gain. It works, but the primary path is preferred wherever available.
- This is attenuation, not noise cancellation or EQ. It makes everything uniformly quieter; it doesn't selectively suppress specific frequencies or filter background noise.
- The overlay needs "Display over other apps" permission, which (like any overlay permission on Android) cannot be silently pre-granted by the app itself. It's a manual step during setup, by design, since Android intentionally makes this permission visible and revocable.

## Screenshots

<table>
<tr>
<th>Setup</th><th>Overlay</th><th>In use</th>
</tr>
<tr>
<td><img src="store-assets/screenshot_1.png" width="260" alt="Setup screen"></td>
<td><img src="store-assets/screenshot_2.png" width="260" alt="Overlay with seven attenuation steps"></td>
<td><img src="store-assets/screenshot_3.png" width="260" alt="Overlay in use over another app"></td>
</tr>
</table>

## Demo

Android's own slider runs out of room, then the dial keeps going below it. The
audio in the video drops with every step shown on screen, so you can hear the
difference rather than just read about it.

<a href="https://www.youtube.com/shorts/pC4Zd8xtCOE">
<img src="https://i.ytimg.com/vi/pC4Zd8xtCOE/hqdefault.jpg" width="320" alt="Quiet Dial: lower your Android volume below the minimum">
</a>

[Watch on YouTube](https://www.youtube.com/shorts/pC4Zd8xtCOE)

## Compatibility

- **Android 9.0 (API 28) and up**: this is the actual `minSdk` in `android/app/build.gradle.kts`. If you've seen "Android 8+" mentioned elsewhere by mistake, this README and the app's own listing are the source of truth.
- **Phones and tablets alike.** The attenuation is applied to the global audio session, not to a specific screen size or form factor, so it behaves identically on both.
- **No root required.**
- Distributed as two functionally identical build flavors: `play` (Google Play, includes an optional in-app review prompt) and `fdroid` (F-Droid, zero Google Play dependencies; see [Building from source](#building-from-source)).

## FAQ

**Does this override my physical volume buttons?**
No. The buttons and the system volume panel work exactly as before. The overlay is an independent control that sits on top and applies additional attenuation underneath whatever the system volume is already set to.

**Why not just use an equalizer app's preamp/gain control instead?**
That works as a rough approximation of the same idea, but it's less precise and easier to overshoot into distortion, since preamp controls are built for tone shaping, not for a clean, predictable volume floor.

**Does it need root?**
No.

**Does it collect or send any data?**
No. There's no network permission in the manifest at all: not "we don't use it," the permission itself isn't requested, so the app has no code path capable of sending data even if it tried.

**Why does the app need "Display over other apps" permission?**
That's what lets the floating dial render above whatever app you're currently using. It's requested explicitly during setup and can be revoked at any time in system settings, which simply removes the overlay until you grant it again.

**Why does it need a foreground service / persistent notification?**
Android requires a foreground service to keep a real-time audio effect and an on-screen overlay alive reliably while you're using other apps. The notification is Android's own requirement for foreground services, not something this app adds voluntarily.

**Will this work with Bluetooth headphones?**
Yes. The attenuation is applied to the audio session before it reaches whatever output device is active, Bluetooth included.

**Does it work differently on tablets versus phones?**
The underlying mechanism is identical. Tablets often have a louder minimum step to begin with (see [Why the minimum step exists](#why-the-minimum-step-exists-and-why-its-still-too-loud) above), so the extra headroom tends to matter even more there.

**Is there a Pro or paid version?**
Not currently. The app is free and open source under GPL-3.0.

**Why is there a Play flavor and an F-Droid flavor?**
F-Droid requires that everything in its build be free and open source, including build dependencies. The `play` flavor includes Google's proprietary in-app review library (only used to occasionally ask for a Play Store rating); the `fdroid` flavor has zero Google Play code and is otherwise functionally identical. See [`build.gradle.kts`](android/app/build.gradle.kts) for the exact flavor split.

**Why not just make Android's default minimum volume lower?**
That's not something a regular app can change. The per-step volume curve is defined by the device manufacturer at the OS/firmware level, not exposed to third-party apps through any public API. Adding attenuation on top, outside the standard volume steps entirely, is the only mechanism available to an app that isn't the device's own system software.

**Can I use this alongside an equalizer app?**
Generally yes, since Granular Volume applies a gain-only stage rather than reshaping frequencies, but behavior with other apps that also attach global audio effects (some EQ apps do) depends on Android's effect-chaining order on that specific device, which isn't something a third-party app controls.

**Does closing the dial stop the attenuation?**
Closing the floating dial (the one-tap dismiss) stops the foreground service entirely, which releases the audio effect. Attenuation goes back to whatever the system's own volume step was already set to. The Quick Settings tile is the fast way to bring it back.

**Why doesn't the app just ask for a lower default step 1 instead of building an overlay?**
Because that setting doesn't exist as something a third-party app can request or change. The step curve lives in the OEM's audio HAL/policy configuration, well below any public Android API surface. An overlay with its own attenuation effect is the only mechanism actually available outside of modifying the device's firmware.

**Is the attenuation curve linear or logarithmic?**
The steps are applied as decibel offsets (roughly 5 dB per step down to about -30 dB), which corresponds much more closely to how loudness is actually perceived than a linear percentage scale would, matching the general approach Android's own volume curve uses, just extended further down.

## How it works (technical implementation)

The attenuation is produced by an Android audio effect attached to the global output session, with a graceful fallback chain so it behaves predictably across OEMs:

1. **`DynamicsProcessing`** (API 28+) is the primary engine. It's set up as an output-gain-only stage: no compression, no EQ curve, no limiting. The effect is clean, flat-spectrum attenuation applied uniformly across the signal, rather than anything that reshapes the sound.
2. **`LoudnessEnhancer`** is the fallback when `DynamicsProcessing` is unavailable at runtime on a given device or OEM audio stack. It's a coarser tool (designed for the opposite use case: boosting quiet audio), but with negative gain it still produces a usable attenuation curve as a fallback.

A foreground `Service` owns the effect and the overlay for the full lifetime of the session, with the effect created once and released deterministically in `onDestroy()`. There is no dangling audio effect left attached after the service stops. The service is declared `specialUse`, since it maintains a real-time audio effect and a floating control while the user listens to media in *other* apps, rather than playing media itself, which doesn't map cleanly onto any of Android's other foreground service types.

The attenuation itself is applied globally, at the output-mix level, not per-app and not by intercepting or rewriting any specific app's audio stream. This is deliberate: it means the effect works identically regardless of which app is producing sound, with no per-app allowlist to maintain and no risk of one app's audio being missed.

## Why version 1.3.1 exists

Version 1.3.0 added the optional in-app review prompt, which pulled in Google's proprietary `com.google.android.play:review` library as a build dependency. That's fine for the Play build, but it's a real problem for F-Droid, whose entire premise is that everything in the build (not just the shipped app, but every dependency needed to build it) has to be free and open source software. A dependency that's merely *unused at runtime* on F-Droid still isn't acceptable if it's still present in the build graph.

1.3.1 fixes this properly rather than working around it: the review library is now scoped to the `play` Gradle product flavor only, via `playImplementation` instead of a plain `implementation` dependency, with the review-prompt code itself split into a real implementation (`src/play`) and a no-op stub with an identical method signature (`src/fdroid`). The `fdroid` flavor's compiled output now has zero `com.google.android.play` code, verified directly against the built APK, not assumed from the Gradle config alone. Every other permission and behavior is byte-for-byte identical between the two flavors; nothing else changed in this release.

## Project structure

```
android/                        Android project (Gradle root)
  app/
    src/main/
      AndroidManifest.xml
      java/com/granularvolume/
        MainActivity.kt          Permission flow and service launcher
        service/                 Foreground service, lifecycle owner
        audio/                   Effect strategy: DynamicsProcessing + LoudnessEnhancer
        overlay/                 WindowManager overlay, drag and bounds logic
        receiver/                Auto start on boot
        util/                    Permission checks and SharedPreferences wrapper
      res/                       Layouts, drawables, themes, strings
    src/play/                    Play-only: in-app review prompt (ReviewHelper)
    src/fdroid/                  F-Droid-only: no-op ReviewHelper stub, zero Play dependencies
    build.gradle.kts
  build.gradle.kts
  settings.gradle.kts
store-assets/                   Store icon, feature graphic, screenshots, privacy policy
video/assets/                   Promo and card source files
fdroid-reference/               Reproducible-build verification artifacts for F-Droid
```

## Building from source

Requirements: JDK 17 and the Android SDK (compileSdk 35).

```bash
cd android

# Debug APK (installs alongside the Play build, applicationId suffix .debug)
./gradlew assembleDebug

# Play flavor release bundle (requires your own signing config, see below)
./gradlew bundlePlayRelease

# F-Droid flavor release APK: zero Google Play dependencies
./gradlew assembleFdroidRelease
```

The `play` and `fdroid` flavors are functionally identical apart from the optional in-app review prompt, which only exists in `play` (see [FAQ](#faq)). Both share the exact same `applicationId`, permission set, and attenuation/overlay behavior.

### Signing

Release signing reads from a `local.properties` file that is never committed. Create your own keystore and add:

```properties
keystore.path=/absolute/path/to/your-release.jks
keystore.storePassword=...
keystore.keyAlias=...
keystore.keyPassword=...
```

The debug build needs no signing setup and is the recommended loop for local testing.

## Permissions

| Permission | Why |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Draw the floating volume control over other apps. Granted manually in system settings. |
| `MODIFY_AUDIO_SETTINGS` | Apply the audio attenuation effect. |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | Keep the effect and overlay alive reliably. |
| `RECEIVE_BOOT_COMPLETED` | Restore the control after a reboot. |
| `POST_NOTIFICATIONS` | Show the foreground service notification (Android 13+). |

The overlay permission cannot be granted at runtime by the app. The setup screen guides you to the system toggle.

Notably absent: any network permission. It isn't requested because the app has no networking code at all.

## Contributing

Issues and pull requests are welcome. A few things that make a report or a PR faster to act on:

- **Bug reports:** include the device model, Android version, and app version (from the setup screen). If it's audio-related, mention which app was producing sound and which output device (speaker, wired, Bluetooth) was active.
- **Pull requests:** keep changes focused: one behavior change per PR is easier to review and easier to revert if something's wrong. Match the surrounding Kotlin style rather than introducing a new one.
- **Flavor-affecting changes:** if a change touches anything under `android/app/src/play/` or `android/app/src/fdroid/`, please note in the PR description whether it was tested on both flavors, since the two are expected to stay behaviorally identical outside of the review-prompt difference documented above.
- **New dependencies:** since the `fdroid` flavor exists specifically to stay free-software-only, any new dependency should be added as `implementation` (available to both flavors) only if it's fully open source; anything proprietary needs to go behind `playImplementation`, the same pattern used for the review library.

## License

Granular Volume is free software, licensed under the **GNU General Public License v3.0**. See [LICENSE](LICENSE) for the full text. You are free to use, study, share, and modify it, provided derivative works remain under the same license.

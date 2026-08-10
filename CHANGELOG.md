# Changelog

All notable changes to Granular Volume are documented here. Format loosely follows [Keep a Changelog](https://keepachangelog.com/).

## 1.4.1 (versionCode 13)

Bluetooth correctness fix, reported from the field one day after 1.4.0 shipped. On wireless routes (Bluetooth A2DP, LE Audio, hearing aids) with Absolute Volume active, Android forwards the volume index to the headset and the headset applies its own loudness curve, so the per-index dB table the phone reports is not what actually plays. The 1.4.0 upper zone built its uniform 5 dB ladder from that table, which could paint lit bars over headset silence: steps remained on screen after the sound was already gone. The upper zone now uses raw hardware indices on those routes, one bar per real step, exactly what the volume keys do, so nothing on screen can be a step the ear never hears. When the user has disabled Absolute Volume in developer options, the phone-side curve is authoritative again and the uniform ladder returns.

Route detection now also recognizes LE Audio headsets, hearing aids and SCO, which previously fell through to the built-in speaker's curve. And unmuting after switching outputs re-anchors to the new route's own level instead of restoring the previous route's index, which could have been a loud surprise. The quiet zone is untouched: its attenuation is applied to the signal inside the phone, before any wireless encoding, and was never affected.

## 1.4.0 (versionCode 12)

Full-range mode. The dial is now one continuous scale instead of a quiet-only one: above the device's minimum line it controls normal system volume, replacing the physical volume buttons for anyone whose buttons are broken, stiff, or hard to reach; below the line it does exactly what it always did, down to -30 dB. The whole scale moves in uniform 5 dB steps, built at runtime from the device's own volume curve via `AudioManager.getStreamVolumeDb`, so a step means the same thing on every handset. The slider drives whichever stream the volume buttons would drive: media while audio is playing, ringtone otherwise. No new permissions.

Above the line the hardware volume index does the coarse work and the audio effect supplies only the sub-step remainder, so if the effect is ever unavailable the level can move by at most one hardware step. The last rung sits exactly on the device floor, which makes the crossing between the two zones continuous, and that level is drawn once so every press moves the highlight by exactly one bar. A mute control on the overlay silences media only, leaving alarms audible, and restores the previous level on a second tap. Volume-button presses inside the quiet zone are absorbed into the scale rather than fighting it, and any correction we make can only ever lower the hardware volume, never raise it.

The overlay keeps its previous footprint: the step bars and chevrons were made smaller to make room. This release also adds the consent gate and the Terms of Use and Privacy Policy links to the setup screen, so the app's terms are actively accepted rather than merely published. The F-Droid flavor remains free of proprietary dependencies.

## 1.3.4 (versionCode 11)

The volume control now survives a reboot. Previously, the service's `onDestroy` cleared the "was running" flag unconditionally, and since a device shutdown also destroys the service, `BootReceiver` always found the flag false and never restored the control. The flag is now cleared only on user-intended stops (the notification's Stop action, dismissing the overlay, or toggling the Quick Settings tile off), so a control that was on at shutdown comes back after boot at its saved attenuation level, while a control the user stopped stays stopped.

The on-device app name is now "Quiet Dial", aligning with the store title ("Volume Control: Quiet Dial") while staying short enough for launcher and Quick Settings labels. The QS tile label now references `@string/app_name` instead of a hardcoded string (in two places: the manifest and the tile's `syncTile`), so it can never drift again. No new permissions, no change to the dial itself, and the F-Droid flavor remains free of proprietary dependencies.

## 1.3.3 (versionCode 10)

Maintenance release: the app now targets Android 16 (API 36), meeting Google Play's target-API requirement ahead of the August 31, 2026 deadline. `compileSdk` and `targetSdk` were bumped to 36, and the release was verified on an Android 16 emulator (overlay, Quick Settings tile, foreground service, and attenuation all behave identically). No new permissions, no behavior changes, and the F-Droid flavor remains free of proprietary dependencies.

## 1.3.2 (versionCode 9)

The Play flavor's optional in-app review prompt now has a second, more effective trigger point. It was previously offered only on a return visit to the main screen, which tile-driven users almost never make. It is now also offered after the user has turned the Quick Settings tile on a few times (the real usage signal), hosted by a transparent, no-UI `ReviewActivity` that the tile launches via `startActivityAndCollapse`.

Both trigger paths share the same one-shot flag, so the prompt is still offered at most once per install, and still never on first-time setup. The F-Droid flavor is entirely unaffected: it has no `ReviewActivity`, no review library, and its `ReviewHelper` stub returns no intent, so the tile code compiles to a pure no-op there. `applicationId`, signing, and the permission set are unchanged; no new permissions were added.

## 1.3.1 (versionCode 8)

Behind-the-scenes rebuild: the app is now built as separate `play` and `fdroid` Gradle product flavors from one codebase, so the F-Droid build has zero proprietary dependencies. No visible changes to how the app works or looks.

The Play flavor keeps the optional in-app review prompt (Google's `com.google.android.play:review` library); the F-Droid flavor doesn't include it at all, not just at runtime but out of the build graph entirely. `applicationId`, the signing configuration, and the full permission set are unchanged and identical between both flavors. See the [README's "Why version 1.3.1 exists" section](README.md#why-version-131-exists) for the full technical writeup.

## 1.3.0 (versionCode 7)

On Android 13 and above: the app now requests notification permission on first launch (so the foreground control is visible in your notification shade) and offers to add the Quick Settings tile in one tap, no manual tile search needed. Toggle the overlay straight from the notification shade once added.

## 1.1.2 (versionCode 4)

Rebuilt setup screen and a smoother floating dial. Drag it anywhere, tuck it to an edge, and close it with one tap. Stability and compatibility fixes.

Also added: Quick Settings tile, swipe down, tap once, the volume control is on or off without opening the app. Add it to your Quick Settings panel from the tile editor.

---

Full commit history is on [GitHub](https://github.com/Rzuss/granular-volume/commits/main). Per-version F-Droid changelog text lives in `fastlane/metadata/android/en-US/changelogs/`.

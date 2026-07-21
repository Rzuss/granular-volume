# Changelog

All notable changes to Granular Volume are documented here. Format loosely follows [Keep a Changelog](https://keepachangelog.com/).

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

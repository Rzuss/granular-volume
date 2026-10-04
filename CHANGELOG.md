# Changelog

All notable changes to Granular Volume are documented here. Format loosely follows [Keep a Changelog](https://keepachangelog.com/).

## 1.7.0 (versionCode 49)

One idea behind the release: every surface tells the same true story about where the user stands.

### Changed
- The app icon and the control's notification are doors now. While the control is running, tapping either
  one opens "Your access" (status, price, restore, help) instead of starting a control that is already on.
- Google Play version, after the free week: a control that was on when the week ended keeps its level
  exactly as it is until it is stopped or the device restarts. The first tap on the dial that would change
  the level asks for the unlock. The device's own volume buttons keep working, and volume up still makes
  the sound louder. Nothing gets louder or quieter by itself.
- Google Play version: our own update no longer ends that held level. After an update the control comes
  back as it was. A restart of the device and a control you stopped behave as before.
- Google Play version: the first screen states the whole arrangement in one sentence, with the real number
  of days left, and the button in "Your access" reads "Keep it for good" during the free week.
- Google Play version: a first open that finds the free week already over gets 24 hours of everything,
  once, and the first screen says so.
- The setup screen shows one button at a time (the step you are on), explains the optional notification
  permission before Android asks for it, and its hints follow what is actually missing.
- The notification says "On" instead of "Pass-through".

### Added
- Google Play version: when a newer version is in the store, "Your access" shows a row with an Update
  button and the dial's info button carries a small dot. The app asks the Play Store app on the device;
  it still has no internet permission.
- A Help link on the setup screen and in "Your access".
- When a device does not let the app attach its quiet steps, the app says so once and keeps saying it in
  "Your access". Until now this was only written to the log.
- A line about battery managers on the setup screen, shown only while the system may put the app to sleep.

### Fixed
- Rotating the screen: the dial and the edge tab are placed again for the new screen shape. On a phone held
  sideways the dial is drawn slightly smaller so its lowest part (the level readout and mute) is on screen.
- The Quick Settings tile, the app icon and the free-week note read whether the control is really running.
  After the system closed the control, the tile could show On and need two taps.
- The feature tour waits until the "Add tile" question has been answered. It used to start underneath it.
- Google Play version: a purchase the device had not heard of yet (new device, reinstall, a payment that
  was confirmed later) is found whenever a purchase screen or the setup screen opens, not only at the next
  start. A second tap on the buy button while Google Play is opening is ignored. Restore that finds nothing
  explains the account switch. A product that is not on sale for the account says so, and that nothing was
  charged. While a payment is pending the buy button is hidden.
- The control no longer keeps running without its dial when the "display over other apps" permission was
  switched off: it stops and opens the setup screen.
- Screen readers: the links in "Your access" are announced as buttons and are full-size targets, and the
  dial says when it is locked.

### Unchanged on purpose
- Trial length, the long-time-user grant, the price, the audio engine, the tour's order and text (its last
  card now names the X and the i), and the F-Droid build, which stays free and complete.

## 1.6.4 (versionCode 43)

### Fixed
- Google Play version: the buy button now always carries the price. It is asked from Google Play as each
  sheet opens; before, a sheet opened right after a start could show the button with no number, and a
  long-running app could keep showing a price that had since changed.
- The access sheet of a finished free week says so: "Your free week is over", with the reason first. It
  used to read "The dial is locked", which looked like a fault.
- An app update no longer switches the control off. It comes back by itself under the same rules as a
  restart: only if it was on, never if you stopped it, and a locked control stays off.
- The feature tour is shown once. It used to replay after every update.
- A payment that Google Play is still confirming is shown on the sheets, not only in a passing message.
- When Google Play purchases are not available on a device or account, the message says that, instead of
  suggesting a connection problem.
- The two free-week notes are only counted as shown when they were really shown.
- The ongoing notification is titled with the app's name.

### Added
- The purchase, access, free-week and notice texts in German, Spanish, French, Brazilian Portuguese,
  Indonesian and Polish. The rest of the app stays in English for now.
- The last-three-days card shows the price on its button once Google Play has reported it.

### Unchanged on purpose
- Trial length, the long-time-user grant, the price, the dial and the audio engine.

## 1.6.3 (versionCode 42)

### Fixed
- Tablets, and phones held sideways: the access sheet and the full-range sheet opened as a thin
  title strip, with the explanation and the buttons hidden below the screen edge until the sheet was
  dragged up. Both now open fully in every orientation and still close with a swipe down.
- The feature tour says "Quieter than your device allows" (it said phone, on tablets too).

## 1.6.2 (versionCode 41)

### Changed
- Google Play version: the end of the free week is now explained, never silent. Two quiet notes in
  the notification shade, at most once each per install: one within the last 24 hours of the week,
  and one when the week has ended. The second says plainly what happens next: a control that is
  running keeps working until it is stopped or the phone restarts, and a control that did not come
  back after a restart is paused, not broken. Each note is skipped when the app has already said the
  same thing on screen, never makes a sound, and opens the access sheet on a tap.
- Nobody who has the full range for good ever sees either note: long-time users, buyers, key owners
  and the F-Droid build. With notifications turned off for the app, nothing is shown.
- The trial-over card ends on "Nothing renews and there is no subscription."
- The feature tour counts six quiet steps below the orange line, as the dial shows (it said seven).

### Unchanged on purpose
- Trial length and its anchor, the long-time-user grant, the purchase flow, the dial and the audio
  engine. A trial that ends while the dial runs still takes effect at the next start, never mid-use.

## 1.6.1 (versionCode 40)

### Changed
- One way to buy. When Google Play cannot complete a purchase on a device, the message now offers
  a retry only; the app no longer points anyone at the separate Full Range Key app. A key that is
  already installed still unlocks the app exactly as before, forever.

## 1.6.0 (versionCode 39)

### Changed
- Google Play version: the full-range unlock can now be bought inside the app. The unlock button on
  the access sheet, the paywall and the trial card opens Google Play's own purchase window; the
  price shown on the button is the one Google Play reports for the account and region. A "Restore"
  link brings a purchase back on a new phone or after a reinstall. The purchase is re-checked
  against Google Play's record each time the control starts, so a refund locks the range again.
- The separate Full Range Key app still unlocks the app exactly as before, and is offered as the
  fallback when Google Play cannot run a purchase on a device. A key owner is thanked for the key
  and offered nothing to buy.
- The access sheet and the paywall carry a state badge beside the headline: a closed padlock when
  the range is locked, an open padlock with a check when it is open, a clock during the free week.
- No new network access: the app still has no INTERNET permission. Google's billing library is
  built in without its telemetry component; the only new permission is com.android.vending.BILLING
  (Google Play version only). The F-Droid build has no billing code and stays free and complete.

### Unchanged on purpose
- Trial length and its anchor, the long-time-user grant, the tour, the dial and the audio engine
  are byte-identical to 1.5.3.

## 1.5.3 (versionCode 36)

### Fixed
- Stopping the control (notification, Quick Settings tile) during the 0.2 s fold into the edge tab
  could leave the tab or the dial on screen with no service behind it. Every fold now checks that
  the control is still running before it adds the next window.
- When the Full Range Key arrived while the dial was folded into the tab, the dial came back but the
  unlock wave was skipped. It now plays once the dial is back on screen.
- The main screen could crash when Google Play's rating prompt finished after the screen had
  already closed (IllegalStateException in launchService, present since 1.4.x). The control now
  starts directly in that case.
- The restart after a phone reboot could crash the app when Android refused to let it start in the
  background at that moment (ForegroundServiceStartNotAllowedException in BootReceiver). A refused
  restart now leaves the control off until the next time it is opened, without a crash.
- When Android refused to open the access sheet from the background (seen right after a reboot), the
  refusal was thrown from the service and stopped the whole control. Sheet opens are now guarded:
  a refused sheet is skipped and the dial keeps running.

## 1.5.2 (versionCode 35)

### Fixed
- After the dial came back from the edge tab, the upper-zone bars (normal volume, above the orange
  line) were not drawn, although taps still changed the volume. The dial is rebuilt fresh on every
  restore, and the bar cache now checks that its bars belong to the dial on screen.

## 1.5.1 (versionCode 34)

### Added
- Minimize. The new minus button, or a drag that pushes the dial past a screen edge, folds it into
  a small tab with a plus sign at that edge. The tab still shows the level and the device-minimum
  line, and the attenuation keeps running. Tap the plus, or pull the tab inward, and the dial
  returns to exactly where it was. The tab survives a service restart and a reboot.
- Docking. Release the dial near either side of the screen and it glides flush to that edge.
- A four-step feature tour on the first start after installing or updating: normal volume, the
  quiet zone, mute, and the tab. The dial stays live during the tour, so each step can be tried.
  Skip at any step; replay from the info sheet.

### Changed
- The info button sits alone at the top of the dial; minimize and close are paired below it. A
  finger that aims at the info button and lands low now minimizes instead of closing.
- The dial no longer parks half off the screen; the tab replaces that.
- The one-line hint next to the orange line is replaced by the tour.
- Grandfather cutoff for updates set to 2026-10-01 (installs before the paid version, updated
  later, still keep everything).

## 1.5.0 (versionCode 33)

**If you installed the app before this version, nothing changes for you.** The complete app stays
yours, free, permanently.

### Added
- On Google Play, new installs get the complete app for seven days. After that, keeping the control
  takes a one-time purchase of a separate companion app, Full Range Key. There is no subscription and
  nothing renews. The app counts down the last days, locks the dial when the week is up, and your
  device's own volume keys keep working throughout.
- The dial speaks its level to screen readers, for example "Volume minus 15 dB, below the device
  minimum", so changing the volume no longer depends on seeing the screen.
- An open source licences screen, reachable from the main screen, listing every bundled component
  with its licence and the full Apache-2.0 text.

### Changed
- On a regular phone call the quiet steps show dimmed, because Android runs no audio effects on
  cellular call audio. Calls in apps such as WhatsApp keep both zones where the phone plays them as
  media.
- Text and buttons meet the WCAG 2.1 AA contrast minimum, the upgrade and access sheets scroll at
  large font sizes, and their text actions are announced as buttons.
- The main screen now describes what the app does: "Quieter than your phone or tablet allows".
- Terms of Use updated for the trial and the optional purchase; the app asks you to accept them once.

### Unchanged
- The F-Droid build includes everything at no charge, permanently, with no trial and no purchase
  feature in it.
- No ads, no tracking, no accounts, and still no internet permission in any version.

## 1.4.8 (versionCode 28)

F-Droid build hygiene release, no behaviour change. The Android Gradle Plugin embeds a block of dependency metadata, encrypted with a Google public key, into every APK's signing block. F-Droid now rejects updates that carry it, because nobody but Google can read what is inside. This release sets `dependenciesInfo.includeInApk = false` (and the bundle equivalent) so the block is gone, verified by parsing the signing block of the built APK before and after. Published to F-Droid only; Google Play stays on 1.4.7, which is identical in behaviour.

## 1.4.7 (versionCode 27)

Two fixes on the production line, both first written for the coming 1.5.0 and backported. First, volume-up presses inside the quiet zone could be pulled straight back down by the zone's own defence, so the volume seemed stuck at silence; presses now climb out of the zone, and if some other app keeps forcing the volume up, a second deliberate press by the user wins. Second, the quiet steps are dimmed during a regular cellular call, with a one-line note, because Android carries call audio on an output that runs no audio effects and nothing can lower it further; the earlier claim that the app lowers regular calls was wrong and has been withdrawn everywhere. App calls such as WhatsApp keep the full range on most phones.

## 1.4.6 (versionCode 18)

Reliability release for in-call control, closing the intermittency 1.4.5 left behind. That release re-attached the audio effect once, at the instant the audio mode changed, and that single moment can lose three races: on some phones the call's output path opens a beat after the mode flips, so the re-attach landed on the old path; switching to speaker or Bluetooth mid-call moved the voice audio to an output the effect never followed; and a transient effect-initialisation failure during call setup stranded the app on the weaker fallback with no retry. Each race explains the same field report: control worked in some calls and not in others.

All three are closed. Every re-attach now runs twice, immediately and again one second later once routing has settled. Route changes during a call (speaker, Bluetooth, wired) trigger their own re-attach. And if the effect lands on the fallback strategy, the app retries up to three times. Every pass preserves the chosen attenuation level, and all passes are idempotent, so the worst case of an extra pass is a few silent milliseconds.

Verified on real hardware across consecutive calls with mid-call speaker switching, and on an emulator across rapid mode-change stress, a live GSM call, and effect-chain integrity checks: exactly one effect chain at all times, no crashes, level preserved through every transition.

## 1.4.5 (versionCode 17)

In-call fix for the quiet zone, from two matching field reports reproduced at the audio-engine layer. A global audio effect lives on one output path, chosen by the system when the effect is created, and that choice follows where music plays. Call audio, both cellular and VoIP, travels a different output path on many phones, so the quiet zone's attenuation never reached it: steps below the line did nothing during calls while working normally for media on the same device.

The fix re-creates the effect on every audio-mode transition, call start and call end alike, giving the system the chance to attach it to the output path that is actually carrying sound. The chosen attenuation level is preserved across the swap, and the same re-attach on hang-up restores normal media coverage afterwards. Verified on real hardware: quiet-zone steps now audibly lower both regular cellular calls and VoIP calls on the test device.

Stated honestly, two boundaries remain. The audio-mode listener this fix relies on exists from Android 12; on Android 9 to 11 the re-attach does not trigger and in-call behaviour is unchanged. And on devices that route call audio entirely in hardware, past every software output, no app-level effect can reach it; the fix covers the cases where call audio is in software on a different path, which both field devices turned out to be.

No other changes: the interface, step values, permissions and size are identical to 1.4.4.

## 1.4.4 (versionCode 16)

Interface release. Two changes, both aimed at the same measured problem: users were not understanding the two-zone scale they could already see.

The controls were redesigned. The chevrons became wide sculpted glass keys, and the mute control became a full-width bar at the bottom of the pill, below a centred level label. Mute is now unlike every other control in shape, position and size, so a tap aimed at a chevron can no longer plausibly land on it, and the muted state is carried by three redundant channels at once: the bar fills, the glyph and text invert, and the label above turns red and reads MUTED. Nothing is signalled by colour alone. The manual hit regions were re-derived for the new geometry, and the chevrons are tested before the mute bar so contested space between them can never resolve to mute.

The overlay's outer size is unchanged. That was a hard constraint rather than an accident: the geometry was tuned so the control can be tucked against the screen edge, stay usable, and never cover the navigation buttons. Measured against the previous release on the same device, the width is identical and the height differs by one pixel.

A physical volume key press that lands inside a step used to look like nothing happened, because the hardware index moved but the highlighted bar did not. The bar now gives a short flash to acknowledge the press. On the lowest step above the device-minimum line, a downward press flashes the first step below the line instead, so the boundary reads as an invitation into the quiet range rather than as the end of the scale. One limitation is worth stating: when the hardware is already at its own floor a press produces no system broadcast at all, so that final press cannot be detected or acknowledged.

Volume behaviour is untouched. The step values, the 5 dB rungs, the audio effect and the absorb policy are byte-identical to 1.4.3.

## 1.4.3 (versionCode 15)

In-call control, closing the one case where the dial and the physical keys still disagreed. During a cellular or VoIP call, Android routes the physical volume keys to the call volume, a third scale separate from media and ringtone. The dial's upper zone stayed on media volume, so moving it during a call changed nothing audible. The upper zone now follows the same rule as the keys: during a call it drives the call volume, using the call scale's real hardware steps, and the moment the call ends it returns to media volume. The call state is evaluated at the moment of each interaction, the same way Android itself resolves the keys, so this needs no new permissions.

The quiet zone is untouched and keeps its unique property in calls: because it attenuates the signal itself rather than moving any volume scale, it reaches below the call scale's minimum, which no volume key can do. The ringtone stream remains completely untouched, as decided in 1.4.2.

## 1.4.2 (versionCode 14)

Idle-state fix, found during hardware verification of 1.4.1. Since 1.4.0 the dial followed the rule "media stream while audio plays, ringtone stream otherwise". Android itself does not work that way: when nothing is playing, the physical volume keys adjust media volume, not ringtone volume. The mismatch had two effects. With no audio playing, pressing the volume keys moved nothing on the dial, so it looked frozen. Worse, dragging the upper zone while idle silently changed the ringtone volume instead of the media volume.

The dial now always drives the media stream, matching what the volume keys actually do. A side benefit: the app never touches the ringtone stream at all anymore, which removes the only code path that could have interacted with silent mode. The quiet zone, mute, and Bluetooth behavior are unchanged.

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

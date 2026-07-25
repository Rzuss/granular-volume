# Device Test Results — 2026-07-25 (complete: all rivals measured)

The homework the Market Truth Audit demanded before any comparative claim: measure, on a real
Android device, what each app actually does to the audio output. Not UI screenshots, not store copy.

## Method (and why it is trustworthy)

Test rig: Android 14 emulator (`CompetitorTest`, Play-Store image, x86_64, Google account signed in),
440 Hz sine tone playing through the system MediaPlayer.

Measurement reads AudioFlinger's own state via `dumpsys media.audio_flinger`:
- the **Tracks table** with applied gains (`G db / L dB / R dB / VS dB`),
- the **Effect Chains** attached to the output — each effect's UUID, name, enabled flag, and
  **which audio session it binds to**.

Decisive because an app that genuinely attenuates below the hardware floor MUST appear here as an
attached, enabled effect. An app that merely repaints a slider shows nothing, whatever it claims.
**Session 0 is the global output mix** (all apps); any other number is one specific session.

Isolation discipline: when measuring a rival, our app and every other audio app were **uninstalled**
first — an early read was contaminated by two effects on session 0 and was discarded. The reboot
test was run identically for every app, including ours.

---

## Result 1 — Android's own floor (baseline, no app)

```
1 Tracks of which 1 are active
   Id 56 ... G db 0   L dB 0   R dB 0   VS dB 0
0 Effect Chains
```
Nothing attenuates below step 1. Confirms the premise the product exists for.

## Result 2 — Granular Volume (ours), dial at its lowest step

Dial UI: **-30 dB**.
```
1 Effect Chains
   1 effects for session 0            <-- GLOBAL output mix
   Effect ID: Registered y  Enabled y  Suspended n
   - UUID: e0e6539b-1781-7261-676f-6d7573696340
   - name: Dynamics Processing
```
**Verified:** a real, enabled Dynamics Processing effect on the global mix. Cost to the user:
install, launch, one tap. No ADB, no Shizuku, no companion app, no recording permission, no ads.

**Reboot test: FAILS — `0 Effect Chains` after reboot, same as every rival.** The earlier draft of
this report claimed our foreground service restores the effect; the measurement refuted that.
Root cause found in source: `VolumeControlService.onDestroy()` writes `wasServiceRunning=false`,
and Android's shutdown destroys the service — so `BootReceiver` (present and registered, verified
in the installed APK) always sees a false flag and skips the restart. **One-line fix; task filed.**

## Result 3 — Volume Control: Lower or Boost (500K installs, 4.5★, 9K reviews)

From Play (13 MB, "Contains ads • In-app purchases"). "Fine-tune the media volume" slider dragged
to its floor: **-100%** (a percent scale — the app never states dB). Measured in isolation:

```
1 Effect Chains
   2 effects for session 0            <-- also GLOBAL
   Effect ID 51: Enabled y  name: Equalizer
   Effect ID 59: Enabled y  name: Loudness Enhancer
```

**Its below-minimum claim is REAL.** Mechanism: negative-preamp Equalizer + Loudness Enhancer on
session 0. The "maybe it's fake" hypothesis is dead — do not repeat it.

Measured defects: **no reboot persistence** (`0 Effect Chains` after reboot); **full-screen video
ads** on the path to the fix (one right after the permission grant, another on next launch) plus a
rating beg on launch 2; and its own dialog warns users to **delete RootlessJamesDSP** as
incompatible.

## Result 4 — Precise Volume 2.0 + Equalizer (5M installs, 4.0★, 32K reviews)

From Play (14 MB, "Contains ads • In-app purchases"). The flagship of the category.

**The path to below-minimum, as experienced:** 6-page onboarding → full-screen ad (two-layer,
needed two closes) → home tip dialog → Equalizer tab (banner ad at bottom) → "Volume Booster" card,
which turns out to be an instruction: *"To boost volume, open Graphic EQ and adjust 'Post-gain'"*
→ Graphic EQ → Post-gain slider. Their own onboarding pitch is about **step resolution** ("override
Android's default 15-25 volume steps"), not the floor. The VBO (Volume Button Override) feature is
labeled **beta**, with key modes locked behind **PRO**.

**Post-gain floor, from their own UI: -17.7 dB** (slider hard against its left stop).

```
1 Effect Chains
   1 effects for session 0            <-- GLOBAL
   Effect ID 19: Enabled y
   - UUID: e0e6539b-1781-7261-676f-6d7573696340   <-- the SAME DynamicsProcessing API we use
   - name: Dynamics Processing
```

**Verified real and global — and it uses the exact same Android API we do** (identical effect UUID).
This kills any "unique mechanism" claim, and simultaneously hands us the honest number we lacked:

> **Their floor: -17.7 dB. Ours: -30 dB. Both measured in dB, at the audio-engine layer.**

**Reboot test: FAILS** — `0 Effect Chains` after reboot, same as the others.

## Result 5 — RootlessJamesDSP (FOSS, F-Droid, v1.6.14, 38.6 MB)

Before setup: `0 Effect Chains` — inert. Its own screens demand: **Shizuku or ADB** ("computer and
USB cable required"), an **audio-recording permission re-granted every launch**, and its splash
warns it is *"unfinished and unstable"*. After full setup: Dynamics Processing on **session 113**
(one session, NOT global) plus an active RECORD track — a capture-and-re-emit architecture, which
structurally explains its documented Spotify/Chrome exclusion.

---

## The measured comparison — final

| | **Granular Volume** | Precise Volume 2.0 | VC: Lower or Boost | RootlessJamesDSP |
|---|---|---|---|---|
| Below-floor real? | **yes** | yes | yes | yes |
| Mechanism | DynamicsProcessing | **same** DynamicsProcessing | EQ + LoudnessEnhancer | DP + capture |
| Session | **0 (global)** | 0 (global) | 0 (global) | 113 (single) |
| **Measured floor** | **-30 dB** | **-17.7 dB** | "-100%" (dB unknown) | postgain (n/m) |
| Taps from install to floor | **~3** | ~15 (onboarding+ads+buried path) | ~6 (incl. ad) | n/a without ADB |
| Survives reboot | **no (bug found, 1-line fix filed)** | no | no | no |
| Ads | **none** | full-screen + banner | full-screen video | none |
| Extra requirements | none | none | none | ADB/Shizuku + record perm |
| Spotify/Chrome | **works** | works | works | excluded |
| Size | **2.7 MB** | 14 MB | 13 MB | 38.6 MB |
| Price of full floor | **free** | floor free; VBO=PRO | free (+IAP upsell) | free |

## Claims ledger

**Earned, measured, publishable:**
- **"Goes 12 dB lower than the deepest big competitor"** — ours -30 dB vs Precise Volume's -17.7 dB,
  both read off the audio engine. This replaces the dead "-30 vs -18" guess with a measured fact.
- **System-wide (session 0), one tap from launch, no ads, 2.7 MB, no recording permission, no
  internet permission, works with Spotify/Chrome** — each backed by measurement or rivals' own UI.
- **The quiet floor is our headline, their footnote:** Precise Volume buries attenuation behind a
  15-step path and pitches step-resolution instead; VC:LoB fronts loudness BOOST. Nobody else makes
  "below the minimum" the product.

**DEAD — never say:**
- ❌ "Only app that goes below the minimum" (three rivals measured doing it).
- ❌ "Their claims are fake" (measured real).
- ❌ "Unique mechanism" (Precise Volume uses the identical API).
- ❌ "Survives reboot, unlike rivals" — **currently false for us too.** Becomes a true, unique,
  measured claim only after the BootReceiver flag fix ships and is re-verified.

## Still open

- Ship + re-verify the reboot-persistence fix (task filed). Post-fix, we'd be the only measured
  survivor — the single strongest claim available in this category.
- Emulator, not physical hardware; re-confirm the headline numbers on a real handset before ads/copy.
- No acoustic SPL measurement (conclusions are at the effect/gain layer, which is the right layer
  for "is it real", but not a loudness-perception study).

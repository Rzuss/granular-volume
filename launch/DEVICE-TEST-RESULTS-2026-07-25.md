# Device Test Results — 2026-07-25

The homework the Market Truth Audit demanded before any comparative claim: measure, on a real
Android device, what each app actually does to the audio output. Not UI screenshots, not store copy.

## Method (and why it is trustworthy)

Test rig: fresh Android 14 emulator (`CompetitorTest`, Play-Store image, x86_64), 440 Hz sine tone
playing through the system MediaPlayer, media stream at a fixed index.

The measurement reads AudioFlinger's own state via `dumpsys media.audio_flinger`, which reports:
- the **Tracks table** with the applied gains (`G db / L dB / R dB / VS dB`),
- the **Effect Chains** attached to the output, including each effect's UUID, name, enabled flag,
  and **which audio session it is bound to**.

This is decisive because an app that genuinely attenuates below the hardware floor MUST appear here
as an attached, enabled effect (or a negative track gain). An app that merely repaints a volume
slider shows nothing at this layer, no matter what its listing claims. Session binding is equally
decisive: **session 0 is the global output mix** (every app's audio); any other session number is
one specific audio session.

## Result 1 — Android's own floor (baseline, no app)

```
1 Tracks of which 1 are active
   Id 56  ...  G db 0   L dB 0   R dB 0   VS dB 0
0 Effect Chains
```
Nothing attenuates below step 1. Confirms the premise the product exists for.

## Result 2 — Granular Volume (ours), dial set to its lowest step

Dial UI read: **-30 dB**. AudioFlinger:
```
1 Effect Chains
   1 effects for session 0            <-- GLOBAL output mix
   Effect ID 11:  Registered y  Enabled y  Suspended n
   - UUID: e0e6539b-1781-7261-676f-6d7573696340
   - name: Dynamics Processing
   - flags: ... volume mgmt: implements control
```
**Verified:** a real, enabled Dynamics Processing effect attached to **session 0**, i.e. the global
media output. Setup cost: install, launch, one tap. No ADB, no Shizuku, no companion app, no
recording permission.

## Result 3 — RootlessJamesDSP (the closest technical rival, FOSS, F-Droid)

Installed from F-Droid (v1.6.14, 38.6 MB — ours is 2.7 MB).

**3a. Before completing its setup:**
```
0 Effect Chains
```
It does nothing to audio at all until the user completes a privileged setup flow.

**3b. Its own setup screens (verbatim):**
- Splash: *"Currently, this application is unfinished and unstable. Please note that it may not work
  on some devices as expected since it uses Android APIs not designed for use by third parties."*
- "Choose setup method": **Shizuku** ("Android 11+ only", requires installing a separate app) or
  **ADB** ("For advanced users · Computer and USB cable required").
- "Other permissions": **Audio recording permission** ("Required to record internal audio"),
  notification permission, and **Cast/recording permission** — *"You need to explicitly grant this
  app permission to record audio content every time it launches."*

**3c. After full setup, with permissions granted via ADB:**
```
1 Effect Chains
   1 effects for session 113          <-- a specific session, NOT global
   Effect ID 19: name: Dynamics Processing
+ an active RECORD track (capture architecture)
```

## The measured differences

| | Granular Volume | RootlessJamesDSP |
|---|---|---|
| Effect attached | Dynamics Processing, **enabled** | Dynamics Processing, enabled |
| **Session binding** | **0 (global output mix)** | 113 (single session) |
| Architecture | attach effect to output | **capture + re-emit** (record track present) |
| Works with no setup | **yes — install, tap** | no (`0 Effect Chains` until ADB/Shizuku) |
| Requires ADB or Shizuku | **no** | **yes** |
| Requires audio-recording permission | **no** | **yes**, re-granted every launch |
| Spotify / Chrome | unaffected by capture blocking | excluded (their own README) |
| APK size | 2.7 MB | 38.6 MB |
| Self-described stability | — | *"unfinished and unstable"* (their own splash) |

## What this earns us, and what it does not

**Earned, measured, publishable:**
- Our effect is attached to **session 0, the global output** — a genuinely system-wide attenuation,
  verified at the audio-engine layer, not claimed.
- **Zero-friction setup is real and rival-verified:** the closest technical alternative needs a
  computer, a USB cable or a companion app, plus a recording permission re-granted every launch.
  Ours needs one tap. That gap is now documented from the rival's own screens.
- The capture architecture explains their Spotify/Chrome exclusion structurally, and our approach
  does not share it.

**NOT earned — still unmeasured, do not claim:**
- **Volume Control: Lower or Boost** (the 500K-install rival that markets "below system limits")
  and **Precise Volume 2.0** could not be tested: the Play Store on the test device requires a
  Google account sign-in, and the public APK mirrors returned HTTP 403. Their below-minimum
  capability remains **unverified in both directions**.
- Therefore the "-30 dB vs their -18 dB" superiority line stays **unpublishable** for now.
- No absolute "only app" claim is supported by this test, and none should ever be made.

## To close the remaining gap

Testing the two Play-only rivals needs a Google account signed in on the test device — a credential
step the founder must perform; it is not something to automate. Once signed in, the same
`measure.sh` run against each app answers the question in minutes.

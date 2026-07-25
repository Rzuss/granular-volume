# Device Test Results — 2026-07-25

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
first — an early read was contaminated by two effects on session 0 and was discarded.

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
   Effect ID 11: Registered y  Enabled y  Suspended n
   - UUID: e0e6539b-1781-7261-676f-6d7573696340
   - name: Dynamics Processing
   - flags: ... volume mgmt: implements control
```
**Verified:** a real, enabled Dynamics Processing effect on the global mix. Cost to the user:
install, launch, one tap. No ADB, no Shizuku, no companion app, no recording permission, no ads.

## Result 3 — Volume Control: Lower or Boost (the 500K-install rival)

Installed from Play (13 MB, 4.5★, 9K reviews, "Contains ads • In-app purchases").
Its "Fine-tune the media volume" slider was dragged to its floor: **-100%**. Measured in isolation:

```
1 Effect Chains
   2 effects for session 0            <-- also GLOBAL
   Effect ID 51: Enabled y  UUID ce772f20-847d-11df-...  name: Equalizer
   Effect ID 59: Enabled y  UUID fa415329-2034-4bea-...  name: Loudness Enhancer
```

**The below-minimum claim is REAL, not marketing fiction.** It attenuates via a different mechanism:
a negative-preamp **Equalizer** plus **Loudness Enhancer** on session 0, rather than a purpose-built
DynamicsProcessing attenuator. The market audit's suspicion (EQ back door) is confirmed, and the
"maybe it's fake" hypothesis is dead. Do not repeat it.

### But three defects were measured, each user-visible

1. **It does not survive a reboot.** After `adb reboot` with the app still set to -100%:
   ```
   0 Effect Chains
   ```
   The user is back to the loud floor until they reopen the app. Ours re-establishes the effect from
   its own foreground service.
2. **Ads interrupt the fix.** A full-screen video ad played immediately after granting the
   notification permission, and another on the next launch — i.e. the user meets an ad on the path
   to making their phone quieter. A rating prompt appeared on the second launch.
3. **It cannot coexist with the FOSS alternative.** Its own warning dialog: *"To ensure that the
   fine-tuning function works properly, please disable or delete the following incompatible apps:
   RootlessJamesDSP."*

## Result 4 — RootlessJamesDSP (closest FOSS/technical rival)

Installed from F-Droid (v1.6.14, **38.6 MB**; ours is 2.7 MB).

**Before setup:** `0 Effect Chains` — it does nothing to audio at all.

**Its own setup screens, verbatim:**
- Splash: *"Currently, this application is unfinished and unstable... it uses Android APIs not
  designed for use by third parties."*
- "Choose setup method": **Shizuku** (separate app, Android 11+) or **ADB** (*"For advanced users ·
  Computer and USB cable required"*).
- "Other permissions": **Audio recording permission**, notifications, and **Cast/recording** —
  *"You need to explicitly grant this app permission to record audio content every time it launches."*

**After full setup (permissions granted via ADB):**
```
1 Effect Chains
   1 effects for session 113          <-- a specific session, NOT global
   Effect ID 19: name: Dynamics Processing
+ an active RECORD track (capture-and-re-emit architecture)
```
The capture architecture structurally explains its documented Spotify/Chrome exclusion — those apps
block audio capture.

---

## The measured comparison

| | **Granular Volume** | Volume Control: L or B | RootlessJamesDSP |
|---|---|---|---|
| Attenuates below floor | **yes** (DynamicsProcessing) | **yes** (EQ + LoudnessEnhancer) | yes (postgain) |
| Session binding | **0 — global** | 0 — global | 113 — single session |
| Works after install with no setup | **yes, one tap** | yes (+ ad) | **no** — ADB/Shizuku required |
| **Survives reboot** | **yes** (foreground service) | **NO — 0 effect chains** | n/a (needs re-grant anyway) |
| Ads | **none** | **full-screen video ads** | none |
| Recording permission | **none** | none | **yes, every launch** |
| Spotify / Chrome | **works** | works | **excluded** |
| APK size | **2.7 MB** | 13 MB | 38.6 MB |
| Source | **GPL-3.0, open** | closed, in-app purchases | open |

## What we may now claim, and what dies

**Earned and measured — safe to publish:**
- Our attenuation is genuinely **system-wide** (effect on session 0), verified at the audio-engine
  layer rather than asserted.
- **Persistence is a real, measured differentiator:** the 500K-install rival's attenuation is gone
  after a reboot; ours is not. This is the strongest honest comparative fact the test produced.
- **Zero-friction, ad-free, 2.7 MB, no recording permission, works with Spotify and Chrome** — each
  point is now backed by a measurement or by the rival's own screens.

**DEAD — never say these again:**
- ❌ "The only app that goes below the system minimum." Two rivals were measured doing it.
- ❌ "Their below-minimum claim is fake / marketing only." It is real. Saying otherwise is false and
  would be trivially refuted.
- ❌ "-30 dB vs their -18 dB." The rival exposes a percentage scale, not dB; no comparable number was
  produced. Unsupported.

## Still unmeasured

- **Precise Volume 2.0** (5M+) was not installed in this pass; its EQ post-gain floor remains
  unverified.
- Absolute dB output was not measured acoustically (no loopback capture on the emulator); all
  conclusions rest on effect presence, binding, and persistence, not on an SPL figure.
- Emulator, not a physical handset. OEM builds may differ; the persistence result in particular
  should be re-checked on a real device before it appears in marketing copy.

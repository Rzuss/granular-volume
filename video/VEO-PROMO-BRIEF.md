# "Late Night" — YouTube promo, production brief (Veo + real footage hybrid)

Goal: a 35-45s, 16:9 YouTube promo. AI (Veo via Gemini) generates ONLY the
live-action character shots. The app's UI moments come from our REAL captured
footage (video/GranularVolume-promo-horizontal.mp4 and the raw clips), so the
app is shown exactly as it behaves in reality. The audible volume drop is
built in post with our proven ffmpeg audio-ducking pipeline, synced to the
real dial taps. Final cut ends on the existing branded outro card with the
Play Store link in the YouTube description.

Why hybrid, not full-AI: AI models mangle real UI. A studio would shoot the
actor and screen-record the product separately, then composite. Same here.

---

## Locked design blocks (paste VERBATIM into every Veo prompt for consistency)

CHARACTER: "a man in his early 30s with short dark hair and light stubble,
wearing a dark charcoal hoodie"

SET: "a small cozy gaming room at night, lit by soft purple and blue LED
strips, a curved monitor glowing idle in the background, shelves with game
figurines, enclosed and intimate"

STYLE: "cinematic commercial, shallow depth of field, 35mm lens look, soft
film grain, moody but warm color grade with violet-blue accents, smooth
subtle camera movement"

RULES (end of every prompt): "No on-screen text, no subtitles, no captions,
no logos, no visible user interface close-ups. The tablet screen shows only a
dim generic dark audio player, angled slightly away from camera."

The purple-blue lighting is deliberate: it matches the brand violet (#6C63FF)
so the AI shots and our real branded UI inserts grade together seamlessly.

---

## Shot list

Face-continuity is protected by design: shot 1 is side/behind, shot 2 is
hands-focused, only shot 5 is a clear front view. Small drift between takes
won't break the film.

### SHOT 1 — the setup (Veo, 8s, generate with audio ON)

> Cinematic commercial, 8 seconds. A man in his early 30s with short dark
> hair and light stubble, wearing a dark charcoal hoodie, sits deep in a
> gaming chair holding a tablet, seen from a three-quarter back-side angle.
> A small cozy gaming room at night, lit by soft purple and blue LED strips,
> a curved monitor glowing idle in the background, shelves with game
> figurines, enclosed and intimate. A podcast with a calm male voice plays
> clearly from the tablet, slightly too loud for the quiet room. He presses
> the physical volume-down button on the tablet edge twice, glances at the
> screen, and his shoulders tense with mild frustration. Shallow depth of
> field, 35mm lens look, soft film grain, moody but warm color grade with
> violet-blue accents, slow subtle push-in. No on-screen text, no subtitles,
> no captions, no logos, no visible user interface close-ups. The tablet
> screen shows only a dim generic dark audio player, angled slightly away
> from camera.

(We will extract this take's podcast voice audio and reuse it across the
whole timeline for perfect continuity, then duck it in post at the exact
moment the real dial steps down.)

### SHOT 2 — the frustration peak (Veo, 8s)

> Cinematic commercial, 8 seconds. Extreme close-up of a man's thumb pressing
> the volume-down button on the metal edge of a tablet, pressing three times,
> insistent. His face is soft and out of focus in the background, jaw tight.
> He exhales a short frustrated sigh and briefly glances toward a closed door
> as if worried about the noise. A small cozy gaming room at night, lit by
> soft purple and blue LED strips. Shallow depth of field, 35mm lens look,
> soft film grain, moody but warm color grade with violet-blue accents,
> locked-off camera with a slight slow drift. No on-screen text, no
> subtitles, no captions, no logos, no visible user interface close-ups. The
> tablet screen shows only a dim generic dark audio player, angled slightly
> away from camera.

### SHOTS 3-4 — the product (REAL FOOTAGE, no AI)

Cut from video/GranularVolume-promo-horizontal.mp4 (real UI on the branded
16:9 background): dial appears → steps down below the hardware minimum (the
audible drop lands here, ducked in post, step by step with the taps) → drag
to the corner → the dial fades to translucent, exactly as the real app
behaves. ~12-15s total.

### SHOT 5 — the relief (Veo, 8s)

> Cinematic commercial, 8 seconds. Medium front shot of a man in his early
> 30s with short dark hair and light stubble, wearing a dark charcoal hoodie,
> sinking back comfortably into his gaming chair holding a tablet, eyes
> softening, a small satisfied smile, shoulders finally relaxed, he closes
> his eyes for a moment enjoying the quiet. A small cozy gaming room at
> night, lit by soft purple and blue LED strips, a curved monitor glowing
> idle in the background, shelves with game figurines, enclosed and intimate.
> The room feels calm and peaceful now. Shallow depth of field, 35mm lens
> look, soft film grain, moody but warm color grade with violet-blue accents,
> very slow gentle pull-back. No on-screen text, no subtitles, no captions,
> no logos, no visible user interface close-ups.

### OUTRO — existing branded card (real asset)

video/assets/outro_card_v3.png + music resolve. "Volume Control: Quiet Dial —
Quieter than your phone or tablet allows." Play link in the YouTube
description.

---

## How to run (Gemini / Veo)

1. In Gemini, choose video generation (Veo), 16:9 landscape, 8 seconds.
2. Generate 2-3 takes per shot. Pick takes where hands and tablet look
   anatomically clean and the LED lighting reads purple-blue.
3. Shot 1 must have its audio (the podcast voice). Shots 2 and 5 audio is
   nice-to-have; final sound is built in post anyway.
4. Download the highest-quality MP4s and save as:
   video/veo/shot1_a.mp4, shot1_b.mp4, shot2_a.mp4 ... shot5_a.mp4
5. Hand back to Claude for post-production: color-match grade, real UI
   inserts, audio ducking synced to the dial steps, music bed
   (video/assets/music_v3.wav, our original royalty-free track), branded
   outro, loudness-normalized YouTube master (1080p, -14 LUFS).

Post-production checklist (Claude, ffmpeg pipeline):
- [ ] Extract podcast VO stem from best shot 1 take
- [ ] Assemble: shot1 → shot2 → real UI (duck lands here) → shot5 → outro
- [ ] Duck VO by real dB steps in sync with the on-screen dial taps
- [ ] Music bed low under VO, swells slightly at relief + outro
- [ ] Crossfades 12-18 frames, consistent grade across AI/real segments
- [ ] Verify cold: watch full render, check audio drop is unmistakable
- [ ] New YouTube upload (16:9, NOT a Short), custom thumbnail, link swap
      NOT needed (this is an additional content video, the listing keeps the
      current demo)

# Staged reply drafts — ready for the next founder posting blocks

Each draft: verify the thread is live AND open at posting time (iron rule), paste as PLAIN
paragraphs (Samsung lesson: rich-paste trips HTML filters), full disclosure, tracked link, no
review ask. One post per forum per day.

---

## Draft #2 — Spotify Community, "Any way to LOWER lowest volume, Ears ringing" (1344270)

Thread: https://community.spotify.com/t5/forums/searchpage/tab/message?q=1344270 (search "lower lowest volume ears ringing" if ID moved). VERIFY OPEN before posting — old thread, may be locked; if locked, use draft #3 instead.

> Spotify's volume steps ride on top of the system volume, so when Android's lowest step is already
> too loud there isn't much the app itself can do. The lowest step is a fixed fraction of your
> device's maximum output, not a fixed loudness, which is why it feels painfully loud on some
> phones and fine on others.
>
> What actually fixed it for me was lowering the Android media output itself below its minimum,
> system-wide, so it applies to Spotify and everything else. I wrote up how that works here:
> https://rzuss.github.io/granular-volume/?src=spotify
>
> Disclosure: I'm the developer of a small free, open-source app that does this (it attenuates
> below the floor through Android's own audio engine, no root). The write-up explains the mechanism
> either way, so it's useful even without the app.

## Draft #3 — Spotify Community, "Volume steps too big when using Spotify Connect" (5218996)

Post the day AFTER draft #2 (1/day/forum). Angle: steps resolution, not floor.

> The jumps between steps are the system's, not Spotify's — Android maps its volume steps across
> the device's full output range, so on a loud device each step is a big jump. Disabling Absolute
> Volume in Developer options splits phone/headset volume and gives finer steps, which helps the
> "too big" part but doesn't lower the quietest available level.
>
> For the bottom end specifically (quietest step still too loud), the thing that worked for me was
> attenuating the media output below the minimum: https://rzuss.github.io/granular-volume/?src=spotify
>
> Disclosure: I'm the developer of the free/open-source app described there. The mechanism
> (Android's DynamicsProcessing effect) is the point, whichever tool you use.

## Draft #4 — XDA, "Bluetooth Volume Steps too big" (3578370) — NEEDS XDA RE-LOGIN

Approved draft already exists in wave2-posting-kit.md §2 — use it verbatim, but change the guide
link to: https://rzuss.github.io/granular-volume/guide/bluetooth-earbuds-volume-too-loud-lowest.html?src=xda

## Draft #5 — Fairphone forum volume chain (131702)

Hold lifted 07-12. Casual FOSS-friendly tone; F-Droid first (that community prefers it):

> Late reply, but this is a hardware-floor issue rather than a settings one: Android's lowest step
> is a percentage of max output, and Fairphone's speaker tuning makes that percentage loud. Nothing
> below the floor exists in stock settings.
>
> I ended up building a small GPL-3.0 app that attenuates below the minimum through Android's own
> audio framework (no root, no internet permission at all). It's on F-Droid:
> https://f-droid.org/packages/granularvolume.com/ — background write-up:
> https://rzuss.github.io/granular-volume/?src=fairphone
>
> I'm the developer, so grain of salt, and happy to answer anything about how it works.

---

## Two-touch follow-up template (ONLY after a confirmed "it worked" reply, never before)

> Glad it helped. If you feel like it, a short review on Google Play genuinely helps other people
> with sensitive ears find it — it's a tiny solo project and reviews are the only way it surfaces.
> Either way, happy listening.

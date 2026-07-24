# Attribution links — the measurement layer for the 5-week demand test

Every outbound link we hand out carries a `?src=<venue>` tag. This is the ONLY way to answer the
question the whole 5-week test exists to answer: **does the intercept motion produce real stranger
traffic, and from which venue?** Without this, Sep 1 would be decided on vibes — exactly the failure
the war-room named.

## How it is measured (verified 2026-07-24)

- **Guide/site links** carry `?src=venue`. GitHub Pages serves them 200 (tested samsung/xda/spotify/
  fdroid). The hit shows up in **Google Search Console → Pages** and in server-side referrer data.
  This is the primary instrument — web-side, not Play-side.
- **Play links** carry `&referrer=utm_source%3D<venue>...`. Honest limit (war-room, verified): Play
  Console no longer breaks installs down by UTM, and our app has NO internet permission so it cannot
  read the Install Referrer. Play UTM therefore only helps Play *aggregate* a referral as
  "third-party"; per-venue install counts are NOT measurable. **Measure venue yield on the WEB side
  (guide-page visits per src), never as per-venue installs.** Do not promise per-venue install numbers.
- **Rule:** every reply links a `?src=` GUIDE page (which then links the store), not the store
  directly. This keeps the click measurable AND lands the reader on the page that already ranks and
  does the arguing, before the store.

## The link table (copy the exact string per venue)

| Venue | Primary guide link to paste | Notes |
|---|---|---|
| Samsung Community | `https://rzuss.github.io/granular-volume/guide/samsung-galaxy-volume-too-loud-minimum.html?src=samsung` | Galaxy phones/Tab/Buds threads |
| Spotify Community | `https://rzuss.github.io/granular-volume/?src=spotify` | home page; pain is system-wide, not Spotify-specific |
| XDA | `https://rzuss.github.io/granular-volume/guide/bluetooth-earbuds-volume-too-loud-lowest.html?src=xda` | "steps too big" thread |
| Google Android Help | `https://rzuss.github.io/granular-volume/guide/lower-android-volume-below-minimum.html?src=ghelp` | only if a thread verifies open |
| Fairphone forum | `https://rzuss.github.io/granular-volume/?src=fairphone` | hold lifted 07-12 |
| r/tinnitus, r/hyperacusis | `https://rzuss.github.io/granular-volume/use-cases/tinnitus-sensitive-hearing.html?src=hearing` | sensitive-hearing page, NOT the store; medical-space care |
| r/GalaxyBuds, device subs | `https://rzuss.github.io/granular-volume/guide/samsung-galaxy-volume-too-loud-minimum.html?src=reddit-device` | per-model reply |
| Reddit generic / IEM | `https://rzuss.github.io/granular-volume/use-cases/sensitive-iem-headphones.html?src=iem` | anti-boost framing |
| F-Droid forum / r/fossdroid | `https://f-droid.org/packages/granularvolume.com/` | FOSS audience goes straight to F-Droid; no tag (F-Droid strips it) |
| Press pitches | `https://rzuss.github.io/granular-volume/?src=press` | one shared tag for all four outlets |

Store link, only when a reply explicitly needs it AFTER the guide:
`https://play.google.com/store/apps/details?id=granularvolume.com&referrer=utm_source%3D<venue>%26utm_medium%3Dreply`

## How to read it (weekly, fixed weekday)

1. GSC → Performance → **Pages**: which guide URLs got clicks this week.
2. GSC → Performance → **Queries**: what people actually searched to land there (the real TAM signal).
3. Server referrers via GitHub traffic API (`gh api repos/Rzuss/granular-volume/traffic/popular/referrers`)
   for site-to-site clicks.
4. Play Console → **Store listing visitors** + acquisition "third-party referrals" bucket (aggregate only).
5. Log each into the yield table in the war-room verdict. Near-zero everywhere after ~10 replies =
   the Aug 10 checkpoint says stop expanding into marginal threads.

## Baseline captured 2026-07-24 (so we can measure the delta)

- GSC: **24 impressions, 1 click, avg position 9.2, 1 query ("granular volume", brand)** over the
  prior 28 days. Zero non-brand clicks. This is the honest starting line.
- Sitemap: re-submitted 07-24 (prior status "Couldn't fetch" from 07-22; sitemap verified valid,
  200, 9 URLs, referenced in robots.txt — the fetch failure was Google-side/stale).

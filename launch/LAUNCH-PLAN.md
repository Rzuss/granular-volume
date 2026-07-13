# Granular Volume: Launch Plan

The master playbook for taking Granular Volume from closed testing to a strong
public launch on Google Play and F-Droid. Goal: real downloads and genuine good
reviews, driven by reaching the people who actually have this problem.

Positioning anchor: **Quieter than your phone allows.** See store-assets/BRAND.md.

Links used everywhere:
- Play (live): https://play.google.com/store/apps/details?id=granularvolume.com
- Source: https://github.com/Rzuss/granular-volume
- Demo video: https://www.youtube.com/watch?v=DrpbF3r2bhs
- F-Droid (live): https://f-droid.org/en/packages/granularvolume.com/
- Site: https://rzuss.github.io/granular-volume

Copy rule for every public post: no em dashes, nothing that reads as machine
written, always disclose it is your own app.

---

## Where we are and what unblocks launch

The app is in closed testing. Production is gated by Google's requirement:
12 testers opted in for 14 continuous days, then apply for production, then a
review. Public launch (T-0) is the moment production is approved and live.

This plan is built so that at T-0 everything is already prepared and we only
execute, not scramble.

---

## Phase 0: Pre-launch readiness (now to production approval)

Get all of this done while the testing clock runs, so launch day is pure execution.

- [ ] Keep 12+ testers installed for the full 14 days (do not drop below 12). Status 2026-07-02: day 7 of 14, on track.
- [ ] Apply for production the moment the gate clears, then wait for review.
- [x] F-Droid merge request MERGED 2026-07-02 (reproducible build verified, full 9/9 CI green). The actual build+publish to the public F-Droid repo (f-droid.org/packages/granularvolume.com) is still pending on their side as of this writing — check periodically, this is a separate step from the merge and has no fixed timeline.
- [x] Landing page live at GitHub Pages (docs/index.html), now includes explicit tablet-support messaging.
- [x] All launch posts finalized (launch/LAUNCH-POSTS.md).
- [x] Press kit ready (launch/PRESS-KIT.md).
- [x] Review response templates ready (launch/REVIEWS-PLAYBOOK.md).
- [ ] Short demo clips cut per use case (sleep, IEM, library) from the existing video pipeline, for posts that allow media.
- [ ] **NEW, do this now (2026-07-02):** send testers the pre-warm email (see launch/REVIEWS-PLAYBOOK.md "Pre-warm email") explaining honestly that Play blocks writing a review until production exists, and asking for informal text feedback in the meantime instead. This converts an otherwise-wasted ask into real testimonial material and means testers are primed to act fast the moment T-0 hits, instead of forgetting about the app.
- [ ] **Decision needed from you:** consider adding Google Play's in-app review API (native "Rate this app" popup Google shows automatically at a good moment, no need to leave the app). This is the single highest-leverage lever for review *volume* at launch, well above any Reddit post. It requires one code change and one more test-track release before applying for production; it does NOT reset the 14-day tester clock (pushing a new build to the same closed-testing track does not restart tester-opt-in continuity). Tell me if you want this built; if yes, start now so it is tested before T-0.

## Phase 1: Launch day (T-0, production live) — the review blitz

Reviews are won in the first 48 hours, not the first two weeks. Move fast and in
this order. Space the Reddit posts across the day so you can answer comments on
each before the next; everything review-related below fires immediately, same day.

**Within the first hour of going live:**
- [ ] Send testers the "it's live" follow-up email (see launch/REVIEWS-PLAYBOOK.md "The launch ask") with the direct Play link. Testers already know this is coming from the pre-warm email, so this should convert fast.
- [ ] Reply "it's live now" on every existing Reddit thread that already has engagement (the recruitment threads, the Omegv10 review-question thread), with the direct Play link. These threads already have warm, interested readers, this is the fastest review-per-minute channel you have.
- [ ] If F-Droid's public listing is live by T-0, mention it as a trust signal ("now also independently verified and available on F-Droid") in every post below. If not live yet, do not mention it, do not create a false expectation.

**Same day, spaced through the day:**
1. r/androidapps (general discovery, allows app posts).
2. The sensitive hearing communities, one at a time, value first and empathetic.
3. r/headphones or r/HeadphoneAdvice (the IEM angle).
4. A parenting community (the sleep angle).
5. F-Droid forum reply to the existing volume fine-tuner request, once live there.

**Within the same launch week (not the same day, needs its own scheduling):**
6. Product Hunt (a one shot, schedule for 12:01 AM PT on a Tue to Thu).
7. Show HN on Hacker News (the FOSS, no tracking angle).
8. AlternativeTo.net listing (free, 10 minutes, captures people actively comparing volume apps — see launch/LAUNCH-POSTS.md "AlternativeTo.net").
9. A short 15-20 second vertical cut of the existing demo video for TikTok / Instagram Reels / YouTube Shorts, targeting the sleep and ASMR audience specifically. Different reach than every channel above, near-zero extra cost since the footage exists.

**Ongoing, not launch-day-only:**
- [ ] Pin the Play link and the demo video in every thread's first comment.
- [ ] Reply to every review the same day it posts, not within 24 hours. Speed compounds: an early reply on a public review is itself marketing material for the next reader.

## Phase 2: First two weeks (reviews are won here)

The opening rating is decided in the first two weeks. Treat it as the priority.

- [ ] Respond to every review within 24 hours, especially negative ones, using launch/REVIEWS-PLAYBOOK.md. Empathy plus a concrete next step.
- [ ] Triage every bug report fast. A quick fix plus a reply asking the reviewer to re-rate often turns a 1 star into a 5.
- [ ] Watch the crash free rate and the most common complaint. If the rating sits under 4.0, fix the top complaint before chasing more installs.
- [ ] Reply to every comment on the launch posts. Engagement keeps threads visible.
- [ ] Iterate ASO: if a search term keeps appearing in reviews or comments, work it into the short description.

## Phase 3: Sustain (week 3 onward)

- [ ] One value first community post per week, rotating audiences. Answer a real question, mention the app only where it genuinely fits.
- [ ] Publish a short demo clip per use case over time (sleep, IEM, library, office).
- [ ] Send tips to Android news sites (see PRESS-KIT.md). Low hit rate, zero cost.
- [ ] Keep the changelog and the F-Droid build current with each release.

---

## Channels, why each fits, and who acts

| Channel | Why it fits | Who acts |
|---|---|---|
| r/androidapps, r/fossandroid | App discovery, FOSS friendly | You post, I draft |
| r/hyperacusis, r/misophonia, r/tinnitus | The people who feel this pain most, very high word of mouth | You post, I draft, empathy first |
| r/headphones, r/HeadphoneAdvice, r/iems | Step one too loud on sensitive IEMs | You post, I draft |
| Parenting communities | Quiet white noise next to the crib | You post, I draft |
| F-Droid | GPL, no tracking, an open feature request waiting | I prep MR, you confirm |
| Product Hunt | Credibility and backlinks, one shot | You submit, I draft kit |
| Hacker News (Show HN) | The open source, no tracking story | You post, I draft |
| XDA forums | Power users, the original "lower the minimum" threads | You post, I draft |
| Android news tips | Free reach if picked up | You send, I draft blurb |
| AlternativeTo.net | Captures people already comparing volume apps at the exact moment of decision | You submit (10 min), I draft the listing text |
| Hearing aid forums (HearingTracker etc.) | High intent, professionally adjacent audience, near zero competition there | You post, I draft |
| TikTok / Reels / Shorts (short vertical cut) | Sleep and ASMR audiences are very active in short video, a channel none of the above touches | You film/post, I cut the script and shot list from existing footage |

Reddit, Product Hunt, HN, and forums need account actions and sometimes a CAPTCHA,
so you post and I supply exact text. I cannot post to those for you.

---

## Success metrics and decision points

**First 48 hours: check every few hours, not daily.** This is the window that sets
the baseline rating and decides whether momentum builds or stalls.
- Number of ratings (not just average). Target: at least 5-10 genuine ratings within 48 hours from the pre-warmed tester list alone.
- Average rating. Target: hold 4.3 or above.
- Any 1-2 star review: reply same day, not next day.

After the first 48 hours, watch weekly:
- Installs and the install to opt-out ratio.
- Average rating and number of ratings. Target: hold 4.3 or above.
- Crash free users. Target: 99 percent or above.
- Top three review themes.

Decision points:
- Rating under 4.0: stop pushing installs, fix the top complaint first.
- A feature requested by many reviewers: add it, then reply to those reviewers.
- A channel that converts well: do more of it. One that does nothing: drop it.
- **~700 active installs plus a resilient review base (at least 40 real ratings holding 4.3 or above): begin Pro version build.** Raised from the original 100 on purpose: launching Pro on a thin review base means a single bad review or rough patch can sink the app before it has momentum. Invest in marketing first, build a base big enough to absorb a rough review and stay relevant, then move to Pro.

---

## Phase 4: Pro version (triggered at ~700 active installs + a resilient review base)

The full, verified technical plan is in memory (granular-volume-pro-plan) and was
built against the actual codebase. Summary:

Model: freemium, one-time purchase ($2.99), no subscription, no ads ever.

Split (the actual 7-step array in OverlayManager.kt):
- Free: steps 1-2 (the two mildest attenuation steps). Enough to feel the value.
- Pro: steps 3-7 (down to about -30dB). The full range.

F-Droid always gets the fully unlocked build (GPL requirement, and their users
are a word-of-mouth audience, not a revenue audience).

The upgrade UI is a single bottom sheet: "Unlock the full dial. One purchase,
yours forever." Price pulled live from Play Billing, no dark patterns.

Estimated build time once we start: 12 hours of focused development.
I have the full technical spec ready. When active installs are around 700 and
the review base is strong (40+ ratings holding 4.3 or above), tell me and we
start immediately.

---

## The honest blockers

- I cannot post to Reddit, Product Hunt, Hacker News, or forums. I prepare every word; you paste and submit.
- Native file pickers and CAPTCHAs are yours.
- I can draft review responses and can drive the Play Console in the browser to post them, but you make the call on tone for sensitive cases.

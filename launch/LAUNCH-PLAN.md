# Granular Volume: Launch Plan

The master playbook for taking Granular Volume from closed testing to a strong
public launch on Google Play and F-Droid. Goal: real downloads and genuine good
reviews, driven by reaching the people who actually have this problem.

Positioning anchor: **Quieter than your phone allows.** See store-assets/BRAND.md.

Links used everywhere:
- Play (live): https://play.google.com/store/apps/details?id=granularvolume.com
- Source: https://github.com/Rzuss/granular-volume
- Demo video: https://www.youtube.com/watch?v=oQU49OByy10
- F-Droid (after acceptance): https://f-droid.org/packages/granularvolume.com
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

- [ ] Keep 12+ testers installed for the full 14 days (do not drop below 12).
- [ ] Apply for production the moment the gate clears, then wait for review.
- [ ] F-Droid merge request submitted and in their queue (see store-assets/FDROID-SUBMISSION.md). Their review takes weeks, so start early.
- [ ] Landing page live at GitHub Pages (docs/index.html). Gives every post one clean link.
- [ ] All launch posts finalized (launch/LAUNCH-POSTS.md).
- [ ] Press kit ready (launch/PRESS-KIT.md).
- [ ] Review response templates ready (launch/REVIEWS-PLAYBOOK.md).
- [ ] Short demo clips cut per use case (sleep, IEM, library) from the existing video pipeline, for posts that allow media.

## Phase 1: Launch day (T-0, production live)

Post in sequence, not all at once. Space across the day so you can answer comments
on each before the next. Best days are Tuesday to Thursday, mid morning US time.

Order of channels (highest fit first):
1. r/androidapps (general discovery, allows app posts).
2. The sensitive hearing communities, one at a time, value first and empathetic.
3. r/headphones or r/HeadphoneAdvice (the IEM angle).
4. A parenting community (the sleep angle).
5. F-Droid forum reply to the existing volume fine-tuner request, once live there.
6. Product Hunt (a one shot, schedule for 12:01 AM PT on a Tue to Thu).
7. Show HN on Hacker News (the FOSS, no tracking angle).

On launch day also:
- [ ] Ask existing testers to leave an honest review now that it is public. This is allowed. Do not offer anything in return. Honest reviews from real users at launch set the baseline rating.
- [ ] Pin the Play link and the demo video in every thread's first comment.

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

Reddit, Product Hunt, HN, and forums need account actions and sometimes a CAPTCHA,
so you post and I supply exact text. I cannot post to those for you.

---

## Success metrics and decision points

Watch weekly:
- Installs and the install to opt-out ratio.
- Average rating and number of ratings. Target: hold 4.3 or above.
- Crash free users. Target: 99 percent or above.
- Top three review themes.

Decision points:
- Rating under 4.0: stop pushing installs, fix the top complaint first.
- A feature requested by many reviewers: add it, then reply to those reviewers.
- A channel that converts well: do more of it. One that does nothing: drop it.

---

## The honest blockers

- I cannot post to Reddit, Product Hunt, Hacker News, or forums. I prepare every word; you paste and submit.
- Native file pickers and CAPTCHAs are yours.
- I can draft review responses and can drive the Play Console in the browser to post them, but you make the call on tone for sensitive cases.

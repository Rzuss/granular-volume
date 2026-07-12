# Deferred work register

The single, authoritative list of every piece of work that was deliberately postponed,
and the exact event that unblocks each one.

**Why this file exists.** Deferred items were accumulating across several memory notes and
plan docs. The failure mode is not forgetting an item, it is confusing which trigger it
belongs to, and shipping something before the thing that legally or technically has to
accompany it. This file is organized by **trigger**, not by topic, for that reason.

**How to use it.**
1. When a trigger event fires, open this file first, before doing anything else.
2. Work the whole trigger block. Do not cherry pick from it.
3. Verify each item cold, from source (git, Play Console, the live URL), never from memory.
   Items in this file have already been wrong once. Two were found stale on 2026-07-09.
4. Update the status here in the same commit as the work.

Last verified against reality: **2026-07-09**.

---

## Trigger A: Google approves the production application

**🔴 FIRED 2026-07-10/11. The app is LIVE on Google Play, verified on the actual public
listing (`https://play.google.com/store/apps/details?id=granularvolume.com`), not just
"approved".** Production release used the existing vc7 (1.3.0) bundle unchanged (already
14+ days clean in closed testing), full rollout, 177 countries. This is T-0. A1 and A3
below are now actionable, starting now.

| # | Item | Source of truth | Notes |
|---|---|---|---|
| A1 | 🟢 ACTIONABLE NOW — Run the launch day review blitz | `launch/LAUNCH-PLAN.md` Phase 1 | Tester "it's live" email first, then replies on the warm Reddit threads that already have engagement, then the cold channel list. Order matters, it is fastest reviews per minute. Ready-to-paste text already exists in `launch/LAUNCH-POSTS.md` and email templates in `launch/REVIEWS-PLAYBOOK.md`. |
| A2 | ✅ DONE 2026-07-10 — Publish the Terms of Use | `store-assets/terms-of-use.html` (live, 13 sections, no purchase sections) | Published ahead of production approval, using the empirical proof (same day) that store-listing/document edits don't interact with a pending production application. Pushed to `Rzuss/granular-volume-privacy/terms-of-use.html`, footer link added to the landing page, both verified live via cache-busted curl. `store-assets/terms-of-use-PRO-READY.html` remains the reference for the FULL 15-section version (adds sections 5-6, the Pro purchase + minors clauses) — copy that one over at trigger C, do not re-draft from scratch. |
| A2b | 🔴 MANDATORY, FIRST NEW BUILD AFTER PRODUCTION GOES LIVE — Build the clickwrap consent gate | `MainActivity.kt` existing onboarding/permission screen | **Locked 2026-07-10 (user's own words: important to lock in memory to do these 2 items the moment we go to production and ship a new version).** The legal text (`terms-of-use.html`, `privacy-policy.html`) is already live, but it is currently Browsewrap only — nobody has affirmatively agreed to it yet. This is the one piece of the free-tier legal protection that is STILL NOT CLOSED. The moment ANY new version is built and released (whether that's the production launch build itself if a new one is cut, or the very next update after production goes live, e.g. the flavor-split release in Trigger B) — do this in that same build, do not slip it to a later release. Add a short consent line + "Agree & Continue" above the existing permission card; it must be tapped before "GRANT OVERLAY PERMISSION" becomes active. Persist `KEY_TERMS_ACCEPTED_VERSION` (a version number, not a boolean, so a future material change can re-prompt). Does not touch the floating overlay UI at all. |
| A2c | 🔴 MANDATORY, SAME BUILD AS A2b — Add Legal links | `activity_main.xml` / `MainActivity.kt` (no new screen) | **LOCKED 2026-07-10, user's own design call, based on a real screenshot of the current live permission screen.** No new Activity, no About screen. Add a very small, minor "Settings" affordance (a tiny "Settings" text or a "⋮" icon) at the bottom of the EXISTING permission screen — the same screen every user already lands on at first install when granting overlay access. Tapping it reveals/expands, on that same screen, the two links ("Terms of Use", "Privacy Policy", opening the external browser) and — from trigger C onward — the "Restore purchase" button (see runbook step 19, same affordance, same location). This must stay minor and must not restructure the screen's current look (per the user: "זה צריך להיות מאוד מינורי ולא לשנות כמעט דבר ממבנה התוסף"). Resolves both A2c and runbook step 19 together, same UI element. |
| A3 | 🟢 ACTIONABLE NOW — Reply to Omegv10 on Reddit | Drafted, not sent | He reported he could not leave a review. Root cause was the production gate, now resolved since the app is genuinely live. Draft exists; user posts it. |
| A4 | Name consistency sweep | Several | The Play listing already says `Volume Control: Quiet Dial` (renamed 2026-07-09). **✅ Feature graphic fixed 2026-07-12** — regenerated via `gen_marketing_v4.py` (title split "Volume Control:" / "Quiet Dial"), synced to `fastlane/images/featureGraphic.png` + `docs/img/feature.png`, uploaded live in Play Console, verified 7 screenshots byte-identical (unaffected). Still saying "Granular Volume": `README.md`, `docs/index.html` body text, `fastlane/metadata/.../title.txt`, launch posts. Deliberately deferred, low urgency, do not bundle with anything risky. |

---

## Trigger B: production is live and has been stable for a few days

Do not start this while the production application is under review, and do not bundle it with
Trigger A. Isolate infrastructure changes from feature changes.

| # | Item | Source of truth | Notes |
|---|---|---|---|
| B1 | Gradle flavor split, `play` and `fdroid` | `launch/FLAVOR-SPLIT-EXECUTION-PLAN.md` | Dual purpose. It unblocks F-Droid (see B2) and lays the groundwork for Pro Billing. **Absolute invariant: both flavors keep `applicationId = granularvolume.com`, signing untouched, versionCode increasing.** All three catastrophic mistakes are hard rejected by Play Console at upload, never silently shipped. |
| B2 | Unblock F-Droid | GitHub issue #1 | F-Droid refuses to build v1.3.0 because it added `com.google.android.play:review`, a proprietary library. Fix is B1: move the Review API into `src/play/`, ship a no-op stub in `src/fdroid/`, move deps to `playImplementation`. Then bump to vc8 / 1.3.1, tag, and F-Droid rebuilds. Play is completely unaffected by this block. |
| B3 | New F-Droid reference release | `fdroid-reference/sign-reference.sh` | Because the merged metadata has `Binaries:` set, **every** version bump also needs a matching `vX.Y.Z-fdroid2` GitHub Release containing a reproducibly signed `app-release.apk`. A git tag alone is not enough. Recipe: run the `fdroid-repro-build` workflow with the new tag, run the signing script, `gh release create`. |
| B4 | Reply to Licaon_Kter on issue #1 | GitHub issue #1 | Courtesy, so the bot MR is not closed as stale. Could be done earlier than this trigger. External post, needs the user's explicit approval of the exact wording. |

---

## Trigger C: roughly 700 active installs, at least 40 real ratings, average holding 4.3 stars or above

This is a marketing driven phase, expect real weeks, not days. Check the rating count and the
average in Play Console, cold. Do not trigger on install count alone.

### C-legal: the non negotiable bundle

**These ship together with Billing, in the same release window. Never let Billing go live while
the privacy policy still says the app has no internet access and no in-app purchases.**
This is the single highest legal exposure item in the project.

| # | Item | Source of truth | Notes |
|---|---|---|---|
| C1 | Publish the Pro privacy policy | `store-assets/privacy-policy-PRO-READY.html` | Fill in the real "Last updated" date, then copy over `store-assets/privacy-policy.html`. |
| C2 | Push it live | `Rzuss/granular-volume-privacy` | Separate repo. Copy as `index.html`, commit, push, poll `gh api repos/Rzuss/granular-volume-privacy/pages` until `"status":"built"`, then curl the live URL with a cache busting param. This is the step that actually changes what users see. |
| C3 | Update the Data Safety form | Play Console, App content | A formal declaration to Google, separate from the policy text. A mismatch here is Google enforced and can trigger listing removal. |
| C4 | Fix the store listing internet claim | `fastlane/metadata/android/en-US/full_description.txt` | The live text currently says the app has no internet permission. |
| C5 | Fix the one categorically false line | `launch/PRESS-KIT.md:40` | It explicitly asserts "no in app purchases". This becomes a checkable falsehood. |
| C6 | Add the Free clarifying note | full_description, `docs/index.html`, `README.md`, `launch/PRESS-KIT.md` | Short form: "(Free up to -5 dB. More requires a one-time purchase.)" Long form: "Free to use for the first two steps, down to -5 dB. Going further, down to -30 dB, requires a one-time Pro purchase." **Not** on F-Droid copy, **not** retroactively on posts already published. Never use the phrase "Free trial", the free steps are permanent, not a trial. |
| C7 | Add sections 5-6 to the Terms of Use, and the paywall micro-copy | `terms-of-use-PRO-READY.html` sections 5-6 (already drafted) | Section 6 words the age question around **purchases**, never around app use. An age gate on *using* a volume reduction utility protects nothing: COPPA is triggered by collecting data from under 13s and we collect none, and GDPR Article 8 governs consent for processing and we process nothing. Also add the line "By upgrading, you agree to our Terms of Use" (linked) under the Pro purchase button in the paywall bottom sheet. |
| C8 | Batch update the remaining no-network claims | `README.md`, `docs/index.html`, `launch/LAUNCH-POSTS.md`, `store-assets/COMMUNITY-POSTS.md`, `store-assets/BRAND.md`, `store-assets/STORE-LISTING*.md`, `store-assets/PLAY-SUBMISSION-GUIDE.md` | Lower urgency, marketing copy rather than formal legal text. **Do not touch `store-assets/FDROID-SUBMISSION.md` or any F-Droid facing copy.** Those claims stay true forever, that flavor never gets Billing. |
| C9 | Have a real lawyer review the final privacy policy wording | | Recommended before real money changes hands. Everything above is practical risk reduction, not legal advice. |

### C-build: the Pro implementation

**The full ordered execution sequence for everything under trigger C, from "go" to live on
Google Play, is `launch/PRO-ROLLOUT-RUNBOOK.md` (50 steps, with the verification gates that
must pass before each phase). Open that file when the trigger fires. The table below is the
summary of the parts most easily got wrong; the runbook is the order of operations.**

| # | Item | Notes |
|---|---|---|
| C10 | Create the in-app product | Play Console, Monetize, Products. ID exactly `granular_volume_pro`, one time, $2.99, Active. The product ID cannot be changed after creation, so confirm the exact fields with the user immediately before creating. |
| C11 | Implement the gate | In `OverlayManager.setStep()`, first thing, before `currentStep` is assigned. **Free is code index set {5, 6}. Pro gated is {0,1,2,3,4}.** Condition: `if (step <= 4 && !isPro) { showPaywall(); return }`. The array is `STEP_DB = [-30,-25,-20,-15,-10,-5, 0]`, so index 0 is the *quietest*. An earlier note had this inverted, which would have shipped the two near silent steps free and locked normal volume. Also clamp stored attenuation to at least -5f on service start for non Pro, since `updateFromDb()` bypasses the gate. |
| C12 | Implement the grandfather clause **before** shipping the gate | Every version released so far had all 7 steps free. Users who already have the app must not lose that. New `Prefs` key `KEY_GRANDFATHERED_PRO`, set true exactly once on first run of the gated version, only if a pre-existing install is detected. `isPro = billingIsPro \|\| Prefs.isGrandfathered(context)`. **Do not ship the gate without this.** |
| C13 | Acknowledge the purchase within 3 days | Google auto refunds otherwise. Easy silent bug. |
| C14 | Read the price from `ProductDetails` | Never hardcode "$2.99" in the UI, it must show local currency. |
| C15 | Re-verify entitlement via `queryPurchasesAsync` on every app start | Not just a persisted local flag, so refunds and chargebacks are handled. |
| C16 | Keep Billing code out of the public GPL repo | Private companion repo `Rzuss/granular-volume-pro` exists (verified 2026-07-09, contains a scaffold README). Pull into the `play` flavor's source set at build time only. |
| C17 | F-Droid flavor stays fully unlocked, with zero Billing references | GPL requirement, non negotiable. |
| C18 | Update the AlternativeTo.net listing to mention the Pro tier | See the open question below about whether to keep this channel at all. |

---

## No trigger: open questions and standing watches

| # | Item | Who decides | Notes |
|---|---|---|---|
| O0 | Entity separation, personal liability shield | User + accountant | **User's own explicit decision, 2026-07-10, REFINED 2026-07-11 with fuller information:** he will handle this himself, later, once there are steady revenue and a real mass of users — not before. **New fact surfaced 2026-07-11: the developer's home address (Karl Netter 36, Rishon LeZion) is ALREADY publicly shown on the live Play Store listing ("About the developer"), TODAY, not just from Pro launch — because monetization capability is already enabled on the account (Payments profile linked in prep for Pro). Google's own text: "If you choose to earn money on Google Play, your full legal address will be shown publicly. This is to comply with consumer protection laws."** This is not a toggle; it is tied to monetization capability itself, not to an actual live paid product. **User's explicit call after being shown this: acceptable to him as-is until Pro/revenue, at which point he plans to register a company anyway (see below), so the address shown will become the company's registered address instead of his home address.** Do not re-raise this before then. |
| O0b | Tech E&O / cyber insurance | User | Same review, same user decision as O0 — his own call, later, tied to the same steady-revenue-and-user-mass milestone, not a scheduled task. |
| O0c | Binding arbitration clause | Rejected 2026-07-10 | Considered and declined. Mandatory consumer arbitration clauses are void or heavily restricted under the EU Unfair Contract Terms Directive in many member states, and disproportionate in cost/complexity for a $2.99 purchase. Kept the lighter "contact the developer first, informal resolution" line in the Terms instead (section 14 of `terms-of-use-PRO-READY.html`). |
| O1 | Keep or drop the AlternativeTo.net channel | User | Its submission form requires naming specific competitor apps on their site. That is in direct tension with the standing instruction to never mention a competitor anywhere. Unresolved. |
| O2 | Keep the F-Droid reproducible build, or switch to F-Droid signed | User | Reproducible gives a trust badge but requires the per release reference APK dance in B3, forever. F-Droid signed means a git tag alone suffices for every future update. Switching cost is near zero right now because vc4 has close to zero installs. Do not make the external MR change without an explicit go ahead. |
| O3 | Trademark registration for the app name | User | The public repo is GPL-3.0, so anyone may legally fork the code. A trademark stops a forker using the name and brand. This is the only mitigation that actually blocks a clone, and it is not started. |
| O4 | US tax status | Watch | Israel and Taiwan closed. US was still "In review" on Google's side as of 2026-07-08. Not a user action item, just confirm it resolves. |
| O5 | 15 percent service fee enrollment | Verify cold | A notification dated 2026-07-05 says the account group is enrolled. An older note says the banner was never actioned. These conflict. Check Payments profile settings before assuming either. |

---

## Corrections applied 2026-07-09

Two long standing "open" items were found already closed. Recorded here so the same false
alarm is not raised again:

- The Quick Settings tile and the In-App Review API **are committed** (`b847f47`), and there is
  no uncommitted app source. An old note claimed they were sitting as uncommitted WIP.
- The private `Rzuss/granular-volume-pro` repo **does contain** its scaffold README, pushed
  2026-07-05. An old note said the push had been blocked and the state was unknown.

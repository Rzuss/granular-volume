# Pro rollout runbook

The exact, ordered sequence from the moment the user says "go" on Pro, to the moment the
update is live for users on Google Play.

Companion to `launch/DEFERRED-WORK-REGISTER.md` (trigger C). That file says *what* is pending.
This file says *in what order*, and *what must not proceed until what passes*.

Standing rule: every verification below is done **cold, from source** (git, Play Console,
`gh api`, a merged manifest dump, the live URL). Never from memory or from this document.

Legend: **[C]** Claude executes. **[U]** User must act personally. **[!]** Irreversible.

---

## Phase 0: Verification gates. No code is written until all five pass.

| # | Step | Who |
|---|---|---|
| 1 | Verify the trigger itself in Play Console: roughly 700 active installs **and** at least 40 real ratings **and** the average holding at 4.3 or above. Install count alone is not the trigger. | [C] |
| 2 | Verify the flavor split (register trigger B) actually shipped: `productFlavors` present in `build.gradle.kts`, F-Droid serving vc8 or higher, GitHub issue #1 closed. **If it did not ship, stop. The flavor split runs first, as its own isolated release.** Pro must never ride on top of an unproven infrastructure change. | [C] |
| 3 | Verify `terms-of-use.html` exists (created at trigger A). Step 30 needs somewhere to live. If missing, create it before continuing. | [C] |
| 4 | Verify payments readiness: Payments profile shows the linked IBAN with no pending banner, license testing list is active, tax forms resolved (US status was still "In review" as of 2026-07-08). | [C] |
| 5 | Verify a clean tree: `git status` empty, branch from a clean `main`. | [C] |

**Gate 0.** Report the five results to the user. Explicit go or no-go. Do not proceed on a partial pass.

---

## Phase 1: Play Console product creation

| # | Step | Who |
|---|---|---|
| 6 | State the exact fields to the user, then create the in-app product. ID exactly `granular_volume_pro`, one time (not subscription), base price $2.99 USD, status Active. **[!] The product ID can never be changed or reused after creation.** | [C] after [U] confirms |
| 7 | Verify the product shows Active and that Google's auto currency conversion populated other regions. | [C] |

---

## Phase 2: Implementation

Order inside this phase is not cosmetic. The grandfather clause is built **before** the gate,
so the gate can never exist in the tree without it.

| # | Step | Who |
|---|---|---|
| 8 | Branch `feat/pro-billing` off clean `main`. | [C] |
| 9 | Build `BillingManager` and `Entitlements` **in the private repo** `Rzuss/granular-volume-pro`. Billing source never lands in the public GPL repo. | [C] |
| 10 | Wire the private source into the `play` flavor's `src/play/` source set at build time only. | [C] |
| 11 | `Prefs.kt`: add `KEY_IS_PRO` and `KEY_GRANDFATHERED_PRO`. | [C] |
| 12 | **Grandfather clause.** Set `KEY_GRANDFATHERED_PRO` true exactly once, on first run of the gated version, if and only if a pre-existing install is detected (`launch_count > 0`, or any existing attenuation / overlay-position pref). A fresh install is never grandfathered. Every version shipped so far had all 7 steps free; nobody may lose a capability they already had. | [C] |
| 13 | **The gate.** First statement in `OverlayManager.setStep()`, before `currentStep` is assigned: `if (step <= 4 && !isPro) { showPaywall(); return }`. `isPro = billingIsPro \|\| Prefs.isGrandfathered(context)`. The array is `STEP_DB = [-30,-25,-20,-15,-10,-5,0]`, so **index 0 is the quietest**. Free is `{5, 6}`. Pro is `{0,1,2,3,4}`. An earlier spec had this inverted, which would have shipped the two near silent steps free and locked normal volume. | [C] |
| 14 | Clamp on service start: if `!isPro && storedAttenuation < -5f`, reset to `-5f`. Required because `updateFromDb()` sets `currentStep` without passing through the gate, so a refunded or carried-over pref could otherwise land on a locked step. | [C] |
| 15 | Paywall as a Material `BottomSheetDialog` per `store-assets/BRAND.md`. Visible "Not now". No guilt design. | [C] |
| 16 | Read the price from `ProductDetails`. Never hardcode "$2.99" in the UI, it must render in local currency. | [C] |
| 17 | Call `acknowledgePurchase()` on success. Google auto refunds an unacknowledged purchase after 3 days. Easy silent bug. | [C] |
| 18 | Call `queryPurchasesAsync` on every app start, not just a persisted local flag, so refunds and chargebacks revoke correctly. | [C] |
| 19 | Add a manual "Restore purchase" action in About or Settings, for reinstall and new device. | [C] |
| 20 | `fdroid` flavor: zero Billing dependency, zero gate, all 7 steps open, permanently. GPL and F-Droid policy requirement, non negotiable. | [C] |
| 21 | Bump `versionCode`. **`applicationId` stays `granularvolume.com`. Signing config untouched.** Play Console hard rejects violations of these at upload, but do not rely on that. | [C] |

---

## Phase 3: Verification. This is what protects the privacy claim.

| # | Step | Who |
|---|---|---|
| 22 | Build the `play` flavor, then dump the **merged** manifest (`aapt2 dump badging`). Record the exact permission list. Determine empirically whether `INTERNET` is now present alongside `com.android.vending.BILLING`. **Do not assume either way.** This exact technique already disproved an assumption once, for the In-App Review API. **The answer here dictates the privacy policy wording in step 29.** | [C] |
| 23 | Build the `fdroid` flavor, dump its manifest, and grep the dex for Billing classes. Assert zero Billing permission and zero Billing code present. | [C] |
| 24 | Emulator, license tester account: complete a test purchase. Confirm steps 3 through 7 unlock immediately, survive an app restart, and restore after a reinstall. | [C] |
| 25 | Revocation path: simulate a refund, confirm `queryPurchasesAsync` revokes entitlement and the dial clamps back to -5 dB without a crash. | [C] |
| 26 | Grandfather path: install a pre-gate version, use a deep step, upgrade in place. Confirm full access, no paywall, no purchase required. | [C] |
| 27 | Fresh install path: confirm the gate fires at step 3, the paywall renders, and "Not now" dismisses cleanly with no attenuation change. | [C] |
| 28 | Degraded path: confirm no crash when Billing is unavailable (no Play Store, or offline). | [C] |

**Gate 3.** All eight pass, or stop and fix. Nothing below this line runs on a partial pass.

---

## Phase 4: Legal. Prepared before the app can reach a single user.

| # | Step | Who |
|---|---|---|
| 29 | Fill the real date into `store-assets/privacy-policy-PRO-READY.html`, then **reconcile its permission list against the actual manifest from step 22.** If `INTERNET` is not present, do not claim it. The document must describe the binary that ships, not the binary we expected. | [C] |
| 30 | Add the purchase capacity clause to `terms-of-use.html`. Word it around **purchases** (minors can void contracts), never around app use. An age gate on *using* a volume reduction utility protects nothing: COPPA is triggered by collecting data from under 13s and we collect none; GDPR Article 8 governs consent for processing and we process nothing. | [C] |
| 31 | Fix `launch/PRESS-KIT.md:40`, which explicitly asserts "no in app purchases". This becomes a checkable falsehood the moment Billing ships. | [C] |
| 32 | Add the Free clarifying note. Short form: "(Free up to -5 dB. More requires a one-time purchase.)" Long form: "Free to use for the first two steps, down to -5 dB. Going further, down to -30 dB, requires a one-time Pro purchase." Apply to `full_description.txt`, `docs/index.html`, `README.md`, `launch/PRESS-KIT.md`. **Not** on F-Droid copy. **Not** retroactively on already published posts. Never write "Free trial", the free steps are permanent. | [C] |
| 33 | Batch update the remaining "no network access / no tracking" claims across `README.md`, `docs/index.html`, `launch/LAUNCH-POSTS.md`, `store-assets/COMMUNITY-POSTS.md`, `store-assets/BRAND.md`, `store-assets/STORE-LISTING*.md`, `store-assets/PLAY-SUBMISSION-GUIDE.md`. **Do not touch `store-assets/FDROID-SUBMISSION.md`** or any F-Droid facing copy; those claims stay true forever. | [C] |
| 34 | Re-answer the Data Safety questionnaire from scratch. **Do not assume the old "no data collected" answer still holds, and do not assume it changes.** Play Billing is Google's own processing, not our collection, so the honest answer may well be unchanged. Verify against Google's then-current guidance and record the reasoning. A mismatch here is Google enforced and can trigger listing removal. | [C] |
| 35 | Have a real lawyer review the final privacy policy and terms of use. Everything above is practical risk reduction, not legal advice, and this is the point where real money starts changing hands. | [U] |

---

## Phase 5: Publish, in the one correct order.

The ordering problem: the privacy policy must never be live saying "no in-app purchases" while
a Billing build is in users' hands. It also should not claim a purchase feature that does not
exist yet. The window between those two errors is small, so we control it deliberately.

| # | Step | Who |
|---|---|---|
| 36 | **Turn managed publishing ON.** This is the key move. It decouples Google's approval from the go-live moment, so the legal text and the Billing app become live in the same minute rather than whenever Google's review happens to finish. | [C] |
| 37 | Upload the AAB to Internal testing first. Smoke test the real signed artifact. | [C] |
| 38 | Submit the Data Safety changes and the store listing text (release notes: "New: unlock 6 more precision steps with a one-time Pro upgrade. The free version is unchanged, same two steps, same license, no ads."). | [C] |
| 39 | Create the Production release. **Staged rollout at 20 percent.** | [C] |
| 40 | Submit for review. The approved release now sits held by managed publishing. | [C] |
| 41 | **The moment approval lands, before clicking Publish:** push the new `privacy-policy.html` as `index.html` to `Rzuss/granular-volume-privacy`; poll `gh api repos/Rzuss/granular-volume-privacy/pages` until `"status":"built"`; `curl` the live URL with a cache busting query param and confirm the new text is actually served. Do the same for `terms-of-use.html`. Push the repo doc changes from Phase 4. | [C] |
| 42 | Only now, click Publish in Play Console. The legal text is live before any user can buy. | [C] |
| 43 | Hold at 20 percent. Monitor crash free rate, first purchases, and incoming reviews. | [C] |
| 44 | Clean after 24 to 48 hours, go to 50 percent, then 100 percent. | [C] |

**Rollback.** Halting a staged rollout is one click. The grandfather clause means no existing
user can lose a capability. `applicationId` is unchanged, so user preferences carry over intact.

---

## Phase 6: After it is live

| # | Step | Who |
|---|---|---|
| 45 | F-Droid: bump the `fdroid` flavor version, tag it, create the matching `vX.Y.Z-fdroid2` reference release via `fdroid-reference/sign-reference.sh`. Confirm F-Droid rebuilds a fully unlocked binary with zero Billing code. | [C] |
| 46 | Confirm the first real purchase lands: Monetize, Financial reports. Check the roughly 15 percent Small Business Program fee tier and the payout schedule. | [C] |
| 47 | Update the AlternativeTo.net listing to mention the Pro tier, **if** that channel is kept. See open question O1 in the register: its submission form requires naming competitor apps, which conflicts with the standing no-competitor-mention rule. | [U] decides |
| 48 | Press follow up, second pitch to the same four outlets, per `launch/GTM-3-PHASE-PLAN.md` Phase 3. | [C] drafts, [U] sends |
| 49 | Reply to every review, same day. A one time purchase makes review sentiment more consequential, not less. | [C] drafts |
| 50 | Update every status in `launch/DEFERRED-WORK-REGISTER.md`, in the same commit as this work. | [C] |

---

## Honest timeline

Phase 0 through 1: hours. Phase 2 through 3: roughly 12 hours of focused development, plus
verification. Phase 4: hours, plus however long the lawyer review takes. Phase 5: Google's
review is typically days. Phase 6: F-Droid moves on its own cycle, hours to days.

The long poles are the lawyer review in step 35 and Google's review in step 40. Neither is
under our control, and neither should be skipped to save time.

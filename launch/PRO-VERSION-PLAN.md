# Granular Volume: Pro Version Plan

**Trigger:** 100 active installs on Google Play.
**Goal:** Convert existing users and all new installs into a sustainable revenue stream.
**Model:** Freemium, one-time purchase (no subscription).

---

## The core split

| | Free | Pro |
|---|---|---|
| Volume steps below minimum | 3 coarse steps | Full granular range (20+ fine steps) |
| Floating dial | Yes | Yes |
| Start on boot | Yes | Yes |
| Price | Free forever | One-time purchase ~$1.99 |

**Why this split works:**
The free user experiences the concept (quieter than minimum is real and useful)
but hits a wall when they want precision. That wall is the natural upgrade trigger.
The paid user gets what they originally wanted when they searched for the app.

---

## What needs to be built (technical checklist)

### 1. Product flavors in Gradle
Two build variants: `free` and `fdroid`.
- `free`: Play Store build, billing enabled, 3 steps in free tier
- `fdroid`: F-Droid build, all steps unlocked (required by GPL — source is public,
  so gating makes no sense there; F-Droid users compile from source anyway)

In `android/app/build.gradle.kts`:
```kotlin
flavorDimensions += "distribution"
productFlavors {
    create("free") {
        dimension = "distribution"
        buildConfigField("boolean", "BILLING_ENABLED", "true")
        buildConfigField("int", "FREE_STEP_LIMIT", "3")
    }
    create("fdroid") {
        dimension = "distribution"
        buildConfigField("boolean", "BILLING_ENABLED", "false")
        buildConfigField("int", "FREE_STEP_LIMIT", "999")
    }
}
```

### 2. Google Play Billing Library
Add to `android/app/build.gradle.kts`:
```kotlin
implementation("com.android.billingclient:billing-ktx:7.0.0")
```

New file: `billing/BillingManager.kt`
- Connect to Play Billing on app start
- Query whether SKU `granular_volume_pro` is purchased
- Expose a `isPro: StateFlow<Boolean>` consumed by the rest of the app
- Handle purchase flow, acknowledge purchases
- Cache purchase state in SharedPreferences so it survives offline

### 3. Step limiter in AudioController
In `AudioController.kt`, read `BuildConfig.FREE_STEP_LIMIT` and the `isPro` state.
When free and not pro: cap the available attenuation steps at 3.
When pro (or fdroid build): no cap.

### 4. UI changes

**Dial overlay:**
- Free tier: dial shows 3 positions, remaining range is grayed out with a lock icon
- Tapping the locked range opens the upgrade sheet

**Upgrade bottom sheet (new screen):**
- Single screen, no navigation stack
- Headline: "Unlock the full dial"
- Body: "20 fine steps instead of 3. One purchase, no subscription."
- Price pulled live from Play Billing (show real price, never hardcode)
- One button: "Unlock for [price]"
- Small text: "One-time purchase. Yours forever."
- No dark patterns, no fake urgency

**Post-purchase:**
- Instant unlock, no restart needed
- Brief confirmation: "All steps unlocked."

### 5. Play Console setup
- Go to Monetize > Products > In-app products
- Create product ID: `granular_volume_pro`
- Type: one-time (not subscription)
- Price: $1.99 USD (adjust per market)
- Activate the product before releasing the Pro build

### 6. New CI workflow
The existing CI builds `assembleDebug`. Add a `free` and `fdroid` release target.
The F-Droid build (`fdroid` flavor) is what goes into the `fdroid/` metadata recipe.
The Play Store build (`free` flavor release) is signed with the keystore.

---

## ASO changes when Pro launches

**App name stays the same.** Do not rename to "Granular Volume Pro" — that splits
reviews and confuses returning users.

**Short description update:**
"Make any app quieter than your phone's minimum. Free to try, unlock the full dial."

**What NOT to do:**
- Do not create a separate "Pro" app. One app, one listing, freemium inside.
- Do not change the package name. Ever.
- Do not remove the free experience. The free tier must remain genuinely useful.

---

## F-Droid handling

F-Droid gets the `fdroid` flavor which is fully unlocked. This is correct:
1. GPL requires the source to be available, so gating in F-Droid is pointless.
2. F-Droid users are a small, privacy-first audience. They drive word of mouth,
   not revenue.
3. Play Store users are the revenue audience. They do not cross-shop on F-Droid.

The `fdroid/granularvolume.com.yml` metadata recipe will need updating to point
to the `fdroid` flavor when flavors are added:
```yaml
gradle:
  - fdroid
```

---

## Development estimate

| Task | Effort |
|---|---|
| Gradle product flavors | 1 hour |
| BillingManager.kt | 3-4 hours |
| Step limiter in AudioController | 1 hour |
| Dial UI changes (lock overlay) | 2-3 hours |
| Upgrade bottom sheet | 2 hours |
| Testing purchase flow end to end | 2 hours |
| Play Console product setup | 30 minutes |
| **Total** | **~12 hours** |

This is a focused build. No redesign, no new screens beyond the upgrade sheet.

---

## Launch sequence for Pro

1. Reach 100 active installs. Check Play Console > Statistics > Active installs.
2. Build the Pro version locally, test with a Play Billing test account.
3. Update the short description in Play Console.
4. Roll out as a staged release (10% first, watch crash rate and reviews).
5. Reply to every early Pro review within 24 hours.
6. Update community posts: "Pro version now available" as a reply to the original
   launch threads, not a new post.

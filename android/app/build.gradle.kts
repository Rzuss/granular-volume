import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Load local.properties for signing credentials (never committed to source control).
val localProps = Properties().also { props ->
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { props.load(it) }
}

// Release signing is only configured when a keystore path is supplied locally.
// Without it (CI, fresh clones, contributors) the project still builds debug
// artifacts cleanly; bundleRelease simply produces an unsigned bundle.
val releaseKeystorePath = localProps.getProperty("keystore.path", "")
val hasReleaseSigning = releaseKeystorePath.isNotEmpty()

android {
    namespace = "com.granularvolume"
    compileSdk = 36

    // F-Droid requires this: AGP otherwise embeds a Google-encrypted dependency
    // metadata block in the APK signing block (fdroiddata issue, 2026-09-02).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    defaultConfig {
        applicationId = "granularvolume.com"
        minSdk = 28
        targetSdk = 36          // Play requires API 36 (Android 16) for updates from Aug 31, 2026
        versionCode = 48
        versionName = "1.7.0"
        // Grandfather cutoff, 2026-10-01T00:00:00Z (1.5.1, mock-trial order item 40: the
        // 1.5.0 rollout reached 100% on 2026-09-23 10:28 UTC, plus seven days, rounded up
        // to the next UTC midnight). An UPDATE whose first install predates this is
        // grandfathered; a fresh install never is. Overridable ONLY for the harness
        // (-PgvGateCutoffMs=...); a release build must be made without the property.
        buildConfigField(
            "long", "GATE_CUTOFF_MS",
            "${project.findProperty("gvGateCutoffMs") ?: "1790812800000"}L"
        )
    }

    // Distribution flavors: "play" keeps the Play-only in-app review prompt;
    // "fdroid" carries zero com.google.android.play code so the F-Droid build
    // reproduces cleanly (see https://github.com/Rzuss/granular-volume/issues/1).
    // Neither flavor sets applicationId/applicationIdSuffix — both must resolve
    // to the exact same applicationId as defaultConfig above.
    flavorDimensions += "distribution"
    productFlavors {
        create("play")   { dimension = "distribution" }
        create("fdroid") { dimension = "distribution" }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = localProps.getProperty("keystore.storePassword", "")
                keyAlias = localProps.getProperty("keystore.keyAlias", "granularvolume")
                keyPassword = localProps.getProperty("keystore.keyPassword", "")
                // Declared explicitly rather than left to AGP defaults. Caught by the cold
                // check before 1.4.0: a Gradle-signed build came out v2-only, while every
                // artifact F-Droid has accepted so far was v3-signed (they were signed by
                // hand with apksigner per the release runbook). Requiring both removes the
                // difference between the two paths, so the reference APK is valid whichever
                // way it was produced.
                enableV1Signing = false   // minSdk 28; v1 adds nothing and slows verification
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Use the release signing config only when it was configured above.
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            // Test product only. Makes the installed build identifiable at a glance during
            // device testing, so a stale install can never be mistaken for the current one.
            // Never reaches a release artifact — this block is the debug build type.
            versionNameSuffix = "-test"
        }
    }

    // 1.6.4: only the purchase, access, trial and notice strings are translated (six languages);
    // everything else falls back to English on purpose, so the "incomplete translation" check
    // would fail every release build over a deliberate choice.
    lint {
        disable += "MissingTranslation"
    }

    buildFeatures {
        // We use classic findViewById on XML layouts — no Compose, no view binding.
        viewBinding = false
        // 1.5.0: BuildConfig.FLAVOR gates the tip-jar card to the play flavor.
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // In-app review prompt. Play-only: F-Droid forbids this proprietary library
    // (see issue #1), so it's scoped to the "play" flavor's own classpath via
    // playImplementation rather than implementation. Verified: adds no manifest
    // permissions (binds to the Play Store app via a local service connection,
    // no direct network access from this app's own process) — confirmed via a
    // merged-manifest dump of a real build plus an on-device run before adding
    // this to the shipping code.
    "playImplementation"("com.google.android.play:review:2.0.2")
    "playImplementation"("com.google.android.play:review-ktx:2.0.2")
    // 1.6.0: Play Billing for the in-app purchase (the separate key app remains a second route).
    // The billing library itself adds only com.android.vending.BILLING; INTERNET and
    // ACCESS_NETWORK_STATE would arrive transitively through Google's datatransport telemetry.
    // Billing wraps every telemetry call in catch (Throwable) (verified by disassembly of 9.1.0,
    // classes zzdt/zzds), so the telemetry is excluded outright: no upload code, no job service,
    // no network permission. The play manifest removes both permissions again, belt and braces.
    "playImplementation"("com.android.billingclient:billing:9.1.0") {
        exclude(group = "com.google.android.datatransport")
    }
    // 1.7.0: "a newer version is ready". Play-only, like the two libraries above: it asks the
    // Play Store app on the device over a local service connection, and Play does the download.
    // It adds no manifest permission (proven on the merged manifest and on the built APK of
    // every release, see the release checklist); the play manifest removes INTERNET regardless.
    "playImplementation"("com.google.android.play:app-update:2.1.0")
}

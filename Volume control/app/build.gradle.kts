import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Load local.properties for signing credentials (never committed to source control).
val localProps = Properties().also { props ->
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { props.load(it) }
}

android {
    namespace = "com.granularvolume"
    compileSdk = 35

    defaultConfig {
        applicationId = "granularvolume.com"
        minSdk = 28
        targetSdk = 35          // Play requires API 35 (Android 15) for new apps in 2025+
        versionCode = 4
        versionName = "1.1.2"
    }

    signingConfigs {
        create("release") {
            storeFile = file(localProps.getProperty("keystore.path", ""))
            storePassword = localProps.getProperty("keystore.storePassword", "")
            keyAlias = localProps.getProperty("keystore.keyAlias", "granularvolume")
            keyPassword = localProps.getProperty("keystore.keyPassword", "")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    buildFeatures {
        // We use classic findViewById on XML layouts — no Compose, no view binding.
        viewBinding = false
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
}

package com.granularvolume.util

/**
 * Play build: the same open-source components as the F-Droid build, plus the proprietary Google
 * libraries.
 *
 * They are deliberately listed separately rather than folded into the open-source list, because
 * they are **not** open source. Calling them open source on a licences screen would be exactly
 * the kind of small false statement this screen exists to prevent.
 *
 * 1.7.0: the list is complete again. The billing library (in the build since 1.6.0) was missing
 * from it, and the in-app update library is new. Licence names are the ones each library's own
 * published POM declares.
 */
object LicenseInfo {

    /** name to licence, shown in order. */
    val openSource: List<Pair<String, String>> = listOf(
        "AndroidX Core KTX" to "The Android Open Source Project, Apache License 2.0",
        "AndroidX AppCompat" to "The Android Open Source Project, Apache License 2.0",
        "AndroidX Lifecycle Service" to "The Android Open Source Project, Apache License 2.0",
        "Material Components for Android" to "Google LLC, Apache License 2.0",
        "Kotlin Coroutines for Android" to "JetBrains s.r.o., Apache License 2.0",
        "Kotlin Standard Library" to "JetBrains s.r.o. and Kotlin Programming Language contributors, Apache License 2.0",
    )

    val proprietary: List<Pair<String, String>> = listOf(
        "Google Play In-App Review library" to
            "Google LLC. Not open source: distributed under the Play Core Software Development " +
            "Kit Terms of Service. Used only to display Google's own rate-this-app dialog, and " +
            "absent from the F-Droid build.",
        "Google Play Billing Library" to
            "Google LLC. Not open source: distributed under the Android Software Development " +
            "Kit License. Used only to open Google Play's own purchase window and to read " +
            "whether this Google account has bought the unlock. Built without its " +
            "usage-reporting part, and absent from the F-Droid build.",
        "Google Play In-App Update library" to
            "Google LLC. Not open source: distributed under the Play Core Software Development " +
            "Kit Terms of Service. Used only to ask the Play Store app on this device whether " +
            "a newer version exists and to open Google's own update screen. Absent from the " +
            "F-Droid build.",
        "Google Play services client libraries" to
            "Google LLC. Not open source: distributed under the Android Software Development " +
            "Kit License. Support code the three libraries above depend on. Absent from the " +
            "F-Droid build.",
    )
}

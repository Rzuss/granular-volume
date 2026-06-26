# Keep all AudioEffect framework classes — accessed reflectively / via JNI.
-keep class android.media.audiofx.** { *; }

# Keep our own classes (Service, receivers, etc.) intact for the framework.
-keepclassmembers class com.granularvolume.** { *; }

# Framework audio classes are part of the platform — silence missing-class warnings.
-dontwarn android.media.audiofx.**

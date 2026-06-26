# Publishing Granular Volume on F-Droid

F-Droid is a second, free distribution channel and a strong fit for this app:
GPL-3.0, no ads, no tracking, no Google Play Services, no proprietary
dependencies. The FOSS and privacy minded audience lives there, and the paid
closed source competitors cannot reach it.

## Eligibility check (all pass)

- License: GPL-3.0, listed in LICENSE at the repo root.
- No proprietary libraries: only androidx and com.google.android.material,
  which are open source and allowed. No Firebase, no Play Services, no analytics.
- No tracking, no ads, no network calls.
- Builds from source with the Gradle wrapper.
- Metadata and graphics are in this repo under fastlane/metadata/android/en-US,
  which F-Droid reads automatically.

## How submission works

F-Droid does not take an upload. You submit a metadata recipe to the fdroiddata
repository and their build server compiles the app from this Git repo.

1. Tag a release in Git so F-Droid has a versioned commit to build:
   git tag -a v1.1.2 -m "Granular Volume 1.1.2" && git push origin v1.1.2

2. Fork https://gitlab.com/fdroid/fdroiddata

3. Add a metadata file at metadata/granularvolume.com.yml along these lines:

   Categories:
     - Multimedia
   License: GPL-3.0-only
   SourceCode: https://github.com/Rzuss/granular-volume
   IssueTracker: https://github.com/Rzuss/granular-volume/issues
   RepoType: git
   Repo: https://github.com/Rzuss/granular-volume.git

   Builds:
     - versionName: 1.1.2
       versionCode: 4
       commit: v1.1.2
       subdir: Volume control/app
       gradle:
         - yes

   AutoUpdateMode: Version v%v
   UpdateCheckMode: Tags
   CurrentVersion: 1.1.2
   CurrentVersionCode: 4

4. Open a merge request. The F-Droid team reviews, the build server compiles
   from source, and the app appears in the F-Droid client.

Note: the build subdir is "Volume control/app". If F-Droid tooling struggles
with the space in the path, the cleanest fix is to move the Android project to
the repo root in a future commit. Not required, but it simplifies the recipe.

## After it lands

F-Droid signs with its own key, so it is a separate install from the Play
build (different signature). That is expected and fine. Link both from the
README and the project site.

# F-Droid release runbook

**Every time a `vX.Y.Z` tag is pushed, F-Droid needs a matching `vX.Y.Z-fdroid` GitHub Release
carrying a signed reference APK. If that release is missing, F-Droid's build fails silently on their
side and our new version simply never ships.** This happened with 1.3.3: the tag went up on 07-22,
and on 07-23 an F-Droid maintainer had to open [issue #3](https://github.com/Rzuss/granular-volume/issues/3)
asking for the APK. Two versions were stuck for roughly fifteen hours.

## Why the step exists

`metadata/granularvolume.com.yml` in fdroiddata contains:

```yaml
Binaries:
  https://github.com/Rzuss/granular-volume/releases/download/v%v-fdroid/app-fdroid-release.apk
AllowedAPKSigningKeys: 01cc6025ffce326c32324a3e441355dbdc3934abd2d656bed1b2ec62ff0cced8
AutoUpdateMode: Version
UpdateCheckMode: Tags ^v[\d.]+$
```

F-Droid builds from source, then downloads that binary and verifies its own build reproduces ours.
No binary at that exact URL means no verification, which means no publish. The URL is literal:
`%v` is the versionName, the tag must be `v<versionName>-fdroid`, and the asset filename must be
exactly `app-fdroid-release.apk`.

## Procedure

Everything below was executed and verified for 1.3.3 on 2026-07-23.

### 1. Confirm CI built the right commit

Pushing a `vX.Y.Z` tag triggers `.github/workflows/fdroid-repro-build.yml`, which runs
`assembleFdroidRelease` on ubuntu-latest and uploads `fdroid-release-unsigned`.

```bash
git rev-parse vX.Y.Z^{}                                   # note: ^{} , a bare tag name gives the tag object
gh run list --repo Rzuss/granular-volume --limit 5
gh api repos/Rzuss/granular-volume/actions/runs/<RUN_ID> --jq '.head_sha'
```

The commit must match three things: the tag, the CI run's `head_sha`, and the `commit:` field in the
fdroiddata bot MR. Check the MR at
`https://gitlab.com/api/v4/projects/36528/merge_requests?search=granularvolume&state=all`.

### 2. Download the unsigned APK

```bash
mkdir -p .fdroid-release && cd .fdroid-release
gh run download <RUN_ID> --repo Rzuss/granular-volume --name fdroid-release-unsigned --dir .
```

Use the CI artifact, not a local build. It is produced on Linux with the same layout F-Droid's server
uses, which is the whole point of the reproducibility comparison.

### 3. Verify before signing

Never sign something unverified.

```bash
BT="D:/Android/sdk/build-tools/36.0.0"
"$BT/aapt.exe" dump badging app-fdroid-release-unsigned.apk | head -1
```

Expect the right `versionCode`, `versionName`, `granularvolume.com`, and no INTERNET permission.
Then confirm the fdroid flavor is actually free of proprietary code:

```bash
python - <<'EOF'
import zipfile, re
z = zipfile.ZipFile("app-fdroid-release-unsigned.apk")
blob = z.read("classes.dex")
for p in (rb"com/google/android/play", rb"com/google/android/gms", rb"com/google/firebase"):
    print(p, len(re.findall(p, blob)))
EOF
```

All three must be `0`. This is the condition that caused issue #1 in the first place.

### 4. Sign

```bash
KS="D:/Claude Projects/claude/Volume control/granularvolume-release.jks"
PW=$(grep '^keystore.storePassword=' android/local.properties | cut -d= -f2-)
"$BT/apksigner.bat" sign --ks "$KS" --ks-key-alias granularvolume \
  --ks-pass "pass:$PW" --key-pass "pass:$PW" \
  --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true \
  --out app-fdroid-release.apk app-fdroid-release-unsigned.apk
```

The output filename must be `app-fdroid-release.apk`, matching the `Binaries:` URL.

### 5. Verify the signature

```bash
"$BT/apksigner.bat" verify --verbose --print-certs --min-sdk-version 24 app-fdroid-release.apk
"$BT/zipalign.exe" -c -v 4 app-fdroid-release.apk | tail -2
```

The certificate SHA-256 must read `01cc6025ffce326c32324a3e441355dbdc3934abd2d656bed1b2ec62ff0cced8`.
Anything else and F-Droid will reject it against `AllowedAPKSigningKeys`.

Pass `--min-sdk-version 24` deliberately. Without it, apksigner verifies via v3 alone and reports
`v2: false` even though the v2 block is present, which looks like a regression and is not one.

### 6. Publish

```bash
gh release create vX.Y.Z-fdroid app-fdroid-release.apk \
  --repo Rzuss/granular-volume \
  --target <COMMIT_SHA> \
  --title "vX.Y.Z (F-Droid reproducible build reference)" \
  --notes-file notes.md
```

### 7. Cold-verify the URL F-Droid will actually hit

Do not skip this. It is the only check that proves the whole chain works.

```bash
URL="https://github.com/Rzuss/granular-volume/releases/download/vX.Y.Z-fdroid/app-fdroid-release.apk"
curl -sIL -o /dev/null -w "%{http_code}\n" "$URL"     # must be 200
curl -sL -o dl.apk "$URL" && sha256sum dl.apk         # must equal the signed APK's hash
```

### 8. Reply on the issue or MR

If a maintainer opened an issue, answer it with the URL, the commit, the certificate fingerprint and
the flavor-cleanliness result, then say it is ready for a re-run.

## Gotchas found the hard way

- `git rev-parse vX.Y.Z` on an annotated tag returns the **tag object** SHA, not the commit. Use `^{}`.
- A signed APK can come out byte-identical in size to a previous release. That is the signing block
  being padded to a 4096 boundary, not a mixed-up file. Confirm identity with `aapt dump badging`.
- Version numbers can legitimately skip. 1.3.2 (versionCode 9) was Play-only and never tagged, so
  F-Droid goes 8 to 10. Say so explicitly when replying, or it looks like a mistake.
- `AutoUpdateMode: Version` means the bot only ever tracks the newest matching tag. Intermediate
  versions are skipped, not queued.

## Open decision: automate the signing in CI?

The build is automated; the signing and publishing are not, which is exactly how 1.3.3 slipped.
Full automation means putting the release keystore and its password into GitHub Actions secrets.
That removes the human step but puts the app's signing identity inside CI, where a compromised
workflow or a malicious dependency could sign anything as us. For a single-maintainer FOSS app with
a live Play listing, that is a real risk and not obviously worth it.

The guard workflow (`fdroid-release-guard.yml`) is the middle path: it does not hold the key, it just
fails loudly within a day if a version tag is missing its `-fdroid` release.

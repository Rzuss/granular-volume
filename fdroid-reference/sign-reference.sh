#!/bin/bash
# Sign the F-Droid reproducible reference APK for v1.3.1 (fdroid flavor).
#
# You run this (not the assistant) because it uses your keystore password.
# It reads the keystore path + passwords straight from android/local.properties
# (the same file gradle uses), so you never type a password. Nothing is printed
# except the final certificate fingerprint, which you check against the value
# F-Droid expects.
#
# Usage (in Git Bash), from the repo root:
#   bash fdroid-reference/sign-reference.sh
#
set -e
export MSYS_NO_PATHCONV=1

BT="/c/Android/sdk/build-tools/35.0.0"
DIR="fdroid-reference"
UNSIGNED="$DIR/app-fdroid-release-unsigned.apk"
ALIGNED="$DIR/app-fdroid-release-aligned.apk"
SIGNED="$DIR/app-fdroid-release.apk"
PROPS="android/local.properties"

# --- read keystore config from local.properties (Java-properties un-escaping) ---
getprop () {
  # $1 = key name; strips "key=", then un-escapes \: and \\ that Java uses
  sed -n "s/^$1=//p" "$PROPS" | sed 's/\\:/:/g; s/\\\\/\\/g'
}
KS_PATH="$(getprop keystore.path)"
KS_STORE_PW="$(getprop keystore.storePassword)"
KS_ALIAS="$(getprop keystore.keyAlias)"
KS_KEY_PW="$(getprop keystore.keyPassword)"

if [ -z "$KS_PATH" ] || [ -z "$KS_STORE_PW" ]; then
  echo "ERROR: could not read keystore.path / keystore.storePassword from $PROPS"
  exit 1
fi

# --- step 1: align (page-size-4; harmless no-op for this .so-free app) ---
"$BT/zipalign.exe" -f -p 4 "$UNSIGNED" "$ALIGNED"

# --- step 2: sign, preserving alignment so it matches F-Droid's server build ---
# v2-only signature, --alignment-preserved is the fix for build-tools >= 35.
"$BT/apksigner.bat" sign \
  --v1-signing-enabled false \
  --v2-signing-enabled true \
  --v3-signing-enabled false \
  --v4-signing-enabled false \
  --alignment-preserved true \
  --ks "$KS_PATH" \
  --ks-pass "pass:$KS_STORE_PW" \
  --ks-key-alias "$KS_ALIAS" \
  --key-pass "pass:$KS_KEY_PW" \
  --out "$SIGNED" \
  "$ALIGNED"

rm -f "$ALIGNED"

echo ""
echo "=== SIGNED: $SIGNED ==="
echo "=== Certificate fingerprint (must equal the F-Droid AllowedAPKSigningKeys value) ==="
"$BT/apksigner.bat" verify --print-certs "$SIGNED" | grep -i "certificate SHA-256"
echo ""
echo "Expected: 01cc6025ffce326c32324a3e441355dbdc3934abd2d656bed1b2ec62ff0cced8"
echo "If the SHA-256 above matches, tell the assistant and it will create the GitHub Release."

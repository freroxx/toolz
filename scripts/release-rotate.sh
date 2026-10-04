#!/usr/bin/env bash
# Release signing with v3.1 key rotation (debug -> release).
# WHY: Gradle signs with the release key only. Without the rotation lineage,
# devices holding older debug-signed installs reject updates with
# INSTALL_FAILED_UPDATE_INCOMPATIBLE. Run this AFTER every assembleRelease
# and BEFORE publishing APKs. Never publish the Gradle raw output directly.
#
# Requires: ~/.android/toolz-release.keystore, ~/.config/toolz/release-store.pass,
# ~/.config/toolz/lineage (created once via `apksigner rotate`).
# Usage: ./scripts/release-rotate.sh [apk-dir]
set -euo pipefail
APK_DIR="${1:-app/build/outputs/apk/release}"
BT="${ANDROID_HOME:-$HOME/Android/Sdk}/build-tools/36.0.0/apksigner"
CFG="$HOME/.config/toolz"
if [ ! -f "$CFG/lineage" ]; then echo "missing lineage; create it with apksigner rotate first" >&2; exit 1; fi
printf 'android\nandroid\n' > "$CFG/oldpass.tmp"
P=$(cat "$CFG/release-store.pass")
printf '%s\n%s\n' "$P" "$P" > "$CFG/newpass.tmp"
chmod 600 "$CFG/oldpass.tmp" "$CFG/newpass.tmp"
trap 'rm -f "$CFG/oldpass.tmp" "$CFG/newpass.tmp"' EXIT
for a in "$APK_DIR"/app-*-release.apk; do
  case "$a" in *-rot.apk) continue;; esac
  "$BT" sign \
    --ks "$HOME/.android/debug.keystore" --ks-key-alias androiddebugkey \
    --ks-pass "file:$CFG/oldpass.tmp" --key-pass "file:$CFG/oldpass.tmp" \
    --next-signer --ks "$HOME/.android/toolz-release.keystore" --ks-key-alias toolz-release \
    --ks-pass "file:$CFG/newpass.tmp" --key-pass "file:$CFG/newpass.tmp" \
    --lineage "$CFG/lineage" --rotation-min-sdk-version 33 \
    --out "${a%.apk}-rot.apk" "$a"
  mv "${a%.apk}-rot.apk" "$a"
  echo "rotated: $a"
  "$BT" verify --print-certs "$a" | grep -c 'SHA-256 digest' | xargs -I{} echo "  certs={} (expect 2)"
done

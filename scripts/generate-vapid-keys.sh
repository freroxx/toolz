#!/usr/bin/env bash
# Generates a VAPID (RFC 8292) P-256 keypair for Whisper push.
#
# Run to (re)seed the UnifiedPush push credentials:
#   1. PUBLIC half  → local.properties:
#        WHISPER_VAPID_PUBLIC_KEY=<k value>
#      (or commit it as the default in app/build.gradle.kts — it is a public key)
#   2. PRIVATE half → whisper-push-send Edge Function env:
#        WHISPER_VAPID_JWK={"kty":"EC","crv":"P-256","x":...,"y":...,"d":...}
#
# Rotating: update BOTH sides the same day (stale keys fail VAPID validation on
# the push endpoint; already-registered devices re-register on next app start).
set -euo pipefail

PRIV_PEM=$(mktemp)
trap 'rm -f "$PRIV_PEM"' EXIT
openssl ecparam -name prime256v1 -genkey -noout -out "$PRIV_PEM"

PRIV_HEX=$(openssl ec -in "$PRIV_PEM" -noout -text 2>/dev/null | sed -n '/priv:/,/pub:/p' | grep -E '^[[:space:]]*[0-9a-f]{2}:' | tr -d ' :\n')
PUB_HEX=$(openssl ec -in "$PRIV_PEM" -noout -text 2>/dev/null | sed -n '/pub:/,$p'    | grep -E '^[[:space:]]*[0-9a-f]{2}:' | tr -d ' :\n')

python3 - "$PRIV_HEX" "$PUB_HEX" <<'PY'
import base64, json, sys

def u(hexdir: str) -> str:
    return base64.urlsafe_b64encode(bytes.fromhex(hexdir)).rstrip(b"=").decode()

priv, pub = sys.argv[1], sys.argv[2]
assert len(pub) == 130 and pub.startswith("04"), "unexpected public point encoding"
before = base64.urlsafe_b64encode(bytes.fromhex(pub)).rstrip(b"=").decode()
jwk = {"kty": "EC", "crv": "P-256", "x": u(pub[2:66]), "y": u(pub[66:130]), "d": u(priv)}

after = bytes([0] + bytes.fromhex(pub[2:])).hex()
print("-- Run me again if you lose any half; nothing is stored. --")
print("WHISPER_VAPID_PUBLIC_KEY=" + before)
print("WHISPER_VAPID_JWK=" + json.dumps(jwk, separators=(",", ":")))
PY

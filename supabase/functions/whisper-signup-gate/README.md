# whisper-signup-gate

Pre-auth signup budget: max 5 signups/day/IP via RPC `whisper_check_signup_allowed`.

## Deploy (dashboard)

1. Functions > New function > name `whisper-signup-gate`, paste `index.ts`, Deploy.
2. `config.toml` ships `verify_jwt = false` (pre-auth by design).
3. Secrets: none extra (uses built-in `SUPABASE_URL` / `SUPABASE_SERVICE_ROLE_KEY`).
   Attest secrets (`OFFICIAL_PACKAGE`, `OFFICIAL_CERT_SHA256`, `MIN_VERSION_CODE`)
   are shared with the other whisper functions.

## Contract

- `POST {}` (no body; IP from `x-forwarded-for`/`x-real-ip`, hashed server-side)
- `200 { allowed: true }` | `429 { error: "rate_limited", allowed: false }`
- `503` on RPC/backend failure (client fails OPEN with a warning so a broken
  gate never bricks signup; only 429 blocks).

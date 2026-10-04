# whisper-legacy-migrate

One-shot migration for pre-P1-15 truncated-email token accounts.

## Deploy (dashboard)

1. Functions > New function > name `whisper-legacy-migrate`, paste `index.ts`, Deploy.
2. `config.toml` ships `verify_jwt = false` (the raw token over TLS is the proof).
3. Secrets: none extra (built-in `SUPABASE_URL` / `SUPABASE_SERVICE_ROLE_KEY`;
   the client `apikey` header supplies the anon key for sign-in probes).

## Contract

- `POST { token: "<64 hex>" }`
- `200 { migrated: true, username }` — legacy password randomized, profile
  handle copied to the new full-hash user, `whisper_legacy_disabled` recorded.
  Chat history stays under the legacy id (E2EE — the server cannot re-encrypt).
- `200 { migrated: false, reason: "already_current" }` — just log in.
- `404 { migrated: false, reason: "no_legacy_account" }`
- `400` invalid token, `428` unofficial build, `503` backend error.

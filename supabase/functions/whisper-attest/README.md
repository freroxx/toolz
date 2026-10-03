# whisper-attest — anti-mod / pirated-app protection

Server side of the official-build lock. The Android client
(`PlayIntegrityAttestor.kt`) attaches `X-App-*` headers on every `whisper-*`
edge call; `supabase/functions/_shared/attest.ts` enforces them inside
`whisper-bundle-fetch` and `whisper-image-upload` (3-line gate each).
`whisper-push-send` is intentionally NOT gated — it is invoked by a Supabase
Database Webhook with service-role / webhook-secret auth and never sees
client headers; gating it would break all push delivery.

## Secrets

```bash
# Strict baseline (no Google Cloud needed — runnable as-is):
supabase secrets set OFFICIAL_PACKAGE=com.frerox.toolz
supabase secrets set OFFICIAL_CERT_SHA256=<release keystore SHA-256, colons optional>
supabase secrets set MIN_VERSION_CODE=17

# Optional strong path (hardware verdict via Play Integrity API):
supabase secrets set PLAY_INTEGRITY_ENABLED=true
supabase secrets set GOOGLE_SERVICE_ACCOUNT_JSON='<GCP SA JSON with Play Integrity API enabled>'
```

Get the release cert fingerprint:

```bash
keytool -list -v -keystore <release.keystore> -alias <alias> | grep SHA256
```

## Deploy

```bash
supabase functions deploy whisper-attest
supabase functions deploy whisper-bundle-fetch
supabase functions deploy whisper-image-upload
# whisper-push-send unchanged (webhook auth) — no redeploy needed for attest.
```

## SQL — run once in the Supabase SQL editor

> Deliberately NOT appended to `supabase/migrations/main-whisper-sql.sql`
> (single-consolidator ownership; banner `20261006_whisper_attest_log.sql`
> left free). Append there or run standalone — idempotent either way.

```sql
-- ═══════════════ 20261006_whisper_attest_log.sql ═
create table if not exists public.whisper_attest_log (
  id bigint generated always as identity primary key,
  created_at timestamptz not null default now(),
  user_id uuid null,
  reason text not null default '',
  pkg text not null default '',
  version_code text not null default ''
);

alter table public.whisper_attest_log enable row level security;

-- Deny-all for client roles; edge functions write via service_role (bypasses RLS).
drop policy if exists "no_client_access" on public.whisper_attest_log;
create policy "no_client_access" on public.whisper_attest_log
  for all to anon, authenticated using (false) with check (false);

revoke all on public.whisper_attest_log from anon, authenticated;
grant insert, select on public.whisper_attest_log to service_role;
```

## Behavior matrix

| Caller | Result |
|---|---|
| Official APK, current version | `{ok:true}` — no extra friction (Integrity token fetched silently, cached 1h) |
| Repackaged app (other package / cert) | `428 Unofficial build blocked - install official Toolz APK` + log row |
| Outdated official build (< MIN_VERSION_CODE) | `428` + log row (update-lock) |
| Secrets missing (`OFFICIAL_CERT_SHA256` unset) | cert check skipped with warn log; package still enforced |

Play Integrity notes: `PLAY_RECOGNIZED` app verdict + `MEETS_DEVICE_INTEGRITY`
are required on the strong path. `appLicensingVerdict` is informational only —
`UNLICENSED` is accepted so sideloaded official GitHub APKs keep working.

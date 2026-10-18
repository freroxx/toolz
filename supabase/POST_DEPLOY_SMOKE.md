# Whisper post-deploy smoke (v1.1.7)

Run this ONCE after ship-day deploys — SQL re-run, the four INSERT webhooks
(whisper_group_invites, whisper_group_messages, whisper_group_events,
whisper_story_recipients → whisper-push-send), and the edge redeployments
(whisper-story-sweep, whisper-push-send, whisper-bundle-fetch) — and BEFORE
the APK rollout goes wide. Everything here is five to ten minutes.

Why this exists: the two redeployed functions fail SILENTLY when stale —
a stale `whisper-bundle-fetch` drops `signer_history` (reinstall-proofing
quietly reverts to fragile verification), a stale `whisper-push-send`
leaks account names to plain ntfy targets. A dashboard deploy that "worked"
is not proof; these checks are.

Every check prints PASS or a failure that names the function to redeploy.
Run from any machine with `curl` and `jq`.

Shared shell vars (fill once, used by all parts):

```bash
URL="https://<project-ref>.supabase.co"        # project URL
ANON="<supabase anon key>"                     # Project Settings → API → anon public
CERT="<release signing cert SHA-256>"          # hex, no colons — the value in the functions' OFFICIAL_CERT_SHA256 env
MIN=18                                         # value of the functions' MIN_VERSION_CODE env
CALL_EMAIL="you@<throwaway-test-account.example>"  # an account you know the PASSWORD of (creates the JWT)
CALL_PWD="<its password>"
TARGET_UUID="<auth uid of ANOTHER test account that has opened Whisper once>"
```

## PART A — SQL side (dashboard → SQL editor; no phone needed)

**A1 — every published binding is archived.** Must return ZERO rows:

```sql
select p.id
from public.profiles p
where p.identity_binding ->> 'signer' is not null
  and not exists (
    select 1 from public.whisper_group_signer_history h
    where h.user_id = p.id
      and h.signer_cert = p.identity_binding ->> 'signer'
  );
```

**A2 — the archive trigger fires live (not just the backfill).** A no-op
rewrite of your own row still fires the `before update of identity_binding`
trigger; a broken trigger errors HERE instead of bricking every identity
publish:

```sql
update public.profiles set identity_binding = identity_binding
where id = '<your-account-uuid>';
```

`UPDATE 1`, no error → trigger healthy. Verify it stayed deduped (row count
MUST be identical before and after):

```sql
select count(*) from public.whisper_group_signer_history
where user_id = '<your-account-uuid>';
```

**A3 — the per-event column exists:**

```sql
select exists (
  select 1 from information_schema.columns
  where table_schema = 'public' and table_name = 'whisper_group_events'
    and column_name = 'signer_cert'
);   -- must be true
```

## PART B — bundle-fetch serves signer history

Mint a user JWT with a password account (dashboard-created users work):

```bash
JWT=$(curl -s -X POST "$URL/auth/v1/token?grant_type=password" \
  -H "apikey: $ANON" -H "Content-Type: application/json" \
  -d "{\"email\":\"$CALL_EMAIL\",\"password\":\"$CALL_PWD\"}" | jq -r .access_token)
test -n "$JWT" && test "$JWT" != "null" && echo "token ok" || echo "NO TOKEN — check credentials"
```

Fetch the bundle of the OTHER test account (self-bundles are rejected by
design — never use the caller's own uuid). The three `x-app-*` headers are
the attest gate's mandatory baseline: use the SAME values set in the
function's env (OFFICIAL_PACKAGE default `com.frerox.toolz`):

```bash
resp=$(curl -s -X POST "$URL/functions/v1/whisper-bundle-fetch" \
  -H "Authorization: Bearer $JWT" -H "apikey: $ANON" \
  -H "Content-Type: application/json" \
  -H "x-app-package: com.frerox.toolz" \
  -H "x-app-cert-sha256: $CERT" \
  -H "x-app-versioncode: $MIN" \
  -d "{\"account\":\"$TARGET_UUID\"}")
```

Assert: non-empty `signer_history`, and the account's CURRENT binding signer
is a member of it:

```bash
echo "$resp" | jq -e '
  (.identity_binding.signer | type == "string") and
  ((.signer_history | type) == "array") and
  (.signer_history | length >= 1) and
  (.identity_binding.signer as $b | (.signer_history | index($b)) != null)
' >/dev/null && echo "PASS bundle-fetch signer_history" || { echo "FAIL PASS bundle-fetch signer_history — response:"; echo "$resp" | jq .; }
```

- `error: "No prekey bundle published"` → target account never opened
  Whisper (pick one that did).
- HTTP 428 → attest headers wrong (cert/package/floor mismatch).
- `signer_history: []` while the binding is non-null → SQL side missing or
  stale deploy: re-run PART A1 (must be zero rows) then redeploy
  `whisper-bundle-fetch` and re-run this part.

## PART C — plain (ntfy) push scrub, end to end

Validates the whole real chain at once: webhook wiring (the four INSERT
webhooks), recipient target lookup, and the name scrub on the plain flavor.

1. On ONE test device, install ntfy (or NextPush) BEFORE / then reopen
   Whisper — app start re-registers (`ensureRegistered`) and the distributor
   endpoint lands as a plain target.
2. Dashboard SQL — confirm the row (expect provider `plain` + topic URL):

   ```sql
   select provider, endpoint from whisper_push_targets
   where user_id = '<that-device-account-uuid>';
   ```

3. On a SECOND device/account: create a group and invite step-2's account —
   the invite insert fires `whisper-push-send` through the webhook.
4. Poll the topic (look first, then assert):

   ```bash
   TOPIC="<endpoint from step 2>"    # e.g. https://ntfy.sh/toolz-smoke-xyz
   curl -s "$TOPIC/json?poll=1&since=15m" \
     | jq -s '[.[] | select(.event == "message") | .message | fromjson]'

   # assert: every payload key survives the scrub set inspection
   curl -s "$TOPIC/json?poll=1&since=15m" \
     | jq -s '[.[] | select(.event == "message") | .message | fromjson] |
              all((. // {}) | keys |
                  map(select(. == "senderName" or . == "groupName"
                    or . == "inviterName" or . == "actorName"
                    or . == "subjectName" or . == "posterName")) | length == 0)' \
     && echo "PASS plain push scrub" || echo "FAIL — a name key reached plain"
   ```

   (Empty array — no ping arrived at all — counts as FAIL: check the target
   row exists and the webhook exists; a missing webhook is visible in the
   function's live logs ⇒ the invite insert never reached `whisper-push-send`.)
- Any of the six name keys present ⇒ **stale deploy**: redeploy
  `whisper-push-send` and re-run.

## Lock the result

All three parts PASS → the smoke contract holds ship-day. Log one line in the
release notes / plan: date, project-ref, and which build got verified. Any
FAIL is a redeploy + re-run of that part ONLY — do not proceed with the APK
rollout on a failed smoke.

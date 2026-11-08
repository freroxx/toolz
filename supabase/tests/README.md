# Whisper SQL local smoke harness

This directory exists because `supabase/migrations/main-whisper-sql.sql` is the
canonical server baseline and must be executable on both:

- a **brand-new** project (it claims to build the full final schema), and
- an **existing production** project (it must be safe to re-run).

The repo does not vendor the hosted Supabase baseline (`auth`, `storage`,
pgcrypto location, API roles, the `supabase_realtime` publication, and the
older app tables). `00_supabase_shim.sql` emulates just enough of those so the
Whisper SQL can run on a plain Postgres container.

## Run it

```bash
./supabase/tests/run-local-smoke.sh
```

Requires docker. The script starts a `postgres:16-alpine` container, applies
the shim, applies the migration **twice**, then runs the behaviour suite. The
container is removed on exit.

Useful overrides:

```bash
POSTGRES_IMAGE=postgres:16-alpine WHISPER_SMOKE_PORT=55433 \
  ./supabase/tests/run-local-smoke.sh
```

## What the behaviour suite covers

`main-whisper-smoke.sql` seeds accounts/profiles/friends/groups/stories and
asserts the hardening invariants:

- A1/A2/A3 from `supabase/POST_DEPLOY_SMOKE.md` (signer archive completeness,
  live trigger + dedupe, `signer_cert` column).
- Invitee event policy: only `join`/`decline`; uninvited outsiders cannot
  append events.
- Epoch chain: `+1` accepted, `+2` rejected, stale rejected.
- Receipt/reaction writes require a real `(group, message)` row.
- Typing rows require membership.
- Story reaction removal is keyed by identity, not by the client's emoji.
- Account purge: owned groups removed, surviving groups keep their signed
  append-only event log, every FK-less authored row is removed, and the
  story/1:1/quota/profile surfaces are covered.
- The newest-first story recipient index exists.
- Reproduces the documented Groups launch blocker: GoTrue's `auth.users`
  cascade removes the public signer archive while signed events from that
  actor remain.

## Safety guards

Both files refuse to run unless the caller opts in:

- shim: `whisper.shim=1`
- smoke: `whisper.smoke=1` (it **truncates** the Whisper tables)

The runner sets both through `PGOPTIONS`. Never paste these files into a
production SQL editor.

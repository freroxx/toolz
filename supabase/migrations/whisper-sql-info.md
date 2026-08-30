# Whisper SQL — Single-File Policy

> **CANONICAL FILE: `supabase/migrations/main-whisper-sql.sql`**
> **RULE: ALL Whisper SQL changes MUST be merged into that single fully-working file. Do NOT create new timestamped migration files for Whisper.**

This document is for **human contributors and AI agents**. If you are an LLM, read this first before touching any Whisper SQL.

---

## Table of Contents
- [Why One File](#why-one-file)
- [Golden Rule](#golden-rule)
- [What Is Squashed](#what-is-squashed)
- [Where to Edit](#where-to-edit)
- [How to Add a New Change](#how-to-add-a-new-change)
- [Idempotency Patterns (required)](#idempotency-patterns-required)
- [Section Banner Template](#section-banner-template)
- [Testing Checklist](#testing-checklist)
- [Deprecated Files](#deprecated-files)
- [AI Agent Instructions](#ai-agent-instructions)
- [Contributor Workflow Checklist](#contributor-workflow-checklist)
- [Emergency Notes](#emergency-notes)
- [Example New Section](#example-new-section)

---

## Why One File

Whisper's backend originally grew as 11+ incremental timestamped migrations
(`20260820_whisper_realtime_hardening.sql` … `20260830_whisper_prekeys.sql`).  
Running them in order was error-prone, replay could hit `42P13` / `42501` due to
return-type drift, and contributors had to guess ordering.

Squashing to **one idempotent baseline** solves:

- **Atomic deploy** — copy-paste the single file into Supabase SQL Editor and it converges from any state (fresh project or production) without error and without data loss.
- **Deterministic ordering** — no file-system sort surprises.
- **Pruned history** — superseded intermediate function signatures are removed so a replay never downgrades a `RETURNS` type.
- **One source of truth** — reviewers and AI agents look in one place.

The single file is **idempotent (V6-R5 + P6)**: safe to re-run at any time.

---

## Golden Rule

```
┌─────────────────────────────────────────────────────────────────┐
│  NEVER create a new `supabase/migrations/2026*_whisper_*.sql`   │
│  for Whisper. Instead, edit `main-whisper-sql.sql` in place,    │
│  append a new bannered section at the END, verbatim,            │
│  idempotently, and delete any stray timestamped file you may    │
│  have created. The file MUST remain fully working after your    │
│  change — fresh install + rerun must both succeed.              │
└─────────────────────────────────────────────────────────────────┘
```

- **If you prototyped in a separate `202609xx_*.sql` file → squash it into `main-whisper-sql.sql` before committing and delete the separate file.**
- **If you receive a PR that adds a new timestamped Whisper SQL file → request squash into `main-whisper-sql.sql`.**

Non-Whisper migrations (unrelated Supabase features) may still use timestamped files — this rule applies only to `whisper_*` tables / functions / policies.

---

## What Is Squashed

`main-whisper-sql.sql` currently squashes **14 migrations verbatim in application order**:

| # | File | Purpose |
|---|------|---------|
| 1 | `20260820_whisper_realtime_hardening.sql` | RLS on messages / reactions / friends / profiles / whisper_blocks |
| 2 | `20260821_whisper_hardening_fixes.sql` | messages UPDATE guard, friends pending-only, storage avatars, realtime publication, replica identity, upload quota |
| 3 | `20260822_whisper_1_0_hardening.sql` | quota PK fix + atomic RPC, tombstones |
| 4 | `20260823_whisper_discover_rate_limit.sql` | discover quota + `whisper_discover_profiles()` |
| 5 | `20260824_whisper_bypass_rate_limit.sql` | `whisper_bypass_attempts` |
| 6 | `20260825_whisper_quota_refund.sql` | `whisper_refund_upload_quota` |
| 7 | `20260826_whisper_bypass_atomic.sql` | atomic `whisper_bypass_attempt` + advisory lock |
| 8 | `20260827_whisper_review_v2_hardening.sql` | image ownership, uid-param RPCs, RLS shrink, immutability v2, friend transitions, `whisper_purge_account_data`, bypass advisory lock, discover clamps |
| 9 | `20260828_whisper_destructive_rate_limit.sql` | destructive attempts + public view + discover rewrite |
| 10 | `20260829_whisper_fcm_tokens.sql` | FCM token registry |
| 11 | `20260830_whisper_prekeys.sql` | prekeys + identity_binding + purge coverage |
| 12 | `20260831_whisper_typing_signal.sql` | `whisper_typing_signals` durable typing indicator |
| 13 | `20260901_whisper_server_enforcement.sql` | block-aware insert guard + replay `content_hash` guard |
| 14 | `20260904_whisper_block_enforcement.sql` | DB-level block enforcement for messages/friends |

Each section keeps its original filename banner for `grep` traceability:

```sql
-- ═══════════════ 20260831_whisper_typing_signal.sql ═
```

---

## Where to Edit

```
supabase/
└── migrations/
    ├── main-whisper-sql.sql      ← EDIT THIS (canonical)
    └── whisper-sql-info.md       ← YOU ARE HERE
```

Do **not** edit `supabase/config.toml` to add new migration entries for Whisper — the single file is executed manually in the SQL Editor (or via `supabase db push` if you keep a single migration entry).

---

## How to Add a New Change

1. **Open `main-whisper-sql.sql`.**
2. **Go to the very END of the file** (after the `20260904` section).
3. **Append a new section** with:
   - A banner line matching the style: `-- ═══════════════ YYYYMMDD_short_description.sql ═` (at least 15 `═` before/after, keep filename inside).
   - A short comment block stating **why / what P-fix / idempotency note**.
   - Your SQL **verbatim**, but **idempotent** (see patterns below).
4. **Update the header** at the top of the file:
   - Increment count: `fourteen → fifteen` (or next number).
   - Add your filename to the two-per-line list:
     ```
     --   20260901_whisper_server_enforcement.sql        20260904_whisper_block_enforcement.sql
     --   20260930_your_new_feature.sql
     ```
   - Bump idempotency tag if needed (`V6-R5 + P6 + P7` or date).
5. **Delete any separate file** you may have used for drafting (`supabase/migrations/20260930_*.sql`).
6. **Test** (see checklist below) — must pass both fresh and rerun.
7. **Commit** both `main-whisper-sql.sql` + updated `whisper-sql-info.md` table row.

---

## Idempotency Patterns (required)

Every statement **must** be rerun-safe. Use these exact guards:

### Tables
```sql
create table if not exists public.whisper_example (
  id uuid primary key,
  owner uuid not null references auth.users(id) on delete cascade
);
alter table public.whisper_example enable row level security;
```

### Columns
```sql
alter table public.profiles add column if not exists new_col jsonb;
-- or guarded DO block for PK changes (see 20260822 quota PK migration)
```

### Indexes
```sql
create index if not exists whisper_example_owner_idx on public.whisper_example(owner);
create unique index if not exists whisper_messages_replay_guard
  on public.messages (sender_id, receiver_id, content_hash) where content_hash is not null;
```

### Policies
```sql
drop policy if exists "whisper_example_select_own" on public.whisper_example;
create policy "whisper_example_select_own"
  on public.whisper_example for select to authenticated using (owner = auth.uid());
```

### Functions
```sql
create or replace function public.whisper_example_fn(p_uid uuid)
returns int language plpgsql security definer set search_path = public as $$ ... $$;
revoke all on function public.whisper_example_fn(uuid) from public, anon, authenticated;
grant execute on function public.whisper_example_fn(uuid) to service_role; -- or authenticated
```

### Triggers
```sql
drop trigger if exists whisper_example_trigger on public.messages;
create trigger whisper_example_trigger
  before insert on public.messages for each row execute function public.whisper_example_fn();
-- clean up old signatures that were pruned:
drop function if exists public.whisper_example_old_signature();
```

### Publications / Replica Identity (idempotent)
```sql
do $$ begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname='supabase_realtime' and schemaname='public' and tablename='whisper_example'
  ) then
    alter publication supabase_realtime add table public.whisper_example;
  end if;
end $$;
alter table public.whisper_example replica identity full;
```

### Views that change return type
```sql
drop function if exists public.whisper_discover_profiles(integer, integer); -- dependency first!
do $do$ ... execute format('drop view if exists ...; create or replace view ...') ... $do$;
```

### Extensions
```sql
create extension if not exists pgcrypto;
```

**Forbidden:** `create table public.foo` without `if not exists`, `create policy` without preceding `drop policy if exists`, `create function` without `or replace`, bare `alter publication add table` without existence check.

---

## Section Banner Template

Copy-paste:

```sql
-- ═══════════════ 20260930_whisper_your_feature.sql ═
-- YYYYMMDD — Short title (P# fix / feature)
-- WHY: one paragraph explaining the gap and semantics.
-- IDEMPOTENT: safe to re-run (guarded drops / IF NOT EXISTS / OR REPLACE).
-- ============================================================================

-- your SQL here (idempotent)
```

Keep banner exactly ` -- ═══════════════ <filename> ═` — tools `grep "═══════════════"` count sections (must be 14 → 15 → N).

---

## Testing Checklist

Before committing, verify `main-whisper-sql.sql` is **fully working**:

- [ ] **Fresh project**: paste entire file into new Supabase SQL Editor → executes without error → `\d public.whisper_*` shows tables, `\df public.whisper_*` shows functions, `\dp` shows RLS.
- [ ] **Rerun**: execute the **same file again** on same project → **zero errors**, `NOTICE` only → schema converges to same state (`grep -c "═══════════════"` unchanged, idempotency).
- [ ] **No truncation**: `wc -l supabase/migrations/main-whisper-sql.sql` matches expected (≈1417 + your additions), file ends with newline, no `... truncated` markers.
- [ ] **Banners**: `grep "═══════════════" supabase/migrations/main-whisper-sql.sql` lists every filename including yours, header list at top matches.
- [ ] **Grep traceability**: `grep -n "whisper_your_feature" supabase/migrations/main-whisper-sql.sql` finds your section.
- [ ] **Lint**: no bare `create table` / `create policy` without guards (search `^create table public` and verify `if not exists`).

If you cannot test against live Supabase, at least do `grep` + `wc -l` + manual review of guards.

---

## Deprecated Files

The following timestamped files are **deleted and superseded** — do not recreate:

- `20260820_whisper_realtime_hardening.sql` … `20260830_whisper_prekeys.sql` (11 squashed in V6-R2)
- `20260831_whisper_typing_signal.sql` (now in main)
- `20260901_whisper_server_enforcement.sql` (now in main)
- `20260904_whisper_block_enforcement.sql` (now in main)

`git log -- supabase/migrations/` history still holds them. The **only** whisper SQL source of truth is `main-whisper-sql.sql`.

If you see a stray `2026*_whisper_*.sql` in `supabase/migrations/` during a PR, **squash it** per this doc and delete the stray.

---

## AI Agent Instructions

If you are an AI assistant (Muse, Copilot, Cursor, etc.):

1. **STOP** — before creating any `2026*_whisper_*.sql`, read this file and `main-whisper-sql.sql` header.
2. **ALWAYS** append to `main-whisper-sql.sql` at the END with a bannered idempotent section. Never create a side file for Whisper.
3. **ALWAYS** update the header file list and idempotency tag.
4. **ALWAYS** delete any timestamped whisper SQL file you may have just created after squashing.
5. **VERIFY** with `grep "═══════════════"` and `wc -l` and rerun-safety review before marking task complete.
6. If the user explicitly asks for a separate migration file, **still squash** and explain this policy (link this doc), unless the user insists on an exception for non-Whisper work.
7. Keep this `whisper-sql-info.md` up to date: add your new row to the "What Is Squashed" table.

**Example prompt to yourself:**
> "I need to add whisper_foo. I will edit supabase/migrations/main-whisper-sql.sql, append `-- ═══════════════ 20260930_whisper_foo.sql ═` + idempotent SQL, update header from 14 to 15, and ensure no stray file remains."

---

## Contributor Workflow Checklist

- [ ] Branch from `main`
- [ ] Edit `supabase/migrations/main-whisper-sql.sql` (append bannered section at END)
- [ ] Update header (count + file list + idempotency tag)
- [ ] Update `supabase/migrations/whisper-sql-info.md` table (add row)
- [ ] `grep "═══════════════" supabase/migrations/main-whisper-sql.sql` → count = old + 1
- [ ] `wc -l supabase/migrations/main-whisper-sql.sql` → no truncation
- [ ] Test fresh + rerun in Supabase SQL Editor (or review guards if offline)
- [ ] `git status` shows only `main-whisper-sql.sql` + `whisper-sql-info.md` (no new timestamped file)
- [ ] Commit with message: `whisper(sql): squash <feature> into main-whisper-sql.sql`
- [ ] PR description: "Squashed per whisper-sql-info.md policy — single file remains fully working (fresh + rerun verified)."

---

## Emergency Notes

- **Accidental separate file committed?** Squash it in a follow-up commit: copy its content verbatim into `main-whisper-sql.sql` with banner, delete the separate file, update header — push fixup.
- **Merge conflict in `main-whisper-sql.sql`?** Resolve by keeping **both** sections in chronological order (oldest first), keep header merged list sorted, ensure no duplicate banner lost.
- **Need to revert a Whisper change?** Append a new section that reverses it idempotently (e.g., `drop policy if exists`, `drop trigger if exists`) — never edit-delete history in the middle of the file.
- **Supabase CLI `supabase migration new`?** Do not use for Whisper — it creates a timestamped file. If you must, immediately squash its content into `main-whisper-sql.sql` and delete the generated file.

---

## Example New Section

At the END of `main-whisper-sql.sql`, after the `20260904` block:

```sql
-- ═══════════════ 20260930_whisper_example_feature.sql ═
-- 20260930 — Example: per-user whisper pin limit
-- WHY: clients could pin unlimited messages, degrading sync. Server now enforces max 20 pins.
-- IDEMPOTENT: safe to re-run (guarded table / policy / function).
-- ============================================================================

create table if not exists public.whisper_pins (
  user_id uuid not null references auth.users(id) on delete cascade,
  message_id uuid not null references public.messages(id) on delete cascade,
  pinned_at timestamptz not null default now(),
  primary key (user_id, message_id)
);
alter table public.whisper_pins enable row level security;

drop policy if exists "whisper_pins_select_own" on public.whisper_pins;
create policy "whisper_pins_select_own" on public.whisper_pins
  for select to authenticated using (user_id = auth.uid());
drop policy if exists "whisper_pins_insert_own" on public.whisper_pins;
create policy "whisper_pins_insert_own" on public.whisper_pins
  for insert to authenticated with check (user_id = auth.uid());
drop policy if exists "whisper_pins_delete_own" on public.whisper_pins;
create policy "whisper_pins_delete_own" on public.whisper_pins
  for delete to authenticated using (user_id = auth.uid());

create or replace function public.whisper_enforce_pin_limit()
returns trigger language plpgsql set search_path = public as $$
begin
  if (select count(*) from public.whisper_pins where user_id = new.user_id) >= 20 then
    raise exception 'pin_limit_exceeded' using errcode = 'P0001';
  end if;
  return new;
end; $$;
drop trigger if exists whisper_pin_limit on public.whisper_pins;
create trigger whisper_pin_limit before insert on public.whisper_pins
  for each row execute function public.whisper_enforce_pin_limit();
```

Then update header:

```sql
-- This file replaces the fifteen chronological migrations
--   ...
--   20260901_whisper_server_enforcement.sql        20260904_whisper_block_enforcement.sql
--   20260930_whisper_example_feature.sql
```

And add row 15 to the table in this doc.

---

**Last updated:** 2026-08-30 — squashed to 14 (V6-R5 + P6). Keep this doc and `main-whisper-sql.sql` in sync.
**Maintainer rule:** any change to `main-whisper-sql.sql` must be accompanied by an update to this doc's "What Is Squashed" table if you add a section.

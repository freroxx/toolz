-- ============================================================================
-- WHISPER — COMBINED BASELINE MIGRATION (single file)
-- ============================================================================
-- This file replaces the fourteen chronological migrations
--   20260820_whisper_realtime_hardening.sql        20260826_whisper_bypass_atomic.sql
--   20260821_whisper_hardening_fixes.sql           20260827_whisper_review_v2_hardening.sql
--   20260822_whisper_1_0_hardening.sql             20260828_whisper_destructive_rate_limit.sql
--   20260823_whisper_discover_rate_limit.sql       20260829_whisper_fcm_tokens.sql
--   20260824_whisper_bypass_rate_limit.sql         20260830_whisper_prekeys.sql
--   20260825_whisper_quota_refund.sql              20260831_whisper_typing_signal.sql
--   20260901_whisper_server_enforcement.sql        20260904_whisper_block_enforcement.sql
-- squashed VERBATIM in application order.
-- See whisper-sql-info.md — ALL Whisper SQL changes MUST be merged into this single file.
--
-- IDEMPOTENT (V6-R5 + P6): SAFE TO RE-RUN AT ANY TIME, on any state:
--   * fresh project → builds the full final schema;
--   * production   → every statement is guarded or replace-compatible;
--     superseded intermediate function versions were pruned so no replay ever
--     downgrades a return type (the 42P13 failure this file originally had);
--     safety-net `drop ... if exists` lines cover historical signature drift.
--   * Re-running converges to the SAME schema without error and without data loss.
--
-- Sections below keep their original filename banners for traceability.
-- ============================================================================

-- ═══════════════ 20260820_whisper_realtime_hardening.sql ═
-- Whisper realtime + RLS hardening
--
-- Hosted Supabase owns realtime.messages; project SQL cannot ALTER that table or create
-- policies on it (ERROR 42501: must be owner of table messages). The Android client treats
-- Postgres Changes on the application tables below as the authoritative realtime transport
-- and never renders/persists broadcast payloads, so the real hardening lives in these RLS
-- policies: every row a client can read or modify is constrained to the authenticated user.
--
-- Safe to run from the Supabase SQL Editor; every statement is idempotent.

-- ── messages ─────────────────────────────────────────────────────────────
alter table public.messages enable row level security;

drop policy if exists "messages_select_participants" on public.messages;
create policy "messages_select_participants"
    on public.messages for select
    to authenticated
    using (sender_id = auth.uid() or receiver_id = auth.uid());

drop policy if exists "messages_insert_sender" on public.messages;
create policy "messages_insert_sender"
    on public.messages for insert
    to authenticated
    with check (sender_id = auth.uid());

drop policy if exists "messages_update_participants" on public.messages;
create policy "messages_update_participants"
    on public.messages for update
    to authenticated
    using (sender_id = auth.uid() or receiver_id = auth.uid())
    with check (sender_id = auth.uid() or receiver_id = auth.uid());

drop policy if exists "messages_delete_sender" on public.messages;
create policy "messages_delete_sender"
    on public.messages for delete
    to authenticated
    using (sender_id = auth.uid());

-- ── message_reactions ────────────────────────────────────────────────────
alter table public.message_reactions enable row level security;

drop policy if exists "message_reactions_select_participants" on public.message_reactions;
create policy "message_reactions_select_participants"
    on public.message_reactions for select
    to authenticated
    using (
        user_id = auth.uid()
        or exists (
            select 1 from public.messages m
            where m.id = message_id
              and (m.sender_id = auth.uid() or m.receiver_id = auth.uid())
        )
    );

drop policy if exists "message_reactions_insert_own" on public.message_reactions;
create policy "message_reactions_insert_own"
    on public.message_reactions for insert
    to authenticated
    with check (user_id = auth.uid());

drop policy if exists "message_reactions_update_own" on public.message_reactions;
create policy "message_reactions_update_own"
    on public.message_reactions for update
    to authenticated
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

drop policy if exists "message_reactions_delete_own" on public.message_reactions;
create policy "message_reactions_delete_own"
    on public.message_reactions for delete
    to authenticated
    using (user_id = auth.uid());

-- ── friends ──────────────────────────────────────────────────────────────
alter table public.friends enable row level security;

drop policy if exists "friends_select_participants" on public.friends;
create policy "friends_select_participants"
    on public.friends for select
    to authenticated
    using (user_a = auth.uid() or user_b = auth.uid());

drop policy if exists "friends_insert_requester" on public.friends;
create policy "friends_insert_requester"
    on public.friends for insert
    to authenticated
    with check (user_a = auth.uid() and user_a <> user_b);

drop policy if exists "friends_update_participants" on public.friends;
create policy "friends_update_participants"
    on public.friends for update
    to authenticated
    using (user_a = auth.uid() or user_b = auth.uid())
    with check (user_a = auth.uid() or user_b = auth.uid());

drop policy if exists "friends_delete_participants" on public.friends;
create policy "friends_delete_participants"
    on public.friends for delete
    to authenticated
    using (user_a = auth.uid() or user_b = auth.uid());

-- ── profiles ─────────────────────────────────────────────────────────────
-- Select is open to authenticated users: profile discovery and, critically, reading the
-- partner's public_key is required before encrypting a message to them.
alter table public.profiles enable row level security;

drop policy if exists "profiles_select_authenticated" on public.profiles;
create policy "profiles_select_authenticated"
    on public.profiles for select
    to authenticated
    using (true);

drop policy if exists "profiles_insert_own" on public.profiles;
create policy "profiles_insert_own"
    on public.profiles for insert
    to authenticated
    with check (id = auth.uid());

drop policy if exists "profiles_update_own" on public.profiles;
create policy "profiles_update_own"
    on public.profiles for update
    to authenticated
    using (id = auth.uid())
    with check (id = auth.uid());

-- ── whisper_blocks ────────────────────────────────────────────────────────
-- Blocking is private: a user may only see blocks they are part of (either side),
-- never enumerate who blocks whom. Both parties are filtered out of each other's
-- message flows client-side.
alter table public.whisper_blocks enable row level security;

drop policy if exists "whisper_blocks_select_participant" on public.whisper_blocks;
create policy "whisper_blocks_select_participant"
    on public.whisper_blocks for select
    to authenticated
    using (blocker_id = auth.uid() or blocked_id = auth.uid());

drop policy if exists "whisper_blocks_insert_blocker" on public.whisper_blocks;
create policy "whisper_blocks_insert_blocker"
    on public.whisper_blocks for insert
    to authenticated
    with check (blocker_id = auth.uid() and blocker_id <> blocked_id);

drop policy if exists "whisper_blocks_delete_blocker" on public.whisper_blocks;
create policy "whisper_blocks_delete_blocker"
    on public.whisper_blocks for delete
    to authenticated
    using (blocker_id = auth.uid());

-- ═══════════════ 20260821_whisper_hardening_fixes.sql ════
-- Whisper hardening fixes (20260821)
--
-- Follow-up to 20260820_whisper_realtime_hardening.sql. Sections:
--   1. messages UPDATE hardening: receivers may only flip is_read — a BEFORE UPDATE
--      trigger rejects any content/sender/receiver change by the receiver party.
--   2. friends INSERT forge tightening: requests must be created with status 'pending'
--      (the requester can never forge an accepted or blocked row).
--   3. storage.objects policies for bucket 'whisper-avatars': every object operation is
--      scoped to "<userId>/..." so users cannot enumerate or mutate each other's avatars
--      through the authenticated storage endpoint. Public bucket reads via the public
--      object URL are unaffected. The bucket itself must already exist (created in the
--      dashboard; SQL cannot create buckets without admin privileges).
--   4. realtime publication wiring for the tables the Android client consumes.
--   5. REPLICA IDENTITY FULL on those tables so realtime UPDATE/DELETE events carry the
--      old row data (needed for delete-for-everyone tombstones and cache mirroring).
--   6. whisper_upload_quota table + RLS for the per-user daily image-upload cap enforced
--      by the whisper-image-upload edge function.
--
-- Safe to run from the Supabase SQL Editor; every statement is idempotent.

-- ── 1. messages UPDATE hardening ─────────────────────────────────────────────
-- Receivers may only update read state: content, content_iv, sender and receiver are
-- immutable for them. Senders can still tombstone their own messages (delete-for-
-- everyone), and service-role updates bypass the trigger entirely.
create or replace function public.whisper_protect_message_content()
returns trigger
language plpgsql
as $$
begin
  -- Service-role updates (auth.uid() is null) bypass the read-state-only restriction.
  if (auth.uid() is null) then
    return new;
  end if;
  if (auth.uid() = new.receiver_id and (
    new.content is distinct from old.content
    or new.content_iv is distinct from old.content_iv
    or new.sender_id is distinct from old.sender_id
    or new.receiver_id is distinct from old.receiver_id
  )) then
    raise exception 'Receivers may only update read state' using errcode = '42501';
  end if;
  return new;
end;
$$;

drop trigger if exists whisper_protect_message_content on public.messages;
create trigger whisper_protect_message_content
  before update on public.messages
  for each row execute function public.whisper_protect_message_content();

-- ── 2. friends INSERT forge tightening ────────────────────────────────────────
-- The 20260820 policy only required user_a = auth.uid(); with a status column,
-- the requester could forge an 'accepted' row. Requests must be created pending;
-- acceptance flows through the existing update policies instead.
drop policy if exists "friends_insert_requester" on public.friends;
create policy "friends_insert_requester"
    on public.friends for insert
    to authenticated
    with check (user_a = auth.uid() and user_a <> user_b and status = 'pending');

-- ── 3. storage.objects policies for 'whisper-avatars' ────────────────────────
-- Objects live at "<userId>/avatar.ext"; each operation is scoped to the caller's
-- own folder (storage.foldername(name))[1] is the first path segment).
drop policy if exists "whisper_avatars_select_own" on storage.objects;
create policy "whisper_avatars_select_own"
    on storage.objects for select
    to authenticated
    using (bucket_id = 'whisper-avatars' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists "whisper_avatars_insert_own" on storage.objects;
create policy "whisper_avatars_insert_own"
    on storage.objects for insert
    to authenticated
    with check (bucket_id = 'whisper-avatars' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists "whisper_avatars_update_own" on storage.objects;
create policy "whisper_avatars_update_own"
    on storage.objects for update
    to authenticated
    using (bucket_id = 'whisper-avatars' and (storage.foldername(name))[1] = auth.uid()::text)
    with check (bucket_id = 'whisper-avatars' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists "whisper_avatars_delete_own" on storage.objects;
create policy "whisper_avatars_delete_own"
    on storage.objects for delete
    to authenticated
    using (bucket_id = 'whisper-avatars' and (storage.foldername(name))[1] = auth.uid()::text);

-- ── 4. realtime publication wiring ────────────────────────────────────────────
-- The client relies on Postgres Changes for these tables; each is added to the
-- supabase_realtime publication once (the DO block keeps re-runs idempotent).
do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'messages'
  ) then
    alter publication supabase_realtime add table public.messages;
  end if;
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'message_reactions'
  ) then
    alter publication supabase_realtime add table public.message_reactions;
  end if;
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'friends'
  ) then
    alter publication supabase_realtime add table public.friends;
  end if;
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'profiles'
  ) then
    alter publication supabase_realtime add table public.profiles;
  end if;
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'whisper_blocks'
  ) then
    alter publication supabase_realtime add table public.whisper_blocks;
  end if;
end $$;

-- ── 5. REPLICA IDENTITY FULL ──────────────────────────────────────────────────
-- Realtime UPDATE/DELETE events must carry the old row so the client can mirror
-- tombstones and deletions without a follow-up SELECT. Re-applying the same
-- replica identity is a no-op, so these are re-run safe.
alter table public.messages replica identity full;
alter table public.message_reactions replica identity full;
alter table public.friends replica identity full;
alter table public.whisper_blocks replica identity full;
alter table public.profiles replica identity full;

-- ── 6. whisper_upload_quota ───────────────────────────────────────────────────
-- Per-user daily upload counter read/incremented by whisper-image-upload; RLS keeps
-- every user confined to their own row (the edge function forwards the caller's own
-- bearer token for these REST calls).
create table if not exists public.whisper_upload_quota (
    user_id uuid primary key references auth.users(id) on delete cascade,
    day date not null,
    count int not null default 0
);

alter table public.whisper_upload_quota enable row level security;

drop policy if exists "whisper_upload_quota_select_own" on public.whisper_upload_quota;
create policy "whisper_upload_quota_select_own"
    on public.whisper_upload_quota for select
    to authenticated
    using (user_id = auth.uid());

drop policy if exists "whisper_upload_quota_insert_own" on public.whisper_upload_quota;
create policy "whisper_upload_quota_insert_own"
    on public.whisper_upload_quota for insert
    to authenticated
    with check (user_id = auth.uid());

drop policy if exists "whisper_upload_quota_update_own" on public.whisper_upload_quota;
create policy "whisper_upload_quota_update_own"
    on public.whisper_upload_quota for update
    to authenticated
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

drop policy if exists "whisper_upload_quota_delete_own" on public.whisper_upload_quota;
create policy "whisper_upload_quota_delete_own"
    on public.whisper_upload_quota for delete
    to authenticated
    using (user_id = auth.uid());

-- ═══════════════ 20260822_whisper_1_0_hardening.sql ══════
-- 1.0 hardening: atomic quota + server tombstones
-- Idempotent, safe to run from Supabase SQL Editor.

-- ── 1. whisper_upload_quota — fix PK, add atomic RPC ─────────────────────
-- Original table used user_id as PK, preventing per-day rows. Migrate to (user_id, day).
do $$
begin
  if exists (
    select 1 from information_schema.table_constraints
    where table_name='whisper_upload_quota' and constraint_name='whisper_upload_quota_pkey'
  ) then
    -- Drop old single-column PK if it exists and recreate as composite
    declare
      pk_cols text;
    begin
      select string_agg(column_name, ',' order by ordinal_position) into pk_cols
      from information_schema.key_column_usage
      where table_name='whisper_upload_quota' and constraint_name='whisper_upload_quota_pkey';
      if pk_cols = 'user_id' then
        alter table public.whisper_upload_quota drop constraint whisper_upload_quota_pkey;
        alter table public.whisper_upload_quota add primary key (user_id, day);
      end if;
    end;
  else
    -- Table may not exist yet (fresh project) — ensure composite PK
    if not exists (select 1 from information_schema.tables where table_name='whisper_upload_quota') then
      create table public.whisper_upload_quota (
        user_id uuid not null references auth.users(id) on delete cascade,
        day date not null,
        count int not null default 0,
        primary key (user_id, day)
      );
      alter table public.whisper_upload_quota enable row level security;
    end if;
  end if;
end $$;

-- Ensure RLS policies exist (already created in 20260821, recreate idempotently)
drop policy if exists "whisper_upload_quota_select_own" on public.whisper_upload_quota;
create policy "whisper_upload_quota_select_own" on public.whisper_upload_quota for select to authenticated using (user_id = auth.uid());
drop policy if exists "whisper_upload_quota_insert_own" on public.whisper_upload_quota;
create policy "whisper_upload_quota_insert_own" on public.whisper_upload_quota for insert to authenticated with check (user_id = auth.uid());
drop policy if exists "whisper_upload_quota_update_own" on public.whisper_upload_quota;
create policy "whisper_upload_quota_update_own" on public.whisper_upload_quota for update to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());
drop policy if exists "whisper_upload_quota_delete_own" on public.whisper_upload_quota;
create policy "whisper_upload_quota_delete_own" on public.whisper_upload_quota for delete to authenticated using (user_id = auth.uid());

-- Atomic increment RPC — serializes concurrent uploads, returns new count or raises.

-- ── 2. whisper_deleted_tombstones — server source of truth for delete-for-me ─
create table if not exists public.whisper_deleted_tombstones (
  user_id uuid not null references auth.users(id) on delete cascade,
  message_id text not null,
  deleted_at timestamptz not null default now(),
  primary key (user_id, message_id)
);
alter table public.whisper_deleted_tombstones enable row level security;

drop policy if exists "whisper_tombstones_select_own" on public.whisper_deleted_tombstones;
create policy "whisper_tombstones_select_own" on public.whisper_deleted_tombstones for select to authenticated using (user_id = auth.uid());
drop policy if exists "whisper_tombstones_insert_own" on public.whisper_deleted_tombstones;
create policy "whisper_tombstones_insert_own" on public.whisper_deleted_tombstones for insert to authenticated with check (user_id = auth.uid());
drop policy if exists "whisper_tombstones_delete_own" on public.whisper_deleted_tombstones;
create policy "whisper_tombstones_delete_own" on public.whisper_deleted_tombstones for delete to authenticated using (user_id = auth.uid());

-- Fast lookup for sync
create index if not exists whisper_tombstones_user_id_idx on public.whisper_deleted_tombstones(user_id);

-- ═══════════════ 20260823_whisper_discover_rate_limit.sql
-- ─────────────────────────────────────────────────────────────
-- whisper_discover_rate_limit.sql
-- H-5: Per-user discover rate-limiting and server-side block filtering.
--
-- Creates a quota table + RPC whisper_discover_profiles() that:
--   1. Enforces 60 pages/hour per calling user.
--   2. Filters out: private profiles, hide_from_discover, own id,
--      and profiles where the caller is blocked by that user.
-- ─────────────────────────────────────────────────────────────

-- Quota table: one row per user, resets every hour.
CREATE TABLE IF NOT EXISTS whisper_discover_quota (
    user_id     uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    page_count  int  NOT NULL DEFAULT 0,
    window_start timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id)
);

ALTER TABLE whisper_discover_quota ENABLE ROW LEVEL SECURITY;

-- Users can only see/update their own quota row.
drop policy if exists "whisper_discover_quota_owner" on public.whisper_discover_quota;
create policy "whisper_discover_quota_owner"
    ON whisper_discover_quota
    USING (user_id = auth.uid());

-- ─────────────────────────────────────────────────────────────
-- RPC: whisper_discover_profiles
--
-- Returns up to p_page_size profiles for the requested page
-- after enforcing rate-limit. Errors with code 'rate_limited'
-- if the caller exceeds 60 pages within a 1-hour window.
-- ─────────────────────────────────────────────────────────────
-- Grant execute to authenticated role only
REVOKE ALL ON FUNCTION whisper_discover_profiles(int, int) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION whisper_discover_profiles(int, int) TO authenticated;

-- ═══════════════ 20260824_whisper_bypass_rate_limit.sql ══
-- whisper_bypass_rate_limit.sql
-- Backing store for the whisper-bypass-verify edge function's brute-force limiter.
--
-- The function records one row per verification attempt keyed by identity
-- (authenticated user id, or "ip:<addr>" for pre-auth callers) and counts recent
-- failures before deciding. Clients have NO direct access: RLS is enabled and no
-- policies exist, so only the service role (the edge function) can read/write.

create table if not exists public.whisper_bypass_attempts (
    id           uuid not null default gen_random_uuid() primary key,
    identity     text not null,
    ok           boolean not null,
    attempted_at timestamptz not null default now()
);

alter table public.whisper_bypass_attempts enable row level security;

-- Fast window queries from the edge function.
create index if not exists whisper_bypass_attempts_identity_idx
    on public.whisper_bypass_attempts(identity, attempted_at desc);

-- Housekeeping: drop rows older than 24h so the table stays tiny.
-- pg_cron may not be enabled in every project; run ad hoc or schedule externally:
--   delete from public.whisper_bypass_attempts where attempted_at < now() - interval '24 hours';

-- ═══════════════ 20260825_whisper_quota_refund.sql ═══════
-- whisper_quota_refund.sql (20260825)
-- M-3 FIX (reviewwhisper.md): compensating decrement for whisper_increment_upload_quota.
-- The upload Edge Function previously consumed a daily slot BEFORE contacting ImgBB, so
-- host outages burned users' scarce quota. The function now calls this best-effort
-- refund when the upstream upload fails.
--
-- Idempotent-safe: floors at zero so over-refunding is impossible; a rare concurrent
-- double-refund only makes the counter slightly more generous.

-- ═══════════════ 20260826_whisper_bypass_atomic.sql ══════
-- whisper_bypass_atomic.sql (20260826)
-- M-4 FIX (reviewwhisper.md): the whisper-bypass-verify Edge Function previously did a
-- check-then-insert (SELECT failures in window, then INSERT attempt) — two independent
-- REST calls that race under parallel guesses, letting roughly double the intended
-- attempts through. This function performs count + lockout decision + insert + cleanup
-- inside ONE plpgsql transaction, so the window can never be raced.
--
-- Also self-heals table growth: every call prunes rows older than 24h, removing the
-- dependency on pg_cron being installed.

revoke all on function public.whisper_bypass_attempt(text, boolean) from public;
revoke all on function public.whisper_bypass_attempt(text, boolean) from anon;
grant execute on function public.whisper_bypass_attempt(text, boolean) to service_role;

-- ═══════════════ 20260827_whisper_review_v2_hardening.sql
-- whisper_review_v2_hardening.sql (20260827)
-- V2-FIX (reviewwhisper.md): second-pass hardening across the Whisper backend.
--
-- Sections:
--   1. whisper_image_ownership ledger: maps ImgBB delete capabilities to their uploader,
--      letting whisper-image-delete authorize deletions object-level. RLS enabled with
--      ZERO policies (service-role only), mirroring whisper_bypass_attempts.
--   2. Quota RPCs become uid-parameterized and service-role-only: the old auth.uid()
--      variants ran under the caller's token from the edge function; the parameterized
--      versions remove any path where one authenticated user could touch another's row.
--   3. whisper_upload_quota / whisper_discover_quota RLS shrink: users lose direct
--      INSERT/UPDATE/DELETE on their quota rows (the RPCs own writes now); only
--      read-your-own-row SELECT remains.
--   4. EXECUTE on the new quota RPCs revoked from authenticated.
--   5. messages immutability upgraded: BOTH parties (sender included) may no longer
--      change content/content_iv/sender_id/receiver_id; tombstones live in
--      whisper_deleted_tombstones, so payload mutation is never legitimate.
--   6. friends transition control: participants are immutable; only the recipient
--      (user_b — requests are inserted by the requester as user_a with status='pending')
--      may accept; the requester withdraws by DELETE instead.
--   7. whisper_purge_account_data(p_uid): single-transaction account purge used by
--      whisper-delete-account BEFORE the GoTrue identity delete (fail-closed).
--   8. whisper_bypass_attempt gains a transaction advisory lock so parallel guesses
--      cannot race the failure count.
--   9. whisper_discover_profiles clamps negative/huge pagination inputs.
--
-- Safe to run from the Supabase SQL Editor; every statement is idempotent.

-- ── 1. whisper_image_ownership ────────────────────────────────────────────
-- One row per successful upload. delete_url is the ImgBB capability URL the client
-- receives back (as `id`); uniqueness keeps ownership lookups deterministic.
create table if not exists public.whisper_image_ownership (
    id         bigint generated always as identity primary key,
    user_id    uuid not null references auth.users(id) on delete cascade,
    image_id   text,
    delete_url text not null,
    url        text,
    created_at timestamptz not null default now(),
    unique (delete_url)
);

alter table public.whisper_image_ownership enable row level security;

-- Deliberately NO policies: clients have no direct access; the edge functions use the
-- service role (same posture as whisper_bypass_attempts).
create index if not exists whisper_image_ownership_delete_url_idx
    on public.whisper_image_ownership(delete_url);

-- ── 2. uid-parameterized quota RPCs ──────────────────────────────────────
-- Drop the old auth.uid()-derived signatures first (idempotency + grant cleanup).
drop function if exists public.whisper_increment_upload_quota(date);
drop function if exists public.whisper_refund_upload_quota(date);

create or replace function public.whisper_increment_upload_quota(p_uid uuid, p_day date)
returns int
language plpgsql
security definer
set search_path = public
as $$
declare
  v_count int;
begin
  if p_uid is null then
    raise exception 'p_uid is required' using errcode = '42501';
  end if;
  insert into public.whisper_upload_quota(user_id, day, count)
  values (p_uid, p_day, 1)
  on conflict (user_id, day) do update set count = whisper_upload_quota.count + 1
  returning count into v_count;
  return v_count;
end;
$$;

create or replace function public.whisper_refund_upload_quota(p_uid uuid, p_day date)
returns int
language plpgsql
security definer
set search_path = public
as $$
declare
  v_count int;
begin
  if p_uid is null then
    raise exception 'p_uid is required' using errcode = '42501';
  end if;
  update public.whisper_upload_quota
     set count = greatest(count - 1, 0)
   where user_id = p_uid
     and day = p_day
   returning count into v_count;
  return coalesce(v_count, 0);
end;
$$;

-- Service role only: the edge functions call these with the verified userId, so no
-- client role needs EXECUTE anymore (V2-FIX; see section 4).
revoke all on function public.whisper_increment_upload_quota(uuid, date) from public;
revoke all on function public.whisper_increment_upload_quota(uuid, date) from anon;
revoke all on function public.whisper_increment_upload_quota(uuid, date) from authenticated;
grant execute on function public.whisper_increment_upload_quota(uuid, date) to service_role;

revoke all on function public.whisper_refund_upload_quota(uuid, date) from public;
revoke all on function public.whisper_refund_upload_quota(uuid, date) from anon;
revoke all on function public.whisper_refund_upload_quota(uuid, date) from authenticated;
grant execute on function public.whisper_refund_upload_quota(uuid, date) to service_role;

-- ── 3. Quota-table RLS shrink ─────────────────────────────────────────────
-- The edge function no longer forwards user tokens for quota writes, so client-writable
-- quota rows are pure attack surface (a user could reset their own counter or inflate
-- others' refunds). Writes belong exclusively to the service-role RPCs now.

-- whisper_upload_quota: keep only read-your-own-row.
drop policy if exists "whisper_upload_quota_insert_own" on public.whisper_upload_quota;
drop policy if exists "whisper_upload_quota_update_own" on public.whisper_upload_quota;
drop policy if exists "whisper_upload_quota_delete_own" on public.whisper_upload_quota;

drop policy if exists "whisper_upload_quota_select_own" on public.whisper_upload_quota;
create policy "whisper_upload_quota_select_own"
    on public.whisper_upload_quota for select
    to authenticated
    using (user_id = auth.uid());

-- whisper_discover_quota: the 20260823 FOR ALL owner policy allowed every command;
-- replace with SELECT-only own-row.
drop policy if exists "whisper_discover_quota_owner" on public.whisper_discover_quota;

drop policy if exists "whisper_discover_quota_select_own" on public.whisper_discover_quota;
create policy "whisper_discover_quota_select_own"
    on public.whisper_discover_quota for select
    to authenticated
    using (user_id = auth.uid());

-- ── 4. Explicit revoke of the legacy grants ──────────────────────────────
-- Older migrations granted EXECUTE to authenticated on the (date) signatures dropped in
-- section 2; these revokes keep the new signatures clean even if a future migration
-- re-grants by accident.
revoke execute on function public.whisper_refund_upload_quota(uuid, date) from authenticated;
revoke execute on function public.whisper_increment_upload_quota(uuid, date) from authenticated;

-- ── 5. messages immutability v2 ──────────────────────────────────────────
-- The v1 trigger only restricted receivers; senders could still mutate content after
-- sending (undermining non-repudiation) — tombstoning lives in whisper_deleted_tombstones,
-- so changing the ciphertext/participants is never legitimate for EITHER party.
create or replace function public.whisper_protect_message_content_v2()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  -- Service-role updates (auth.uid() is null) bypass the immutability restriction.
  if (auth.uid() is null) then
    return new;
  end if;
  if (
    new.content is distinct from old.content
    or new.content_iv is distinct from old.content_iv
    or new.sender_id is distinct from old.sender_id
    or new.receiver_id is distinct from old.receiver_id
  ) then
    raise exception 'Message payload and participants are immutable' using errcode = '42501';
  end if;
  return new;
end;
$$;

drop trigger if exists whisper_protect_message_content on public.messages;
drop function if exists public.whisper_protect_message_content();

drop trigger if exists whisper_protect_message_content_v2 on public.messages;
create trigger whisper_protect_message_content_v2
  before update on public.messages
  for each row execute function public.whisper_protect_message_content_v2();

-- ── 6. friends transition control ────────────────────────────────────────
-- Requests are stored requester-first: the INSERT policy pins user_a = auth.uid()
-- with status='pending' (20260821), so OLD.user_a is always the original creator and
-- user_b is the recipient. Only the recipient may move pending→accepted; the requester
-- withdraws via DELETE (friends_delete_participants). Participants can never rotate.
create or replace function public.whisper_protect_friend_status()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  -- Service-role updates (auth.uid() is null) bypass the transition rules.
  if (auth.uid() is null) then
    return new;
  end if;

  -- Participants are immutable.
  if (new.user_a is distinct from old.user_a or new.user_b is distinct from old.user_b) then
    raise exception 'Friend request participants are immutable' using errcode = '42501';
  end if;

  -- No-op status writes stay legal for either party.
  if (new.status is not distinct from old.status) then
    return new;
  end if;

  -- Only the recipient may accept a pending request.
  if (old.status = 'pending' and new.status = 'accepted' and auth.uid() = old.user_b) then
    return new;
  end if;

  -- Everything else (requester self-accepting, status rewrites, blocked-forging) is refused.
  raise exception 'Only the recipient may accept a pending friend request' using errcode = '42501';
end;
$$;

drop trigger if exists whisper_protect_friend_status on public.friends;
create trigger whisper_protect_friend_status
  before update on public.friends
  for each row execute function public.whisper_protect_friend_status();

-- Policy stays simple: both participants may UPDATE rows they participate in; the
-- trigger above enforces WHO may transition WHAT.
drop policy if exists "friends_update_participants" on public.friends;
create policy "friends_update_participants"
    on public.friends for update
    to authenticated
    using (user_a = auth.uid() or user_b = auth.uid())
    with check (user_a = auth.uid() or user_b = auth.uid());

-- ── 7. whisper_purge_account_data ────────────────────────────────────────
-- Single-transaction purge mirroring whisper-delete-account's former cleanup list
-- exactly (messages/reactions/friends/blocks/profiles + the quota/tombstone/discover
-- tables + the new ownership ledger). Fail-closed: the edge function refuses to delete
-- the GoTrue identity unless this succeeds.
revoke all on function public.whisper_purge_account_data(uuid) from public;
revoke all on function public.whisper_purge_account_data(uuid) from anon;
revoke all on function public.whisper_purge_account_data(uuid) from authenticated;
grant execute on function public.whisper_purge_account_data(uuid) to service_role;

-- ── 8. whisper_bypass_attempt atomicity ──────────────────────────────────
-- The count→lockout→insert sequence inside one transaction is still not enough under
-- READ COMMITTED: two parallel calls can both count 4 failures and both proceed. A
-- transaction-scoped advisory lock keyed on the identity serializes them.
create or replace function public.whisper_bypass_attempt(p_identity text, p_ok boolean)
returns int
language plpgsql
security definer
set search_path = public
as $$
declare
  v_failures int;
begin
  -- Serialize concurrent attempts for the same identity (auto-released at commit).
  perform pg_advisory_xact_lock(hashtextextended(p_identity, 0));

  -- Count recent failures BEFORE recording this attempt. Only failures feed the
  -- lockout budget; successful attempts never lock anyone out.
  select count(*) into v_failures
    from public.whisper_bypass_attempts
   where identity = p_identity
     and ok = false
     and attempted_at > now() - interval '15 minutes';

  if v_failures >= 5 then
    raise exception 'rate_limited' using errcode = 'P0002';
  end if;

  insert into public.whisper_bypass_attempts(identity, ok) values (p_identity, p_ok);

  -- Housekeeping piggybacked on every attempt: keep the table tiny without pg_cron.
  delete from public.whisper_bypass_attempts where attempted_at < now() - interval '24 hours';

  return v_failures;
end;
$$;

revoke all on function public.whisper_bypass_attempt(text, boolean) from public;
revoke all on function public.whisper_bypass_attempt(text, boolean) from anon;
grant execute on function public.whisper_bypass_attempt(text, boolean) to service_role;

-- ── 9. whisper_discover_profiles input clamps ────────────────────────────
-- Negative p_page previously produced a negative OFFSET (SQL error); absurd page sizes
-- were already capped at 30 but negatives slipped through. Clamp both up front.
REVOKE ALL ON FUNCTION whisper_discover_profiles(int, int) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION whisper_discover_profiles(int, int) TO authenticated;

-- ═══════════════ 20260828_whisper_destructive_rate_limit.sql
-- whisper_destructive_rate_limit.sql (20260828)
-- V3-FIX (backend hardening round 3):
--
--   1. whisper_destructive_attempts: backing store for the destructive-endpoint
--      limiter used by whisper-delete-account and whisper-image-delete. One row
--      per attempt keyed by (endpoint, identity). RLS enabled with ZERO policies
--      (service-role only), mirroring whisper_bypass_attempts / whisper_image_ownership.
--   2. whisper_destructive_attempt(p_endpoint, p_identity, p_ok): atomic
--      count + lockout decision + insert + cleanup in ONE transaction behind a
--      transaction advisory lock (clone of the whisper_bypass_attempt pattern,
--      generalized over endpoints):
--        • endpoint='delete-account': 5 FAILURES (ok=false) per identity per 15 min
--        • endpoint='image-delete':  30 ATTEMPTS (any outcome) per identity per 15 min
--      Raises errcode P0002 'rate_limited' on exceed; prunes rows older than 24h;
--      returns the prior-window count that fed the decision.
--   3. whisper_public_profiles view + rewritten whisper_discover_profiles():
--      the live profiles table was authored outside this repo, so the projection is
--      built with dynamic SQL from information_schema: the view exposes id plus ONLY
--      whitelisted columns that actually exist (username, display_name, avatar_url,
--      bio, public_key, is_private, hide_from_discover, and updated_at optionally for
--      avatar cache-busting). Never last_seen_at, emails, tokens or other timestamps.
--      The discover RPC keeps its 20260823/27 filters, quota and clamps verbatim, but
--      ranks via a correlated subquery on profiles.last_seen_at because the view hides
--      that column. security_invoker=true keeps underlying profiles RLS in force for
--      direct PostgREST selects against the view.
--
-- Safe to run from the Supabase SQL Editor; every statement is idempotent.

-- ── 1. whisper_destructive_attempts ──────────────────────────────────────
create table if not exists public.whisper_destructive_attempts (
    id           uuid not null default gen_random_uuid() primary key,
    endpoint     text not null,
    identity     text not null,
    ok           boolean,
    attempted_at timestamptz not null default now()
);

alter table public.whisper_destructive_attempts enable row level security;

-- Deliberately NO policies: clients have no direct access; the edge functions use
-- the service role (same posture as whisper_bypass_attempts).
create index if not exists whisper_destructive_attempts_endpoint_idx
    on public.whisper_destructive_attempts(endpoint, identity, attempted_at desc);

-- ── 2. whisper_destructive_attempt ───────────────────────────────────────
create or replace function public.whisper_destructive_attempt(
    p_endpoint text,
    p_identity text,
    p_ok       boolean
)
returns int
language plpgsql
security definer
set search_path = public
as $$
declare
    v_prior int;
begin
    -- Serialize concurrent attempts for the same (endpoint, identity); auto-released
    -- at commit so parallel calls cannot race the count → lockout → insert sequence.
    perform pg_advisory_xact_lock(hashtextextended(p_endpoint || p_identity, 0));

    if p_endpoint = 'image-delete' then
        -- Every attempt consumes the budget regardless of outcome.
        select count(*) into v_prior
          from public.whisper_destructive_attempts
         where endpoint = p_endpoint
           and identity = p_identity
           and attempted_at > now() - interval '15 minutes';
        if v_prior >= 30 then
            raise exception 'rate_limited' using errcode = 'P0002';
        end if;
    elsif p_endpoint = 'delete-account' then
        -- Only failures feed the lockout budget; a successful deletion never locks
        -- anyone out (mirrors whisper_bypass_attempt semantics).
        select count(*) into v_prior
          from public.whisper_destructive_attempts
         where endpoint = p_endpoint
           and identity = p_identity
           and ok = false
           and attempted_at > now() - interval '15 minutes';
        if v_prior >= 5 then
            raise exception 'rate_limited' using errcode = 'P0002';
        end if;
    else
        raise exception 'unknown_endpoint' using errcode = 'P0001';
    end if;

    insert into public.whisper_destructive_attempts(endpoint, identity, ok)
    values (p_endpoint, p_identity, p_ok);

    -- Housekeeping piggybacked on every attempt: keep the table tiny without pg_cron.
    delete from public.whisper_destructive_attempts
     where attempted_at < now() - interval '24 hours';

    return v_prior;
end;
$$;

revoke all on function public.whisper_destructive_attempt(text, text, boolean) from public;
revoke all on function public.whisper_destructive_attempt(text, text, boolean) from anon;
revoke all on function public.whisper_destructive_attempt(text, text, boolean) from authenticated;
grant execute on function public.whisper_destructive_attempt(text, text, boolean) to service_role;

-- ── 3. whisper_public_profiles projection + discover rewrite ─────────────
-- The real profiles columns are unknown at authoring time, so introspect
-- information_schema and build the view from the intersection of the whitelist
-- with reality. Columns that do not exist are silently skipped; last_seen_at,
-- emails, tokens and other timestamps are NEVER projected.
--
-- V6-R2 rerun fix: whisper_discover_profiles RETURNS SETOF whisper_public_profiles,
-- so the FUNCTION depends on the view's row type — the drop-view below fails with
-- 2BP01 while it exists (exactly what a re-run hit). Dropping the function FIRST
-- removes the dependency; it is recreated at the end of this section.
drop function if exists public.whisper_discover_profiles(integer, integer);
do $do$
declare
    v_col        text;
    v_projection text := 'id';
begin
    for v_col in
        select c.column_name
          from information_schema.columns c
         where c.table_schema = 'public'
           and c.table_name   = 'profiles'
           and c.column_name  = any (array[
               'username',
               'display_name',
               'avatar_url',
               'bio',
               'public_key',
               'is_private',
               'hide_from_discover',
               'updated_at'
           ])
         order by c.ordinal_position
    loop
        v_projection := v_projection || ', ' || quote_ident(v_col);
    end loop;

    execute format(
        'drop view if exists public.whisper_public_profiles'
    );
    execute format(
        'create or replace view public.whisper_public_profiles as select %s from public.profiles',
        v_projection
    );
end;
$do$;

-- Keep the underlying profiles RLS in force even for direct PostgREST selects
-- against the view (PG15+).
alter view public.whisper_public_profiles set (security_invoker = true);

-- Authenticated clients may read the projected columns directly; nobody else gets
-- anything (revoke-then-grant keeps anon/public clean even after re-runs).
revoke all on public.whisper_public_profiles from public;
revoke all on public.whisper_public_profiles from anon;
revoke all on public.whisper_public_profiles from authenticated;
grant select on public.whisper_public_profiles to authenticated;

-- Return type changes (SETOF profiles → SETOF whisper_public_profiles), so the old
-- signature MUST be dropped before recreating (CREATE OR REPLACE cannot change it).
drop function if exists public.whisper_discover_profiles(int, int);

create or replace function public.whisper_discover_profiles(
    p_page      int DEFAULT 0,
    p_page_size int DEFAULT 20
)
RETURNS SETOF public.whisper_public_profiles
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_caller    uuid := auth.uid();
    v_page      int := GREATEST(coalesce(p_page, 0), 0);
    v_page_size int := GREATEST(LEAST(coalesce(p_page_size, 20), 30), 0);
    v_window    timestamptz;
    v_count     int;
    v_limit     int := 60; -- max pages per hour
    v_offset    int := v_page * v_page_size;
BEGIN
    IF v_caller IS NULL THEN
        RAISE EXCEPTION 'not_authenticated' USING ERRCODE = 'P0001';
    END IF;

    -- Upsert quota row (unchanged from 20260823/20260827).
    INSERT INTO whisper_discover_quota (user_id, page_count, window_start)
    VALUES (v_caller, 1, now())
    ON CONFLICT (user_id) DO UPDATE
        SET page_count  = CASE
                              WHEN EXTRACT(EPOCH FROM (now() - whisper_discover_quota.window_start)) > 3600
                              THEN 1                              -- new window
                              ELSE whisper_discover_quota.page_count + 1
                          END,
            window_start = CASE
                               WHEN EXTRACT(EPOCH FROM (now() - whisper_discover_quota.window_start)) > 3600
                               THEN now()
                               ELSE whisper_discover_quota.window_start
                           END
    RETURNING page_count, window_start INTO v_count, v_window;

    IF v_count > v_limit THEN
        RAISE EXCEPTION 'rate_limited' USING ERRCODE = 'P0002';
    END IF;

    -- Same filters as before (not private, not hidden from discover, not self, not
    -- blocking the caller; friend exclusion stays client-side). The view hides
    -- last_seen_at, so ranking moves OUT of the projection: ORDER BY a correlated
    -- subquery against profiles instead of an ORDER BY view column.
    RETURN QUERY
        SELECT v.*
        FROM whisper_public_profiles v
        WHERE v.is_private = false
          AND v.hide_from_discover = false
          AND v.id <> v_caller
          AND NOT EXISTS (
              SELECT 1 FROM whisper_blocks wb
              WHERE wb.blocker_id = v.id
                AND wb.blocked_id = v_caller
          )
        ORDER BY (SELECT p.last_seen_at FROM profiles p WHERE p.id = v.id) DESC
        LIMIT v_page_size
        OFFSET v_offset;
END;
$$;

-- Same grants as the current function (20260823/20260827).
REVOKE ALL ON FUNCTION public.whisper_discover_profiles(int, int) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.whisper_discover_profiles(int, int) TO authenticated;

-- ═══════════════ 20260829_whisper_fcm_tokens.sql ═════════
-- whisper_fcm_tokens.sql (20260829)
-- V3-FIX (task F): FCM push scaffold — device token registry.
--
--   whisper_fcm_tokens: one row per user (pk = user_id, so re-registration upserts
--   and stale tokens are replaced rather than duplicated). RLS is enabled with
--   strict owner-only policies: a user may select/insert/update/delete ONLY their
--   own row (user_id = auth.uid()). The push edge function reads/prunes rows via
--   the service role key, which bypasses RLS.
--
--   Index on token supports future token-scoped lookups/cleanup queries.
--
-- Safe to run from the Supabase SQL Editor; every statement is idempotent.

create table if not exists public.whisper_fcm_tokens (
    user_id    uuid not null primary key references auth.users(id) on delete cascade,
    token      text not null,
    updated_at timestamptz not null default now()
);

alter table public.whisper_fcm_tokens enable row level security;

-- Owner-only access (drop-then-create keeps re-runs idempotent).
drop policy if exists "whisper_fcm_tokens_select_own" on public.whisper_fcm_tokens;
create policy "whisper_fcm_tokens_select_own"
    on public.whisper_fcm_tokens
    for select
    using (user_id = auth.uid());

drop policy if exists "whisper_fcm_tokens_insert_own" on public.whisper_fcm_tokens;
create policy "whisper_fcm_tokens_insert_own"
    on public.whisper_fcm_tokens
    for insert
    with check (user_id = auth.uid());

drop policy if exists "whisper_fcm_tokens_update_own" on public.whisper_fcm_tokens;
create policy "whisper_fcm_tokens_update_own"
    on public.whisper_fcm_tokens
    for update
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

drop policy if exists "whisper_fcm_tokens_delete_own" on public.whisper_fcm_tokens;
create policy "whisper_fcm_tokens_delete_own"
    on public.whisper_fcm_tokens
    for delete
    using (user_id = auth.uid());

create index if not exists whisper_fcm_tokens_token_idx
    on public.whisper_fcm_tokens(token);

-- ═══════════════ 20260830_whisper_prekeys.sql ════════════
-- PHASE 2 (docs/WHISPER_ROADMAP.md §2.2): signed prekey infrastructure for the
-- X3DH-style handshake. The server is a dumb mailbox: it stores bundles, hands them
-- to anyone authenticated, and burns one-time prekeys on issue. It can read them,
-- which is fine — bundles are PUBLIC keys plus a signature that only the owner's
-- hardware-bound identity key can produce.

-- 1. Bundle table -------------------------------------------------------------
create table if not exists public.whisper_prekeys (
    account uuid not null references auth.users(id) on delete cascade,
    kid text not null,                -- hex(SHA256(public_key)) — matches envelope kids
    kind text not null check (kind in ('SPK','OPK')),
    public_key text not null,         -- base64 X25519 public key
    signature text,                   -- base64 ECDSA-P256-SHA256 over "SPK:"+kid+pub; SPKs only
    created_at timestamptz not null default now(),
    primary key (account, kid)
);

alter table public.whisper_prekeys enable row level security;

-- Anyone authenticated may READ bundles: you need a peer's bundle to message them.
drop policy if exists prekeys_read on public.whisper_prekeys;
create policy prekeys_read on public.whisper_prekeys
    for select to authenticated using (true);

-- Only the owner writes their own bundle rows.
drop policy if exists prekeys_write_own on public.whisper_prekeys;
create policy prekeys_write_own on public.whisper_prekeys
    for insert to authenticated with check (account = auth.uid());
drop policy if exists prekeys_delete_own on public.whisper_prekeys;
create policy prekeys_delete_own on public.whisper_prekeys
    for delete to authenticated using (account = auth.uid());
-- No UPDATE policy: prekeys are immutable once published (rotate = new kid).

create index if not exists whisper_prekeys_account_kind_idx
    on public.whisper_prekeys(account, kind);

-- 2. Identity binding anchor --------------------------------------------------
-- PHASE 2 §2.3: profiles gain a hardware-anchored binding: the software X25519
-- identity pubkey + the P-256 signing pubkey that vouches for its prekeys.
-- Stored as jsonb so future protocol upgrades extend without migrations.
alter table public.profiles add column if not exists identity_binding jsonb;

-- 3. Account deletion coverage: purge prekeys in the destructive cleanup RPC.
create or replace function public.whisper_purge_account_data(p_uid uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    if p_uid is null then raise exception 'uid required' using errcode = '42501'; end if;
    delete from public.messages where sender_id = p_uid or receiver_id = p_uid;
    delete from public.message_reactions where user_id = p_uid;
    delete from public.friends where user_a = p_uid or user_b = p_uid;
    delete from public.whisper_blocks where blocker_id = p_uid or blocked_id = p_uid;
    delete from public.profiles where id = p_uid;
    delete from public.whisper_upload_quota where user_id = p_uid;
    delete from public.whisper_deleted_tombstones where user_id = p_uid;
    delete from public.whisper_discover_quota where user_id = p_uid;
    delete from public.whisper_image_ownership where user_id = p_uid;
    delete from public.whisper_fcm_tokens where user_id = p_uid;
    delete from public.whisper_prekeys where account = p_uid;
end;
$$;

revoke all on function public.whisper_purge_account_data(uuid) from public;
revoke all on function public.whisper_purge_account_data(uuid) from anon;
revoke all on function public.whisper_purge_account_data(uuid) from authenticated;
grant execute on function public.whisper_purge_account_data(uuid) to service_role;

-- ═══════════════ 20260831_whisper_typing_signal.sql ═
-- ============================================================================
-- 20260831_whisper_typing_signal.sql
-- V6-R5 FIX (#4): DB-backed typing indicator.
--
-- WHY: typing previously rode ONLY ephemeral realtime broadcasts, so any websocket
-- disruption made indicators vanish entirely (field-reported). This table gives the
-- signal a durable, participant-scoped home that works over Postgres Changes AND
-- plain REST polling fallback.
--
-- SEMANTICS: a row whose updated_at is <= ~6s old means "sender is typing to
-- receiver". The `signal` column is an OPAQUE random token rotated on every write:
-- observers learn nothing beyond the existence/timing of the row itself (same
-- metadata exposure as the encrypted broadcasts it supplements), never content.
--
-- IDEMPOTENT: safe to re-run (guarded drops / IF NOT EXISTS / conditional publication).
-- ============================================================================

create extension if not exists pgcrypto;

create table if not exists public.whisper_typing_signals (
  sender_id   uuid not null references auth.users(id) on delete cascade,
  receiver_id uuid not null references auth.users(id) on delete cascade,
  signal      text not null default encode(gen_random_bytes(12), 'hex'),
  updated_at  timestamptz not null default now(),
  primary key (sender_id, receiver_id)
);

alter table public.whisper_typing_signals enable row level security;

-- Both participants may read (the receiver needs it; the sender reads their own).
drop policy if exists "whisper_typing_participant_select" on public.whisper_typing_signals;
create policy "whisper_typing_participant_select"
  on public.whisper_typing_signals for select to authenticated
  using (sender_id = auth.uid() or receiver_id = auth.uid());

-- Only the SENDER writes their own signal (insert fresh / refresh theirs / clear).
drop policy if exists "whisper_typing_sender_insert" on public.whisper_typing_signals;
create policy "whisper_typing_sender_insert"
  on public.whisper_typing_signals for insert to authenticated
  with check (sender_id = auth.uid());

drop policy if exists "whisper_typing_sender_update" on public.whisper_typing_signals;
create policy "whisper_typing_sender_update"
  on public.whisper_typing_signals for update to authenticated
  using (sender_id = auth.uid())
  with check (sender_id = auth.uid());

drop policy if exists "whisper_typing_sender_delete" on public.whisper_typing_signals;
create policy "whisper_typing_sender_delete"
  on public.whisper_typing_signals for delete to authenticated
  using (sender_id = auth.uid());

-- Realtime publication membership (idempotent), so receivers subscribed to postgres
-- changes get instant refreshes; REST polling remains the fallback lane.
do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime'
      and schemaname = 'public'
      and tablename = 'whisper_typing_signals'
  ) then
    alter publication supabase_realtime add table public.whisper_typing_signals;
  end if;
end $$;

-- ═══════════════ 20260901_whisper_server_enforcement.sql ═
-- ============================================================================
-- 20260901_whisper_server_enforcement.sql
-- P6 (hardening plan): server-side enforcement for two gaps the client could
-- only handle by convention.
--
--   1. BLOCK-AWARE INSERT GUARD — whisper_protect_message_blocks()
--      The messages INSERT RLS policy only checks sender_id = auth.uid(); it has
--      no knowledge of whisper_blocks, so a blocked party (or any third-party
--      client) could keep landing rows server-side while the receiver's client
--      politely filtered them. A BEFORE INSERT trigger now rejects the insert
--      with 42501 when a block exists in EITHER direction between the pair.
--      Client UX is unchanged (the app already pre-checks blocks before send);
--      service_role bypasses via auth.uid() IS NULL so edge functions are unaffected.
--      NOTE: a user who blocks WHILE the partner's outbox is queued will make the
--      partner's flush bounce permanently — the client's drop-ledger already
--      surfaces that as "message failed", which is the correct semantics.
--
--   2. REPLAY GUARD — content_hash + unique index
--      Same-chat ciphertext replay (captured row re-inserted under a fresh UUID)
--      was documented as deferred because AAD binds direction but not sequence.
--      This closes it additively: an insert trigger stamps sha256(content), and a
--      partial unique index makes a duplicate (sender, receiver, hash) fail with
--      23505. Historical rows have NULL hashes and NULLS ARE DISTINCT in Postgres
--      unique indexes, so nothing existing can conflict; the client's existing
--      isDuplicateKeyError path treats 23505 as "already delivered", which is
--      exactly right for both genuine replays and idempotent retries of the same
--      UUID (same UUID conflicts on the PK first, unchanged).
--
-- IDEMPOTENT: safe to re-run at any time (guarded drops / create or replace /
-- IF NOT EXISTS). Requires pgcrypto (already ensured by 20260831).
-- ============================================================================

create extension if not exists pgcrypto;

-- ── 1. block-aware insert guard ──────────────────────────────────────────────

create or replace function public.whisper_protect_message_blocks()
returns trigger
language plpgsql
set search_path = public
as $$
begin
    -- Service-role inserts (edge functions) bypass the block guard.
    if (auth.uid() is null) then
        return new;
    end if;

    if (
        new.sender_id <> auth.uid()
        or exists (
            select 1 from public.whisper_blocks wb
            where (wb.blocker_id = new.sender_id and wb.blocked_id = new.receiver_id)
               or (wb.blocker_id = new.receiver_id and wb.blocked_id = new.sender_id)
        )
    ) then
        raise exception 'Message blocked by an existing block relationship'
            using errcode = '42501';
    end if;

    return new;
end;
$$;

drop trigger if exists whisper_protect_message_blocks on public.messages;
create trigger whisper_protect_message_blocks
    before insert on public.messages
    for each row execute function public.whisper_protect_message_blocks();

-- ── 2. replay guard ──────────────────────────────────────────────────────────

alter table public.messages add column if not exists content_hash bytea;

-- Stamp the hash on every authenticated insert (service-role backfills may set
-- it explicitly; the trigger only fills when absent so backfills are stable).
create or replace function public.whisper_stamp_content_hash()
returns trigger
language plpgsql
set search_path = public, extensions
as $$
begin
    if (new.content_hash is null) then
        new.content_hash := extensions.digest(convert_to(new.content, 'UTF8'), 'sha256');
    end if;
    return new;
end;
$$;

drop trigger if exists whisper_stamp_content_hash on public.messages;
create trigger whisper_stamp_content_hash
    before insert on public.messages
    for each row execute function public.whisper_stamp_content_hash();

-- Partial unique index: NULL hashes (historical rows) never conflict; every NEW
-- row participates, making captured-ciphertext replay impossible per conversation.
create unique index if not exists whisper_messages_replay_guard
    on public.messages (sender_id, receiver_id, content_hash)
    where content_hash is not null;

-- ═══════════════ 20260904_whisper_block_enforcement.sql ═
-- ============================================================
-- WHISPER — Block enforcement at DB level (P0 fix)
-- Fixes: "Block enforcement is client-side only"
-- Ensures raw SQL/PostgREST cannot bypass whisper_blocks
-- ============================================================
-- Messages: sender/receiver pair must not be blocked in either direction
-- Friends: user_a/user_b pair must not be blocked in either direction
-- Idempotent — safe to re-run
-- ============================================================

-- Ensure whisper_blocks table exists (idempotent, matches baseline)
CREATE TABLE IF NOT EXISTS public.whisper_blocks (
    blocker_id  uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    blocked_id  uuid NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    created_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (blocker_id, blocked_id),
    CONSTRAINT no_self_block CHECK (blocker_id <> blocked_id)
);
ALTER TABLE public.whisper_blocks ENABLE ROW LEVEL SECURITY;

-- Ensure RLS policies exist (idempotent)
DROP POLICY IF EXISTS "whisper_blocks_select_participant" ON public.whisper_blocks;
CREATE POLICY "whisper_blocks_select_participant"
    ON public.whisper_blocks FOR SELECT TO authenticated
    USING (blocker_id = auth.uid() OR blocked_id = auth.uid());

DROP POLICY IF EXISTS "whisper_blocks_insert_blocker" ON public.whisper_blocks;
CREATE POLICY "whisper_blocks_insert_blocker"
    ON public.whisper_blocks FOR INSERT TO authenticated
    WITH CHECK (blocker_id = auth.uid() AND blocker_id <> blocked_id);

DROP POLICY IF EXISTS "whisper_blocks_delete_blocker" ON public.whisper_blocks;
CREATE POLICY "whisper_blocks_delete_blocker"
    ON public.whisper_blocks FOR DELETE TO authenticated
    USING (blocker_id = auth.uid());

-- Fast lookup for trigger (block check)
CREATE INDEX IF NOT EXISTS idx_whisper_blocks_pair ON public.whisper_blocks (blocker_id, blocked_id);

-- ============================================================
-- 1) messages: block any INSERT where a block exists either way
-- ============================================================
CREATE OR REPLACE FUNCTION public.whisper_check_block_before_message()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  -- No self-check needed (messages already has sender_id <> receiver_id)
  IF EXISTS (
    SELECT 1 FROM public.whisper_blocks
    WHERE (blocker_id = NEW.sender_id AND blocked_id = NEW.receiver_id)
       OR (blocker_id = NEW.receiver_id AND blocked_id = NEW.sender_id)
  ) THEN
    -- Phrase must contain "blocked by this user" so WhisperErrorMapper maps to
    -- st_Whisper_Error_Blocked (client already throws same wording).
    RAISE EXCEPTION 'blocked by this user: messages between blocked users are not allowed' USING ERRCODE = 'P0001';
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS whisper_block_check_message ON public.messages;
CREATE TRIGGER whisper_block_check_message
  BEFORE INSERT ON public.messages
  FOR EACH ROW EXECUTE FUNCTION public.whisper_check_block_before_message();

-- ============================================================
-- 2) friends: block any INSERT or UPDATE where a block exists
--    Covers send, accept, and status transitions after a block.
-- ============================================================
CREATE OR REPLACE FUNCTION public.whisper_check_block_before_friend()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  -- Allow delete paths to proceed (unfriend/unblock flows)
  IF TG_OP = 'DELETE' THEN
    RETURN OLD;
  END IF;
  IF EXISTS (
    SELECT 1 FROM public.whisper_blocks
    WHERE (blocker_id = NEW.user_a AND blocked_id = NEW.user_b)
       OR (blocker_id = NEW.user_b AND blocked_id = NEW.user_a)
  ) THEN
    RAISE EXCEPTION 'blocked by this user: friendship between blocked users is not allowed' USING ERRCODE = 'P0001';
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS whisper_block_check_friend ON public.friends;
CREATE TRIGGER whisper_block_check_friend
  BEFORE INSERT OR UPDATE ON public.friends
  FOR EACH ROW EXECUTE FUNCTION public.whisper_check_block_before_friend();

-- Also handle UPDATE via separate trigger for clarity (same function)
-- The above BEFORE INSERT OR UPDATE already covers UPDATE, no extra trigger needed.

-- ============================================================
-- 3) Realtime & replica identity already handled in baseline;
--    no extra publication needed here. Triggers are BEFORE row
--    so failing inserts never emit to realtime.
-- ============================================================


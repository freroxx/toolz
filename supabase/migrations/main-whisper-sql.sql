-- ============================================================================
-- WHISPER — COMBINED BASELINE MIGRATION (single file)
-- ============================================================================
-- This file replaces the thirty-four chronological migrations
--   20260820_whisper_realtime_hardening.sql        20260826_whisper_bypass_atomic.sql
--   20260821_whisper_hardening_fixes.sql           20260827_whisper_review_v2_hardening.sql
--   20260822_whisper_1_0_hardening.sql             20260828_whisper_destructive_rate_limit.sql
--   20260823_whisper_discover_rate_limit.sql       20260829_whisper_fcm_tokens.sql
--   20260824_whisper_bypass_rate_limit.sql         20260830_whisper_prekeys.sql
--   20260825_whisper_quota_refund.sql              20260831_whisper_typing_signal.sql
--   20260901_whisper_server_enforcement.sql        20260904_whisper_block_enforcement.sql
--   20261003_whisper_phase1_hardening.sql
--   20261004_whisper_remove_presence_typing.sql
--   20261005_whisper_signup_discover_hardening.sql
--   20261006_whisper_rotation_cert_transport.sql
--   20261007_whisper_groups_phase2.sql             20261010_whisper_groups_disband.sql
--   20261007_whisper_groups_payload_text_fix.sql   20261010_whisper_groups_receipts_typing.sql
--   20261010_whisper_groups_invites.sql            20261010_whisper_groups_social.sql
--   20261010_whisper_groups_invites_fix.sql        20261011_whisper_groups_adminsend_guard.sql
--   20261010_whisper_groups_retire_add.sql         20261011_whisper_groups_msgkind.sql
--   20261011_whisper_groups_reactions_requests.sql 20261011_whisper_groups_invitee_history.sql
--   20261017_whisper_push_targets.sql
--   20261018_whisper_stories_lifecycle.sql         20261018_whisper_stories_actions.sql
--   20261018_whisper_group_policy_boundary.sql
-- squashed VERBATIM in application order.
-- See whisper-sql-info.md — ALL Whisper SQL changes MUST be merged into this single file.
--
-- IDEMPOTENT (V6-R5 + P6 + 20261003-P1 + 20261004-RM + 20261005-P2 + 20261006-RCT
--             + 20261018-GPB + 20261018-DUA): SAFE TO RE-RUN AT ANY TIME, on any state:
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

-- ═══════════════ 20261003_whisper_phase1_hardening.sql ═
-- 20261003 — Phase 1 SQL + privacy hardening
-- WHY: three gaps. (1) TOMBSTONE BLOCKED: whisper_protect_message_content_v2
--   (20260827 section 5) rejects ANY content/content_iv UPDATE, so the client's
--   delete-for-everyone tombstone write (WhisperRepository.deleteMessageForEveryone)
--   bounces with 42501 and delete-for-everyone silently fails client-side.
--   (2) NO OPT-OUTS: profiles had no presence/typing toggles and typing signals
--   were stored regardless of the receiver's preference. (3) USERNAME ORACLE:
--   checkUsernameAvailable probed profiles with an exact-eq select — an
--   unlimited, unbudgeted enumeration oracle over registered handles.
-- IDEMPOTENT: safe to re-run (ADD COLUMN IF NOT EXISTS + SET DEFAULT + null
--   backfill / CREATE OR REPLACE / guarded drops / IF NOT EXISTS). Amends v2 in
--   place (same name/signature/trigger) so history replays converge; adds one
--   quota table + one RPC + one trigger, all guarded.
-- ============================================================================

-- ── 1. Sender-authorized tombstone UPDATE (amends content_v2) ───────────────
-- The ONLY legal payload mutation is now the SENDER rewriting their OWN row to
-- a tombstone constant (WhisperTombstone: legacy '[deleted_by_sender]',
-- DISPLAY_TEXT 'This message has been deleted', or the named
-- '[deleted_by_sender:<name>]' prefix). Participants stay immutable always;
-- non-payload columns (status/read flags) stay freely updatable by either
-- party. True DELETE stays RLS sender-only (messages_delete_sender) — untouched.
create or replace function public.whisper_protect_message_content_v2()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  -- Service-role updates (auth.uid() is null) bypass the restriction.
  if (auth.uid() is null) then
    return new;
  end if;
  -- Participants are immutable for everyone, tombstone or not.
  if (
    new.sender_id is distinct from old.sender_id
    or new.receiver_id is distinct from old.receiver_id
  ) then
    raise exception 'Message payload and participants are immutable' using errcode = '42501';
  end if;
  -- Non-payload updates (status/read flags) stay legal for either party.
  if (
    new.content is not distinct from old.content
    and new.content_iv is not distinct from old.content_iv
  ) then
    return new;
  end if;
  -- Payload change: only the SENDER tombstoning their OWN row (old row sender
  -- is the caller, which — with immutable participants — also pins
  -- new.sender_id to the caller). The client nulls content_iv on tombstone;
  -- any IV value is accepted so other clients that keep it are not broken.
  if (
    auth.uid() = old.sender_id
    and (
      new.content = '[deleted_by_sender]'
      or new.content = 'This message has been deleted'
      or new.content like '[deleted_by_sender:%]'
    )
  ) then
    return new;
  end if;
  raise exception 'Message payload and participants are immutable' using errcode = '42501';
end;
$$;

drop trigger if exists whisper_protect_message_content_v2 on public.messages;
create trigger whisper_protect_message_content_v2
  before update on public.messages
  for each row execute function public.whisper_protect_message_content_v2();

-- ── 2. Presence / typing opt-outs ──────────────────────────────────────────
-- Owner-scoped toggles, default ON (existing behavior preserved). RLS needs no
-- change: profiles_update_own already restricts UPDATE to id = auth.uid(),
-- which covers the new columns. Clients render presence/typing from
-- whisper_public_profiles and must stop emitting their own signals/stamps when
-- disabled (see WhisperRepository TODOs).
alter table public.profiles add column if not exists presence_enabled boolean not null default true;
alter table public.profiles add column if not exists typing_enabled boolean not null default true;
alter table public.profiles alter column presence_enabled set default true;
alter table public.profiles alter column typing_enabled set default true;
update public.profiles set presence_enabled = true where presence_enabled is null;
update public.profiles set typing_enabled = true where typing_enabled is null;

-- Server-side typing enforcement: writes targeting a receiver who disabled
-- typing are skipped SILENTLY (BEFORE-trigger NULL return → PostgREST sees 0
-- rows, no error), so the sender's throttled retry just no-ops until the stale
-- row ages out. Fail-OPEN when the receiver row is unreadable: a missing
-- profile must never wedge the typing lane.
create or replace function public.whisper_suppress_typing_when_disabled()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_enabled boolean;
begin
  select typing_enabled into v_enabled from public.profiles where id = new.receiver_id;
  if v_enabled = false then
    return null;
  end if;
  return new;
end;
$$;

drop trigger if exists whisper_typing_respect_optout on public.whisper_typing_signals;
create trigger whisper_typing_respect_optout
  before insert or update on public.whisper_typing_signals
  for each row execute function public.whisper_suppress_typing_when_disabled();

-- ── 3. Presence-gated last_seen in whisper_public_profiles + discover ──────
-- The view previously hid last_seen_at entirely while discover still RANKED by
-- the raw column (presence-disabled users leaked ordering signal). The view now
-- projects a gated last_seen_at (NULL when the owner disabled presence) and
-- the discover ranking uses the same gated expression with NULLS LAST, so
-- opted-out users sink instead of floating to the top (DESC defaults NULLS
-- FIRST — the old query never specified null ordering).
-- NOTE: direct PostgREST reads of profiles.last_seen_at stay visible to
-- authenticated users (the open select policy feeds the public_key discovery
-- path); clients MUST render presence from whisper_public_profiles.last_seen_at.
-- V6-R2 rerun pattern: drop the dependent function BEFORE rebuilding the view.
drop function if exists public.whisper_discover_profiles(integer, integer);
do $do$
declare
    v_col         text;
    v_projection  text := 'id';
    v_has_seen    boolean := false;
    v_has_presence boolean := false;
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

    select exists (
        select 1 from information_schema.columns
         where table_schema = 'public' and table_name = 'profiles'
           and column_name = 'last_seen_at'
    ) into v_has_seen;
    select exists (
        select 1 from information_schema.columns
         where table_schema = 'public' and table_name = 'profiles'
           and column_name = 'presence_enabled'
    ) into v_has_presence;

    -- Gate only when BOTH columns exist; otherwise keep the old behavior of
    -- never projecting last_seen_at (never expose it raw).
    if v_has_seen and v_has_presence then
        v_projection := v_projection ||
            ', case when presence_enabled is distinct from false' ||
            ' then last_seen_at else null end as last_seen_at';
    end if;

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

-- Return-type dependency was dropped above; recreate with the SAME quota (60
-- pages/hour unchanged — tightening it would break existing clients; the
-- username oracle below gets the strict budget instead) and presence-gated
-- ranking with explicit NULLS LAST.
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
    v_limit     int := 60; -- max pages per hour (unchanged)
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

    -- Same filters as before (not private, not hidden from discover, not self,
    -- not blocking the caller; friend exclusion stays client-side). Ranking now
    -- uses the presence-gated timestamp so opted-out users are not ordered by
    -- a timestamp nobody is allowed to see; NULLS LAST sinks them (and users
    -- with no stamp at all) to the bottom.
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
        ORDER BY (SELECT CASE WHEN p.presence_enabled IS DISTINCT FROM false
                              THEN p.last_seen_at ELSE NULL END
                    FROM profiles p WHERE p.id = v.id) DESC NULLS LAST
        LIMIT v_page_size
        OFFSET v_offset;
END;
$$;

-- Same grants as the current function (20260823/20260827).
REVOKE ALL ON FUNCTION public.whisper_discover_profiles(int, int) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.whisper_discover_profiles(int, int) TO authenticated;

-- ── 4. Quota-guarded username-availability RPC ─────────────────────────────
-- Replaces unlimited direct exact-eq probes on profiles(username) with a
-- 1-bit SECURITY DEFINER oracle budgeted at 10 checks/hour per caller (advisory
-- lock serializes races like the bypass/destructive limiters). Input is
-- normalized exactly like the client (trim + lowercase) and validated against
-- the canonical handle charset (WhisperAuthManager.USERNAME_PATTERN,
-- lowercased; upper bound 32 generous vs the 20-char registration cap so no
-- legitimate handle is ever rejected). CLIENT MUST MIGRATE: TODO on
-- WhisperRepositoryProfile.checkUsernameAvailable.
create table if not exists public.whisper_username_check_quota (
    user_id     uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    check_count int NOT NULL DEFAULT 0,
    window_start timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id)
);

alter table public.whisper_username_check_quota enable row level security;

-- Deliberately NO policies: only the SECURITY DEFINER RPC below touches this
-- table (same posture as whisper_bypass_attempts / whisper_destructive_attempts).
create index if not exists whisper_username_check_quota_window_idx
    on public.whisper_username_check_quota(user_id, window_start desc);

create or replace function public.whisper_check_username_available(p_username text)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  v_caller uuid := auth.uid();
  v_uname  text := lower(trim(both from coalesce(p_username, '')));
  v_count  int;
  v_window timestamptz;
  v_limit  int := 10; -- max availability checks per hour per caller
  v_taken  boolean;
begin
  if v_caller is null then
    raise exception 'not_authenticated' using errcode = 'P0001';
  end if;
  if v_uname !~ '^[a-z0-9_]{1,32}$' then
    raise exception 'invalid_username' using errcode = 'P0001';
  end if;

  -- Serialize concurrent checks for the same caller (auto-released at commit).
  perform pg_advisory_xact_lock(hashtextextended('username-check:' || v_caller::text, 0));

  insert into public.whisper_username_check_quota(user_id, check_count, window_start)
  values (v_caller, 1, now())
  on conflict (user_id) do update set
    check_count  = case
                     when extract(epoch from (now() - whisper_username_check_quota.window_start)) > 3600
                     then 1
                     else whisper_username_check_quota.check_count + 1
                   end,
    window_start = case
                     when extract(epoch from (now() - whisper_username_check_quota.window_start)) > 3600
                     then now()
                     else whisper_username_check_quota.window_start
                   end
  returning check_count, window_start into v_count, v_window;

  if v_count > v_limit then
    raise exception 'rate_limited' using errcode = 'P0002';
  end if;

  select exists(
    select 1 from public.profiles where lower(username) = v_uname
  ) into v_taken;

  -- Housekeeping piggybacked on every check: keep the table tiny without pg_cron.
  delete from public.whisper_username_check_quota where window_start < now() - interval '24 hours';

  return not v_taken;
end;
$$;

revoke all on function public.whisper_check_username_available(text) from public;
revoke all on function public.whisper_check_username_available(text) from anon;
grant execute on function public.whisper_check_username_available(text) to authenticated;

-- ── 5. Purge coverage for the new quota table ──────────────────────────────
-- whisper_purge_account_data mirrors the delete-account cleanup list exactly;
-- the username-check quota joins it (FK cascade on auth.users would catch it
-- anyway, but the purge runs BEFORE GoTrue deletion and must leave no rows).
-- Body is otherwise verbatim (20260830 version).
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
    delete from public.whisper_username_check_quota where user_id = p_uid;
    delete from public.whisper_image_ownership where user_id = p_uid;
    delete from public.whisper_fcm_tokens where user_id = p_uid;
    delete from public.whisper_prekeys where account = p_uid;
end;
$$;

revoke all on function public.whisper_purge_account_data(uuid) from public;
revoke all on function public.whisper_purge_account_data(uuid) from anon;
revoke all on function public.whisper_purge_account_data(uuid) from authenticated;
grant execute on function public.whisper_purge_account_data(uuid) to service_role;

-- ═══════════════ 20261004_whisper_remove_presence_typing.sql ═
-- 20261004 — Remove presence/typing opt-outs (product: same rights for all users)
-- WHY: product decision same-rights, remove opt-outs. The 20261003 section added
--   per-user presence_enabled/typing_enabled columns on profiles, a
--   typing-suppress trigger (whisper_typing_respect_optout) and a presence-gated
--   last_seen_at projection/ranking in whisper_public_profiles +
--   whisper_discover_profiles(). All users now share the same presence/typing
--   abilities, so the toggles go away: presence stamps and typing signals are
--   unconditional again. This section reverses ONLY that slice — the
--   sender-authorized tombstone UPDATE, the whisper_check_username_available()
--   RPC + whisper_username_check_quota table, and the purge coverage from
--   20261003 are KEPT (username quota is enumeration hardening, not presence).
-- IDEMPOTENT: safe to re-run (guarded drops / IF EXISTS / OR REPLACE / dynamic
--   view rebuild). No-ops on a prod that never ran 20261003 (drops match nothing).
-- ============================================================================

-- ── 1. Drop the typing-suppress trigger + function ─────────────────────────
-- Trigger name first (dependency), then the real 20261003 function, then the
-- bare trigger-name signature as a safety net for any historical drift.
drop trigger if exists whisper_typing_respect_optout on public.whisper_typing_signals;
drop function if exists public.whisper_suppress_typing_when_disabled();
drop function if exists public.whisper_typing_respect_optout();

-- ── 2. Drop the opt-out columns ────────────────────────────────────────────
-- IF EXISTS: fresh projects and prod-that-never-ran-20261003 have no columns.
-- Dependency order: the view projects these columns, so drop dependents FIRST
-- (function returns the view type; the view itself references the columns).
-- Later drops in steps 3-4 become harmless no-ops on re-run.
drop function if exists public.whisper_discover_profiles(integer, integer);
drop view if exists public.whisper_public_profiles;
alter table public.profiles drop column if exists presence_enabled;
alter table public.profiles drop column if exists typing_enabled;

-- ── 3. Restore direct last_seen_at in whisper_public_profiles ──────────────
-- Same dynamic whitelist projection as 20260828, but last_seen_at is projected
-- DIRECTLY again (no CASE gating): every user shares the same presence
-- ability. V6-R2 rerun pattern: drop the dependent function BEFORE rebuilding
-- the view.
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
               'updated_at',
               'last_seen_at'
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

-- ── 4. Restore ungated discover ranking (quota + filters unchanged) ────────
-- Return-type dependency was dropped above; recreate with the SAME quota (60
-- pages/hour), SAME filters, SAME clamps — only the ORDER BY loses its
-- presence CASE and ranks on the direct last_seen_at again (20260828 shape).
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
    v_limit     int := 60; -- max pages per hour (unchanged)
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

    -- Same filters as before (not private, not hidden from discover, not self,
    -- not blocking the caller; friend exclusion stays client-side). Ranking is
    -- the direct last_seen_at again — no opt-outs, no NULLS LAST carve-out.
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

-- ═══════════════ 20261005_whisper_signup_discover_hardening.sql ═
-- 20261005 — Signup Sybil quotas + Discover opt-in default + legacy-disable registry
-- WHY: three gaps. (1) NO SIGNUP GATE: nothing budgets account creation per IP,
--   so one network can mint unlimited Sybil identities. The (anti-mod-owned)
--   whisper-signup-gate edge function needs a server-side counter — provided
--   here as whisper_signup_quota + whisper_check_signup_allowed() (5/day/IP,
--   advisory-lock serialized like the bypass/destructive limiters). Each check
--   consumes one slot; the edge function MUST call it pre-auth (hence EXECUTE
--   for anon + authenticated). (2) DISCOVER OPT-OUT: hide_from_discover
--   defaulted to visible, so every new account was searchable until the owner
--   found the toggle. New rows now default to hidden (opt-IN to Discover);
--   existing rows are NOT backfilled so nobody silently vanishes.
--   (3) LEGACY SUNSET: whisper_legacy_disabled + whisper_is_legacy_disabled()
--   give the (anti-mod-owned) whisper-legacy-migrate edge function an
--   admin-readable kill-switch registry. GoTrue user deletion itself needs the
--   Auth admin API — SQL cannot delete GoTrue users, so this table records
--   disable intent/state only.
-- IDEMPOTENT: safe to re-run (IF NOT EXISTS / OR REPLACE / guarded revokes /
--   COMMENT ON is replace-safe). No-ops data-wise on first run except the new
--   empty tables and the changed column DEFAULT for future rows.
-- NOTE (username oracle, verified 20261005): the 10 checks/hour/caller quota on
--   whisper_check_username_available() is intact (20261003 §4, kept by 20261004).
--   The open profiles_select_authenticated policy (using(true)) is INTENTIONALLY
--   kept: public_key discovery via bundle-fetch/messaging needs it. Clients MUST
--   use the quota-guarded RPC instead of direct exact-eq username probes. A
--   lower(username) index below speeds the RPC's existence check.
-- ============================================================================

-- ── 1. Signup Sybil quota ─────────────────────────────────────────────
-- Backing store for the whisper-signup-gate edge function: one row per
-- (hashed client IP, day). The raw IP never reaches the database — the edge
-- function hashes it first. RLS enabled with ZERO policies (SECURITY DEFINER
-- gate only), mirroring whisper_bypass_attempts.
create table if not exists public.whisper_signup_quota (
    ip_hash text not null,
    day date not null,
    count int not null default 0,
    primary key (ip_hash, day)
);

alter table public.whisper_signup_quota enable row level security;

-- Deliberately NO policies: only the SECURITY DEFINER gate below touches this
-- table (same posture as whisper_bypass_attempts / whisper_destructive_attempts).

create or replace function public.whisper_check_signup_allowed(p_ip_hash text)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  v_count int;
  v_limit int := 5; -- max signups per IP per day
begin
  if p_ip_hash is null or p_ip_hash = '' then
    raise exception 'ip_hash required' using errcode = '42501';
  end if;

  -- Serialize concurrent signups from the same IP (auto-released at commit) so
  -- parallel requests cannot race the count → allow → insert sequence.
  perform pg_advisory_xact_lock(hashtextextended('signup-quota:' || p_ip_hash || ':' || current_date::text, 0));

  insert into public.whisper_signup_quota(ip_hash, day, count)
  values (p_ip_hash, current_date, 1)
  on conflict (ip_hash, day) do update set count = whisper_signup_quota.count + 1
  returning count into v_count;

  -- Housekeeping piggybacked on every check: keep the table tiny without pg_cron.
  delete from public.whisper_signup_quota where day < current_date - 7;

  return v_count <= v_limit;
end;
$$;

-- Signup is pre-auth: anon MUST be able to reach the gate (the edge function
-- enforces it before creating the GoTrue user). Revoke-then-grant keeps
-- public clean even after re-runs.
revoke all on function public.whisper_check_signup_allowed(text) from public;
grant execute on function public.whisper_check_signup_allowed(text) to anon;
grant execute on function public.whisper_check_signup_allowed(text) to authenticated;
grant execute on function public.whisper_check_signup_allowed(text) to service_role;

-- ── 2. Discover opt-in: new profiles hidden by default ─────────────────
-- Opt-IN instead of opt-out: hide_from_discover now defaults to true, so fresh
-- accounts are invisible to Discover/search until the owner enables it.
-- Deliberately NO backfill: existing rows keep their current value so nobody
-- silently vanishes from search results on upgrade. is_private is untouched.
alter table public.profiles add column if not exists hide_from_discover boolean;
alter table public.profiles alter column hide_from_discover set default true;
comment on column public.profiles.hide_from_discover is
  '20261005 opt-in: new profiles default hidden from Discover; existing rows were NOT backfilled.';

-- ── 3. Username oracle: document the RPC-only contract + speed the RPC ──
-- profiles_select_authenticated (using(true)) is REQUIRED for public_key
-- discovery via bundle-fetch/messaging — do NOT revoke it. Enumeration is
-- budgeted at the RPC layer instead (10 checks/hour/caller).
comment on policy "profiles_select_authenticated" on public.profiles is
  '20261005: intentionally open for public_key discovery; clients MUST use the quota-guarded whisper_check_username_available() RPC instead of direct username probes.';
comment on function public.whisper_check_username_available(text) is
  '20261005 verified: 10 checks/hour/caller quota intact; the budgeted oracle — prefer over direct profiles(username) probes.';

-- Case-insensitive handle lookup backing the RPC above.
create index if not exists whisper_profiles_username_lower_idx
    on public.profiles (lower(username));

-- ── 4. Legacy-disable registry (admin kill-switch) ─────────────────────
-- Rows are written by the whisper-legacy-migrate edge function (service role)
-- AFTER it retires a pre-P1-15 GoTrue identity via the Auth admin API — SQL
-- cannot delete GoTrue users, so this table records disable intent/state only.
create table if not exists public.whisper_legacy_disabled (
    email text primary key,
    disabled_at timestamptz not null default now()
);

alter table public.whisper_legacy_disabled enable row level security;

-- Deliberately NO policies: edge/admin service-role only (same posture as
-- whisper_bypass_attempts).

create or replace function public.whisper_is_legacy_disabled(p_email text)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
begin
  return exists(select 1 from public.whisper_legacy_disabled where email = p_email);
end;
$$;

revoke all on function public.whisper_is_legacy_disabled(text) from public;
revoke all on function public.whisper_is_legacy_disabled(text) from anon;
revoke all on function public.whisper_is_legacy_disabled(text) from authenticated;
grant execute on function public.whisper_is_legacy_disabled(text) to service_role;

-- ═══════════════ 20261006_whisper_rotation_cert_transport.sql ═
-- whisper_rotation_cert_transport.sql (20261006)
-- Phase-1 rotation-cert transport: the ROTv2 rotation certificate a
-- device publishes when its hardware protocol signer rotates now
-- lives ON the profile row (rotation_cert + rotation_counter), so
-- peers can fetch the proof for a candidate key and chain it to
-- their pinned one (WireProtocol.verifyRotationCert ROTv2 overload:
-- counter must strictly increase, cert age ≤ 24h). Clients read the
-- record via the prekey manager / bundle fetch; the fail-closed
-- decoder is WireProtocol.rotationCertRecordOf (blank cert or
-- non-positive counter = "no cert published", never a trusted
-- rotation).

alter table public.profiles add column if not exists rotation_cert text;
alter table public.profiles add column if not exists rotation_counter bigint;

comment on column public.profiles.rotation_cert is
  '20261006: ROTv2 rotation certificate (base64 ECDSA/SHA256 signature over "ROTv2:<prev>:<new>:<counter>:<ts>"), written by the device at protocol-signer rotation; null until the first rotation.';
comment on column public.profiles.rotation_counter is
  '20261006: monotonic counter of the published rotation certificate (null until the first rotation); peers require a strictly greater counter (anti-replay).';

-- RLS: deliberately NO new policies. Existing profiles policies are
-- column-agnostic, so they already cover the new columns:
--   * profiles_select_authenticated (SELECT, using(true)) — every
--     authenticated client may read any profile, including
--     rotation_cert/rotation_counter (needed by bundle-fetch and
--     per-candidate rotation-cert lookups);
--   * profiles_update_own (UPDATE, using + check id = auth.uid()) —
--     the owner publishes their own cert in the same profiles
--     update that carries the identity binding.
-- No index: lookups key on id / public_key, never on the cert.

-- ═══════════════ 20261007_whisper_groups_phase2.sql ═
-- whisper_groups_phase2.sql (20261007)
-- Phase-2 groups (pairwise fan-out, ≤12 members, v1.1.7 dev, flag OFF).
-- ADDITIVE ONLY: four new tables + one quota table + gates/triggers. Zero
-- alters to existing tables, zero changes to 1:1 policies — current users
-- are unaffected by re-running this file (all objects IF NOT EXISTS,
-- policies dropped + recreated for idempotency).
--
-- Trust model: the server is an ordered log + notifier, NEVER the
-- membership authority. Clients build membership from verified
-- whisper_group_events only (admin-signed, epoch-chained) and fail closed
-- on epoch mismatch. RLS enforces coarse membership; signatures are
-- verified client-side (mods can spoof SQL but cannot forge admin_sig,
-- and the ship-day global floor blocks <18 clients entirely).
--
-- Version gating: every group write row carries client_version_code with
-- CHECK (>= 18). Official ≤1.1.6 clients never write these tables, so old
-- versions are rejected at the schema layer even before the ship-day
-- MIN_VERSION_CODE flip. (A mod can lie in this column — accepted and
-- documented; the attest edge gate + ship floor are the real walls.)

-- ── 1. Groups + membership ──────────────────────────────────────────
create table if not exists public.whisper_groups (
    id uuid not null default gen_random_uuid() primary key,
    name text not null default '',
    epoch bigint not null default 0,
    created_by uuid not null,
    created_at timestamptz not null default now()
);

create table if not exists public.whisper_group_members (
    group_id uuid not null references public.whisper_groups(id) on delete cascade,
    user_id uuid not null,
    role text not null default 'member' check (role in ('admin', 'member')),
    joined_at timestamptz not null default now(),
    invited_by uuid,
    primary key (group_id, user_id)
);

alter table public.whisper_groups enable row level security;
alter table public.whisper_group_members enable row level security;

-- Own membership rows are self-readable (no self-referential policy: group /
-- event / message policies probe this table and the caller's own row always
-- satisfies user_id = auth.uid()).
drop policy if exists "whisper_group_members_select_own" on public.whisper_group_members;
create policy "whisper_group_members_select_own" on public.whisper_group_members
    for select using (user_id = auth.uid());

-- Invite-accept = insert own row; admin-add = admin inserts a friend's row
-- (friends-only is enforced client-side; server sees membership only).
drop policy if exists "whisper_group_members_insert" on public.whisper_group_members;
create policy "whisper_group_members_insert" on public.whisper_group_members
    for insert with check (
        user_id = auth.uid()
        or exists (
            select 1 from public.whisper_group_members m
            where m.group_id = whisper_group_members.group_id
              and m.user_id = auth.uid() and m.role = 'admin'
        )
    );

-- Leave = delete own row; remove = admin deletes any row.
drop policy if exists "whisper_group_members_delete" on public.whisper_group_members;
create policy "whisper_group_members_delete" on public.whisper_group_members
    for delete using (
        user_id = auth.uid()
        or exists (
            select 1 from public.whisper_group_members m
            where m.group_id = whisper_group_members.group_id
              and m.user_id = auth.uid() and m.role = 'admin'
        )
    );

-- Role changes are admin-only (member rows are otherwise immutable).
drop policy if exists "whisper_group_members_update_admin" on public.whisper_group_members;
create policy "whisper_group_members_update_admin" on public.whisper_group_members
    for update using (
        exists (
            select 1 from public.whisper_group_members m
            where m.group_id = whisper_group_members.group_id
              and m.user_id = auth.uid() and m.role = 'admin'
        )
    );

drop policy if exists "whisper_groups_select_member" on public.whisper_groups;
create policy "whisper_groups_select_member" on public.whisper_groups
    for select using (
        exists (
            select 1 from public.whisper_group_members m
            where m.group_id = whisper_groups.id and m.user_id = auth.uid()
        )
    );

drop policy if exists "whisper_groups_insert_own" on public.whisper_groups;
create policy "whisper_groups_insert_own" on public.whisper_groups
    for insert with check (created_by = auth.uid());

drop policy if exists "whisper_groups_update_admin" on public.whisper_groups;
create policy "whisper_groups_update_admin" on public.whisper_groups
    for update using (
        exists (
            select 1 from public.whisper_group_members m
            where m.group_id = whisper_groups.id
              and m.user_id = auth.uid() and m.role = 'admin'
        )
    );

-- No group DELETE policy: groups die when the last member leaves (client
-- deletes its row; an orphan group with zero members is invisible to every
-- SELECT policy and harmless). Server-side expiry is out of scope for v1.

-- ── 2. Signed admin events (the membership source of truth) ─────────
create table if not exists public.whisper_group_events (
    id uuid not null default gen_random_uuid() primary key,
    group_id uuid not null references public.whisper_groups(id) on delete cascade,
    seq bigint not null check (seq > 0),
    epoch bigint not null check (epoch >= 0),
    type text not null check (type in ('create', 'add', 'remove', 'leave', 'rename', 'promote')),
    actor uuid not null,
    payload jsonb not null default '{}'::jsonb,
    admin_sig text not null default '',
    client_version_code int not null check (client_version_code >= 18),
    created_at timestamptz not null default now(),
    unique (group_id, seq)
);

alter table public.whisper_group_events enable row level security;

drop policy if exists "whisper_group_events_select_member" on public.whisper_group_events;
create policy "whisper_group_events_select_member" on public.whisper_group_events
    for select using (
        exists (
            select 1 from public.whisper_group_members m
            where m.group_id = whisper_group_events.group_id and m.user_id = auth.uid()
        )
    );

drop policy if exists "whisper_group_events_insert_member" on public.whisper_group_events;
create policy "whisper_group_events_insert_member" on public.whisper_group_events
    for insert with check (
        actor = auth.uid()
        and exists (
            select 1 from public.whisper_group_members m
            where m.group_id = whisper_group_events.group_id and m.user_id = auth.uid()
        )
    );

-- No UPDATE/DELETE policies: the event log is append-only. A forged seq
-- collides on unique(group_id, seq) → 23505 → the client retries with the
-- next seq (documented in WhisperRepositoryGroups).

create index if not exists whisper_group_events_group_seq_idx
    on public.whisper_group_events (group_id, seq);

-- ── 3. Group messages (ciphertext only, epoch-bound) ────────────────
create table if not exists public.whisper_group_messages (
    id uuid not null default gen_random_uuid() primary key,
    group_id uuid not null references public.whisper_groups(id) on delete cascade,
    sender_id uuid not null,
    client_id text not null unique,
    content text not null,
    content_iv text not null default '',
    epoch bigint not null check (epoch >= 0),
    client_version_code int not null check (client_version_code >= 18),
    created_at timestamptz not null default now()
);

alter table public.whisper_group_messages enable row level security;

drop policy if exists "whisper_group_messages_select_member" on public.whisper_group_messages;
create policy "whisper_group_messages_select_member" on public.whisper_group_messages
    for select using (
        exists (
            select 1 from public.whisper_group_members m
            where m.group_id = whisper_group_messages.group_id and m.user_id = auth.uid()
        )
    );

drop policy if exists "whisper_group_messages_insert_member" on public.whisper_group_messages;
create policy "whisper_group_messages_insert_member" on public.whisper_group_messages
    for insert with check (
        sender_id = auth.uid()
        and exists (
            select 1 from public.whisper_group_members m
            where m.group_id = whisper_group_messages.group_id and m.user_id = auth.uid()
        )
    );

-- No UPDATE policies: messages are immutable (delete-for-everyone reuses the
-- 1:1 tombstone convention in a later slice, not this one).
-- Deletes are sender-only (sender-side wipe; best-effort, like 1:1).
drop policy if exists "whisper_group_messages_delete_sender" on public.whisper_group_messages;
create policy "whisper_group_messages_delete_sender" on public.whisper_group_messages
    for delete using (sender_id = auth.uid());

create index if not exists whisper_group_messages_group_created_idx
    on public.whisper_group_messages (group_id, created_at);

-- ── 4. Fan-out quota: 50 envelopes/day × member count ───────────────
-- 1→N sends cost N envelopes; the budget scales with fan-out (max 12 → 600).
-- Mirrors whisper_signup_quota posture: RLS on, ZERO policies, gate only.
create table if not exists public.whisper_group_send_quota (
    sender_id uuid not null,
    day date not null,
    count int not null default 0,
    primary key (sender_id, day)
);

alter table public.whisper_group_send_quota enable row level security;

create or replace function public.whisper_check_group_send_allowed(p_sender uuid, p_fanout int)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  v_count int;
  v_limit int := 600; -- 50 msgs/day x 12 max members (1→N envelope budget)
  v_cost int;
begin
  if p_sender is null then
    raise exception 'sender required' using errcode = '42501';
  end if;
  v_cost := greatest(coalesce(p_fanout, 1), 1);

  perform pg_advisory_xact_lock(hashtextextended('group-quota:' || p_sender::text || ':' || current_date::text, 0));

  insert into public.whisper_group_send_quota(sender_id, day, count)
  values (p_sender, current_date, v_cost)
  on conflict (sender_id, day) do update set count = whisper_group_send_quota.count + v_cost
  returning count into v_count;

  delete from public.whisper_group_send_quota where day < current_date - 7;

  return v_count <= v_limit;
end;
$$;

revoke all on function public.whisper_check_group_send_allowed(uuid, int) from public;
revoke all on function public.whisper_check_group_send_allowed(uuid, int) from anon;
revoke all on function public.whisper_check_group_send_allowed(uuid, int) from authenticated;
grant execute on function public.whisper_check_group_send_allowed(uuid, int) to service_role;

-- ── 5. Guard triggers (fail closed, group tables only) ─────────────
-- Member cap: 12 max (mirrors WhisperGroupsConfig.MAX_MEMBERS).
create or replace function public.whisper_group_enforce_member_cap()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_count int;
begin
  select count(*) into v_count
  from public.whisper_group_members where group_id = new.group_id;
  if v_count >= 12 then
    raise exception 'group full (12 max)' using errcode = '42501';
  end if;
  return new;
end;
$$;

drop trigger if exists trg_group_members_cap on public.whisper_group_members;
create trigger trg_group_members_cap
  before insert on public.whisper_group_members
  for each row execute function public.whisper_group_enforce_member_cap();

-- Epoch chain: events must carry epoch >= current; the group epoch ratchets
-- forward. A stale-epoch write is rejected — clients fail closed on mismatch.
create or replace function public.whisper_group_chain_event_epoch()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_epoch bigint;
begin
  select epoch into v_epoch from public.whisper_groups where id = new.group_id;
  if not found then
    raise exception 'unknown group' using errcode = '42501';
  end if;
  if new.epoch < v_epoch then
    raise exception 'stale epoch' using errcode = '42501';
  end if;
  if new.epoch > v_epoch then
    update public.whisper_groups set epoch = new.epoch where id = new.group_id;
  end if;
  return new;
end;
$$;

drop trigger if exists trg_group_events_epoch on public.whisper_group_events;
create trigger trg_group_events_epoch
  before insert on public.whisper_group_events
  for each row execute function public.whisper_group_chain_event_epoch();

-- Message guard: sender must still be a member, message epoch must equal the
-- live group epoch (a removed member's stale client fails closed here), and
-- the fan-out quota is consumed (fail closed when over budget).
create or replace function public.whisper_group_guard_message()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_epoch bigint;
  v_members int;
  v_ok boolean;
begin
  select epoch into v_epoch from public.whisper_groups where id = new.group_id;
  if not found then
    raise exception 'unknown group' using errcode = '42501';
  end if;
  if not exists (
    select 1 from public.whisper_group_members m
    where m.group_id = new.group_id and m.user_id = new.sender_id
  ) then
    raise exception 'not a member' using errcode = '42501';
  end if;
  if new.epoch <> v_epoch then
    raise exception 'stale epoch' using errcode = '42501';
  end if;
  select count(*) into v_members
  from public.whisper_group_members where group_id = new.group_id;
  select public.whisper_check_group_send_allowed(new.sender_id, v_members) into v_ok;
  if not v_ok then
    raise exception 'group send quota exceeded' using errcode = '42501';
  end if;
  return new;
end;
$$;

drop trigger if exists trg_group_messages_guard on public.whisper_group_messages;
create trigger trg_group_messages_guard
  before insert on public.whisper_group_messages
  for each row execute function public.whisper_group_guard_message();

comment on table public.whisper_groups is
  '20261007 Phase-2 groups: ordered-log metadata only; clients build membership from verified whisper_group_events, never from this table alone.';
comment on function public.whisper_check_group_send_allowed(uuid, int) is
  '20261007: 600 envelopes/day/sender (50 x 12 max fan-out); fail-closed trigger gate on whisper_group_messages.';

-- ═══════════════ 20261007_whisper_groups_payload_text_fix ═
-- The event payload MUST round-trip byte-identically: clients sign the exact
-- string at write time and re-verify it on every fetch. jsonb normalizes key
-- order, which would break signature checks after a server round-trip — so the
-- column is TEXT (opaque signed blob; the server never queries inside it).
-- Safe to re-run (fresh v1.1.7-dev table, no production rows behind the flag).
alter table public.whisper_group_events alter column payload type text using payload::text;
alter table public.whisper_group_events alter column payload set default '{}';
alter table public.whisper_group_events alter column payload set not null;

-- ═══════════════ 20261010_whisper_groups_invites.sql ═
-- Phase-2 invites: creating a group no longer adds members directly — the
-- creator picks friends, each gets a persistent INVITE, and membership starts
-- at join. An invite row dies ONLY by join (trigger-consumed), decline
-- (invitee), or cancel (owner/admin). No expiry, no other delete path.
-- Group picture rides a signed `picture` admin event carrying per-member
-- sealed key frames (bytes opaque on ImgBB; URL visible like other group
-- metadata; key sealed per member like message frames).

-- ── 1. Invites table ──────────────────────────────────────────────
create table if not exists public.whisper_group_invites (
    group_id uuid not null references public.whisper_groups(id) on delete cascade,
    user_id uuid not null,
    invited_by uuid not null,
    client_version_code int not null check (client_version_code >= 18),
    created_at timestamptz not null default now(),
    primary key (group_id, user_id)
);

alter table public.whisper_group_invites enable row level security;

-- Invitee reads + deletes (join/decline) only their own rows.
drop policy if exists "whisper_group_invites_select_own" on public.whisper_group_invites;
create policy "whisper_group_invites_select_own" on public.whisper_group_invites
    for select using (user_id = auth.uid());

drop policy if exists "whisper_group_invites_delete_own" on public.whisper_group_invites;
create policy "whisper_group_invites_delete_own" on public.whisper_group_invites
    for delete using (user_id = auth.uid());

-- Invite (member with invite rights) and cancel (admin) write paths.
drop policy if exists "whisper_group_invites_insert_inviter" on public.whisper_group_invites;
create policy "whisper_group_invites_insert_inviter" on public.whisper_group_invites
    for insert with check (
        invited_by = auth.uid()
        and exists (
            select 1 from public.whisper_group_members m
            where m.group_id = whisper_group_invites.group_id and m.user_id = auth.uid()
        )
    );

drop policy if exists "whisper_group_invites_delete_admin" on public.whisper_group_invites;
create policy "whisper_group_invites_delete_admin" on public.whisper_group_invites
    for delete using (
        exists (
            select 1 from public.whisper_group_members m
            where m.group_id = whisper_group_invites.group_id
              and m.user_id = auth.uid() and m.role = 'admin'
        )
    );

create index if not exists whisper_group_invites_user_idx
    on public.whisper_group_invites (user_id);

-- ── 2. New event types: invite/join/decline/cancel/picture ────────
-- Events stay append-only; the CHECK grows (additive — old rows unaffected).
-- Re-run guard: on a live database rows already carry event types from LATER
-- sections (demote/settings), which this historical intermediate set would
-- reject with 23514. Re-add only when every existing row conforms; the final
-- section (20261010_whisper_groups_social) below always installs the
-- complete set.
alter table public.whisper_group_events drop constraint if exists whisper_group_events_type_check;
do $$
begin
  if not exists (
    select 1 from public.whisper_group_events
    where type not in ('create', 'add', 'remove', 'leave', 'rename', 'promote',
                       'invite', 'join', 'decline', 'cancel', 'picture')
  ) then
    alter table public.whisper_group_events add constraint whisper_group_events_type_check
        check (type in ('create', 'add', 'remove', 'leave', 'rename', 'promote',
                        'invite', 'join', 'decline', 'cancel', 'picture'));
  end if;
end $$;

-- ── 3. Join consumes the invite (the ONLY silent invite death) ────
create or replace function public.whisper_group_consume_invite()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  delete from public.whisper_group_invites
  where group_id = new.group_id and user_id = new.user_id;
  return new;
end;
$$;

drop trigger if exists trg_group_members_consume_invite on public.whisper_group_members;
create trigger trg_group_members_consume_invite
  after insert on public.whisper_group_members
  for each row execute function public.whisper_group_consume_invite();

-- ── 4. Member JOINs require a live invite (admins exempt for recovery) ──
-- With invites, direct member-row inserts are invite redemptions — except an
-- admin restoring state. Mods bypassing the invite table still face the
-- signature-checked event log (JOIN needs a prior INVITE event) and the
-- ship-day floor.
create or replace function public.whisper_group_require_invite()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if exists (
    select 1 from public.whisper_group_members m
    where m.group_id = new.group_id and m.user_id = auth.uid() and m.role = 'admin'
  ) then
    return new;
  end if;
  if not exists (
    select 1 from public.whisper_group_invites i
    where i.group_id = new.group_id and i.user_id = new.user_id
  ) then
    raise exception 'no invite' using errcode = '42501';
  end if;
  return new;
end;
$$;

drop trigger if exists trg_group_members_require_invite on public.whisper_group_members;
create trigger trg_group_members_require_invite
  before insert on public.whisper_group_members
  for each row execute function public.whisper_group_require_invite();

-- ── 5. Cap counts members + pending invites (12 max, either way) ──
create or replace function public.whisper_group_enforce_member_cap()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_count int;
begin
  select count(*) into v_count
  from public.whisper_group_members where group_id = new.group_id;
  if v_count >= 12 then
    raise exception 'group full (12 max)' using errcode = '42501';
  end if;
  return new;
end;
$$;

create or replace function public.whisper_group_enforce_invite_cap()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_total int;
begin
  select
    (select count(*) from public.whisper_group_members where group_id = new.group_id) +
    (select count(*) from public.whisper_group_invites where group_id = new.group_id)
    into v_total;
  if v_total >= 12 then
    raise exception 'group full (12 max)' using errcode = '42501';
  end if;
  return new;
end;
$$;

drop trigger if exists trg_group_invites_cap on public.whisper_group_invites;
create trigger trg_group_invites_cap
  before insert on public.whisper_group_invites
  for each row execute function public.whisper_group_enforce_invite_cap();

-- ── 6. Realtime: group tables join the publication (idempotent) ───
-- Without these, NO realtime fires on group tables — verified missing 20261010.
do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'whisper_groups'
  ) then
    alter publication supabase_realtime add table public.whisper_groups;
  end if;
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'whisper_group_members'
  ) then
    alter publication supabase_realtime add table public.whisper_group_members;
  end if;
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'whisper_group_events'
  ) then
    alter publication supabase_realtime add table public.whisper_group_events;
  end if;
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'whisper_group_messages'
  ) then
    alter publication supabase_realtime add table public.whisper_group_messages;
  end if;
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'whisper_group_invites'
  ) then
    alter publication supabase_realtime add table public.whisper_group_invites;
  end if;
end $$;

-- Invite DELETE must carry the row (cancel/decline fan-out without re-select).
alter table public.whisper_group_invites replica identity full;
alter table public.whisper_group_members replica identity full;
alter table public.whisper_group_events replica identity full;
alter table public.whisper_group_messages replica identity full;

comment on table public.whisper_group_invites is
  '20261010 Phase-2 invites: persistent until join (trigger-consumed), decline (invitee), or cancel (admin). No expiry, no other delete path.';

-- ═══════════════ 20261010_whisper_groups_invites_fix.sql ═
-- Three gaps found while wiring transport (all on dev-only group tables):
-- (a) the group creator bootstraps the first admin row with no invite and no
--     admin row yet — the require-invite trigger would reject it;
-- (b) invitees must READ the group row (name for the invite card) before they
--     are members;
-- (c) invitees must WRITE join/decline events before they are members.
drop policy if exists "whisper_groups_select_member" on public.whisper_groups;
create policy "whisper_groups_select_member" on public.whisper_groups
    for select using (
        exists (
            select 1 from public.whisper_group_members m
            where m.group_id = whisper_groups.id and m.user_id = auth.uid()
        )
        or exists (
            select 1 from public.whisper_group_invites i
            where i.group_id = whisper_groups.id and i.user_id = auth.uid()
        )
    );

drop policy if exists "whisper_group_events_insert_member" on public.whisper_group_events;
create policy "whisper_group_events_insert_member" on public.whisper_group_events
    for insert with check (
        actor = auth.uid()
        and (
            exists (
                select 1 from public.whisper_group_members m
                where m.group_id = whisper_group_events.group_id and m.user_id = auth.uid()
            )
            or exists (
                select 1 from public.whisper_group_invites i
                where i.group_id = whisper_group_events.group_id and i.user_id = auth.uid()
            )
        )
    );

create or replace function public.whisper_group_require_invite()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_creator uuid;
begin
  -- (a) the creator bootstraps the first admin row.
  select created_by into v_creator from public.whisper_groups where id = new.group_id;
  if found and v_creator = auth.uid() and new.user_id = auth.uid() then
    return new;
  end if;
  if exists (
    select 1 from public.whisper_group_members m
    where m.group_id = new.group_id and m.user_id = auth.uid() and m.role = 'admin'
  ) then
    return new;
  end if;
  if not exists (
    select 1 from public.whisper_group_invites i
    where i.group_id = new.group_id and i.user_id = new.user_id
  ) then
    raise exception 'no invite' using errcode = '42501';
  end if;
  return new;
end;
$$;

-- ═══════════════ 20261010_whisper_groups_retire_add.sql ═
-- The direct-add event is retired: membership starts at JOIN, never at admin
-- fiat. `add` leaves the CHECK so no new row can carry it; the client rejects
-- legacy `add` rows fail-closed (verified prefix survives via truncation).
-- Safe: no production rows behind the flag; dev logs with `add` degrade to
-- their verified prefix instead of breaking.
-- Re-run guard: same as above — skip the re-add when live rows predate this
-- narrowing; the final section below always installs the complete set.
alter table public.whisper_group_events drop constraint if exists whisper_group_events_type_check;
do $$
begin
  if not exists (
    select 1 from public.whisper_group_events
    where type not in ('create', 'remove', 'leave', 'rename', 'promote',
                       'invite', 'join', 'decline', 'cancel', 'picture')
  ) then
    alter table public.whisper_group_events add constraint whisper_group_events_type_check
        check (type in ('create', 'remove', 'leave', 'rename', 'promote',
                        'invite', 'join', 'decline', 'cancel', 'picture'));
  end if;
end $$;

-- ═══════════════ 20261010_whisper_groups_disband.sql ═
-- Disband: the LAST remaining member may delete the group row (everything
-- cascades: members, events, messages, invites). A live group can never be
-- nuked — the policy requires zero OTHER members, so no admin can delete a
-- group out from under anyone. Without this, a creator whose invites all
-- declined would be stranded: the last admin cannot leave, and nothing could
-- delete the row. The client gates the button to members.size <= 1.
drop policy if exists "whisper_groups_delete_last_member" on public.whisper_groups;
create policy "whisper_groups_delete_last_member" on public.whisper_groups
    for delete using (
        exists (
            select 1 from public.whisper_group_members m
            where m.group_id = whisper_groups.id and m.user_id = auth.uid()
        )
        and not exists (
            select 1 from public.whisper_group_members m
            where m.group_id = whisper_groups.id and m.user_id <> auth.uid()
        )
    );

-- ═══════════════ 20261010_whisper_groups_receipts_typing.sql ═
-- Parity slice: per-message seen receipts + ephemeral typing signals.
-- Receipts are presence metadata (who saw which client_id when) — same
-- posture as 1:1 read state, no content. Typing rows are upserts refreshed
-- while typing; readers treat rows older than ~8s as gone (client-side).
create table if not exists public.whisper_group_receipts (
    group_id uuid not null references public.whisper_groups(id) on delete cascade,
    message_id text not null,
    user_id uuid not null,
    at timestamptz not null default now(),
    primary key (group_id, message_id, user_id)
);

alter table public.whisper_group_receipts enable row level security;

drop policy if exists "whisper_group_receipts_select_member" on public.whisper_group_receipts;
create policy "whisper_group_receipts_select_member" on public.whisper_group_receipts
    for select using (
        exists (
            select 1 from public.whisper_group_members m
            where m.group_id = whisper_group_receipts.group_id and m.user_id = auth.uid()
        )
    );

drop policy if exists "whisper_group_receipts_insert_own" on public.whisper_group_receipts;
create policy "whisper_group_receipts_insert_own" on public.whisper_group_receipts
    for insert with check (
        user_id = auth.uid()
        and exists (
            select 1 from public.whisper_group_members m
            where m.group_id = whisper_group_receipts.group_id and m.user_id = auth.uid()
        )
    );

create index if not exists whisper_group_receipts_group_idx
    on public.whisper_group_receipts (group_id, at desc);

create table if not exists public.whisper_group_typing (
    group_id uuid not null references public.whisper_groups(id) on delete cascade,
    user_id uuid not null,
    updated_at timestamptz not null default now(),
    primary key (group_id, user_id)
);

alter table public.whisper_group_typing enable row level security;

drop policy if exists "whisper_group_typing_select_member" on public.whisper_group_typing;
create policy "whisper_group_typing_select_member" on public.whisper_group_typing
    for select using (
        exists (
            select 1 from public.whisper_group_members m
            where m.group_id = whisper_group_typing.group_id and m.user_id = auth.uid()
        )
    );

drop policy if exists "whisper_group_typing_write_own" on public.whisper_group_typing;
create policy "whisper_group_typing_write_own" on public.whisper_group_typing
    for all using (user_id = auth.uid())
    with check (user_id = auth.uid());

-- Realtime: receipts + typing join the publication (idempotent).
do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'whisper_group_receipts'
  ) then
    alter publication supabase_realtime add table public.whisper_group_receipts;
  end if;
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'whisper_group_typing'
  ) then
    alter publication supabase_realtime add table public.whisper_group_typing;
  end if;
end $$;

alter table public.whisper_group_receipts replica identity full;
alter table public.whisper_group_typing replica identity full;

-- ═══════════════ 20261010_whisper_groups_social.sql ═
-- Social slice: `demote` (admin→member) and `settings` (admin-only send mode
-- + group description) events. Both are admin-signed, verified client-side
-- like every other event. Settings deliberately do NOT bump the epoch (they
-- change no crypto scope), so in-flight sends never go stale on a settings
-- change. Ownership transfer needs no schema: promote + UPDATE
-- whisper_groups.created_by (the admin UPDATE policy already covers it).
alter table public.whisper_group_events drop constraint if exists whisper_group_events_type_check;
alter table public.whisper_group_events add constraint whisper_group_events_type_check
    check (type in ('create', 'remove', 'leave', 'rename', 'promote', 'demote',
                    'settings', 'invite', 'join', 'decline', 'cancel', 'picture'));

-- ═══════════════ 20261011_whisper_groups_adminsend_guard.sql ═
-- Server enforcement for admin-only send mode. The mode itself lives in the
-- signed SETTINGS event log (client authority); this mirrors it into
-- whisper_groups.admin_only_send so the message guard can reject member sends
-- even from modded clients. Backfilled from each group's latest SETTINGS event.
alter table public.whisper_groups add column if not exists admin_only_send boolean not null default false;

create or replace function public.whisper_group_mirror_settings()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_send boolean;
begin
  if new.type = 'settings' then
    begin
      v_send := (new.payload::jsonb ->> 'adminSend')::boolean;
    exception when others then
      v_send := null;
    end;
    if v_send is not null then
      update public.whisper_groups set admin_only_send = v_send where id = new.group_id;
    end if;
  end if;
  return new;
end;
$$;

drop trigger if exists trg_group_events_mirror_settings on public.whisper_group_events;
create trigger trg_group_events_mirror_settings
  after insert on public.whisper_group_events
  for each row execute function public.whisper_group_mirror_settings();

-- Backfill from latest SETTINGS per group (idempotent; default false stays).
do $$
declare
  r record;
begin
  for r in
    select distinct group_id from public.whisper_group_events where type = 'settings'
  loop
    update public.whisper_groups g set admin_only_send = coalesce((
      select ((e.payload::jsonb ->> 'adminSend')::boolean)
      from public.whisper_group_events e
      where e.group_id = r.group_id and e.type = 'settings'
        and (e.payload::jsonb ? 'adminSend')
      order by e.seq desc limit 1
    ), false)
    where g.id = r.group_id;
  end loop;
end $$;

-- Message guard now also rejects non-admin senders in admin-only groups.
create or replace function public.whisper_group_guard_message()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_epoch bigint;
  v_members int;
  v_ok boolean;
  v_admin_only boolean;
begin
  select epoch, admin_only_send into v_epoch, v_admin_only from public.whisper_groups where id = new.group_id;
  if not found then
    raise exception 'unknown group' using errcode = '42501';
  end if;
  if not exists (
    select 1 from public.whisper_group_members m
    where m.group_id = new.group_id and m.user_id = new.sender_id
  ) then
    raise exception 'not a member' using errcode = '42501';
  end if;
  if v_admin_only then
    if not exists (
      select 1 from public.whisper_group_members m
      where m.group_id = new.group_id and m.user_id = new.sender_id and m.role = 'admin'
    ) then
      raise exception 'only admins can send messages in this group' using errcode = '42501';
    end if;
  end if;
  if new.epoch <> v_epoch then
    raise exception 'stale epoch' using errcode = '42501';
  end if;
  select count(*) into v_members
  from public.whisper_group_members where group_id = new.group_id;
  select public.whisper_check_group_send_allowed(new.sender_id, v_members) into v_ok;
  if not v_ok then
    raise exception 'group send quota exceeded' using errcode = '42501';
  end if;
  return new;
end;
$$;

-- Receipt hygiene: wiping a message also wipes its seen-state (no orphans,
-- no lingering presence after a delete-for-everyone).
create or replace function public.whisper_group_cleanup_receipts()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  delete from public.whisper_group_receipts
  where group_id = old.group_id and message_id = old.client_id;
  return old;
end;
$$;

drop trigger if exists trg_group_messages_cleanup_receipts on public.whisper_group_messages;
create trigger trg_group_messages_cleanup_receipts
  after delete on public.whisper_group_messages
  for each row execute function public.whisper_group_cleanup_receipts();

-- Webhook policy (no code): whisper-push-send webhooks stay INSERT-only on
-- whisper_group_invites / whisper_group_messages / whisper_group_events.
-- NEVER add webhooks on whisper_group_receipts / whisper_group_typing:
-- presence rows churn constantly and carry no pushable content.

-- ═══════════════ 20261011_whisper_groups_msgkind.sql ═
-- Vote/poll traffic control. Votes are tally data, not chat lines: fanning a
-- push per vote spams every member and burns the send quota. A `msg_kind`
-- flag lets the push layer stay silent for votes while realtime still
-- delivers them to open chats (tally refreshes, no ping). Metadata cost is
-- one small label per row (chat/poll/vote/image) — documented in WHISPER.md
-- §11 alongside the other group-row metadata. Content stays opaque.
alter table public.whisper_group_messages
    add column if not exists msg_kind text not null default 'chat'
    check (msg_kind in ('chat', 'poll', 'vote', 'image'));

-- ═══════════════ 20261011_whisper_groups_reactions_requests.sql ═
-- Beyond-parity slice: per-message emoji reactions + inbound join requests.
-- Reactions are presence-style metadata (message client_id + emoji, no
-- content); one emoji per user per message (upsert replaces). Join requests
-- let friends ask in (paste an ID / scan a QR) without an invite first;
-- admins approve (invite) or deny. Both tables are member-RLS + realtime.
create table if not exists public.whisper_group_reactions (
    group_id uuid not null references public.whisper_groups(id) on delete cascade,
    message_id text not null,
    user_id uuid not null,
    emoji text not null check (char_length(emoji) between 1 and 8),
    at timestamptz not null default now(),
    primary key (group_id, message_id, user_id)
);

alter table public.whisper_group_reactions enable row level security;

drop policy if exists "whisper_group_reactions_select_member" on public.whisper_group_reactions;
create policy "whisper_group_reactions_select_member" on public.whisper_group_reactions
    for select using (
        exists (
            select 1 from public.whisper_group_members m
            where m.group_id = whisper_group_reactions.group_id and m.user_id = auth.uid()
        )
    );

drop policy if exists "whisper_group_reactions_write_own" on public.whisper_group_reactions;
create policy "whisper_group_reactions_write_own" on public.whisper_group_reactions
    for all using (
        user_id = auth.uid()
        and exists (
            select 1 from public.whisper_group_members m
            where m.group_id = whisper_group_reactions.group_id and m.user_id = auth.uid()
        )
    )
    with check (
        user_id = auth.uid()
        and exists (
            select 1 from public.whisper_group_members m
            where m.group_id = whisper_group_reactions.group_id and m.user_id = auth.uid()
        )
    );

create index if not exists whisper_group_reactions_group_idx
    on public.whisper_group_reactions (group_id, at desc);

create table if not exists public.whisper_group_join_requests (
    group_id uuid not null references public.whisper_groups(id) on delete cascade,
    user_id uuid not null,
    created_at timestamptz not null default now(),
    primary key (group_id, user_id)
);

alter table public.whisper_group_join_requests enable row level security;

-- Requester reads own row; admins read all rows of their groups.
drop policy if exists "whisper_group_join_requests_select" on public.whisper_group_join_requests;
create policy "whisper_group_join_requests_select" on public.whisper_group_join_requests
    for select using (
        user_id = auth.uid()
        or exists (
            select 1 from public.whisper_group_members m
            where m.group_id = whisper_group_join_requests.group_id
              and m.user_id = auth.uid() and m.role = 'admin'
        )
    );

-- Any signed-in user may ask in (admins approve via invite; friendship is
-- checked client-side before showing the button, like invites).
drop policy if exists "whisper_group_join_requests_insert_own" on public.whisper_group_join_requests;
create policy "whisper_group_join_requests_insert_own" on public.whisper_group_join_requests
    for insert with check (user_id = auth.uid());

-- Requester may withdraw; admins may deny.
drop policy if exists "whisper_group_join_requests_delete" on public.whisper_group_join_requests;
create policy "whisper_group_join_requests_delete" on public.whisper_group_join_requests
    for delete using (
        user_id = auth.uid()
        or exists (
            select 1 from public.whisper_group_members m
            where m.group_id = whisper_group_join_requests.group_id
              and m.user_id = auth.uid() and m.role = 'admin'
        )
    );

-- Wiping a message wipes its reactions too (no orphaned emoji).
create or replace function public.whisper_group_cleanup_reactions()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  delete from public.whisper_group_reactions
  where group_id = old.group_id and message_id = old.client_id;
  return old;
end;
$$;

drop trigger if exists trg_group_messages_cleanup_reactions on public.whisper_group_messages;
create trigger trg_group_messages_cleanup_reactions
  after delete on public.whisper_group_messages
  for each row execute function public.whisper_group_cleanup_reactions();

-- Approving consumes the request (invite insert wipes it, like join consumes
-- invites). Deny/withdraw is a plain delete via RLS.
create or replace function public.whisper_group_consume_join_request()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  delete from public.whisper_group_join_requests
  where group_id = new.group_id and user_id = new.user_id;
  return new;
end;
$$;

drop trigger if exists trg_group_invites_consume_request on public.whisper_group_invites;
create trigger trg_group_invites_consume_request
  after insert on public.whisper_group_invites
  for each row execute function public.whisper_group_consume_join_request();

do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'whisper_group_reactions'
  ) then
    alter publication supabase_realtime add table public.whisper_group_reactions;
  end if;
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'whisper_group_join_requests'
  ) then
    alter publication supabase_realtime add table public.whisper_group_join_requests;
  end if;
end $$;

alter table public.whisper_group_reactions replica identity full;
alter table public.whisper_group_join_requests replica identity full;

-- Webhook policy: NO webhooks on reactions / join-requests either. Reactions
-- would spam a push per tap; requests are covered by a single admin ping via
-- the invites webhook when approved (deny/withdraw stays silent in-app).

-- ═══════════════ 20261011_whisper_groups_invitee_history.sql ═
-- Join-path fix ("Group has no history" on Join): invitees could read the
-- group row and WRITE join/decline events, but could never READ the event
-- log — so fetchVerifiedMembership (and nextGroupSeqEpoch) saw zero rows.
-- The invite OR-branch below mirrors the groups-select/invites pattern.
-- Read-only for invitees (no UPDATE/DELETE policies exist on events); the
-- log stays append-only and signature-checked client-side either way.
drop policy if exists "whisper_group_events_select_member" on public.whisper_group_events;
create policy "whisper_group_events_select_member" on public.whisper_group_events
    for select using (
        exists (
            select 1 from public.whisper_group_members m
            where m.group_id = whisper_group_events.group_id and m.user_id = auth.uid()
        )
        or exists (
            select 1 from public.whisper_group_invites i
            where i.group_id = whisper_group_events.group_id and i.user_id = auth.uid()
        )
    );

-- ═══════════════ 20261017_whisper_push_targets.sql ═════════
-- FCM → UnifiedPush swap (F-Droid leaves the Firebase SDK and the foojay
-- resolver plugin non-compliant; see the app-side swap in
-- app/build.gradle.kts + push/WhisperPushService.kt). Push now rides
-- UnifiedPush (spec AND-3.1.0) with two delivery flavors —
--   * provider = 'webpush' — the endpoint came with RFC 8291 key sets; the
--     whisper-push-send edge function encrypts each ping (aes128gcm, RFC 8290)
--     and signs it with VAPID (RFC 8292). Serves the embedded FCM distributor
--     bundled in the app and webpush-capable externals (Sunup, gCompat-UP); the
--     ping is an opaque encrypted blob to the push service.
--   * provider = 'plain'  — the endpoint expects the raw wake-ping JSON bytes
--     (ntfy / NextPush UnifiedPush mode); same keys, no encryption envelope.
--
--   whisper_push_targets: one row per user (pk = user_id, so re-registration
--   upserts and endpoint rotation overwrite the prior target). The 'provider'
--   column is how the edge function branches between the two send paths.
--
--   keys_p256dh/keys_auth: the per-registration WebPush key set, base64url.
--   NULL for plain providers. Registry secrets, never logged; RLS keeps the
--   rows owner-only, the edge function uses the service role key.
--
--   The legacy whisper_fcm_tokens table is dropped: its only consumer (the
--   FCM send path) is removed in the same change.
--
-- Safe to run from the Supabase SQL Editor; every statement is idempotent.

create table if not exists public.whisper_push_targets (
    user_id     uuid not null primary key references auth.users(id) on delete cascade,
    provider    text not null check (provider in ('webpush', 'plain')),
    endpoint    text not null,
    keys_p256dh text,
    keys_auth   text,
    updated_at  timestamptz not null default now()
);

alter table public.whisper_push_targets enable row level security;

-- Owner-only access (drop-then-create keeps re-runs idempotent); the push edge
-- function reads/prunes rows via the service role key, which bypasses RLS.
drop policy if exists "whisper_push_targets_select_own" on public.whisper_push_targets;
create policy "whisper_push_targets_select_own"
    on public.whisper_push_targets
    for select
    using (user_id = auth.uid());

drop policy if exists "whisper_push_targets_insert_own" on public.whisper_push_targets;
create policy "whisper_push_targets_insert_own"
    on public.whisper_push_targets
    for insert
    with check (user_id = auth.uid());

drop policy if exists "whisper_push_targets_update_own" on public.whisper_push_targets;
create policy "whisper_push_targets_update_own"
    on public.whisper_push_targets
    for update
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

drop policy if exists "whisper_push_targets_delete_own" on public.whisper_push_targets;
create policy "whisper_push_targets_delete_own"
    on public.whisper_push_targets
    for delete
    using (user_id = auth.uid());

-- Stale-endpoint pruning (push service replied 404/410 for that endpoint).
create index if not exists whisper_push_targets_endpoint_idx
    on public.whisper_push_targets(endpoint);

-- Legacy FCM token registry — no client or function references it anymore.
drop table if exists public.whisper_fcm_tokens;
-- ═══════════════ 20261018_whisper_stories_lifecycle.sql ═
-- Daily Ups are photo-only, short-lived private posts.  These tables deliberately
-- contain only hosting metadata, audience identity, lifecycle state, and an opaque
-- per-recipient encrypted descriptor.  They must never receive a plaintext caption
-- or encryption key.  Client roles have no direct write route: publish, receipts,
-- expiry, and deletion are introduced through narrowly scoped RPCs/edge work in
-- later sections, while this contract already makes direct forgery fail closed.
--
-- Server time is authoritative.  RLS calls whisper_story_can_read(), which rejects
-- every story at expires_at even if an expiry worker has not run yet.  The audience
-- is an immutable snapshot: future friends have no recipient row to read.

create table if not exists public.whisper_stories (
    id uuid primary key default gen_random_uuid(),
    poster_id uuid not null references auth.users(id) on delete cascade,
    client_id uuid not null,
    host_url text not null check (char_length(host_url) between 1 and 4096),
    host_object_id text not null check (char_length(host_object_id) between 1 and 512),
    receipts_enabled boolean not null default false,
    state text not null default 'published'
        check (state in ('published', 'delete_pending', 'deleted', 'cleanup_failed')),
    created_at timestamptz not null default now(),
    expires_at timestamptz not null,
    deleted_at timestamptz,
    unique (poster_id, client_id),
    check (expires_at > created_at),
    check ((state = 'deleted') = (deleted_at is not null))
);

create index if not exists whisper_stories_poster_created_idx
    on public.whisper_stories (poster_id, created_at desc);
create index if not exists whisper_stories_expiry_idx
    on public.whisper_stories (expires_at)
    where state in ('published', 'delete_pending', 'cleanup_failed');

create table if not exists public.whisper_story_recipients (
    story_id uuid not null references public.whisper_stories(id) on delete cascade,
    recipient_id uuid not null references auth.users(id) on delete cascade,
    sealed_descriptor text not null check (char_length(sealed_descriptor) between 1 and 32768),
    created_at timestamptz not null default now(),
    delivered_at timestamptz,
    primary key (story_id, recipient_id)
);

create index if not exists whisper_story_recipients_recipient_idx
    on public.whisper_story_recipients (recipient_id, story_id);

create table if not exists public.whisper_story_views (
    story_id uuid not null references public.whisper_stories(id) on delete cascade,
    viewer_id uuid not null references auth.users(id) on delete cascade,
    viewed_at timestamptz not null default now(),
    primary key (story_id, viewer_id)
);

create index if not exists whisper_story_views_story_idx
    on public.whisper_story_views (story_id, viewed_at desc);

create table if not exists public.whisper_story_cleanup (
    story_id uuid primary key references public.whisper_stories(id) on delete cascade,
    status text not null default 'pending'
        check (status in ('pending', 'in_progress', 'retry', 'complete', 'terminal')),
    attempt_count integer not null default 0 check (attempt_count >= 0 and attempt_count <= 20),
    next_attempt_at timestamptz not null default now(),
    last_failure_class text check (last_failure_class is null or char_length(last_failure_class) <= 64),
    updated_at timestamptz not null default now()
);

create index if not exists whisper_story_cleanup_due_idx
    on public.whisper_story_cleanup (next_attempt_at)
    where status in ('pending', 'retry');

-- Successful Daily Ups are capped per UTC calendar day.  The table is
-- intentionally not client-writable: the atomic publishing function will count a
-- completed transaction, never an attempted upload.
create table if not exists public.whisper_story_publish_quota (
    poster_id uuid not null references auth.users(id) on delete cascade,
    day date not null,
    successful_count integer not null default 0
        check (successful_count >= 0 and successful_count <= 5),
    primary key (poster_id, day)
);

alter table public.whisper_stories enable row level security;
alter table public.whisper_story_recipients enable row level security;
alter table public.whisper_story_views enable row level security;
alter table public.whisper_story_cleanup enable row level security;
alter table public.whisper_story_publish_quota enable row level security;

-- The caller cannot choose either time.  This trigger covers service-role and
-- future RPC inserts too, making the 24-hour lifetime an invariant rather than a
-- convention in one publisher implementation.
create or replace function public.whisper_story_enforce_server_expiry()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if tg_op = 'INSERT' then
        new.created_at := now();
        new.expires_at := new.created_at + interval '24 hours';
        return new;
    end if;

    if new.created_at is distinct from old.created_at
       or new.expires_at is distinct from old.expires_at then
        raise exception 'Daily Up lifetime is server controlled' using errcode = '42501';
    end if;
    return new;
end;
$$;

drop trigger if exists whisper_story_enforce_server_expiry on public.whisper_stories;
create trigger whisper_story_enforce_server_expiry
    before insert or update on public.whisper_stories
    for each row execute function public.whisper_story_enforce_server_expiry();

-- SECURITY DEFINER avoids recursive RLS evaluation while preserving the caller's
-- auth.uid() in the policy argument.  It returns no row data and treats a block in
-- either direction as an immediate denial, including after a story was published.
create or replace function public.whisper_story_can_read(p_story_id uuid, p_user_id uuid)
returns boolean
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    v_poster_id uuid;
    v_expires_at timestamptz;
    v_state text;
begin
    if p_story_id is null or p_user_id is null then
        return false;
    end if;

    select poster_id, expires_at, state
      into v_poster_id, v_expires_at, v_state
      from public.whisper_stories
     where id = p_story_id;

    if not found or v_state <> 'published' or v_expires_at <= now() then
        return false;
    end if;

    if v_poster_id = p_user_id then
        return true;
    end if;

    if exists (
        select 1 from public.whisper_blocks wb
        where (wb.blocker_id = v_poster_id and wb.blocked_id = p_user_id)
           or (wb.blocker_id = p_user_id and wb.blocked_id = v_poster_id)
    ) then
        return false;
    end if;

    return exists (
        select 1 from public.whisper_story_recipients sr
        where sr.story_id = p_story_id and sr.recipient_id = p_user_id
    );
end;
$$;

create or replace function public.whisper_story_can_read_receipts(p_story_id uuid, p_user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
          from public.whisper_stories s
         where s.id = p_story_id
           and s.poster_id = p_user_id
           and s.receipts_enabled
           and s.state = 'published'
           and s.expires_at > now()
    );
$$;

revoke all on function public.whisper_story_can_read(uuid, uuid) from public, anon;
grant execute on function public.whisper_story_can_read(uuid, uuid) to authenticated;
revoke all on function public.whisper_story_can_read_receipts(uuid, uuid) from public, anon;
grant execute on function public.whisper_story_can_read_receipts(uuid, uuid) to authenticated;

drop policy if exists "whisper_stories_select_live_audience" on public.whisper_stories;
create policy "whisper_stories_select_live_audience"
    on public.whisper_stories for select to authenticated
    using (public.whisper_story_can_read(id, auth.uid()));

drop policy if exists "whisper_story_recipients_select_live_audience" on public.whisper_story_recipients;
create policy "whisper_story_recipients_select_live_audience"
    on public.whisper_story_recipients for select to authenticated
    using (public.whisper_story_can_read(story_id, auth.uid()));

drop policy if exists "whisper_story_views_select_live_authorized" on public.whisper_story_views;
create policy "whisper_story_views_select_live_authorized"
    on public.whisper_story_views for select to authenticated
    using (
        (viewer_id = auth.uid() and public.whisper_story_can_read(story_id, auth.uid()))
        or public.whisper_story_can_read_receipts(story_id, auth.uid())
    );

-- Quota is safe for a user to inspect but all mutation is held for the atomic
-- publishing RPC.  Cleanup has no client policy at all; only server work may touch it.
drop policy if exists "whisper_story_publish_quota_select_own" on public.whisper_story_publish_quota;
create policy "whisper_story_publish_quota_select_own"
    on public.whisper_story_publish_quota for select to authenticated
    using (poster_id = auth.uid());

-- Keep account deletion complete before GoTrue removes the identity.  This replaces
-- the earlier function body rather than adding a second cleanup path.
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
    delete from public.whisper_story_views where viewer_id = p_uid;
    delete from public.whisper_story_recipients where recipient_id = p_uid;
    delete from public.whisper_stories where poster_id = p_uid;
    delete from public.whisper_story_publish_quota where poster_id = p_uid;
    delete from public.profiles where id = p_uid;
    delete from public.whisper_upload_quota where user_id = p_uid;
    delete from public.whisper_deleted_tombstones where user_id = p_uid;
    delete from public.whisper_discover_quota where user_id = p_uid;
    delete from public.whisper_username_check_quota where user_id = p_uid;
    delete from public.whisper_image_ownership where user_id = p_uid;
    -- UnifiedPush targets are cascade-cleaned with their account; the legacy
    -- FCM registry was removed by the UnifiedPush migration above.
    delete from public.whisper_prekeys where account = p_uid;
end;
$$;

revoke all on function public.whisper_purge_account_data(uuid) from public, anon, authenticated;
grant execute on function public.whisper_purge_account_data(uuid) to service_role;

-- ═══════════════ 20261018_whisper_group_policy_boundary.sql ═
-- GroupPolicyV1 is signed and verified by clients, while this server boundary
-- makes a modified client unable to directly change ownership. A POLICY row is
-- accepted only from the current owner and may nominate only an existing admin.

alter table public.whisper_group_events drop constraint if exists whisper_group_events_type_check;
alter table public.whisper_group_events add constraint whisper_group_events_type_check
    check (type in ('create', 'remove', 'leave', 'rename', 'promote', 'demote',
                    'settings', 'invite', 'join', 'decline', 'cancel', 'picture', 'policy'));

create or replace function public.whisper_group_validate_policy_event()
returns trigger language plpgsql security definer set search_path = public as $$
declare
    v_owner uuid;
    v_epoch bigint;
    v_policy jsonb;
    v_next_owner uuid;
    v_capability text;
begin
    if new.type <> 'policy' then return new; end if;
    select created_by, epoch into v_owner, v_epoch
      from public.whisper_groups where id = new.group_id for update;
    if not found then raise exception 'unknown group' using errcode = '42501'; end if;
    if new.actor <> auth.uid() or new.actor <> v_owner then
        raise exception 'only the current owner may publish group policy' using errcode = '42501';
    end if;
    begin
        v_policy := new.payload::jsonb;
        v_next_owner := (v_policy ->> 'owner')::uuid;
    exception when others then
        raise exception 'invalid group policy payload' using errcode = '22023';
    end;
    if jsonb_typeof(v_policy) <> 'object'
       or (select count(*) from jsonb_object_keys(v_policy)) <> 4
       or not (v_policy ? 'v' and v_policy ? 'owner' and v_policy ? 'admin' and v_policy ? 'member')
       or v_policy ->> 'v' <> '1'
       or jsonb_typeof(v_policy -> 'admin') <> 'array'
       or jsonb_typeof(v_policy -> 'member') <> 'array' then
        raise exception 'invalid group policy payload' using errcode = '22023';
    end if;
    if not exists (
        select 1 from public.whisper_group_members m
         where m.group_id = new.group_id and m.user_id = v_next_owner and m.role = 'admin'
    ) then raise exception 'group policy owner must be a current admin' using errcode = '42501'; end if;
    for v_capability in
        select value from jsonb_array_elements_text(v_policy -> 'admin') as caps(value)
        union all select value from jsonb_array_elements_text(v_policy -> 'member') as caps(value)
    loop
        if v_capability not in ('send', 'invite', 'approveJoin', 'remove', 'manageRoles', 'editInfo', 'editPicture', 'createPoll', 'closePoll') then
            raise exception 'unknown group policy capability' using errcode = '22023';
        end if;
    end loop;
    if exists (
        select 1 from jsonb_array_elements_text(v_policy -> 'member') as caps(value)
         where value not in ('send', 'invite', 'editInfo', 'editPicture', 'createPoll')
    ) then raise exception 'member policy grants a privileged capability' using errcode = '42501'; end if;
    if v_next_owner <> v_owner and new.epoch <> v_epoch + 1 then
        raise exception 'ownership transfer requires the next epoch' using errcode = '42501';
    end if;
    if v_next_owner = v_owner and new.epoch <> v_epoch then
        raise exception 'policy-only update must retain the current epoch' using errcode = '42501';
    end if;
    return new;
end;
$$;

-- This name sorts before the existing epoch-ratchet trigger, so validation sees
-- the pre-ratchet epoch.
drop trigger if exists aaa_group_events_policy_validate on public.whisper_group_events;
create trigger aaa_group_events_policy_validate before insert on public.whisper_group_events
    for each row execute function public.whisper_group_validate_policy_event();

create or replace function public.whisper_group_apply_policy_owner()
returns trigger language plpgsql security definer set search_path = public as $$
declare v_next_owner uuid;
begin
    if new.type <> 'policy' then return new; end if;
    v_next_owner := (new.payload::jsonb ->> 'owner')::uuid;
    if v_next_owner is distinct from (select created_by from public.whisper_groups where id = new.group_id) then
        perform set_config('whisper.group_policy_transfer', '1', true);
        update public.whisper_groups set created_by = v_next_owner where id = new.group_id;
    end if;
    return new;
end;
$$;

drop trigger if exists trg_group_events_policy_owner on public.whisper_group_events;
create trigger trg_group_events_policy_owner after insert on public.whisper_group_events
    for each row execute function public.whisper_group_apply_policy_owner();

create or replace function public.whisper_group_reject_direct_owner_change()
returns trigger language plpgsql security definer set search_path = public as $$
begin
    if new.created_by is distinct from old.created_by
       and current_setting('whisper.group_policy_transfer', true) is distinct from '1' then
        raise exception 'ownership transfer requires a signed policy event' using errcode = '42501';
    end if;
    return new;
end;
$$;

drop trigger if exists trg_group_reject_direct_owner_change on public.whisper_groups;
create trigger trg_group_reject_direct_owner_change before update on public.whisper_groups
    for each row execute function public.whisper_group_reject_direct_owner_change();

-- ═══════════════ 20261018_whisper_stories_actions.sql ═
-- Daily Ups write plane.  The lifecycle section above created the tables and
-- their read-only RLS; this section introduces the ONLY write routes:
--
--   whisper_story_publish      — idempotent, atomic (story + audience + quota
--                                in one transaction), validates the audience
--                                against CURRENT accepted friendships and the
--                                block ledger, and stores one sealed descriptor
--                                per recipient (never a plaintext caption key).
--   whisper_story_view         — idempotent view receipt, recorded only for
--                                recipients of a receipts_enabled story.
--   whisper_story_react        — one toggleable emoji per viewer per story,
--                                independent of the receipts toggle.
--   whisper_story_delete       — poster-side delete: flips state to
--                                'delete_pending' (RLS hides the story the
--                                same instant) and queues hosting cleanup.
--   whisper_story_cleanup_*    — service-role lease/retry registry consumed by
--                                the whisper-story-sweep edge function, which
--                                resolves the hosted copy through the
--                                whisper_image_ownership ledger, deletes it, and
--                                converges the story to state='deleted'
--                                (≤ 20 attempts).
--
-- RECEIPTS OF TRUST: p_host_object_id is the ImgBB delete capability the
-- upload edge hands back (the client never receives the raw ImgBB image id).
-- The sweep NEVER GETs a caller-supplied string; it looks the capability up in
-- whisper_image_ownership (unique delete_url, written server-side at upload)
-- and GETs the ledger's own delete_url, so a forged meta row can only point at
-- a capability Supabase itself recorded.  A missing ledger row is a failure
-- class, not a silent success.
--
-- There is deliberately no pg_cron: the publish RPC runs a bounded watchdog
-- that backfills missing cleanup rows for expired stories, and the sweep edge
-- function is invoked opportunistically by clients (and can be wired to any
-- external scheduler later without a schema change).
--
-- Server time is authoritative everywhere (the lifetime trigger above rewrites
-- created_at/expires_at).  A viewer can never be "denied later" inconsistently:
-- whisper_story_can_read() re-checks state, expiry, blocks and the recipient
-- snapshot on every read.

-- The poster re-publishing with the same client_id must not widen the audience:
-- unique (poster_id, client_id) already covers this; nothing else changes shape.
-- Hosting state stays in whisper_image_ownership (written by the upload edge);
-- whisper_stories deliberately stores no delete capability column.

-- ── 1. Reactions ────────────────────────────────────────────────────────────
-- One row per (story, viewer): a viewer holds at most one emoji.  Visible to
-- the story poster (always — even with view receipts off, per the §11
-- server-visibility contract) and to the reaction author.  Stored as the
-- unicode emoji itself; the whitelist is the wire vocabulary.

create table if not exists public.whisper_story_reactions (
    story_id uuid not null references public.whisper_stories(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    emoji text not null check (emoji in ('👍', '❤️', '😂', '😮', '😢', '🙏')),
    created_at timestamptz not null default now(),
    primary key (story_id, user_id)
);

create index if not exists whisper_story_reactions_story_idx
    on public.whisper_story_reactions (story_id, created_at);

alter table public.whisper_story_reactions enable row level security;

-- Poster identity is needed for both policies; SECURITY DEFINER keeps the
-- sub-select out of recursive whisper_stories RLS evaluation.
create or replace function public.whisper_story_is_poster(p_story_id uuid, p_user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1 from public.whisper_stories s
         where s.id = p_story_id and s.poster_id = p_user_id
    );
$$;

revoke all on function public.whisper_story_is_poster(uuid, uuid) from public, anon;
grant execute on function public.whisper_story_is_poster(uuid, uuid) to authenticated;

-- Viewers see only their own rows; the poster sees the full tally.
drop policy if exists "whisper_story_reactions_select_poster_or_author" on public.whisper_story_reactions;
create policy "whisper_story_reactions_select_poster_or_author"
    on public.whisper_story_reactions for select to authenticated
    using (
        user_id = auth.uid()
        or public.whisper_story_is_poster(story_id, auth.uid())
    );

drop policy if exists "whisper_story_reactions_insert_audience" on public.whisper_story_reactions;
create policy "whisper_story_reactions_insert_audience"
    on public.whisper_story_reactions for insert to authenticated
    with check (
        user_id = auth.uid()
        and public.whisper_story_can_read(story_id, auth.uid())
        and not public.whisper_story_is_poster(story_id, auth.uid())
    );

-- An author may withdraw their emoji but never edit someone else's.
drop policy if exists "whisper_story_reactions_delete_author" on public.whisper_story_reactions;
create policy "whisper_story_reactions_delete_author"
    on public.whisper_story_reactions for delete to authenticated
    using (user_id = auth.uid());

-- Poster edits (the RPC upserts the emoji in place on a re-choice) — author-only.
drop policy if exists "whisper_story_reactions_update_author" on public.whisper_story_reactions;
create policy "whisper_story_reactions_update_author"
    on public.whisper_story_reactions for update to authenticated
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

-- ── 2. Publishing ───────────────────────────────────────────────────────────
-- p_recipients wire format: [{"r":"<recipient uuid>","d":"<sealed descriptor>"}].
-- Everything is validated server-side: current accepted friendship, block ledger
-- in either direction, descriptor size, and the 5-per-UTC-day quota inside the
-- same transaction as the story + audience rows (a failed quota never strands
-- rows, and a successful one always counts exactly once).

create or replace function public.whisper_story_publish(
    p_client_id uuid,
    p_host_url text,
    p_host_object_id text,
    p_receipts_enabled boolean,
    p_recipients jsonb,
    p_client_version_code int
)
returns public.whisper_stories
language plpgsql
security definer
set search_path = public
as $$
declare
    v_caller uuid := auth.uid();
    v_story public.whisper_stories;
    v_entry jsonb;
    v_recipient_id uuid;
    v_descriptor text;
begin
    if v_caller is null then
        raise exception 'authenticated caller required' using errcode = '42501';
    end if;
    if p_client_id is null then
        raise exception 'client_id required' using errcode = '22023';
    end if;
    if p_client_version_code is null or p_client_version_code < 18 then
        raise exception 'stale client version' using errcode = '42501';
    end if;
    if p_host_url is null or p_host_url !~ '^https://[[:alnum:]]' or char_length(p_host_url) > 4096 then
        raise exception 'invalid hosting metadata' using errcode = '22023';
    end if;
    -- p_host_object_id is the delete capability the upload edge returned; the
    -- sweep resolves the real URL through whisper_image_ownership, so this
    -- column never has to be a trustworthy URL itself — just a bounded handle.
    if p_host_object_id is null or char_length(p_host_object_id) not between 1 and 512 then
        raise exception 'invalid hosting metadata' using errcode = '22023';
    end if;
    if p_recipients is null
       or jsonb_typeof(p_recipients) <> 'array'
       or jsonb_array_length(p_recipients) > 96 then
        raise exception 'invalid recipients payload' using errcode = '22023';
    end if;

    -- Idempotency probe: a retry with the same client_id returns the live story
    -- without re-counting quota and without touching the audience snapshot.
    select * into v_story
      from public.whisper_stories
     where poster_id = v_caller and client_id = p_client_id;
    if found then
        return v_story;
    end if;

    -- Audience validation.  One pass per entry; duplicates collapse later.
    for v_entry in select * from jsonb_array_elements(p_recipients) loop
        if jsonb_typeof(v_entry) <> 'object'
           or not (v_entry ? 'r') or not (v_entry ? 'd') then
            raise exception 'invalid recipients payload' using errcode = '22023';
        end if;
        v_recipient_id := (v_entry ->> 'r')::uuid;
        v_descriptor := v_entry ->> 'd';
        if v_recipient_id is null then
            raise exception 'invalid recipients payload' using errcode = '22023';
        end if;
        if char_length(coalesce(v_descriptor, '')) not between 1 and 32768 then
            raise exception 'invalid recipients payload' using errcode = '22023';
        end if;
        if v_recipient_id = v_caller then
            continue; -- the poster never needs a descriptor to their own copy
        end if;
        if not exists (
            select 1 from public.friends f
             where ((f.user_a = v_caller and f.user_b = v_recipient_id)
                 or (f.user_a = v_recipient_id and f.user_b = v_caller))
               and f.status = 'accepted'
        ) then
            raise exception 'story audience must be current friends' using errcode = '42501';
        end if;
        if exists (
            select 1 from public.whisper_blocks wb
             where (wb.blocker_id = v_caller and wb.blocked_id = v_recipient_id)
                or (wb.blocker_id = v_recipient_id and wb.blocked_id = v_caller)
        ) then
            raise exception 'story audience must be current friends' using errcode = '42501';
        end if;
    end loop;

    insert into public.whisper_stories (
        poster_id, client_id, host_url, host_object_id,
        receipts_enabled, state
    ) values (
        v_caller, p_client_id, p_host_url, p_host_object_id,
        coalesce(p_receipts_enabled, false), 'published'
    )
    on conflict (poster_id, client_id) do nothing
    returning * into v_story;

    -- Lost a concurrent race: the winner already published this client_id.
    if not found then
        select * into v_story from public.whisper_stories
         where poster_id = v_caller and client_id = p_client_id;
        return v_story;
    end if;

    -- Quota: atomic 5-per-UTC-day cap; the check runs on the ROW being updated.
    insert into public.whisper_story_publish_quota (poster_id, day, successful_count)
    values (v_caller, (now() at time zone 'utc')::date, 1)
    on conflict (poster_id, day) do update
        set successful_count = public.whisper_story_publish_quota.successful_count + 1
      where public.whisper_story_publish_quota.successful_count < 5;
    if not found then
        raise exception 'quota exceeded' using errcode = '42501';
    end if;

    -- Audience snapshot: first descriptor per recipient wins.
    insert into public.whisper_story_recipients (story_id, recipient_id, sealed_descriptor)
    select v_story.id,
           (entry ->> 'r')::uuid,
           entry ->> 'd'
      from jsonb_array_elements(p_recipients) as entry
     where (entry ->> 'r')::uuid is distinct from v_caller
    on conflict (story_id, recipient_id) do nothing;

    -- Housekeeping without pg_cron: prune this poster's stale quota rows and
    -- backfill missing cleanup registrations for expired stories (bounded).
    delete from public.whisper_story_publish_quota
     where poster_id = v_caller and day < (now() at time zone 'utc')::date - 8;
    insert into public.whisper_story_cleanup (story_id, status)
    select s.id, 'pending'
      from public.whisper_stories s
     where s.expires_at < now() - interval '1 hour'
       and s.state in ('published', 'delete_pending', 'cleanup_failed')
       and not exists (select 1 from public.whisper_story_cleanup c where c.story_id = s.id)
     limit 50
    on conflict (story_id) do nothing;

    return v_story;
end;
$$;

create or replace function public.whisper_story_view(
    p_story_id uuid,
    p_client_version_code int
)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
    v_caller uuid := auth.uid();
    v_receipts boolean;
    v_poster uuid;
begin
    if v_caller is null then
        raise exception 'authenticated caller required' using errcode = '42501';
    end if;
    if p_client_version_code is null or p_client_version_code < 18 then
        raise exception 'stale client version' using errcode = '42501';
    end if;
    -- can_read() enforces state, expiry, the block ledger and the audience.
    if not public.whisper_story_can_read(p_story_id, v_caller) then
        raise exception 'not a member' using errcode = '42501';
    end if;

    select receipts_enabled, poster_id into v_receipts, v_poster
      from public.whisper_stories where id = p_story_id;
    -- The poster opening their own story is not an audience receipt.
    if v_poster = v_caller then
        return true;
    end if;
    -- Without receipts the view stays private and unrecorded.
    if not coalesce(v_receipts, false) then
        return true;
    end if;

    insert into public.whisper_story_views (story_id, viewer_id)
    values (p_story_id, v_caller)
    on conflict (story_id, viewer_id) do nothing;

    return true;
end;
$$;

create or replace function public.whisper_story_react(
    p_story_id uuid,
    p_emoji text,
    p_on boolean,
    p_client_version_code int
)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
    v_caller uuid := auth.uid();
begin
    if v_caller is null then
        raise exception 'authenticated caller required' using errcode = '42501';
    end if;
    if p_client_version_code is null or p_client_version_code < 18 then
        raise exception 'stale client version' using errcode = '42501';
    end if;
    if p_emoji is null
       or p_emoji not in ('👍', '❤️', '😂', '😮', '😢', '🙏') then
        raise exception 'unknown reaction emoji' using errcode = '22023';
    end if;
    if not public.whisper_story_can_read(p_story_id, v_caller) then
        raise exception 'not a member' using errcode = '42501';
    end if;
    if public.whisper_story_is_poster(p_story_id, v_caller) then
        raise exception 'story reactions are for the audience' using errcode = '42501';
    end if;

    if coalesce(p_on, false) then
        insert into public.whisper_story_reactions (story_id, user_id, emoji)
        values (p_story_id, v_caller, p_emoji)
        on conflict (story_id, user_id)
        do update set emoji = excluded.emoji;
    else
        delete from public.whisper_story_reactions
         where story_id = p_story_id and user_id = v_caller and emoji = p_emoji;
    end if;
    return true;
end;
$$;

create or replace function public.whisper_story_delete(p_story_id uuid)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
    v_caller uuid := auth.uid();
begin
    if v_caller is null then
        raise exception 'authenticated caller required' using errcode = '42501';
    end if;
    -- Only the poster may delete; everything else is a silent no (encryption
    -- keys of other stories are, inviolably, none of the caller's business).
    perform 1 from public.whisper_stories
     where id = p_story_id and poster_id = v_caller;
    if not found then
        return false;
    end if;

    -- 'delete_pending' already hides the story from every reader (including
    -- the poster): whisper_story_can_read() requires state = 'published'.
    update public.whisper_stories
       set state = 'delete_pending'
     where id = p_story_id
       and state in ('published', 'cleanup_failed', 'delete_pending')
       and deleted_at is null;

    -- (Re)queue hosting cleanup — a poster retry un-sticks a terminal streak.
    insert into public.whisper_story_cleanup (story_id, status, next_attempt_at)
    values (p_story_id, 'pending', now())
    on conflict (story_id) do update
        set status = 'pending',
            attempt_count = 0,
            next_attempt_at = now(),
            last_failure_class = null,
            updated_at = now();

    return true;
end;
$$;

-- ── 3. Cleanup registry (service-role only) ─────────────────────────────────
-- whisper-story-sweep leases work with SKIP LOCKED, deletes the hosted copy via
-- the stored capability URL, and reports one of three outcomes:
--   ok              → story converges to state='deleted' (+deleted_at).
--   failure         → status 'retry' with exponential backoff (≤ 20 attempts).
--   silent abandon  → the 10-minute lease expires and the row becomes due again.
-- Terminal rows stop being claimed; a poster-side whisper_story_delete retry
-- resets them (attempt_count back to 0), so nothing is ever permanently stuck.

create or replace function public.whisper_story_cleanup_claim(
    p_limit int,
    p_lease_seconds int
)
returns table (
    story_id uuid,
    host_url text,
    host_object_id text,
    attempt_count int,
    expires_at timestamptz
)
language plpgsql
security definer
set search_path = public
as $$
declare
    v_ids uuid[];
    v_limit int := 10;
    v_lease interval := interval '10 minutes';
begin
    -- The caller is the edge function running under the service role: any
    -- authenticated human caller must not be able to lease cleanup work.
    if auth.uid() is not null then
        raise exception 'service role only' using errcode = '42501';
    end if;
    if p_limit is not null and p_limit between 1 and 25 then
        v_limit := p_limit;
    end if;
    if p_lease_seconds is not null and p_lease_seconds between 30 and 1800 then
        v_lease := make_interval(secs => p_lease_seconds);
    end if;

    -- Watchdog: register expired-but-unregistered stories so hygiene never
    -- depends on a scheduler having run before every failure.
    insert into public.whisper_story_cleanup (story_id, status)
    select s.id, 'pending'
      from public.whisper_stories s
     where s.expires_at < now() - interval '1 hour'
       and s.state in ('published', 'delete_pending', 'cleanup_failed')
       and not exists (select 1 from public.whisper_story_cleanup c where c.story_id = s.id)
     limit 50
    on conflict (story_id) do nothing;

    -- Lock then lease: the row locks hold for the rest of this transaction,
    -- so concurrent sweeps serialise here instead of double-deleting.
    select coalesce(array_agg(due.story_id), '{}') into v_ids
      from (
        select d.story_id
          from public.whisper_story_cleanup d
          join public.whisper_stories st on st.id = d.story_id
         where d.status in ('pending', 'retry', 'in_progress')
           and d.attempt_count < 20
           and d.next_attempt_at <= now()
           and (st.state in ('delete_pending', 'cleanup_failed') or st.expires_at <= now())
         order by d.next_attempt_at
         for update of d skip locked
         limit v_limit
      ) due;

    return query
    with leased as (
        update public.whisper_story_cleanup c
           set status = 'in_progress',
               attempt_count = c.attempt_count + 1,
               next_attempt_at = now() + v_lease,
               updated_at = now()
         where c.story_id = any(v_ids)
        returning c.story_id, c.attempt_count
    )
    select s.id,
           s.host_url,
           s.host_object_id,
           leased.attempt_count,
           s.expires_at
      from leased
      join public.whisper_stories s on s.id = leased.story_id;
end;
$$;

create or replace function public.whisper_story_cleanup_result(
    p_story_id uuid,
    p_ok boolean,
    p_failure_class text
)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
    v_attempts int;
begin
    if auth.uid() is not null then
        raise exception 'service role only' using errcode = '42501';
    end if;
    if p_story_id is null then
        return false;
    end if;

    select attempt_count into v_attempts
      from public.whisper_story_cleanup
     where story_id = p_story_id
     for update;
    if not found then
        return false;
    end if;

    if coalesce(p_ok, false) then
        update public.whisper_stories
           set state = 'deleted',
               deleted_at = now()
         where id = p_story_id
           and deleted_at is null;
        update public.whisper_story_cleanup
           set status = 'complete',
               next_attempt_at = now(),
               last_failure_class = null,
               updated_at = now()
         where story_id = p_story_id;
        -- Prune long-complete rows so the registry stays tiny.
        delete from public.whisper_story_cleanup
         where status = 'complete' and updated_at < now() - interval '7 days';
        return true;
    end if;

    if coalesce(v_attempts, 0) >= 20 then
        update public.whisper_story_cleanup
           set status = 'terminal',
               last_failure_class = left(coalesce(p_failure_class, 'unknown'), 64),
               next_attempt_at = now(),
               updated_at = now()
         where story_id = p_story_id;
        return false;
    end if;

    update public.whisper_story_cleanup
       set status = 'retry',
           last_failure_class = left(coalesce(p_failure_class, 'unknown'), 64),
           next_attempt_at = now()
               + least(power(2, greatest(coalesce(v_attempts, 1), 1)) * interval '15 minutes',
                       interval '6 hours'),
           updated_at = now()
     where story_id = p_story_id;
    return false;
end;
$$;

-- ── 4. Grants + realtime wiring ─────────────────────────────────────────────
revoke all on function public.whisper_story_publish(uuid, text, text, boolean, jsonb, int)
    from public, anon;
grant execute on function public.whisper_story_publish(uuid, text, text, boolean, jsonb, int)
    to authenticated;

revoke all on function public.whisper_story_view(uuid, int) from public, anon;
grant execute on function public.whisper_story_view(uuid, int) to authenticated;

revoke all on function public.whisper_story_react(uuid, text, boolean, int)
    from public, anon;
grant execute on function public.whisper_story_react(uuid, text, boolean, int)
    to authenticated;

revoke all on function public.whisper_story_delete(uuid) from public, anon;
grant execute on function public.whisper_story_delete(uuid) to authenticated;

revoke all on function public.whisper_story_cleanup_claim(int, int)
    from public, anon, authenticated;
grant execute on function public.whisper_story_cleanup_claim(int, int) to service_role;

revoke all on function public.whisper_story_cleanup_result(uuid, boolean, text)
    from public, anon, authenticated;
grant execute on function public.whisper_story_cleanup_result(uuid, boolean, text) to service_role;

-- The story plane rides Postgres Changes like groups do: recipients receive
-- their sealed descriptors (INSERT), the poster receives receipts and reactions,
-- and replica identity full lets DELETE/UPDATE events carry the old row so the
-- client can mirror removals without a follow-up SELECT.
do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'whisper_stories'
  ) then
    alter publication supabase_realtime add table public.whisper_stories;
  end if;
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'whisper_story_recipients'
  ) then
    alter publication supabase_realtime add table public.whisper_story_recipients;
  end if;
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'whisper_story_views'
  ) then
    alter publication supabase_realtime add table public.whisper_story_views;
  end if;
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'whisper_story_reactions'
  ) then
    alter publication supabase_realtime add table public.whisper_story_reactions;
  end if;
end $$;

alter table public.whisper_stories replica identity full;
alter table public.whisper_story_recipients replica identity full;
alter table public.whisper_story_views replica identity full;
alter table public.whisper_story_reactions replica identity full;


-- ═══════════════ 20261019_whisper_signer_history.sql ═══════════════
-- 20261019 — Account-owned signer history (reinstall-proof group events)
-- WHY (second-pass review, §15.4): group events are signed by the
--   per-install protocol signer. A reinstall (new phone — ROUTINE) destroys
--   the old keystore key with no way to sign an ROTv2 chain (the old
--   private key is gone), so the actor's OLD signatures could never verify
--   on the new install and the group failed closed FOR THE CREATOR. The
--   group belongs to the ACCOUNT, not the device, so the trust root for
--   event verification moves to the account:
--   - every signer the account ever PUBLISHED is archived server-side
--     (public-key material only — the same certs whisper-bundle-fetch
--     already serves), and
--   - each event row now carries the signer cert that made its admin_sig.
--   Verification = signature under the row cert, and the row cert must be
--   a member of the actor's published signer set. The server cannot forge
--   events (signatures cover the full body and it holds no private keys);
--   the set pins which keys were ever legitimately the account's.
-- NOTE: verifier fall-back for rows WITHOUT signer_cert keeps the old
--   current-bundle-only behavior (pre-1.1.7 rows only — groups were not
--   publicly shipped before this release).

create table if not exists public.whisper_group_signer_history (
    user_id     uuid not null references auth.users (id) on delete cascade,
    signer_cert text not null,
    first_seen  timestamptz not null default now(),
    primary key (user_id, signer_cert)
);
comment on table public.whisper_group_signer_history is
  'Every protocol signer the account ever published in identity_binding (archived by trigger). Public-key material only. INSERT-only; clients read their OWN rows; peers read via whisper-bundle-fetch (service role).';

alter table public.whisper_group_signer_history enable row level security;
drop policy if exists "whisper signer history self read" on public.whisper_group_signer_history;
create policy "whisper signer history self read"
    on public.whisper_group_signer_history
    for select to authenticated
    using (user_id = auth.uid());
-- Deliberately NO insert/update/delete policy: rows are written ONLY by the
-- archive trigger (security definer) or service role. A stolen client
-- session token cannot mint signer rows directly.

-- Archive on the same client write that publishes a new identity binding.
-- Covers insert (first publish) and update (rotation / reinstall republish).
create or replace function public.whisper_archive_group_signer()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    v_signer text := new.identity_binding ->> 'signer';
begin
    -- profiles' PK is `id` (the auth uid) — other whisper tables'
    -- `user_id` naming does NOT exist on profiles. Using new.user_id
    -- here would throw "record new has no field user_id" on every
    -- identity publish and brick the write.
    if v_signer is not null and length(v_signer) > 0 and not exists (
        select 1 from public.whisper_group_signer_history h
        where h.user_id = new.id and h.signer_cert = v_signer
    ) then
        insert into public.whisper_group_signer_history (user_id, signer_cert)
        values (new.id, v_signer);
    end if;
    return new;
end;
$$;

drop trigger if exists whisper_archive_group_signer on public.profiles;
create trigger whisper_archive_group_signer
    before insert or update of identity_binding
    on public.profiles
    for each row execute function public.whisper_archive_group_signer();

-- One-time backfill so already-published bindings are archived even if they
-- are never republished (idempotent; runs on every SQL re-run).
-- profiles PK: `id` (auth uid). See the trigger note above.
insert into public.whisper_group_signer_history (user_id, signer_cert)
select p.id, p.identity_binding ->> 'signer'
from public.profiles p
where p.identity_binding ->> 'signer' is not null
on conflict (user_id, signer_cert) do nothing;

-- The per-event signer: which key made admin_sig, stored ON the row.
alter table public.whisper_group_events add column if not exists signer_cert text;

-- ═══════════════ end 20261019_whisper_signer_history.sql ═══════════════


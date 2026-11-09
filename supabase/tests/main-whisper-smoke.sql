-- ============================================================================
-- Whisper SQL smoke — behavioural checks for the triggers/policies/purge added
-- in the hidden Groups / Daily Ups hardening pass.
--
-- Run against a DISPOSABLE database only (it truncates the Whisper tables):
--   psql -v ON_ERROR_STOP=1 -f supabase/tests/main-whisper-smoke.sql
--
-- Every check raises `SMOKE FAIL: ...` and aborts the run. Expected-rejection
-- checks wrap the statement so only an ACCEPTED forbidden write fails.
-- ============================================================================
\set ON_ERROR_STOP on
\pset pager off

-- SAFETY: this file TRUNCATES the Whisper tables. Refuse to run unless the
-- caller explicitly marks the database disposable.
do $$
begin
    if coalesce(current_setting('whisper.smoke', true), '') <> '1' then
        raise exception 'Refusing to run: this suite wipes Whisper tables. Set whisper.smoke=1 on a DISPOSABLE database';
    end if;
end $$;

-- ── Reset (disposable DB) ───────────────────────────────────────────────────
do $$
declare
    r record;
begin
    for r in
        select tablename from pg_tables
         where schemaname = 'public'
           and (tablename like 'whisper\_%' or tablename in ('messages', 'message_reactions', 'friends', 'profiles'))
    loop
        execute format('truncate table public.%I cascade', r.tablename);
    end loop;
    delete from auth.users;
end $$;

-- ── Seed accounts / profiles ────────────────────────────────────────────────
insert into auth.users (id, email) values
  ('00000000-0000-0000-0000-000000000001', 'u1@smoke.test'),
  ('00000000-0000-0000-0000-000000000002', 'u2@smoke.test'),
  ('00000000-0000-0000-0000-000000000003', 'u3@smoke.test'),
  ('00000000-0000-0000-0000-000000000004', 'u4@smoke.test'),
  ('00000000-0000-0000-0000-000000000005', 'u5@smoke.test');

insert into public.profiles (id, username, display_name, identity_binding) values
  ('00000000-0000-0000-0000-000000000001', 'smoke_u1', 'Smoke One', '{"signer":"CERT-U1"}'::jsonb),
  ('00000000-0000-0000-0000-000000000002', 'smoke_u2', 'Smoke Two', '{"signer":"CERT-U2"}'::jsonb),
  ('00000000-0000-0000-0000-000000000003', 'smoke_u3', 'Smoke Three', '{"signer":"CERT-U3"}'::jsonb),
  ('00000000-0000-0000-0000-000000000004', 'smoke_u4', 'Smoke Four', null),
  ('00000000-0000-0000-0000-000000000005', 'smoke_u5', 'Smoke Five', null);

-- PART A1: every published binding is archived.
do $$
declare v_missing int;
begin
    select count(*) into v_missing
      from public.profiles p
     where p.identity_binding ->> 'signer' is not null
       and not exists (
           select 1 from public.whisper_group_signer_history h
            where h.user_id = p.id and h.signer_cert = p.identity_binding ->> 'signer');
    if v_missing <> 0 then
        raise exception 'SMOKE FAIL A1: % published binding(s) not archived', v_missing;
    end if;
end $$;

-- PART A2: the archive trigger fires live and stays deduped.
do $$
declare v_before int; v_after int;
begin
    select count(*) into v_before from public.whisper_group_signer_history
     where user_id = '00000000-0000-0000-0000-000000000001';
    update public.profiles set identity_binding = identity_binding
     where id = '00000000-0000-0000-0000-000000000001';
    select count(*) into v_after from public.whisper_group_signer_history
     where user_id = '00000000-0000-0000-0000-000000000001';
    if v_after <> v_before then
        raise exception 'SMOKE FAIL A2: signer archive duplicated on no-op update (% -> %)', v_before, v_after;
    end if;
end $$;

-- PART A3: the per-event signer column exists.
do $$
begin
    if not exists (
        select 1 from information_schema.columns
         where table_schema = 'public' and table_name = 'whisper_group_events'
           and column_name = 'signer_cert')
    then
        raise exception 'SMOKE FAIL A3: whisper_group_events.signer_cert is missing';
    end if;
end $$;

-- ── Seed social + 1:1 rows ──────────────────────────────────────────────────
insert into public.friends (user_a, user_b, status) values
  ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', 'accepted'),
  ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000003', 'accepted');

insert into public.whisper_blocks (blocker_id, blocked_id)
values ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000005');

insert into public.messages (id, sender_id, receiver_id, content, content_iv) values
  ('20000000-0000-0000-0000-000000000001',
   '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', 'c1', 'iv1'),
  ('20000000-0000-0000-0000-000000000002',
   '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'c2', 'iv2');

insert into public.message_reactions (message_id, user_id, emoji)
values ('20000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', '👍');

insert into public.whisper_upload_quota (user_id, day, count)
values ('00000000-0000-0000-0000-000000000001', current_date, 1);
insert into public.whisper_deleted_tombstones (user_id, message_id)
values ('00000000-0000-0000-0000-000000000001', 'tomb-1');
insert into public.whisper_discover_quota (user_id, page_count)
values ('00000000-0000-0000-0000-000000000001', 1);
insert into public.whisper_username_check_quota (user_id, check_count)
values ('00000000-0000-0000-0000-000000000001', 1);
insert into public.whisper_image_ownership (user_id, delete_url)
values ('00000000-0000-0000-0000-000000000001', 'https://img.test/delete/u1');
insert into public.whisper_prekeys (account, kid, kind, public_key)
values ('00000000-0000-0000-0000-000000000001', 'kid1', 'SPK', 'pk1');

-- ── Seed groups ─────────────────────────────────────────────────────────────
insert into public.whisper_groups (id, name, epoch, created_by) values
  ('10000000-0000-0000-0000-000000000001', 'owned-by-u1', 0, '00000000-0000-0000-0000-000000000001'),
  ('10000000-0000-0000-0000-000000000002', 'survives',   0, '00000000-0000-0000-0000-000000000002'),
  ('10000000-0000-0000-0000-000000000003', 'invitee',    0, '00000000-0000-0000-0000-000000000002'),
  ('10000000-0000-0000-0000-000000000004', 'epoch',      0, '00000000-0000-0000-0000-000000000002');

-- Bootstrap admins (creator exemption) then invited members.
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000001', false);
insert into public.whisper_group_members (group_id, user_id, role)
values ('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'admin');

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000002', false);
insert into public.whisper_group_members (group_id, user_id, role) values
  ('10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002', 'admin'),
  ('10000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000002', 'admin'),
  ('10000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000002', 'admin');

insert into public.whisper_group_invites (group_id, user_id, invited_by, client_version_code) values
  ('10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', 18),
  ('10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000002', 18),
  ('10000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000002', 18);

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000001', false);
insert into public.whisper_group_members (group_id, user_id, role)
values ('10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'member');

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000003', false);
insert into public.whisper_group_members (group_id, user_id, role)
values ('10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000003', 'member');

-- Signed-log seed: create, invite, join (join bumps epoch to 1).
insert into public.whisper_group_events (group_id, seq, epoch, type, actor, payload, admin_sig, client_version_code) values
  ('10000000-0000-0000-0000-000000000001', 1, 0, 'create', '00000000-0000-0000-0000-000000000001', '{}', 'sig', 18);

insert into public.whisper_group_events (group_id, seq, epoch, type, actor, payload, admin_sig, client_version_code) values
  ('10000000-0000-0000-0000-000000000002', 1, 0, 'create', '00000000-0000-0000-0000-000000000002', '{}', 'sig', 18),
  ('10000000-0000-0000-0000-000000000002', 2, 0, 'invite', '00000000-0000-0000-0000-000000000002', '"00000000-0000-0000-0000-000000000001"', 'sig', 18),
  ('10000000-0000-0000-0000-000000000002', 3, 1, 'join',   '00000000-0000-0000-0000-000000000001', '""', 'sig', 18);

insert into public.whisper_group_events (group_id, seq, epoch, type, actor, payload, admin_sig, client_version_code) values
  ('10000000-0000-0000-0000-000000000003', 1, 0, 'create', '00000000-0000-0000-0000-000000000002', '{}', 'sig', 18),
  ('10000000-0000-0000-0000-000000000004', 1, 0, 'create', '00000000-0000-0000-0000-000000000002', '{}', 'sig', 18);

-- Group messages (g2 epoch is now 1).
insert into public.whisper_group_messages (group_id, sender_id, client_id, content, content_iv, epoch, client_version_code) values
  ('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'm-owned', 'c', 'iv', 0, 18),
  ('10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'm-u1',    'c', 'iv', 1, 18),
  ('10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002', 'm-u2',    'c', 'iv', 1, 18);

-- Membership metadata authored by u1 (purge must remove it, log must stay).
insert into public.whisper_group_invites (group_id, user_id, invited_by, client_version_code)
values ('10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000005',
        '00000000-0000-0000-0000-000000000001', 18);
insert into public.whisper_group_join_requests (group_id, user_id)
values ('10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001');
insert into public.whisper_group_typing (group_id, user_id)
values ('10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001');
insert into public.whisper_group_send_quota (sender_id, day, count)
values ('00000000-0000-0000-0000-000000000001', current_date, 1)
on conflict (sender_id, day) do nothing;

-- ── Seed stories ────────────────────────────────────────────────────────────
insert into public.whisper_stories (id, poster_id, client_id, host_url, host_object_id, receipts_enabled, state) values
  ('30000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001',
   '40000000-0000-0000-0000-000000000001', 'https://img.test/a.jpg', 'obj-a', true, 'published'),
  ('30000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002',
   '40000000-0000-0000-0000-000000000002', 'https://img.test/b.jpg', 'obj-b', true, 'published'),
  ('30000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000002',
   '40000000-0000-0000-0000-000000000003', 'https://img.test/c.jpg', 'obj-c', true, 'published');

insert into public.whisper_story_recipients (story_id, recipient_id, sealed_descriptor) values
  ('30000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', 'd'),
  ('30000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'd'),
  ('30000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', 'd');

insert into public.whisper_story_views (story_id, viewer_id) values
  ('30000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002'),
  ('30000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001');

insert into public.whisper_story_reactions (story_id, user_id, emoji) values
  ('30000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', '👍'),
  ('30000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', '❤️');

insert into public.whisper_story_publish_quota (poster_id, day, successful_count)
values ('00000000-0000-0000-0000-000000000001', current_date, 2);

-- ════════════════════════════════════════════════════════════════════════════
-- Behaviour checks
-- ════════════════════════════════════════════════════════════════════════════

-- 1. Invitee may write only join/decline, never a privileged event type.
begin;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000004', true);
select set_config('request.jwt.claim.role', 'authenticated', true);
set local role authenticated;
do $$
declare accepted boolean := false;
begin
    begin
        insert into public.whisper_group_events (group_id, seq, epoch, type, actor, payload, admin_sig, client_version_code)
        values ('10000000-0000-0000-0000-000000000003', 2, 1, 'rename',
                '00000000-0000-0000-0000-000000000004', '"pwned"', 'sig', 18);
    exception when others then
        accepted := true; -- expected: RLS with-check rejects the write
    end;
    if not accepted then
        raise exception 'SMOKE FAIL: invitee wrote a privileged (rename) event';
    end if;
end $$;
do $$
begin
    insert into public.whisper_group_events (group_id, seq, epoch, type, actor, payload, admin_sig, client_version_code)
    values ('10000000-0000-0000-0000-000000000003', 2, 1, 'join',
            '00000000-0000-0000-0000-000000000004', '""', 'sig', 18);
end $$;
-- An uninvited outsider cannot append any event.
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000005', true);
do $$
declare accepted boolean := false;
begin
    begin
        insert into public.whisper_group_events (group_id, seq, epoch, type, actor, payload, admin_sig, client_version_code)
        values ('10000000-0000-0000-0000-000000000003', 3, 1, 'join',
                '00000000-0000-0000-0000-000000000005', '""', 'sig', 18);
    exception when others then
        accepted := true;
    end;
    if not accepted then
        raise exception 'SMOKE FAIL: uninvited outsider appended a group event';
    end if;
end $$;
rollback;

-- 2. Epoch jump larger than +1 is rejected; +1 is accepted; stale is rejected.
do $$
declare rejected boolean;
begin
    rejected := false;
    begin
        insert into public.whisper_group_events (group_id, seq, epoch, type, actor, payload, admin_sig, client_version_code)
        values ('10000000-0000-0000-0000-000000000004', 2, 2, 'rename',
                '00000000-0000-0000-0000-000000000002', '"x"', 'sig', 18);
    exception when others then rejected := true;
    end;
    if not rejected then raise exception 'SMOKE FAIL: epoch jump +2 was accepted'; end if;

    insert into public.whisper_group_events (group_id, seq, epoch, type, actor, payload, admin_sig, client_version_code)
    values ('10000000-0000-0000-0000-000000000004', 2, 1, 'rename',
            '00000000-0000-0000-0000-000000000002', '"ok"', 'sig', 18);

    rejected := false;
    begin
        insert into public.whisper_group_events (group_id, seq, epoch, type, actor, payload, admin_sig, client_version_code)
        values ('10000000-0000-0000-0000-000000000004', 3, 0, 'rename',
                '00000000-0000-0000-0000-000000000002', '"stale"', 'sig', 18);
    exception when others then rejected := true;
    end;
    if not rejected then raise exception 'SMOKE FAIL: stale epoch was accepted'; end if;

    if (select epoch from public.whisper_groups where id = '10000000-0000-0000-0000-000000000004') <> 1 then
        raise exception 'SMOKE FAIL: group epoch did not ratchet to 1';
    end if;
end $$;

-- 3. Receipts/reactions must reference a real message in the same group.
begin;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000003', true);
select set_config('request.jwt.claim.role', 'authenticated', true);
set local role authenticated;
do $$
declare rejected boolean := false;
begin
    begin
        insert into public.whisper_group_receipts (group_id, message_id, user_id)
        values ('10000000-0000-0000-0000-000000000002', 'invented', '00000000-0000-0000-0000-000000000003');
    exception when others then rejected := true;
    end;
    if not rejected then raise exception 'SMOKE FAIL: receipt for an invented message was accepted'; end if;
end $$;
do $$
declare rejected boolean := false;
begin
    begin
        insert into public.whisper_group_reactions (group_id, message_id, user_id, emoji)
        values ('10000000-0000-0000-0000-000000000002', 'invented', '00000000-0000-0000-0000-000000000003', '👍');
    exception when others then rejected := true;
    end;
    if not rejected then raise exception 'SMOKE FAIL: reaction for an invented message was accepted'; end if;
end $$;
insert into public.whisper_group_receipts (group_id, message_id, user_id)
values ('10000000-0000-0000-0000-000000000002', 'm-u2', '00000000-0000-0000-0000-000000000003');
insert into public.whisper_group_reactions (group_id, message_id, user_id, emoji)
values ('10000000-0000-0000-0000-000000000002', 'm-u2', '00000000-0000-0000-0000-000000000003', '👍');
rollback;

-- 4. Outsiders cannot inject typing rows; members can.
begin;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000005', true);
select set_config('request.jwt.claim.role', 'authenticated', true);
set local role authenticated;
do $$
declare rejected boolean := false;
begin
    begin
        insert into public.whisper_group_typing (group_id, user_id)
        values ('10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000005');
    exception when others then rejected := true;
    end;
    if not rejected then raise exception 'SMOKE FAIL: outsider injected a typing row'; end if;
end $$;
rollback;

begin;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000003', true);
select set_config('request.jwt.claim.role', 'authenticated', true);
set local role authenticated;
insert into public.whisper_group_typing (group_id, user_id)
values ('10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000003');
rollback;

-- 5. Story reaction removal is keyed by identity, not by the client's emoji.
begin;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000001', true);
select set_config('request.jwt.claim.role', 'authenticated', true);
set local role authenticated;
select public.whisper_story_react('30000000-0000-0000-0000-000000000003', '👍', true, 18);
select public.whisper_story_react('30000000-0000-0000-0000-000000000003', '❤️', true, 18);
do $$
begin
    if (select emoji from public.whisper_story_reactions
         where story_id = '30000000-0000-0000-0000-000000000003'
           and user_id = '00000000-0000-0000-0000-000000000001') <> '❤️' then
        raise exception 'SMOKE FAIL: reaction upsert did not replace the emoji';
    end if;
end $$;
-- "off" is sent with a DIFFERENT emoji than the stored one (another device changed it).
select public.whisper_story_react('30000000-0000-0000-0000-000000000003', '😢', false, 18);
do $$
begin
    if exists (select 1 from public.whisper_story_reactions
                where story_id = '30000000-0000-0000-0000-000000000003'
                  and user_id = '00000000-0000-0000-0000-000000000001') then
        raise exception 'SMOKE FAIL: reaction removal left the row behind (emoji-keyed delete)';
    end if;
end $$;
rollback;

-- 6. Account purge: owned groups die, surviving groups keep their signed log,
--    and every FK-less row authored by the deleted account is removed.
do $$
declare
    v_events_before int;
    v_events_after int;
    v_n int;
begin
    select count(*) into v_events_before
      from public.whisper_group_events where group_id = '10000000-0000-0000-0000-000000000002';

    perform public.whisper_purge_account_data('00000000-0000-0000-0000-000000000001');

    -- Owned group removed (cascade).
    if exists (select 1 from public.whisper_groups where id = '10000000-0000-0000-0000-000000000001') then
        raise exception 'SMOKE FAIL: purge kept the owned group';
    end if;
    -- Surviving group + append-only log retained.
    if not exists (select 1 from public.whisper_groups where id = '10000000-0000-0000-0000-000000000002') then
        raise exception 'SMOKE FAIL: purge deleted a group owned by somebody else';
    end if;
    select count(*) into v_events_after
      from public.whisper_group_events where group_id = '10000000-0000-0000-0000-000000000002';
    if v_events_after <> v_events_before then
        raise exception 'SMOKE FAIL: purge removed signed events from a surviving group (% -> %)',
            v_events_before, v_events_after;
    end if;
    if not exists (
        select 1 from public.whisper_group_events
         where group_id = '10000000-0000-0000-0000-000000000002'
           and actor = '00000000-0000-0000-0000-000000000001')
    then
        raise exception 'SMOKE FAIL: purge removed the departed member''s historical event';
    end if;

    -- Membership + authored content gone from the surviving group.
    select count(*) into v_n from public.whisper_group_members
     where group_id = '10000000-0000-0000-0000-000000000002'
       and (user_id = '00000000-0000-0000-0000-000000000001'
            or invited_by = '00000000-0000-0000-0000-000000000001');
    if v_n <> 0 then raise exception 'SMOKE FAIL: purge kept surviving-group membership/invites'; end if;

    select count(*) into v_n from public.whisper_group_messages
     where sender_id = '00000000-0000-0000-0000-000000000001';
    if v_n <> 0 then raise exception 'SMOKE FAIL: purge kept authored group messages'; end if;
    if not exists (select 1 from public.whisper_group_messages
                    where group_id = '10000000-0000-0000-0000-000000000002' and client_id = 'm-u2') then
        raise exception 'SMOKE FAIL: purge removed another member''s group message';
    end if;

    select count(*) into v_n from public.whisper_group_invites
     where user_id = '00000000-0000-0000-0000-000000000001'
        or invited_by = '00000000-0000-0000-0000-000000000001';
    if v_n <> 0 then raise exception 'SMOKE FAIL: purge kept group invites'; end if;

    select count(*) into v_n from public.whisper_group_join_requests
     where user_id = '00000000-0000-0000-0000-000000000001';
    if v_n <> 0 then raise exception 'SMOKE FAIL: purge kept join requests'; end if;

    select count(*) into v_n from public.whisper_group_typing
     where user_id = '00000000-0000-0000-0000-000000000001';
    if v_n <> 0 then raise exception 'SMOKE FAIL: purge kept typing rows'; end if;

    select count(*) into v_n from public.whisper_group_send_quota
     where sender_id = '00000000-0000-0000-0000-000000000001';
    if v_n <> 0 then raise exception 'SMOKE FAIL: purge kept group send quota'; end if;

    select count(*) into v_n from public.whisper_group_receipts
     where user_id = '00000000-0000-0000-0000-000000000001';
    if v_n <> 0 then raise exception 'SMOKE FAIL: purge kept group receipts'; end if;

    select count(*) into v_n from public.whisper_group_reactions
     where user_id = '00000000-0000-0000-0000-000000000001';
    if v_n <> 0 then raise exception 'SMOKE FAIL: purge kept group reactions'; end if;

    -- The public signer archive is deliberately RETAINED on account
    -- deletion: surviving groups keep signed events the departed account
    -- authored, and peers verify them against this set (signers-only
    -- bundle). It is public-key material only, never content.
    select count(*) into v_n from public.whisper_group_signer_history
     where user_id = '00000000-0000-0000-0000-000000000001';
    if v_n = 0 then
        raise exception 'SMOKE FAIL: purge deleted the public signer archive';
    end if;

    -- 1:1 / profile / quota / story coverage.
    select count(*) into v_n from public.messages
     where sender_id = '00000000-0000-0000-0000-000000000001'
        or receiver_id = '00000000-0000-0000-0000-000000000001';
    if v_n <> 0 then raise exception 'SMOKE FAIL: purge kept 1:1 messages'; end if;

    select count(*) into v_n from public.message_reactions
     where user_id = '00000000-0000-0000-0000-000000000001';
    if v_n <> 0 then raise exception 'SMOKE FAIL: purge kept 1:1 reactions'; end if;

    select count(*) into v_n from public.friends
     where user_a = '00000000-0000-0000-0000-000000000001'
        or user_b = '00000000-0000-0000-0000-000000000001';
    if v_n <> 0 then raise exception 'SMOKE FAIL: purge kept friendships'; end if;

    select count(*) into v_n from public.whisper_blocks
     where blocker_id = '00000000-0000-0000-0000-000000000001'
        or blocked_id = '00000000-0000-0000-0000-000000000001';
    if v_n <> 0 then raise exception 'SMOKE FAIL: purge kept blocks'; end if;

    if exists (select 1 from public.profiles where id = '00000000-0000-0000-0000-000000000001') then
        raise exception 'SMOKE FAIL: purge kept the profile';
    end if;

    select count(*) into v_n from public.whisper_stories
     where poster_id = '00000000-0000-0000-0000-000000000001';
    if v_n <> 0 then raise exception 'SMOKE FAIL: purge kept authored stories'; end if;
    select count(*) into v_n from public.whisper_story_recipients
     where recipient_id = '00000000-0000-0000-0000-000000000001';
    if v_n <> 0 then raise exception 'SMOKE FAIL: purge kept story recipient rows'; end if;
    select count(*) into v_n from public.whisper_story_views
     where viewer_id = '00000000-0000-0000-0000-000000000001';
    if v_n <> 0 then raise exception 'SMOKE FAIL: purge kept story views'; end if;
    select count(*) into v_n from public.whisper_story_reactions
     where user_id = '00000000-0000-0000-0000-000000000001';
    if v_n <> 0 then raise exception 'SMOKE FAIL: purge kept story reactions'; end if;
    select count(*) into v_n from public.whisper_story_publish_quota
     where poster_id = '00000000-0000-0000-0000-000000000001';
    if v_n <> 0 then raise exception 'SMOKE FAIL: purge kept story publish quota'; end if;

    select count(*) into v_n from public.whisper_upload_quota
     where user_id = '00000000-0000-0000-0000-000000000001';
    if v_n <> 0 then raise exception 'SMOKE FAIL: purge kept upload quota'; end if;
    select count(*) into v_n from public.whisper_deleted_tombstones
     where user_id = '00000000-0000-0000-0000-000000000001';
    if v_n <> 0 then raise exception 'SMOKE FAIL: purge kept tombstones'; end if;
    select count(*) into v_n from public.whisper_discover_quota
     where user_id = '00000000-0000-0000-0000-000000000001';
    if v_n <> 0 then raise exception 'SMOKE FAIL: purge kept discover quota'; end if;
    select count(*) into v_n from public.whisper_username_check_quota
     where user_id = '00000000-0000-0000-0000-000000000001';
    if v_n <> 0 then raise exception 'SMOKE FAIL: purge kept username quota'; end if;
    select count(*) into v_n from public.whisper_image_ownership
     where user_id = '00000000-0000-0000-0000-000000000001';
    if v_n <> 0 then raise exception 'SMOKE FAIL: purge kept image ownership'; end if;
    select count(*) into v_n from public.whisper_prekeys
     where account = '00000000-0000-0000-0000-000000000001';
    if v_n <> 0 then raise exception 'SMOKE FAIL: purge kept prekeys'; end if;
end $$;

-- 7. The newest-first story envelope index exists.
do $$
begin
    if not exists (
        select 1 from pg_indexes
         where schemaname = 'public'
           and indexname = 'whisper_story_recipients_recipient_created_idx')
    then
        raise exception 'SMOKE FAIL: newest-first story recipient index is missing';
    end if;
end $$;

-- 8. Account deletion must NOT cascade the public signer archive: the
--    surviving group keeps signed events the departed account authored, and
--    fresh peers verify them against the archived cert (signers-only bundle).
do $$
declare
    v_before int;
    v_after int;
    v_hist int;
begin
    insert into public.whisper_group_events
        (group_id, seq, epoch, type, actor, payload, admin_sig, client_version_code, signer_cert)
    values
        ('10000000-0000-0000-0000-000000000002', 4, 1, 'rename',
         '00000000-0000-0000-0000-000000000003', '"departed"', 'sig', 18, 'CERT-U3');

    select count(*) into v_hist from public.whisper_group_signer_history
     where user_id = '00000000-0000-0000-0000-000000000003';
    if v_hist < 1 then
        raise exception 'SMOKE FAIL: signer history was not archived before the delete test';
    end if;
    select count(*) into v_before from public.whisper_group_events
     where group_id = '10000000-0000-0000-0000-000000000002';

    -- Same cascade GoTrue's auth.users delete performs.
    delete from auth.users where id = '00000000-0000-0000-0000-000000000003';

    select count(*) into v_after from public.whisper_group_events
     where group_id = '10000000-0000-0000-0000-000000000002';
    if v_after <> v_before then
        raise exception 'SMOKE FAIL: auth-user delete removed signed group events (% -> %)', v_before, v_after;
    end if;
    select count(*) into v_hist from public.whisper_group_signer_history
     where user_id = '00000000-0000-0000-0000-000000000003';
    if v_hist = 0 then
        raise exception 'SMOKE FAIL: signer archive cascaded with the auth user';
    end if;
    if not exists (
        select 1 from public.whisper_group_signer_history
         where user_id = '00000000-0000-0000-0000-000000000003'
           and signer_cert = 'CERT-U3')
    then
        raise exception 'SMOKE FAIL: the departed account''s signer cert is missing';
    end if;
end $$;

-- 9. The signer archive has no FK to auth.users, so no future GoTrue cascade
--    can silently delete the material surviving groups verify against.
do $$
begin
    if exists (
        select 1
          from pg_constraint c
          join pg_class t on t.oid = c.conrelid
          join pg_namespace n on n.oid = t.relnamespace
         where n.nspname = 'public'
           and t.relname = 'whisper_group_signer_history'
           and c.contype = 'f')
    then
        raise exception 'SMOKE FAIL: whisper_group_signer_history still has a foreign key';
    end if;
end $$;

\echo 'SMOKE NOTE: deleted-account signer archive is retained — surviving groups stay verifiable after account deletion.'
\echo 'SMOKE PASS: all Whisper SQL behaviour checks passed'

-- ============================================================================
-- Minimal Supabase shim for running supabase/migrations/main-whisper-sql.sql
-- against a plain postgres:16 container.  This is TEST-ONLY infrastructure:
-- it emulates the hosted objects the migration expects to already exist
-- (auth/storage schemas, Supabase roles, pgcrypto in the extensions schema,
-- the base app tables, and the realtime publication).
--
-- It is deliberately NOT part of the production migration set.
--
-- SAFETY: refuses to run unless `whisper.shim=1`, so an accidental paste into a
-- production SQL editor cannot create test roles or shadow base tables.
-- ============================================================================

do $$
begin
    if coalesce(current_setting('whisper.shim', true), '') <> '1' then
        raise exception 'Refusing to run the Supabase shim: set whisper.shim=1 and target a DISPOSABLE database';
    end if;
end $$;

-- ── Extensions (hosted Supabase installs pgcrypto in the extensions schema) ──
create schema if not exists extensions;
create extension if not exists pgcrypto with schema extensions;

-- ── Supabase roles ──────────────────────────────────────────────────────────
do $$
begin
    if not exists (select 1 from pg_roles where rolname = 'anon') then
        create role anon nologin noinherit;
    end if;
    if not exists (select 1 from pg_roles where rolname = 'authenticated') then
        create role authenticated nologin noinherit;
    end if;
    if not exists (select 1 from pg_roles where rolname = 'service_role') then
        create role service_role nologin noinherit bypassrls;
    end if;
    if not exists (select 1 from pg_roles where rolname = 'supabase_auth_admin') then
        create role supabase_auth_admin nologin noinherit;
    end if;
end $$;

grant usage on schema public to anon, authenticated, service_role;

-- ── auth schema ─────────────────────────────────────────────────────────────
create schema if not exists auth;

create table if not exists auth.users (
    id uuid primary key default gen_random_uuid(),
    email text unique,
    raw_user_meta_data jsonb,
    created_at timestamptz not null default now()
);

-- Supabase exposes auth.uid() as the JWT sub claim.  Both the legacy
-- per-claim GUC and the modern request.jwt.claims JSON are honoured.
create or replace function auth.uid()
returns uuid
language sql
stable
as $$
    select coalesce(
        nullif(current_setting('request.jwt.claim.sub', true), '')::uuid,
        nullif(current_setting('request.jwt.claims', true)::jsonb ->> 'sub', '')::uuid
    )
$$;

create or replace function auth.role()
returns text
language sql
stable
as $$
    select coalesce(
        nullif(current_setting('request.jwt.claim.role', true), ''),
        nullif(current_setting('request.jwt.claims', true)::jsonb ->> 'role', '')
    )
$$;

grant usage on schema auth to anon, authenticated, service_role;

-- ── storage schema ──────────────────────────────────────────────────────────
create schema if not exists storage;

create table if not exists storage.objects (
    id uuid primary key default gen_random_uuid(),
    bucket_id text not null,
    name text not null,
    owner uuid,
    created_at timestamptz not null default now()
);
alter table storage.objects enable row level security;

-- Supabase helper: every path segment except the final object name.
create or replace function storage.foldername(name text)
returns text[]
language plpgsql
immutable
as $$
declare
    parts text[];
begin
    parts := string_to_array(name, '/');
    if parts is null or array_length(parts, 1) is null then
        return array[]::text[];
    end if;
    if array_length(parts, 1) = 1 then
        return array[]::text[];
    end if;
    return parts[1:array_length(parts, 1) - 1];
end;
$$;

grant usage on schema storage to anon, authenticated, service_role;

-- ── public base tables (authored outside this repo in hosted Supabase) ──────
create table if not exists public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    username text unique,
    display_name text,
    avatar_url text,
    bio text,
    public_key text,
    is_private boolean not null default false,
    hide_from_discover boolean not null default true,
    updated_at timestamptz not null default now(),
    last_seen_at timestamptz,
    identity_binding jsonb,
    rotation_cert text,
    rotation_counter integer
);

create table if not exists public.messages (
    id uuid primary key default gen_random_uuid(),
    sender_id uuid not null references auth.users(id) on delete cascade,
    receiver_id uuid not null references auth.users(id) on delete cascade,
    content text not null,
    content_iv text not null default '',
    is_read boolean not null default false,
    created_at timestamptz not null default now()
);

create table if not exists public.message_reactions (
    id uuid primary key default gen_random_uuid(),
    message_id uuid not null references public.messages(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    emoji text not null default '',
    created_at timestamptz not null default now(),
    unique (message_id, user_id)
);

-- Some clients also mirror a reaction row per message/emoji; widen PK shape.
create table if not exists public.friends (
    user_a uuid not null references auth.users(id) on delete cascade,
    user_b uuid not null references auth.users(id) on delete cascade,
    status text not null default 'pending',
    created_at timestamptz not null default now(),
    primary key (user_a, user_b)
);

-- ── realtime publication ────────────────────────────────────────────────────
do $$
begin
    if not exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
        create publication supabase_realtime;
    end if;
end $$;

grant usage on schema public to anon, authenticated, service_role;

-- Hosted Supabase grants table/sequence/function privileges to the API roles by
-- default; RLS is the actual boundary. Mirror that so smoke tests exercise the
-- policies rather than failing on missing grants.
alter default privileges in schema public grant all on tables to anon, authenticated, service_role;
alter default privileges in schema public grant all on sequences to anon, authenticated, service_role;
alter default privileges in schema public grant execute on functions to anon, authenticated, service_role;

grant all on all tables in schema public to anon, authenticated, service_role;
grant all on all sequences in schema public to anon, authenticated, service_role;
grant execute on all functions in schema public to anon, authenticated, service_role;

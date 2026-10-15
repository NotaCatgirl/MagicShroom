-- Run only against a disposable local PostgreSQL database before measurement.
create schema if not exists auth;
create table if not exists auth.users (id uuid primary key);
do $$ begin
  if not exists (select from pg_roles where rolname = 'anon') then create role anon; end if;
  if not exists (select from pg_roles where rolname = 'authenticated') then create role authenticated; end if;
end $$;

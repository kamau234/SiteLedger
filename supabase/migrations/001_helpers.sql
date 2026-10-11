-- 001_helpers.sql
-- Shared helpers for the SiteLedger schema.
-- Every business table carries server-authoritative timestamps: a BEFORE INSERT OR
-- UPDATE trigger overwrites updated_at with the database clock, so clients can never
-- send a stale or forged revision value (the app stores only server revisions for
-- its conflict checks, never the device clock).

create extension if not exists pgcrypto with schema extensions;

create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

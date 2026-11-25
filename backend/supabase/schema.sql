-- MonitorDad Relay • Schema para Supabase (PostgreSQL Free Tier)
-- Execute este script no SQL Editor do seu projeto Supabase.
-- Seguro para re-execução (idempotente): preserva dados e pareamentos existentes.

CREATE TABLE IF NOT EXISTS public.relay_pairings (
  code TEXT PRIMARY KEY,
  device_id TEXT NOT NULL,
  device_name TEXT NOT NULL,
  claim_token TEXT NOT NULL,
  created_at_millis BIGINT NOT NULL,
  expires_at_millis BIGINT NOT NULL,
  claimed BOOLEAN NOT NULL DEFAULT FALSE,
  parent_id TEXT,
  shared_secret TEXT,
  claimed_at_millis BIGINT
);

CREATE INDEX IF NOT EXISTS idx_relay_pairings_expires
  ON public.relay_pairings (expires_at_millis);

CREATE TABLE IF NOT EXISTS public.relay_devices (
  device_id TEXT PRIMARY KEY,
  parent_id TEXT NOT NULL,
  device_name TEXT NOT NULL,
  shared_secret TEXT NOT NULL,
  paired_at_millis BIGINT NOT NULL,
  last_heartbeat_parent BIGINT,
  last_heartbeat_kids BIGINT,
  commands JSONB NOT NULL DEFAULT '[]'::jsonb,
  acks JSONB NOT NULL DEFAULT '[]'::jsonb,
  rules_envelope JSONB,
  telemetry JSONB,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Segurança: habilitar Row Level Security sem políticas públicas.
ALTER TABLE public.relay_pairings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.relay_devices ENABLE ROW LEVEL SECURITY;

-- Garantir que anon/authenticated/PUBLIC não tenham acesso direto às tabelas.
REVOKE ALL ON TABLE public.relay_pairings, public.relay_devices FROM PUBLIC, anon, authenticated;

-- Garantir permissões explícitas apenas para o papel service_role (usado exclusivamente no backend Render).
GRANT USAGE ON SCHEMA public TO service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.relay_pairings, public.relay_devices TO service_role;

-- Migração segura 001: corrigir permissões GRANT para service_role preservando dados existentes.
-- Execute no SQL Editor do Supabase caso as tabelas já tenham sido criadas anteriormente.

ALTER TABLE public.relay_pairings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.relay_devices ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE public.relay_pairings, public.relay_devices FROM PUBLIC, anon, authenticated;

GRANT USAGE ON SCHEMA public TO service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.relay_pairings, public.relay_devices TO service_role;

-- Callback do Flyway, roda depois de cada migrate.
-- A tabela de histórico do Flyway também fica no schema public, exposto pela Data API
-- do Supabase; ela precisa de RLS como as demais (a V1 roda antes dela existir por completo).
alter table if exists flyway_schema_history enable row level security;

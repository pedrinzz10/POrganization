-- Preferências por usuário. user_id é o "sub" do JWT do Supabase.
create table user_settings (
    user_id    uuid primary key,
    email      text,
    timezone   text        not null default 'America/Sao_Paulo',
    created_at timestamptz not null default now()
);

-- RLS sem policies: a Data API do Supabase (anon/publishable key) não lê nem grava.
-- O backend conecta como postgres (dono das tabelas) e não é afetado.
alter table user_settings enable row level security;

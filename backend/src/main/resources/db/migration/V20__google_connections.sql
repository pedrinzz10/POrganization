-- Conexão do usuário com o Google Calendar (OAuth). Os tokens ficam cifrados com AES-GCM
-- (chave em GOOGLE_TOKEN_KEY, fora do banco): um vazamento do banco não entrega o acesso à agenda.
create table google_connections (
    user_id              uuid primary key,
    google_email         text,
    refresh_token_enc    text        not null,
    access_token_enc     text,
    access_expires_at    timestamptz,
    -- agenda onde os compromissos são publicados
    calendar_id          text        not null default 'primary',
    scope                text,
    created_at           timestamptz not null default now(),
    updated_at           timestamptz not null default now()
);

alter table google_connections enable row level security;

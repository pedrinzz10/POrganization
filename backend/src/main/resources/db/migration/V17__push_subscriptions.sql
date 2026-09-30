-- Assinaturas de Web Push: uma por navegador/dispositivo em que o usuário ativou as notificações.
create table push_subscriptions (
    id         uuid primary key default gen_random_uuid(),
    user_id    uuid        not null,
    -- URL do push service do navegador (FCM, Mozilla, Apple); identifica a assinatura
    endpoint   text        not null unique,
    -- chave pública do navegador (P-256) e segredo de autenticação, em base64url
    p256dh     text        not null,
    auth       text        not null,
    user_agent text,
    created_at timestamptz not null default now()
);

create index push_subscriptions_user_idx on push_subscriptions (user_id);

alter table push_subscriptions enable row level security;

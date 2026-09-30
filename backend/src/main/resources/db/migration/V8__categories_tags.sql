-- Categorias de renda e gasto; nome único por usuário dentro do mesmo tipo.
create table categories (
    id         uuid primary key default gen_random_uuid(),
    user_id    uuid        not null,
    name       text        not null check (length(btrim(name)) > 0),
    kind       text        not null check (kind in ('INCOME', 'EXPENSE')),
    color      text check (color is null or color ~ '^#[0-9A-Fa-f]{6}$'),
    icon       text,
    created_at timestamptz not null default now()
);

create unique index categories_user_kind_name_uidx on categories (user_id, kind, lower(name));

-- Tags livres das transações ("viagem", "presente"); separadas das tags de estudo.
create table finance_tags (
    id         uuid primary key default gen_random_uuid(),
    user_id    uuid        not null,
    name       text        not null check (length(btrim(name)) > 0),
    created_at timestamptz not null default now()
);

create unique index finance_tags_user_name_uidx on finance_tags (user_id, lower(name));

-- Marca que o usuário já recebeu as categorias padrão (acontece uma única vez)
create table finance_setup (
    user_id              uuid primary key,
    categories_seeded_at timestamptz not null default now()
);

alter table categories enable row level security;
alter table finance_tags enable row level security;
alter table finance_setup enable row level security;

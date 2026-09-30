-- Metas de economia e seus aportes. A conta é opcional (onde o dinheiro está guardado).
create table savings_goals (
    id            uuid primary key default gen_random_uuid(),
    user_id       uuid           not null,
    name          text           not null check (length(btrim(name)) between 1 and 80),
    target_amount numeric(14, 2) not null check (target_amount > 0),
    -- prazo; sem prazo não há aporte mensal sugerido
    target_date   date,
    account_id    uuid references accounts (id) on delete set null,
    archived      boolean        not null default false,
    created_at    timestamptz    not null default now(),
    updated_at    timestamptz    not null default now()
);

create index savings_goals_user_idx on savings_goals (user_id);

create table goal_contributions (
    id         uuid primary key default gen_random_uuid(),
    user_id    uuid           not null,
    goal_id    uuid           not null references savings_goals (id) on delete cascade,
    amount     numeric(14, 2) not null check (amount > 0),
    date       date           not null,
    note       text check (note is null or char_length(note) <= 200),
    created_at timestamptz    not null default now()
);

create index goal_contributions_goal_idx on goal_contributions (goal_id, date);

alter table savings_goals enable row level security;
alter table goal_contributions enable row level security;

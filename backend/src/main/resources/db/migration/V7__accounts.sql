-- Contas do usuário (corrente, poupança, dinheiro, investimento). Dinheiro sempre NUMERIC(14,2).
create table accounts (
    id              uuid primary key default gen_random_uuid(),
    user_id         uuid           not null,
    name            text           not null check (length(btrim(name)) > 0),
    type            text           not null check (type in ('CHECKING', 'SAVINGS', 'CASH', 'INVESTMENT')),
    initial_balance numeric(14, 2) not null default 0,
    archived        boolean        not null default false,
    created_at      timestamptz    not null default now(),
    updated_at      timestamptz    not null default now()
);

create index accounts_user_idx on accounts (user_id);

alter table accounts enable row level security;

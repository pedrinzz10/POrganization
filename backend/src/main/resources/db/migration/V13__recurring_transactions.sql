-- Gastos e rendas fixos: um modelo que gera a transação de cada mês (paid = false).
-- Vai numa conta OU num cartão (só gasto); o dia 31 em mês curto cai no último dia.
create table recurring_transactions (
    id           uuid primary key default gen_random_uuid(),
    user_id      uuid           not null,
    type         text           not null check (type in ('INCOME', 'EXPENSE')),
    amount       numeric(14, 2) not null check (amount > 0),
    description  text check (description is null or char_length(description) <= 200),
    account_id   uuid references accounts (id) on delete restrict,
    card_id      uuid references credit_cards (id) on delete restrict,
    category_id  uuid           not null references categories (id) on delete restrict,
    day_of_month integer        not null check (day_of_month between 1 and 31),
    -- primeiro dia do mês inicial e do final (inclusivos); sem fim = para sempre
    start_month  date           not null,
    end_month    date,
    created_at   timestamptz    not null default now(),
    updated_at   timestamptz    not null default now(),
    check ((account_id is null) <> (card_id is null)),
    check (card_id is null or type = 'EXPENSE'),
    check (end_month is null or end_month >= start_month)
);

create index recurring_transactions_user_idx on recurring_transactions (user_id);

-- Meses já gerados de cada modelo: gerar de novo não duplica, e excluir o lançamento gerado
-- não faz ele voltar. Editar o modelo só afeta meses que ainda não estão aqui.
create table recurring_generations (
    recurring_id uuid        not null references recurring_transactions (id) on delete cascade,
    month        date        not null,
    created_at   timestamptz not null default now(),
    primary key (recurring_id, month)
);

-- De qual modelo veio a transação (some se o modelo for excluído; o lançamento fica)
alter table transactions
    add column recurring_id uuid references recurring_transactions (id) on delete set null;

create index transactions_recurring_idx on transactions (recurring_id);

alter table recurring_transactions enable row level security;
alter table recurring_generations enable row level security;

-- Cartões de crédito e suas faturas. A fatura é nomeada pelo mês do vencimento.
create table credit_cards (
    id                 uuid primary key default gen_random_uuid(),
    user_id            uuid           not null,
    name               text           not null check (length(btrim(name)) > 0),
    credit_limit       numeric(14, 2) not null check (credit_limit >= 0),
    closing_day        integer        not null check (closing_day between 1 and 31),
    due_day            integer        not null check (due_day between 1 and 31),
    payment_account_id uuid           not null references accounts (id) on delete restrict,
    archived           boolean        not null default false,
    created_at         timestamptz    not null default now(),
    updated_at         timestamptz    not null default now()
);

create table card_statements (
    id              uuid primary key default gen_random_uuid(),
    user_id         uuid        not null,
    card_id         uuid        not null references credit_cards (id) on delete cascade,
    -- primeiro dia do mês de vencimento ("fatura de outubro")
    reference_month date        not null,
    closing_date    date        not null,
    due_date        date        not null,
    -- OPEN ou PAID; "fechada" é calculado na leitura (hoje depois do fechamento)
    status          text        not null default 'OPEN' check (status in ('OPEN', 'PAID')),
    paid_at         timestamptz,
    created_at      timestamptz not null default now(),
    unique (card_id, reference_month)
);

create index card_statements_user_due_idx on card_statements (user_id, due_date);

-- A FK de transactions.card_statement_id fica para cá, quando card_statements passa a existir (F03)
alter table transactions
    add constraint transactions_card_statement_fk foreign key (card_statement_id)
        references card_statements (id) on delete restrict;

-- Toda transação é de uma conta, de uma fatura de cartão, ou das duas (pagamento da fatura, F07)
alter table transactions
    add constraint transactions_account_or_statement check (account_id is not null or card_statement_id is not null);

alter table credit_cards enable row level security;
alter table card_statements enable row level security;

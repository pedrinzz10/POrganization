-- Lançamentos de renda e gasto. Conta e categoria não podem ser excluídas enquanto usadas
-- (on delete restrict): a API responde 409 e sugere arquivar.
create table transactions (
    id                 uuid primary key default gen_random_uuid(),
    user_id            uuid           not null,
    account_id         uuid references accounts (id) on delete restrict,
    type               text           not null check (type in ('INCOME', 'EXPENSE', 'TRANSFER')),
    amount             numeric(14, 2) not null check (amount > 0),
    date               date           not null,
    description        text,
    category_id        uuid references categories (id) on delete restrict,
    paid               boolean        not null default true,
    -- compra no cartão: a fatura (FK criada na V11, quando card_statements existir)
    card_statement_id  uuid,
    -- parcelamento (F06): mesma compra, parcela N de M
    purchase_id        uuid,
    installment_number integer check (installment_number is null or installment_number >= 1),
    installment_count  integer check (installment_count is null or installment_count >= 1),
    created_at         timestamptz    not null default now(),
    updated_at         timestamptz    not null default now()
);

create index transactions_user_date_idx on transactions (user_id, date);
create index transactions_account_idx on transactions (account_id);
create index transactions_category_idx on transactions (category_id);

create table transaction_tags (
    transaction_id uuid not null references transactions (id) on delete cascade,
    tag_id         uuid not null references finance_tags (id) on delete cascade,
    primary key (transaction_id, tag_id)
);

create index transaction_tags_tag_idx on transaction_tags (tag_id);

alter table transactions enable row level security;
alter table transaction_tags enable row level security;

-- Orçamento por categoria de gasto: de um mês específico ou recorrente (month null = todo mês).
-- O do mês, se existir, vale no lugar do recorrente.
create table budgets (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid           not null,
    category_id uuid           not null references categories (id) on delete cascade,
    -- primeiro dia do mês; null = recorrente
    month       date,
    amount      numeric(14, 2) not null check (amount > 0),
    created_at  timestamptz    not null default now(),
    updated_at  timestamptz    not null default now()
);

-- Um recorrente e no máximo um por mês para cada categoria
create unique index budgets_recurring_uidx on budgets (user_id, category_id) where month is null;
create unique index budgets_month_uidx on budgets (user_id, category_id, month) where month is not null;

alter table budgets enable row level security;

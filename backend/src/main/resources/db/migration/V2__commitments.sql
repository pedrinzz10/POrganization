-- Compromissos. Só title, date e user_id são obrigatórios (criação rápida).
-- recurrence_rule: regra de repetição (RecurrenceRule em JSON); nula = acontece uma vez.
create table commitments (
    id              uuid primary key default gen_random_uuid(),
    user_id         uuid        not null,
    title           text        not null check (length(btrim(title)) > 0),
    date            date        not null,
    start_time      time,
    end_time        time,
    all_day         boolean     not null default false,
    description     text,
    location        text,
    done            boolean     not null default false,
    recurrence_rule jsonb,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now()
);

create index commitments_user_date_idx on commitments (user_id, date);

alter table commitments enable row level security;

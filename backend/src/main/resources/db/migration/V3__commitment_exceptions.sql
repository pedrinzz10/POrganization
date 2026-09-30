-- Ajustes de uma única ocorrência de um compromisso recorrente, sem mexer na série:
-- concluir, cancelar, trocar o título ou o horário daquele dia.
create table commitment_exceptions (
    id              uuid primary key default gen_random_uuid(),
    user_id         uuid        not null,
    commitment_id   uuid        not null references commitments (id) on delete cascade,
    occurrence_date date        not null,
    done            boolean     not null default false,
    cancelled       boolean     not null default false,
    override_title  text check (override_title is null or length(btrim(override_title)) > 0),
    override_time   time,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    unique (commitment_id, occurrence_date)
);

create index commitment_exceptions_user_date_idx on commitment_exceptions (user_id, occurrence_date);

alter table commitment_exceptions enable row level security;

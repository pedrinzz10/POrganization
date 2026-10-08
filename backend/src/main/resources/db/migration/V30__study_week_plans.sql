-- Plano da semana de estudos (E15): semana com linha aqui usa as aulas de study_week_slots;
-- sem linha, a agenda mostra a previsão automática.
create table study_week_plans (
    user_id    uuid        not null,
    -- segunda-feira da semana
    week       date        not null check (extract(isodow from week) = 1),
    created_at timestamptz not null default now(),
    primary key (user_id, week)
);

-- Uma aula da matéria num dia do plano (no máximo uma por matéria por dia)
create table study_week_slots (
    id         uuid primary key default gen_random_uuid(),
    user_id    uuid        not null,
    subject_id uuid        not null references subjects (id) on delete cascade,
    day        date        not null,
    created_at timestamptz not null default now(),
    unique (subject_id, day)
);

create index study_week_slots_user_day_idx on study_week_slots (user_id, day);

alter table study_week_plans enable row level security;
alter table study_week_slots enable row level security;

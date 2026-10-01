-- Dias em que cada matéria pode ter aula (E13): null = qualquer dia; senão ["MON","WED",...]
alter table subjects add column study_days jsonb
    check (study_days is null or (jsonb_typeof(study_days) = 'array' and jsonb_array_length(study_days) between 1 and 7));

-- Aula fixada num dia pelo usuário (arrastar na agenda): a distribuição automática respeita e
-- espalha só o que falta da meta da semana
create table study_lesson_pins (
    id         uuid primary key default gen_random_uuid(),
    user_id    uuid        not null,
    subject_id uuid        not null references subjects (id) on delete cascade,
    day        date        not null,
    created_at timestamptz not null default now(),
    unique (subject_id, day)
);

create index study_lesson_pins_user_day_idx on study_lesson_pins (user_id, day);

alter table study_lesson_pins enable row level security;

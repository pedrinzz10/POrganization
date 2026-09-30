-- Aulas estudadas (o que foi visto) e sessões de estudo (o timer).
create table lessons (
    id               uuid primary key default gen_random_uuid(),
    user_id          uuid        not null,
    subject_id       uuid        not null references subjects (id) on delete cascade,
    title            text        not null check (length(btrim(title)) > 0),
    notes            text,
    studied_at       timestamptz not null,
    duration_minutes integer     not null check (duration_minutes >= 0),
    created_at       timestamptz not null default now()
);

create index lessons_user_subject_idx on lessons (user_id, subject_id, studied_at);

create table study_sessions (
    id             uuid primary key default gen_random_uuid(),
    user_id        uuid        not null,
    subject_id     uuid        not null references subjects (id) on delete cascade,
    -- REVIEW: a aula revisada; LESSON: a aula criada ao terminar
    lesson_id      uuid references lessons (id) on delete set null,
    type           text        not null check (type in ('LESSON', 'REVIEW')),
    status         text        not null check (status in ('RUNNING', 'PAUSED', 'FINISHED', 'ABANDONED')),
    started_at     timestamptz not null,
    ended_at       timestamptz,
    -- pausas já encerradas; a pausa em curso começa em paused_at
    paused_seconds integer     not null default 0 check (paused_seconds >= 0),
    paused_at      timestamptz,
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now()
);

-- No máximo uma sessão em andamento (rodando ou pausada) por usuário
create unique index study_sessions_one_active_uidx on study_sessions (user_id)
    where status in ('RUNNING', 'PAUSED');

create index study_sessions_user_started_idx on study_sessions (user_id, started_at);

alter table lessons enable row level security;
alter table study_sessions enable row level security;

-- Uma revisão agendada por aula estudada, com o estado de memória do FSRS.
create table review_items (
    id             uuid primary key default gen_random_uuid(),
    user_id        uuid             not null,
    lesson_id      uuid             not null unique references lessons (id) on delete cascade,
    subject_id     uuid             not null references subjects (id) on delete cascade,
    fsrs_state     text             not null check (fsrs_state in ('NEW', 'LEARNING', 'REVIEW', 'RELEARNING')),
    stability      double precision not null default 0,
    difficulty     double precision not null default 0,
    reps           integer          not null default 0,
    lapses         integer          not null default 0,
    last_review    date,
    due_date       date             not null,
    review_minutes integer          not null check (review_minutes >= 5),
    last_grade     text check (last_grade in ('DIFICIL', 'OK', 'FACIL')),
    created_at     timestamptz      not null default now(),
    updated_at     timestamptz      not null default now()
);

create index review_items_user_due_idx on review_items (user_id, due_date);

alter table review_items enable row level security;

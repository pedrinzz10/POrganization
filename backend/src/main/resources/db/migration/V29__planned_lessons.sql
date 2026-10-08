-- Matéria com aulas definidas (E14): FREE = o título da aula é digitado ao terminar o timer;
-- PLANNED = a matéria tem a lista de aulas, na ordem do curso.
alter table subjects
    add column lesson_mode text not null default 'FREE' check (lesson_mode in ('FREE', 'PLANNED'));

-- Aulas definidas de uma matéria. position é a sequência (1, 2, 3...); lesson_id aponta para a
-- aula estudada (lessons) quando ela é feita.
create table planned_lessons (
    id         uuid primary key default gen_random_uuid(),
    user_id    uuid        not null,
    subject_id uuid        not null references subjects (id) on delete cascade,
    title      text        not null check (length(btrim(title)) between 1 and 200),
    position   integer     not null,
    lesson_id  uuid references lessons (id) on delete set null,
    created_at timestamptz not null default now()
);

create index planned_lessons_subject_position_idx on planned_lessons (subject_id, position);
create index planned_lessons_user_idx on planned_lessons (user_id);

alter table planned_lessons enable row level security;

-- Pré-requisitos entre matérias (E17): a matéria só entra no plano depois que as de que ela
-- depende estão completas (com aulas definidas: todas estudadas; livre: marcada como concluída).
alter table subjects
    add column completed_at timestamptz;

create table subject_prerequisites (
    subject_id          uuid not null references subjects (id) on delete cascade,
    required_subject_id uuid not null references subjects (id) on delete cascade,
    primary key (subject_id, required_subject_id),
    check (subject_id <> required_subject_id)
);

create index subject_prerequisites_required_idx on subject_prerequisites (required_subject_id);

alter table subject_prerequisites enable row level security;

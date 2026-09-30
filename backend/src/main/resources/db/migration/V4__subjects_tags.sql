-- Matérias de estudo, com prioridade (ordem), meta de sessões por semana e duração da aula.
create table subjects (
    id                uuid primary key default gen_random_uuid(),
    user_id           uuid        not null,
    name              text        not null check (length(btrim(name)) > 0),
    color             text check (color is null or color ~ '^#[0-9A-Fa-f]{6}$'),
    priority_order    integer     not null,
    sessions_per_week integer     not null default 2 check (sessions_per_week between 0 and 21),
    lesson_minutes    integer     not null default 50 check (lesson_minutes between 5 and 240),
    archived          boolean     not null default false,
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now()
);

create index subjects_user_priority_idx on subjects (user_id, priority_order);

-- Tags livres ("faculdade", "línguas"): nome único por usuário, sem diferenciar maiúsculas.
create table tags (
    id         uuid primary key default gen_random_uuid(),
    user_id    uuid        not null,
    name       text        not null check (length(btrim(name)) > 0),
    created_at timestamptz not null default now()
);

create unique index tags_user_name_uidx on tags (user_id, lower(name));

create table subject_tags (
    subject_id uuid not null references subjects (id) on delete cascade,
    tag_id     uuid not null references tags (id) on delete cascade,
    primary key (subject_id, tag_id)
);

create index subject_tags_tag_idx on subject_tags (tag_id);

alter table subjects enable row level security;
alter table tags enable row level security;
alter table subject_tags enable row level security;

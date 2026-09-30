-- Tarefas diárias (Etapa 6): hábitos que se repetem todo dia ou em dias escolhidos da semana.
create table daily_tasks (
    id         uuid primary key default gen_random_uuid(),
    user_id    uuid        not null,
    title      text        not null check (length(btrim(title)) between 1 and 100),
    emoji      text check (emoji is null or char_length(emoji) <= 16),
    position   integer     not null default 0,
    archived   boolean     not null default false,
    -- dia da criação no fuso do usuário: antes dele a tarefa nunca conta
    created_on date        not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index daily_tasks_user_idx on daily_tasks (user_id, position);

-- Dias da semana de cada tarefa, com vigência: mudar os dias vale de hoje em diante e o
-- passado continua pela regra que valia na época.
create table daily_task_schedules (
    id         uuid primary key default gen_random_uuid(),
    task_id    uuid  not null references daily_tasks (id) on delete cascade,
    valid_from date  not null,
    -- ["MON","TUE",...]; todos os 7 = todo dia
    weekdays   jsonb not null check (jsonb_typeof(weekdays) = 'array' and jsonb_array_length(weekdays) between 1 and 7),
    unique (task_id, valid_from)
);

-- Um "feito" por tarefa e dia
create table daily_task_completions (
    task_id    uuid        not null references daily_tasks (id) on delete cascade,
    user_id    uuid        not null,
    day        date        not null,
    created_at timestamptz not null default now(),
    primary key (task_id, day)
);

create index daily_task_completions_user_day_idx on daily_task_completions (user_id, day);

alter table daily_tasks enable row level security;
alter table daily_task_schedules enable row level security;
alter table daily_task_completions enable row level security;

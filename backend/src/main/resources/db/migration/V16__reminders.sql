-- Lembretes de compromisso: N minutos antes de cada ocorrência, por um ou mais canais.
create table reminders (
    id             uuid primary key default gen_random_uuid(),
    user_id        uuid        not null,
    commitment_id  uuid        not null references commitments (id) on delete cascade,
    -- até 4 semanas antes
    minutes_before integer     not null check (minutes_before between 0 and 40320),
    -- ["EMAIL", "PUSH"]
    channels       jsonb       not null check (jsonb_typeof(channels) = 'array' and jsonb_array_length(channels) > 0),
    created_at     timestamptz not null default now()
);

create index reminders_commitment_idx on reminders (commitment_id);
create index reminders_user_idx on reminders (user_id);

-- O que já foi enviado: um envio por lembrete, ocorrência e canal. O cron pode rodar quantas
-- vezes quiser na janela que o unique impede duplicado.
create table notification_log (
    id              uuid primary key default gen_random_uuid(),
    user_id         uuid        not null,
    reminder_id     uuid        not null references reminders (id) on delete cascade,
    occurrence_date date        not null,
    channel         text        not null check (channel in ('EMAIL', 'PUSH')),
    sent_at         timestamptz not null default now(),
    unique (reminder_id, occurrence_date, channel)
);

alter table reminders enable row level security;
alter table notification_log enable row level security;

-- Sincronização com o Google Calendar: o id do evento publicado e se falta sincronizar
-- (falha na API do Google; o próximo cron tenta de novo).
alter table commitments
    add column google_event_id text,
    add column sync_pending    boolean not null default false;

create index commitments_sync_pending_idx on commitments (sync_pending) where sync_pending;

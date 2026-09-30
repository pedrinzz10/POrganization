-- Importação do Google Calendar (sincronização incremental).
alter table google_connections
    -- nextSyncToken da última listagem; null = a próxima é completa
    add column sync_token text;

alter table commitments
    -- APP: criado aqui; GOOGLE: importado da agenda do usuário
    add column source text not null default 'APP' check (source in ('APP', 'GOOGLE'));

-- Um evento do Google vira no máximo um compromisso por usuário (importar de novo não duplica)
create unique index commitments_user_google_event_uidx on commitments (user_id, google_event_id)
    where google_event_id is not null;

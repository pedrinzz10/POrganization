-- Preferências de notificação do usuário.
alter table user_settings
    -- canais usados no lembrete padrão e no resumo diário: ["PUSH"], ["EMAIL"] ou os dois
    add column notify_channels          jsonb   not null default '["PUSH"]'
        check (jsonb_typeof(notify_channels) = 'array' and jsonb_array_length(notify_channels) > 0),
    -- lembrete que todo compromisso novo ganha (null = nenhum); dá para mudar ou tirar no formulário
    add column default_reminder_minutes integer check (default_reminder_minutes between 0 and 40320),
    -- horário do resumo diário no fuso do usuário (null = sem resumo)
    add column digest_time              time;

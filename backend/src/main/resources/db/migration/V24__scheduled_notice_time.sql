-- Aviso diário dos agendados para confirmar (F20): um horário por usuário, um aviso por dia.
alter table user_settings
    -- null = aviso desligado
    add column scheduled_notice_time      time,
    -- dia (no fuso do usuário) do último aviso enviado
    add column last_scheduled_notice_date date;

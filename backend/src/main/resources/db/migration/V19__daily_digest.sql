-- Resumo diário: o dia (no fuso do usuário) em que o último resumo saiu. Garante um por dia.
alter table user_settings
    add column last_digest_date date;

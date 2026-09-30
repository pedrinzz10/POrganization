-- Agendados (F18): a data de cada mês sai de uma regra, não só de um dia fixo.
alter table recurring_transactions
    -- DAY_OF_MONTH (dia N, com ajuste se cair em dia não útil), BUSINESS_DAY (N-ésimo dia útil) ou LAST_BUSINESS_DAY
    add column rule_type    text    not null default 'DAY_OF_MONTH'
        check (rule_type in ('DAY_OF_MONTH', 'BUSINESS_DAY', 'LAST_BUSINESS_DAY')),
    add column business_day integer check (business_day between 1 and 15),
    -- dia do mês que cai em fim de semana ou feriado: KEEP, ANTICIPATE ou POSTPONE
    add column adjustment   text    not null default 'KEEP' check (adjustment in ('KEEP', 'ANTICIPATE', 'POSTPONE')),
    alter column day_of_month drop not null;

alter table recurring_transactions
    add constraint recurring_rule_fields check (
        (rule_type = 'DAY_OF_MONTH' and day_of_month is not null)
        or (rule_type = 'BUSINESS_DAY' and business_day is not null)
        or rule_type = 'LAST_BUSINESS_DAY'
    );

-- Data que a regra deu para a ocorrência; "date" pode mudar (remarcada, ou confirmada em outro dia)
alter table transactions
    add column scheduled_date date;

-- "Não vou receber/pagar este mês": o mês fica marcado (não gera de novo) e o lançamento sai.
-- skipped_transaction_id guarda o id do lançamento cancelado (histórico e 409 numa segunda ação).
alter table recurring_generations
    add column skipped                boolean not null default false,
    add column skipped_transaction_id uuid;

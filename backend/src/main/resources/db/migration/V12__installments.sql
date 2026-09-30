-- Compras no cartão (à vista ou parceladas): as parcelas de uma compra têm o mesmo purchase_id.
create index transactions_purchase_idx on transactions (purchase_id);

-- Parcela N de M: os três campos andam juntos
alter table transactions
    add constraint transactions_installment_consistency check (
        (purchase_id is null and installment_number is null and installment_count is null)
        or (purchase_id is not null and installment_number is not null and installment_count is not null
            and installment_number <= installment_count)
    );

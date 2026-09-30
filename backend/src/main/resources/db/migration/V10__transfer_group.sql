-- Transferência entre contas = duas pernas TRANSFER (saída e entrada) com o mesmo grupo.
alter table transactions
    add column transfer_group_id  uuid,
    add column transfer_direction text check (transfer_direction in ('OUT', 'IN'));

alter table transactions
    add constraint transactions_transfer_consistency check (
        (type = 'TRANSFER') = (transfer_group_id is not null and transfer_direction is not null)
    );

create index transactions_transfer_group_idx on transactions (transfer_group_id);

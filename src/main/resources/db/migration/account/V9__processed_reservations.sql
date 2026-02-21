create table account.processed_reservations
(
    account_id     uuid        not null,
    transaction_id uuid        not null,
    reservation_id uuid        not null,
    phase          varchar(20) not null,
    occurred_at    timestamptz not null,
    PRIMARY KEY (account_id, reservation_id),
    UNIQUE (account_id, transaction_id)
);

create index processed_reservations_occurred_at_idx
    on account.processed_reservations (occurred_at);

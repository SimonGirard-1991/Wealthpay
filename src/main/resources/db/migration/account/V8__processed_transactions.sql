create table account.processed_transactions
(
    account_id     uuid        not null,
    transaction_id uuid        not null,
    occurred_at    timestamptz not null,
    PRIMARY KEY (account_id, transaction_id)
);

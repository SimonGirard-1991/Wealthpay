CREATE TABLE IF NOT EXISTS account.account_snapshot
(
    account_id      UUID PRIMARY KEY,
    state           JSONB       NOT NULL,
    version         BIGINT      NOT NULL,
    last_updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

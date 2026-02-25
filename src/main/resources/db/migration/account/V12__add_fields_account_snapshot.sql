ALTER TABLE account.account_snapshot
    ADD COLUMN schema_version BIGINT NOT NULL DEFAULT 1;

ALTER TABLE account.account_snapshot
    ALTER COLUMN schema_version DROP DEFAULT;


ALTER TABLE account.processed_transactions
    ADD COLUMN fingerprint char(64) NULL;

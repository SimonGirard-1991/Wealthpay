-- V5: Registration idempotency, mirroring account.processed_transactions.
--
-- Natural deduplication on the email unique constraint would have been cheaper and is semantically
-- wrong: a retry carrying a CHANGED payload would come back as a conflict indistinguishable from
-- "someone else already has this email". A stored fingerprint separates the two.
--
-- This is operational data, not an audit table: its natural retention is hours to days and it is
-- prunable on an operational schedule. Pinning it to the audit retention period would grow a hot
-- table without bound AND silently turn it into a personal-data store carrying its own retention and
-- erasure duty, in a table nobody thinks of as holding personal data. What makes pruning safe is the
-- customer_admission link in V4, which gives an admitted decision its own path to its customer
-- (ADR-009 D6). Consequently: no append-only trigger here, unlike the four audit tables.

CREATE TABLE customer.processed_registrations
(
    idempotency_key TEXT        NOT NULL,
    -- SHA-256 hex, same width and convention as account.processed_transactions.
    --
    -- It covers CLIENT-SUPPLIED PAYLOAD ONLY: email, details, sorted nationalities, residence. Never
    -- the minted customer id and never the registration instant. Both are generated server-side per
    -- attempt -- a random UUID and a Clock reading -- so a fingerprint taken over the domain command
    -- would differ on every retry, and the mismatch rule would turn every legitimate retry into a
    -- conflict. The mechanism would fail closed on exactly the case it exists to serve.
    fingerprint     CHAR(64)    NOT NULL,
    -- Stored so a replay can re-read the customer row and rebuild the identical response body. The
    -- body itself is deliberately not denormalised here.
    --
    -- NO FOREIGN KEY, and this is not an oversight to be tidied up later. Within the registration
    -- transaction the idempotency row is inserted FIRST, because its insert is what short-circuits a
    -- replay before any other work happens; the customer row does not exist yet at that point, so a
    -- foreign key would reject every registration. Making it deferrable would work and buys nothing:
    -- this is prunable data whose referent is allowed to be gone.
    customer_id     UUID        NOT NULL,
    occurred_at     TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_processed_registrations PRIMARY KEY (idempotency_key),
    CONSTRAINT ck_processed_registrations_fingerprint_shape CHECK (fingerprint ~ '^[0-9a-f]{64}$')
);

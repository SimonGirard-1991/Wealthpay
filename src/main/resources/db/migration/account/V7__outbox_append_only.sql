-- Enforce append-only behavior on account.outbox

CREATE OR REPLACE FUNCTION account.outbox_append_only()
    RETURNS trigger
    LANGUAGE plpgsql
AS
$$
BEGIN
    RAISE EXCEPTION 'account.outbox is append-only. % is not allowed.', TG_OP;
END;
$$;

DROP TRIGGER IF EXISTS trg_outbox_no_update ON account.outbox;
CREATE TRIGGER trg_outbox_no_update
    BEFORE UPDATE
    ON account.outbox
    FOR EACH ROW
EXECUTE FUNCTION account.outbox_append_only();

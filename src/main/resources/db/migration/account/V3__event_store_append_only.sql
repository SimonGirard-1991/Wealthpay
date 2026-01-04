-- Enforce append-only behavior on account.event_store

CREATE OR REPLACE FUNCTION account.event_store_append_only()
    RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'account.event_store is append-only. % is not allowed.', TG_OP;
END;
$$;

DROP TRIGGER IF EXISTS trg_event_store_no_update ON account.event_store;
CREATE TRIGGER trg_event_store_no_update
    BEFORE UPDATE ON account.event_store
    FOR EACH ROW
EXECUTE FUNCTION account.event_store_append_only();

DROP TRIGGER IF EXISTS trg_event_store_no_delete ON account.event_store;
CREATE TRIGGER trg_event_store_no_delete
    BEFORE DELETE ON account.event_store
    FOR EACH ROW
EXECUTE FUNCTION account.event_store_append_only();
drop index outbox_pending_idx;

alter table outbox
    drop column status,
    drop column publish_attempts,
    drop column last_error,
    drop column available_at,
    drop column published_at;

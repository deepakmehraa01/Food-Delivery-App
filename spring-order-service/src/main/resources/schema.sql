CREATE TABLE IF NOT EXISTS order_notification_outbox (
    event_id UUID PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE,
    payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX IF NOT EXISTS idx_order_notification_pending ON order_notification_outbox(created_at) WHERE published_at IS NULL;

CREATE TABLE IF NOT EXISTS notification_inbox (
    event_id UUID PRIMARY KEY,
    order_id BIGINT NOT NULL,
    order_number VARCHAR(255) NOT NULL,
    customer_id BIGINT NOT NULL,
    total_amount NUMERIC(19,2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP WITH TIME ZONE,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error VARCHAR(255)
);
CREATE INDEX IF NOT EXISTS idx_notification_pending ON notification_inbox(next_attempt_at) WHERE sent_at IS NULL;

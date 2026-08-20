CREATE TABLE notification (
    id        UUID PRIMARY KEY,
    user_id   UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    kind      TEXT        NOT NULL,
    title_key TEXT        NOT NULL,
    params    JSONB       NOT NULL DEFAULT '{}'::JSONB,
    task_key  TEXT,
    read_at   TIMESTAMPTZ
);

CREATE INDEX idx_notification_user ON notification (user_id, at DESC);
CREATE INDEX idx_notification_unread ON notification (user_id) WHERE read_at IS NULL;

CREATE TABLE integration (
    id          UUID PRIMARY KEY,
    kind        TEXT        NOT NULL CHECK (kind IN ('webhook', 'email')),
    name        TEXT        NOT NULL,
    enabled     BOOLEAN     NOT NULL DEFAULT TRUE,
    config      JSONB       NOT NULL DEFAULT '{}'::JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_status TEXT,
    last_at     TIMESTAMPTZ,
    last_detail TEXT        NOT NULL DEFAULT ''
);

CREATE UNIQUE INDEX idx_integration_name ON integration (lower(name));

CREATE TABLE integration_delivery (
    id             UUID PRIMARY KEY,
    integration_id UUID        NOT NULL REFERENCES integration (id) ON DELETE CASCADE,
    at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    event          TEXT        NOT NULL,
    task_key       TEXT,
    ok             BOOLEAN     NOT NULL,
    detail         TEXT        NOT NULL DEFAULT ''
);

CREATE INDEX idx_integration_delivery ON integration_delivery (integration_id, at DESC);

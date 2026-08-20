-- The event log. It records administrative operations and every write request
-- made with an API key, together with which key made it.

CREATE TABLE audit_event (
    id          UUID PRIMARY KEY,
    at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor_id    UUID REFERENCES app_user (id) ON DELETE SET NULL,
    api_key_id  UUID REFERENCES api_key (id) ON DELETE SET NULL,
    actor_label TEXT        NOT NULL DEFAULT '',
    action      TEXT        NOT NULL,
    subject     TEXT        NOT NULL DEFAULT '',
    detail      JSONB       NOT NULL DEFAULT '{}'::JSONB
);

CREATE INDEX idx_audit_at ON audit_event (at DESC);
CREATE INDEX idx_audit_api_key ON audit_event (api_key_id, at DESC);

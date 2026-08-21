CREATE TABLE password_reset (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    email      TEXT        NOT NULL,
    token_hash TEXT        NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ
);

CREATE INDEX idx_password_reset_user ON password_reset (user_id, created_at DESC);

GRANT SELECT, INSERT, UPDATE, DELETE ON password_reset TO nowtask_app;

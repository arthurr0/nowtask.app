ALTER TABLE app_user
    DROP CONSTRAINT app_user_state_check,
    ADD CONSTRAINT app_user_state_check CHECK (state IN ('active', 'disabled', 'deleted'));

CREATE TABLE email_change (
    id           UUID        PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    new_email    TEXT        NOT NULL,
    token_hash   TEXT        NOT NULL UNIQUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ
);

CREATE INDEX idx_email_change_user ON email_change (user_id, created_at DESC);

CREATE TABLE user_session (
    id           UUID        PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    session_key  TEXT        NOT NULL UNIQUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ip           TEXT,
    user_agent   TEXT,
    revoked_at   TIMESTAMPTZ
);

CREATE INDEX idx_user_session_user ON user_session (user_id, last_seen_at DESC);

CREATE TABLE user_notification_pref (
    user_id UUID    NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    kind    TEXT    NOT NULL,
    in_app  BOOLEAN NOT NULL DEFAULT TRUE,
    email   BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (user_id, kind)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON email_change TO nowtask_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON user_session TO nowtask_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON user_notification_pref TO nowtask_app;

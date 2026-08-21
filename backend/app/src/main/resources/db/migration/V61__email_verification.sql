CREATE TABLE email_verification (
    id           UUID        PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    email        TEXT        NOT NULL,
    token_hash   TEXT        NOT NULL UNIQUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ
);

CREATE INDEX idx_email_verification_user ON email_verification (user_id, created_at DESC);

ALTER TABLE app_user
    ADD COLUMN email_verified_at TIMESTAMPTZ,
    ADD COLUMN state             TEXT NOT NULL DEFAULT 'active',
    ADD COLUMN oidc_subject      TEXT UNIQUE,
    ADD CONSTRAINT app_user_state_check CHECK (state IN ('active', 'disabled'));

UPDATE app_user SET email_verified_at = created_at WHERE pending = FALSE;

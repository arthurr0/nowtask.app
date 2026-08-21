CREATE TABLE organization_invite (
    id               UUID        PRIMARY KEY,
    organization_id  UUID        NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
    email            TEXT        NOT NULL,
    role_id          UUID        NOT NULL REFERENCES organization_role (id) ON DELETE RESTRICT,
    token_hash       TEXT        NOT NULL UNIQUE,
    state            TEXT        NOT NULL DEFAULT 'open',
    invited_by       UUID        NOT NULL REFERENCES app_user (id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at       TIMESTAMPTZ NOT NULL,
    accepted_at      TIMESTAMPTZ,
    accepted_by      UUID REFERENCES app_user (id) ON DELETE SET NULL,
    revoked_at       TIMESTAMPTZ,
    reminder_sent_at TIMESTAMPTZ,
    new_requested_at TIMESTAMPTZ,
    CONSTRAINT invite_state_check CHECK (state IN ('open', 'accepted', 'revoked', 'expired'))
);

CREATE UNIQUE INDEX idx_invite_open_email
    ON organization_invite (organization_id, lower(email)) WHERE state = 'open';
CREATE INDEX idx_invite_org ON organization_invite (organization_id, state);

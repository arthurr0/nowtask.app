ALTER TABLE app_user
    ADD COLUMN instance_admin BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_app_user_instance_admin ON app_user (instance_admin) WHERE instance_admin;

ALTER TABLE organization
    ADD COLUMN suspended_at TIMESTAMPTZ;

CREATE TABLE instance_settings (
    id                  BOOLEAN     PRIMARY KEY DEFAULT TRUE,
    signup_mode         TEXT,
    announcement_text   TEXT,
    announcement_level  TEXT,
    announcement_until  TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by          UUID        REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT instance_settings_single_row CHECK (id),
    CONSTRAINT instance_settings_signup_mode_check
        CHECK (signup_mode IS NULL OR signup_mode IN ('open', 'invite-only', 'sso-only')),
    CONSTRAINT instance_settings_announcement_level_check
        CHECK (announcement_level IS NULL OR announcement_level IN ('info', 'warning', 'critical'))
);

INSERT INTO instance_settings (id) VALUES (TRUE);

CREATE TABLE instance_audit_event (
    id           UUID        PRIMARY KEY,
    actor_id     UUID        REFERENCES app_user (id) ON DELETE SET NULL,
    action       TEXT        NOT NULL,
    target_type  TEXT        NOT NULL,
    target_id    TEXT,
    target_label TEXT,
    detail       JSONB       NOT NULL DEFAULT '{}'::JSONB,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_instance_audit_event_created ON instance_audit_event (created_at DESC, id);

REVOKE UPDATE, DELETE ON instance_audit_event FROM nowtask_app;

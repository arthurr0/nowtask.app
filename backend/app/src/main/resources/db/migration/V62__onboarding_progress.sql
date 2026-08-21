CREATE TABLE onboarding_progress (
    id              UUID        PRIMARY KEY,
    organization_id UUID        NOT NULL REFERENCES organization (id) ON DELETE CASCADE
                                DEFAULT nullif(current_setting('app.organization_id', true), '')::UUID,
    user_id         UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    flow            TEXT        NOT NULL,
    step            TEXT        NOT NULL DEFAULT 'orgName',
    checklist       JSONB       NOT NULL DEFAULT '{}'::JSONB,
    tour_seen_at    TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    dismissed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, user_id),
    CONSTRAINT onboarding_flow_check CHECK (flow IN ('founder', 'invitee'))
);

ALTER TABLE onboarding_progress ENABLE ROW LEVEL SECURITY;
CREATE POLICY onboarding_progress_tenant ON onboarding_progress
    USING      (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID)
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID);

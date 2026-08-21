CREATE TABLE task_view_setting (
    id              UUID    PRIMARY KEY,
    organization_id UUID    NOT NULL REFERENCES organization (id) ON DELETE CASCADE
                            DEFAULT nullif(current_setting('app.organization_id', true), '')::UUID,
    project_id      UUID    REFERENCES project (id) ON DELETE CASCADE,
    view_code       TEXT    NOT NULL,
    enabled         BOOLEAN NOT NULL,
    CONSTRAINT task_view_setting_code_check
        CHECK (view_code IN ('board', 'list', 'timeline', 'calendar'))
);

CREATE UNIQUE INDEX idx_task_view_setting_org
    ON task_view_setting (organization_id, view_code) WHERE project_id IS NULL;
CREATE UNIQUE INDEX idx_task_view_setting_project
    ON task_view_setting (project_id, view_code) WHERE project_id IS NOT NULL;

ALTER TABLE task_view_setting ENABLE ROW LEVEL SECURITY;
CREATE POLICY task_view_setting_tenant ON task_view_setting
    USING      (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID)
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID);

CREATE TRIGGER task_view_setting_same_org BEFORE INSERT OR UPDATE ON task_view_setting
    FOR EACH ROW EXECUTE FUNCTION assert_same_org('project', 'project_id');

CREATE TABLE user_view_preference (
    organization_id   UUID NOT NULL REFERENCES organization (id) ON DELETE CASCADE
                           DEFAULT nullif(current_setting('app.organization_id', true), '')::UUID,
    user_id           UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    default_view_code TEXT NOT NULL,
    PRIMARY KEY (organization_id, user_id),
    CONSTRAINT user_view_preference_code_check
        CHECK (default_view_code IN ('board', 'list', 'timeline', 'calendar'))
);

ALTER TABLE user_view_preference ENABLE ROW LEVEL SECURITY;
CREATE POLICY user_view_preference_tenant ON user_view_preference
    USING      (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID)
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID);

CREATE TABLE task_field_setting (
    id              UUID    PRIMARY KEY,
    organization_id UUID    NOT NULL REFERENCES organization (id) ON DELETE CASCADE
                            DEFAULT nullif(current_setting('app.organization_id', true), '')::UUID,
    project_id      UUID    REFERENCES project (id) ON DELETE CASCADE,
    field_key       TEXT    NOT NULL,
    enabled         BOOLEAN NOT NULL
);

CREATE UNIQUE INDEX idx_task_field_setting_org
    ON task_field_setting (organization_id, field_key) WHERE project_id IS NULL;
CREATE UNIQUE INDEX idx_task_field_setting_project
    ON task_field_setting (project_id, field_key) WHERE project_id IS NOT NULL;

ALTER TABLE task_field_setting ENABLE ROW LEVEL SECURITY;
CREATE POLICY task_field_setting_tenant ON task_field_setting
    USING      (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID)
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID);

CREATE TRIGGER task_field_setting_same_org BEFORE INSERT OR UPDATE ON task_field_setting
    FOR EACH ROW EXECUTE FUNCTION assert_same_org('project', 'project_id');

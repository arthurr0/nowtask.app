DO $do$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'nowtask_app') THEN
        CREATE ROLE nowtask_app LOGIN PASSWORD '${appDbPassword}';
    ELSE
        ALTER ROLE nowtask_app LOGIN PASSWORD '${appDbPassword}';
    END IF;
END
$do$;

GRANT USAGE ON SCHEMA public TO nowtask_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO nowtask_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO nowtask_app;

CREATE FUNCTION assert_same_org() RETURNS TRIGGER AS $fn$
DECLARE
    parent_table TEXT := TG_ARGV[0];
    fk_column    TEXT := TG_ARGV[1];
    child_value  UUID := (to_jsonb(NEW) ->> fk_column)::UUID;
    parent_org   UUID;
BEGIN
    IF child_value IS NULL THEN
        RETURN NEW;
    END IF;

    EXECUTE format('SELECT organization_id FROM %I WHERE id = $1', parent_table)
        INTO parent_org USING child_value;

    IF parent_org IS DISTINCT FROM NEW.organization_id THEN
        RAISE EXCEPTION 'organization_id % does not match parent %.id = % (%)',
            NEW.organization_id, parent_table, child_value, parent_org;
    END IF;

    RETURN NEW;
END;
$fn$ LANGUAGE plpgsql;

ALTER TABLE team ENABLE ROW LEVEL SECURITY;
CREATE POLICY team_tenant ON team
    USING      (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID)
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID);
ALTER TABLE team_member ENABLE ROW LEVEL SECURITY;
CREATE POLICY team_member_tenant ON team_member
    USING      (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID)
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID);
ALTER TABLE project ENABLE ROW LEVEL SECURITY;
CREATE POLICY project_tenant ON project
    USING      (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID)
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID);
ALTER TABLE status_def ENABLE ROW LEVEL SECURITY;
CREATE POLICY status_def_tenant ON status_def
    USING      (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID)
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID);
ALTER TABLE status_transition ENABLE ROW LEVEL SECURITY;
CREATE POLICY status_transition_tenant ON status_transition
    USING      (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID)
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID);
ALTER TABLE epic ENABLE ROW LEVEL SECURITY;
CREATE POLICY epic_tenant ON epic
    USING      (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID)
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID);
ALTER TABLE task ENABLE ROW LEVEL SECURITY;
CREATE POLICY task_tenant ON task
    USING      (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID)
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID);
ALTER TABLE task_label ENABLE ROW LEVEL SECURITY;
CREATE POLICY task_label_tenant ON task_label
    USING      (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID)
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID);
ALTER TABLE subtask ENABLE ROW LEVEL SECURITY;
CREATE POLICY subtask_tenant ON subtask
    USING      (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID)
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID);
ALTER TABLE task_relation ENABLE ROW LEVEL SECURITY;
CREATE POLICY task_relation_tenant ON task_relation
    USING      (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID)
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID);
ALTER TABLE task_comment ENABLE ROW LEVEL SECURITY;
CREATE POLICY task_comment_tenant ON task_comment
    USING      (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID)
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID);
ALTER TABLE task_history ENABLE ROW LEVEL SECURITY;
CREATE POLICY task_history_tenant ON task_history
    USING      (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID)
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID);
ALTER TABLE task_watcher ENABLE ROW LEVEL SECURITY;
CREATE POLICY task_watcher_tenant ON task_watcher
    USING      (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID)
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID);
ALTER TABLE task_key_sequence ENABLE ROW LEVEL SECURITY;
CREATE POLICY task_key_sequence_tenant ON task_key_sequence
    USING      (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID)
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID);
ALTER TABLE automation_rule ENABLE ROW LEVEL SECURITY;
CREATE POLICY automation_rule_tenant ON automation_rule
    USING      (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID)
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID);
ALTER TABLE automation_run ENABLE ROW LEVEL SECURITY;
CREATE POLICY automation_run_tenant ON automation_run
    USING      (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID)
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID);
ALTER TABLE custom_field ENABLE ROW LEVEL SECURITY;
CREATE POLICY custom_field_tenant ON custom_field
    USING      (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID)
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID);
ALTER TABLE saved_view ENABLE ROW LEVEL SECURITY;
CREATE POLICY saved_view_tenant ON saved_view
    USING      (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID)
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID);
ALTER TABLE api_key ENABLE ROW LEVEL SECURITY;
CREATE POLICY api_key_tenant ON api_key
    USING      (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID)
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID);
ALTER TABLE milestone ENABLE ROW LEVEL SECURITY;
CREATE POLICY milestone_tenant ON milestone
    USING      (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID)
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID);
ALTER TABLE burndown_point ENABLE ROW LEVEL SECURITY;
CREATE POLICY burndown_point_tenant ON burndown_point
    USING      (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID)
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID);
ALTER TABLE throughput_week ENABLE ROW LEVEL SECURITY;
CREATE POLICY throughput_week_tenant ON throughput_week
    USING      (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID)
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID);
ALTER TABLE workspace_settings ENABLE ROW LEVEL SECURITY;
CREATE POLICY workspace_settings_tenant ON workspace_settings
    USING      (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID)
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID);
ALTER TABLE audit_event ENABLE ROW LEVEL SECURITY;
CREATE POLICY audit_event_tenant ON audit_event
    USING      (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID)
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID
             OR organization_id IS NULL);
ALTER TABLE notification ENABLE ROW LEVEL SECURITY;
CREATE POLICY notification_tenant ON notification
    USING      (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID)
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID);
ALTER TABLE integration ENABLE ROW LEVEL SECURITY;
CREATE POLICY integration_tenant ON integration
    USING      (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID)
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID);
ALTER TABLE integration_delivery ENABLE ROW LEVEL SECURITY;
CREATE POLICY integration_delivery_tenant ON integration_delivery
    USING      (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID)
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID);
ALTER TABLE user_nav_item ENABLE ROW LEVEL SECURITY;
CREATE POLICY user_nav_item_tenant ON user_nav_item
    USING      (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID)
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID);

CREATE TRIGGER team_member_same_org BEFORE INSERT OR UPDATE ON team_member
    FOR EACH ROW EXECUTE FUNCTION assert_same_org('team', 'team_id');
CREATE TRIGGER status_def_same_org BEFORE INSERT OR UPDATE ON status_def
    FOR EACH ROW EXECUTE FUNCTION assert_same_org('project', 'project_id');
CREATE TRIGGER status_transition_same_org BEFORE INSERT OR UPDATE ON status_transition
    FOR EACH ROW EXECUTE FUNCTION assert_same_org('status_def', 'from_status');
CREATE TRIGGER epic_same_org BEFORE INSERT OR UPDATE ON epic
    FOR EACH ROW EXECUTE FUNCTION assert_same_org('project', 'project_id');
CREATE TRIGGER task_same_org BEFORE INSERT OR UPDATE ON task
    FOR EACH ROW EXECUTE FUNCTION assert_same_org('project', 'project_id');
CREATE TRIGGER task_label_same_org BEFORE INSERT OR UPDATE ON task_label
    FOR EACH ROW EXECUTE FUNCTION assert_same_org('task', 'task_id');
CREATE TRIGGER subtask_same_org BEFORE INSERT OR UPDATE ON subtask
    FOR EACH ROW EXECUTE FUNCTION assert_same_org('task', 'task_id');
CREATE TRIGGER task_relation_same_org BEFORE INSERT OR UPDATE ON task_relation
    FOR EACH ROW EXECUTE FUNCTION assert_same_org('task', 'from_task');
CREATE TRIGGER task_comment_same_org BEFORE INSERT OR UPDATE ON task_comment
    FOR EACH ROW EXECUTE FUNCTION assert_same_org('task', 'task_id');
CREATE TRIGGER task_history_same_org BEFORE INSERT OR UPDATE ON task_history
    FOR EACH ROW EXECUTE FUNCTION assert_same_org('task', 'task_id');
CREATE TRIGGER task_watcher_same_org BEFORE INSERT OR UPDATE ON task_watcher
    FOR EACH ROW EXECUTE FUNCTION assert_same_org('task', 'task_id');
CREATE TRIGGER task_key_sequence_same_org BEFORE INSERT OR UPDATE ON task_key_sequence
    FOR EACH ROW EXECUTE FUNCTION assert_same_org('project', 'project_id');
CREATE TRIGGER automation_rule_same_org BEFORE INSERT OR UPDATE ON automation_rule
    FOR EACH ROW EXECUTE FUNCTION assert_same_org('project', 'project_id');
CREATE TRIGGER automation_run_same_org BEFORE INSERT OR UPDATE ON automation_run
    FOR EACH ROW EXECUTE FUNCTION assert_same_org('automation_rule', 'rule_id');
CREATE TRIGGER custom_field_same_org BEFORE INSERT OR UPDATE ON custom_field
    FOR EACH ROW EXECUTE FUNCTION assert_same_org('project', 'project_id');
CREATE TRIGGER milestone_same_org BEFORE INSERT OR UPDATE ON milestone
    FOR EACH ROW EXECUTE FUNCTION assert_same_org('project', 'project_id');
CREATE TRIGGER burndown_point_same_org BEFORE INSERT OR UPDATE ON burndown_point
    FOR EACH ROW EXECUTE FUNCTION assert_same_org('project', 'project_id');
CREATE TRIGGER throughput_week_same_org BEFORE INSERT OR UPDATE ON throughput_week
    FOR EACH ROW EXECUTE FUNCTION assert_same_org('project', 'project_id');
CREATE TRIGGER integration_delivery_same_org BEFORE INSERT OR UPDATE ON integration_delivery
    FOR EACH ROW EXECUTE FUNCTION assert_same_org('integration', 'integration_id');

ALTER TABLE organization ENABLE ROW LEVEL SECURITY;
CREATE POLICY organization_visible ON organization
    USING (EXISTS (
        SELECT 1 FROM organization_member m
        WHERE m.organization_id = organization.id
          AND m.user_id = nullif(current_setting('app.user_id', true), '')::UUID
          AND m.state = 'active'))
    WITH CHECK (id = nullif(current_setting('app.organization_id', true), '')::UUID);

ALTER TABLE organization_member ENABLE ROW LEVEL SECURITY;
CREATE POLICY organization_member_visible ON organization_member
    USING (user_id = nullif(current_setting('app.user_id', true), '')::UUID OR organization_id = nullif(current_setting('app.organization_id', true), '')::UUID)
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID);

ALTER TABLE organization_role ENABLE ROW LEVEL SECURITY;
CREATE POLICY organization_role_visible ON organization_role
    USING (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID
        OR EXISTS (
            SELECT 1 FROM organization_member m
            WHERE m.organization_id = organization_role.organization_id
              AND m.user_id = nullif(current_setting('app.user_id', true), '')::UUID
              AND m.state = 'active'))
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID);

ALTER TABLE organization_role_permission ENABLE ROW LEVEL SECURITY;
CREATE POLICY organization_role_permission_visible ON organization_role_permission
    USING (EXISTS (SELECT 1 FROM organization_role r WHERE r.id = role_id))
    WITH CHECK (EXISTS (
        SELECT 1 FROM organization_role r
        WHERE r.id = role_id AND r.organization_id = nullif(current_setting('app.organization_id', true), '')::UUID));

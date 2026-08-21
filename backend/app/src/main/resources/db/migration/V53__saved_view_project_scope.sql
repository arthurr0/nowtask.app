ALTER TABLE saved_view
    ADD COLUMN project_id UUID REFERENCES project (id) ON DELETE CASCADE,
    ADD COLUMN origin     TEXT NOT NULL DEFAULT 'user',
    ADD CONSTRAINT saved_view_origin_check CHECK (origin IN ('builtin', 'preset', 'user'));

UPDATE saved_view SET origin = 'builtin' WHERE code IS NOT NULL;

ALTER TABLE saved_view DROP CONSTRAINT saved_view_org_code_key;
ALTER TABLE saved_view ADD CONSTRAINT saved_view_org_project_code_key
    UNIQUE NULLS NOT DISTINCT (organization_id, project_id, code);

CREATE TRIGGER saved_view_same_org BEFORE INSERT OR UPDATE ON saved_view
    FOR EACH ROW EXECUTE FUNCTION assert_same_org('project', 'project_id');

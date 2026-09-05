ALTER TABLE saved_view DROP CONSTRAINT saved_view_org_project_code_key;

CREATE UNIQUE INDEX saved_view_org_project_code_key
    ON saved_view (organization_id, project_id, code) NULLS NOT DISTINCT
    WHERE code IS NOT NULL;

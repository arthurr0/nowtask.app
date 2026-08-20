ALTER TABLE workspace_settings
    ADD COLUMN current_sprint TEXT NOT NULL DEFAULT 'S24';

ALTER TABLE project
    ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_task_project ON task (project_id);

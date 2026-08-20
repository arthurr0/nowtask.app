CREATE TABLE task_key_sequence (
    project_id UUID PRIMARY KEY REFERENCES project (id) ON DELETE CASCADE,
    next_value INTEGER NOT NULL
);

INSERT INTO task_key_sequence (project_id, next_value)
SELECT p.id, COALESCE(MAX(NULLIF(regexp_replace(t.task_key, '^.*-', ''), '')::INTEGER), 0) + 1
FROM project p
LEFT JOIN task t ON t.project_id = p.id
GROUP BY p.id;

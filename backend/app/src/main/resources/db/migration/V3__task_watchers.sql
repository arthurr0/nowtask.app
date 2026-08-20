CREATE TABLE task_watcher (
    id      UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES task (id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    UNIQUE (task_id, user_id)
);

CREATE INDEX idx_watcher_task ON task_watcher (task_id);

INSERT INTO task_watcher (id, task_id, user_id)
SELECT gen_random_uuid(), t.id, u.id
FROM task t
JOIN (
    SELECT id, row_number() OVER (ORDER BY created_at, id) AS seat
    FROM app_user
    WHERE NOT pending
) u ON u.seat <= t.watcher_count;

ALTER TABLE task DROP COLUMN watcher_count;

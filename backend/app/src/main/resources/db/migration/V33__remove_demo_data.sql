CREATE FUNCTION pg_temp.uid(key TEXT) RETURNS UUID AS $$
    SELECT md5(key)::UUID
$$ LANGUAGE sql;

CREATE FUNCTION pg_temp.uids(prefix TEXT, count INT) RETURNS SETOF UUID AS $$
    SELECT pg_temp.uid(prefix || i) FROM generate_series(1, count) AS i
$$ LANGUAGE sql;

DELETE FROM task_comment WHERE author_id IN (SELECT pg_temp.uids('user:u', 6));

DELETE FROM automation_rule WHERE id IN (SELECT pg_temp.uids('rule:r', 6));

DELETE FROM project WHERE id IN (SELECT pg_temp.uids('project:p', 3));

DELETE FROM task_watcher WHERE user_id IN (SELECT pg_temp.uids('user:u', 6));

DELETE FROM team WHERE id IN (SELECT pg_temp.uids('team:t', 3));

DELETE FROM app_user WHERE id IN (SELECT pg_temp.uids('user:u', 6));

DELETE FROM custom_field WHERE id IN (SELECT pg_temp.uids('cf:f', 8));

DELETE FROM saved_view WHERE id IN (SELECT pg_temp.uids('view:v', 3));

DELETE FROM api_key WHERE id IN (SELECT pg_temp.uids('key:k', 2));

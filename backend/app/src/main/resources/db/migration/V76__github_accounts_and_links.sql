CREATE TABLE github_account (
    user_id        UUID PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    github_user_id BIGINT      NOT NULL UNIQUE,
    login          TEXT        NOT NULL,
    avatar_url     TEXT        NOT NULL DEFAULT '',
    connected_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_github_account_login ON github_account (lower(login));

ALTER TABLE github_link DROP CONSTRAINT github_link_kind_check;
ALTER TABLE github_link ADD CONSTRAINT github_link_kind_check
    CHECK (kind IN ('issue', 'pull', 'commit', 'branch', 'release', 'workflow'));

ALTER TABLE github_link ADD COLUMN ref          TEXT NOT NULL DEFAULT '';
ALTER TABLE github_link ADD COLUMN author_login TEXT NOT NULL DEFAULT '';
ALTER TABLE github_link ADD COLUMN detail       TEXT NOT NULL DEFAULT '';
ALTER TABLE github_link ADD COLUMN check_state  TEXT NOT NULL DEFAULT '';
ALTER TABLE github_link ALTER COLUMN number DROP NOT NULL;

UPDATE github_link SET ref = number::TEXT WHERE ref = '';

DROP INDEX idx_github_link_target;
CREATE UNIQUE INDEX idx_github_link_target
    ON github_link (organization_id, repo_full_name, kind, ref);

CREATE FUNCTION github_account_lookup(lookup_login TEXT, lookup_github_id BIGINT)
RETURNS TABLE (
    user_id        UUID,
    github_user_id BIGINT,
    login          TEXT)
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT a.user_id, a.github_user_id, a.login
    FROM github_account a
    WHERE a.github_user_id = lookup_github_id
       OR (lookup_github_id IS NULL AND lower(a.login) = lower(lookup_login))
    LIMIT 1
$$;

REVOKE EXECUTE ON FUNCTION github_account_lookup(TEXT, BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION github_account_lookup(TEXT, BIGINT) TO nowtask_app;

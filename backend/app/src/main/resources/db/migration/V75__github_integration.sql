ALTER TABLE integration DROP CONSTRAINT integration_kind_check;
ALTER TABLE integration ADD CONSTRAINT integration_kind_check
    CHECK (kind IN ('webhook', 'email', 'github'));

CREATE TABLE github_installation (
    id              UUID PRIMARY KEY,
    organization_id UUID        NOT NULL DEFAULT nullif(current_setting('app.organization_id', true), '')::UUID,
    installation_id BIGINT      NOT NULL,
    account_login   TEXT        NOT NULL DEFAULT '',
    account_type    TEXT        NOT NULL DEFAULT '',
    suspended       BOOLEAN     NOT NULL DEFAULT FALSE,
    connected_by    UUID REFERENCES app_user (id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    removed_at      TIMESTAMPTZ,
    CONSTRAINT github_installation_org_fk FOREIGN KEY (organization_id)
        REFERENCES organization (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_github_installation_active ON github_installation (installation_id)
    WHERE removed_at IS NULL;
CREATE INDEX idx_github_installation_org ON github_installation (organization_id);

CREATE TABLE github_link (
    id              UUID PRIMARY KEY,
    organization_id UUID        NOT NULL DEFAULT nullif(current_setting('app.organization_id', true), '')::UUID,
    task_key        TEXT        NOT NULL,
    repo_full_name  TEXT        NOT NULL,
    kind            TEXT        NOT NULL CHECK (kind IN ('issue', 'pull')),
    number          INTEGER     NOT NULL,
    node_id         TEXT        NOT NULL DEFAULT '',
    url             TEXT        NOT NULL DEFAULT '',
    state           TEXT        NOT NULL DEFAULT '',
    title           TEXT        NOT NULL DEFAULT '',
    origin          TEXT        NOT NULL DEFAULT 'github' CHECK (origin IN ('github', 'nowtask')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    synced_title    TEXT        NOT NULL DEFAULT '',
    synced_body     TEXT        NOT NULL DEFAULT '',
    CONSTRAINT github_link_org_fk FOREIGN KEY (organization_id)
        REFERENCES organization (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_github_link_target
    ON github_link (organization_id, repo_full_name, kind, number);
CREATE INDEX idx_github_link_task ON github_link (organization_id, task_key);

CREATE TABLE github_delivery (
    id              UUID PRIMARY KEY,
    organization_id UUID        NOT NULL DEFAULT nullif(current_setting('app.organization_id', true), '')::UUID,
    delivery_id     TEXT        NOT NULL,
    event           TEXT        NOT NULL,
    received_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT github_delivery_org_fk FOREIGN KEY (organization_id)
        REFERENCES organization (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_github_delivery_once ON github_delivery (organization_id, delivery_id);
CREATE INDEX idx_github_delivery_age ON github_delivery (received_at);

ALTER TABLE github_installation ENABLE ROW LEVEL SECURITY;
CREATE POLICY github_installation_tenant ON github_installation
    USING      (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID)
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID);

ALTER TABLE github_link ENABLE ROW LEVEL SECURITY;
CREATE POLICY github_link_tenant ON github_link
    USING      (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID)
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID);

ALTER TABLE github_delivery ENABLE ROW LEVEL SECURITY;
CREATE POLICY github_delivery_tenant ON github_delivery
    USING      (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID)
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID);

CREATE FUNCTION github_installation_lookup(lookup_installation BIGINT)
RETURNS TABLE (
    id              UUID,
    organization_id UUID,
    installation_id BIGINT,
    account_login   TEXT,
    account_type    TEXT,
    suspended       BOOLEAN,
    connected_by    UUID)
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT i.id, i.organization_id, i.installation_id, i.account_login, i.account_type, i.suspended,
           i.connected_by
    FROM github_installation i
    WHERE i.installation_id = lookup_installation
      AND i.removed_at IS NULL
$$;

REVOKE EXECUTE ON FUNCTION github_installation_lookup(BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION github_installation_lookup(BIGINT) TO nowtask_app;

CREATE FUNCTION github_installation_release(lookup_installation BIGINT, at TIMESTAMPTZ)
RETURNS VOID
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    UPDATE github_installation
    SET removed_at = at
    WHERE installation_id = lookup_installation
      AND removed_at IS NULL
$$;

REVOKE EXECUTE ON FUNCTION github_installation_release(BIGINT, TIMESTAMPTZ) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION github_installation_release(BIGINT, TIMESTAMPTZ) TO nowtask_app;

CREATE FUNCTION github_deliveries_prune(older_than_days INTEGER)
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $fn$
DECLARE
    removed INTEGER;
BEGIN
    DELETE FROM github_delivery
    WHERE received_at < now() - make_interval(days => older_than_days);

    GET DIAGNOSTICS removed = ROW_COUNT;
    RETURN removed;
END;
$fn$;

REVOKE EXECUTE ON FUNCTION github_deliveries_prune(INTEGER) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION github_deliveries_prune(INTEGER) TO nowtask_app;

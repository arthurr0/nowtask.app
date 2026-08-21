CREATE FUNCTION invite_lookup(p_token_hash TEXT)
RETURNS TABLE (
    id                UUID,
    organization_id   UUID,
    email             TEXT,
    role_id           UUID,
    state             TEXT,
    invited_by        UUID,
    created_at        TIMESTAMPTZ,
    expires_at        TIMESTAMPTZ,
    new_requested_at  TIMESTAMPTZ,
    role_code         TEXT,
    role_name         TEXT,
    invited_by_name   TEXT,
    organization_name TEXT,
    organization_slug TEXT,
    sso_domain        TEXT)
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT i.id, i.organization_id, i.email, i.role_id, i.state, i.invited_by,
           i.created_at, i.expires_at, i.new_requested_at,
           r.code, r.name, u.name, o.name, o.slug, o.sso_domain
    FROM organization_invite i
    JOIN organization_role r ON r.id = i.role_id
    JOIN app_user u          ON u.id = i.invited_by
    JOIN organization o      ON o.id = i.organization_id
    WHERE i.token_hash = p_token_hash
$$;

CREATE FUNCTION organization_by_sso_domain(p_domain TEXT)
RETURNS TABLE (id UUID, name TEXT, slug TEXT)
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT o.id, o.name, o.slug
    FROM organization o
    WHERE o.state = 'active' AND lower(o.sso_domain) = lower(p_domain)
$$;

CREATE FUNCTION organization_slug_taken(p_slug TEXT)
RETURNS BOOLEAN
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (SELECT 1 FROM organization WHERE lower(slug) = lower(p_slug))
$$;

REVOKE EXECUTE ON FUNCTION invite_lookup(TEXT) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION organization_by_sso_domain(TEXT) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION organization_slug_taken(TEXT) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION invite_lookup(TEXT) TO nowtask_app;
GRANT EXECUTE ON FUNCTION organization_by_sso_domain(TEXT) TO nowtask_app;
GRANT EXECUTE ON FUNCTION organization_slug_taken(TEXT) TO nowtask_app;

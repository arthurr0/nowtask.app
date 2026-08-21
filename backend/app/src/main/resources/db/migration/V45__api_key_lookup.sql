CREATE FUNCTION api_key_lookup(lookup_prefix TEXT)
RETURNS TABLE (
    id              UUID,
    organization_id UUID,
    role_id         UUID,
    prefix          TEXT,
    label           TEXT,
    token_hash      TEXT,
    scopes          TEXT,
    owner_id        UUID,
    last_used_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ,
    revoked_at      TIMESTAMPTZ)
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT k.id, k.organization_id, k.role_id, k.prefix, k.label, k.token_hash, k.scopes,
           k.owner_id, k.last_used_at, k.created_at, k.expires_at, k.revoked_at
    FROM api_key k
    WHERE k.prefix = lookup_prefix
$$;

REVOKE EXECUTE ON FUNCTION api_key_lookup(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION api_key_lookup(TEXT) TO nowtask_app;

CREATE FUNCTION api_key_touch(key_id UUID, used_at TIMESTAMPTZ)
RETURNS VOID
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    UPDATE api_key SET last_used_at = used_at WHERE id = key_id
$$;

REVOKE EXECUTE ON FUNCTION api_key_touch(UUID, TIMESTAMPTZ) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION api_key_touch(UUID, TIMESTAMPTZ) TO nowtask_app;

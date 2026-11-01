CREATE FUNCTION instance_organization_counts()
RETURNS TABLE (organizations BIGINT, organizations_suspended BIGINT, tasks BIGINT)
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT count(*) FILTER (WHERE o.state IN ('active', 'suspended')),
           count(*) FILTER (WHERE o.state = 'suspended'),
           (SELECT count(*)
            FROM task t
            JOIN organization lo ON lo.id = t.organization_id
            WHERE lo.state IN ('active', 'suspended'))
    FROM organization o
$$;

CREATE FUNCTION instance_organization_page(
    p_id      UUID,
    p_pattern TEXT,
    p_state   TEXT,
    p_limit   INTEGER,
    p_offset  INTEGER)
RETURNS TABLE (
    id            UUID,
    name          TEXT,
    slug          TEXT,
    state         TEXT,
    sso_domain    TEXT,
    member_count  BIGINT,
    task_count    BIGINT,
    created_at    TIMESTAMPTZ,
    suspended_at  TIMESTAMPTZ,
    total         BIGINT)
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    WITH filtered AS (
        SELECT o.id, o.name, o.slug, o.state, o.sso_domain, o.created_at, o.suspended_at
        FROM organization o
        WHERE o.state IN ('active', 'suspended')
          AND (p_id IS NULL OR o.id = p_id)
          AND (p_state IS NULL OR o.state = p_state)
          AND (p_pattern IS NULL OR o.name ILIKE p_pattern OR o.slug ILIKE p_pattern)
    ),
    counted AS (
        SELECT count(*) AS n FROM filtered
    ),
    paged AS (
        SELECT * FROM filtered ORDER BY created_at DESC, id LIMIT p_limit OFFSET p_offset
    )
    SELECT p.id, p.name, p.slug, p.state, p.sso_domain,
           (SELECT count(*) FROM organization_member m
            WHERE m.organization_id = p.id AND m.state = 'active'),
           (SELECT count(*) FROM task t WHERE t.organization_id = p.id),
           p.created_at, p.suspended_at, c.n
    FROM counted c
    LEFT JOIN paged p ON TRUE
    ORDER BY p.created_at DESC NULLS LAST, p.id
$$;

CREATE FUNCTION instance_organization_set_state(p_id UUID, p_state TEXT)
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    affected INTEGER;
BEGIN
    IF p_state NOT IN ('active', 'suspended') THEN
        RAISE EXCEPTION 'unsupported organization state %', p_state;
    END IF;

    UPDATE organization
    SET state = p_state,
        suspended_at = CASE WHEN p_state = 'suspended' THEN coalesce(suspended_at, now()) ELSE NULL END
    WHERE id = p_id AND state IN ('active', 'suspended');

    GET DIAGNOSTICS affected = ROW_COUNT;
    RETURN affected;
END;
$$;

CREATE FUNCTION instance_organization_close(p_id UUID)
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    affected INTEGER;
BEGIN
    UPDATE organization
    SET state = 'deleted', deleted_at = now()
    WHERE id = p_id AND state IN ('active', 'suspended');

    GET DIAGNOSTICS affected = ROW_COUNT;

    IF affected > 0 THEN
        DELETE FROM api_key WHERE organization_id = p_id;
    END IF;

    RETURN affected;
END;
$$;

CREATE FUNCTION instance_user_page(
    p_id      UUID,
    p_pattern TEXT,
    p_state   TEXT,
    p_admin   BOOLEAN,
    p_limit   INTEGER,
    p_offset  INTEGER)
RETURNS TABLE (
    id                 UUID,
    name               TEXT,
    email              TEXT,
    initials           TEXT,
    state              TEXT,
    instance_admin     BOOLEAN,
    email_verified     BOOLEAN,
    organization_count BIGINT,
    last_seen_at       TIMESTAMPTZ,
    total              BIGINT)
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    WITH filtered AS (
        SELECT u.id, u.name, u.email, u.initials, u.state, u.instance_admin,
               u.email_verified_at IS NOT NULL AS email_verified, u.created_at
        FROM app_user u
        WHERE u.state IN ('active', 'disabled')
          AND (p_id IS NULL OR u.id = p_id)
          AND (p_state IS NULL OR u.state = p_state)
          AND (p_admin IS NULL OR u.instance_admin = p_admin)
          AND (p_pattern IS NULL OR u.name ILIKE p_pattern OR u.email ILIKE p_pattern)
    ),
    counted AS (
        SELECT count(*) AS n FROM filtered
    ),
    paged AS (
        SELECT * FROM filtered ORDER BY created_at DESC, id LIMIT p_limit OFFSET p_offset
    )
    SELECT p.id, p.name, p.email, p.initials, p.state, p.instance_admin, p.email_verified,
           (SELECT count(*)
            FROM organization_member m
            JOIN organization o ON o.id = m.organization_id
            WHERE m.user_id = p.id AND m.state = 'active' AND o.state IN ('active', 'suspended')),
           greatest(
               (SELECT max(s.last_seen_at) FROM user_session s WHERE s.user_id = p.id),
               (SELECT max(m.last_seen_at) FROM organization_member m WHERE m.user_id = p.id)),
           c.n
    FROM counted c
    LEFT JOIN paged p ON TRUE
    ORDER BY p.created_at DESC NULLS LAST, p.id
$$;

REVOKE EXECUTE ON FUNCTION instance_organization_counts() FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION instance_organization_page(UUID, TEXT, TEXT, INTEGER, INTEGER) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION instance_organization_set_state(UUID, TEXT) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION instance_organization_close(UUID) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION instance_user_page(UUID, TEXT, TEXT, BOOLEAN, INTEGER, INTEGER) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION instance_organization_counts() TO nowtask_app;
GRANT EXECUTE ON FUNCTION instance_organization_page(UUID, TEXT, TEXT, INTEGER, INTEGER) TO nowtask_app;
GRANT EXECUTE ON FUNCTION instance_organization_set_state(UUID, TEXT) TO nowtask_app;
GRANT EXECUTE ON FUNCTION instance_organization_close(UUID) TO nowtask_app;
GRANT EXECUTE ON FUNCTION instance_user_page(UUID, TEXT, TEXT, BOOLEAN, INTEGER, INTEGER) TO nowtask_app;

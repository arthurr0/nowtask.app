ALTER TABLE organization_invite ADD COLUMN locale TEXT;

DROP FUNCTION invites_due_for_reminder(INTEGER);

CREATE FUNCTION invites_due_for_reminder(p_after_days INTEGER)
RETURNS TABLE (
    id                UUID,
    organization_id   UUID,
    email             TEXT,
    expires_at        TIMESTAMPTZ,
    role_name         TEXT,
    invited_by_name   TEXT,
    organization_name TEXT,
    locale            TEXT)
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT i.id, i.organization_id, i.email, i.expires_at, r.name, u.name, o.name, i.locale
    FROM organization_invite i
    JOIN organization_role r ON r.id = i.role_id
    JOIN app_user u          ON u.id = i.invited_by
    JOIN organization o      ON o.id = i.organization_id
    WHERE i.state = 'open'
      AND i.reminder_sent_at IS NULL
      AND i.expires_at > now()
      AND i.created_at < now() - make_interval(days => p_after_days)
    ORDER BY i.created_at
    LIMIT 200
$$;

REVOKE EXECUTE ON FUNCTION invites_due_for_reminder(INTEGER) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION invites_due_for_reminder(INTEGER) TO nowtask_app;

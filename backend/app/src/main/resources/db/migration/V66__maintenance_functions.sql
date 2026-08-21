CREATE FUNCTION invites_due_for_reminder(p_after_days INTEGER)
RETURNS TABLE (
    id                UUID,
    organization_id   UUID,
    email             TEXT,
    expires_at        TIMESTAMPTZ,
    role_name         TEXT,
    invited_by_name   TEXT,
    organization_name TEXT)
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT i.id, i.organization_id, i.email, i.expires_at, r.name, u.name, o.name
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

CREATE FUNCTION dismiss_stale_onboarding(p_days INTEGER)
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    affected INTEGER;
BEGIN
    UPDATE onboarding_progress
    SET dismissed_at = now(), updated_at = now()
    WHERE dismissed_at IS NULL
      AND completed_at IS NULL
      AND created_at < now() - make_interval(days => p_days);

    GET DIAGNOSTICS affected = ROW_COUNT;
    RETURN affected;
END;
$$;

CREATE FUNCTION expire_overdue_invites()
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    affected INTEGER;
BEGIN
    UPDATE organization_invite SET state = 'expired'
    WHERE state = 'open' AND expires_at < now();

    GET DIAGNOSTICS affected = ROW_COUNT;
    RETURN affected;
END;
$$;

REVOKE EXECUTE ON FUNCTION invites_due_for_reminder(INTEGER) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION dismiss_stale_onboarding(INTEGER) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION expire_overdue_invites() FROM PUBLIC;

GRANT EXECUTE ON FUNCTION invites_due_for_reminder(INTEGER) TO nowtask_app;
GRANT EXECUTE ON FUNCTION dismiss_stale_onboarding(INTEGER) TO nowtask_app;
GRANT EXECUTE ON FUNCTION expire_overdue_invites() TO nowtask_app;

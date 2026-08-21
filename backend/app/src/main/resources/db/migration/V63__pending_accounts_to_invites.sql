INSERT INTO organization_invite (id, organization_id, email, role_id, token_hash, state, invited_by, created_at, expires_at)
SELECT gen_random_uuid(),
       m.organization_id,
       u.email,
       m.role_id,
       encode(sha256((u.id::TEXT || ':migrated')::BYTEA), 'hex'),
       'open',
       COALESCE(o.created_by, u.id),
       COALESCE(u.invited_on::TIMESTAMPTZ, u.created_at),
       now() + INTERVAL '14 days'
FROM app_user u
JOIN organization_member m ON m.user_id = u.id
JOIN organization o        ON o.id = m.organization_id
WHERE u.pending = TRUE
  AND u.password_hash IS NULL;

DELETE FROM organization_member m
USING app_user u
WHERE m.user_id = u.id
  AND u.pending = TRUE
  AND u.password_hash IS NULL;

DELETE FROM app_user u
WHERE u.pending = TRUE
  AND u.password_hash IS NULL
  AND NOT EXISTS (SELECT 1 FROM organization_member m WHERE m.user_id = u.id);

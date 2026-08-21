CREATE TABLE organization (
    id                  UUID        PRIMARY KEY,
    name                TEXT        NOT NULL,
    slug                TEXT        NOT NULL UNIQUE,
    sso_domain          TEXT        UNIQUE,
    default_preset_code TEXT        NOT NULL DEFAULT 'kanban',
    state               TEXT        NOT NULL DEFAULT 'active',
    created_by          UUID        REFERENCES app_user (id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ,
    CONSTRAINT organization_state_check CHECK (state IN ('active', 'suspended', 'deleted')),
    CONSTRAINT organization_slug_check  CHECK (slug ~ '^[a-z0-9][a-z0-9-]{1,38}[a-z0-9]$')
);

CREATE TABLE organization_role (
    id              UUID        PRIMARY KEY,
    organization_id UUID        NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
    code            TEXT        NOT NULL,
    name            TEXT        NOT NULL,
    position        INTEGER     NOT NULL DEFAULT 0,
    protected       BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, code),
    CONSTRAINT organization_role_code_check CHECK (code ~ '^[a-z0-9][a-z0-9_-]{0,38}$')
);

CREATE INDEX idx_role_org ON organization_role (organization_id, position);

CREATE TABLE organization_role_permission (
    role_id    UUID NOT NULL REFERENCES organization_role (id) ON DELETE CASCADE,
    permission TEXT NOT NULL,
    PRIMARY KEY (role_id, permission)
);

CREATE TABLE organization_member (
    id              UUID        PRIMARY KEY,
    organization_id UUID        NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
    user_id         UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    role_id         UUID        NOT NULL REFERENCES organization_role (id) ON DELETE RESTRICT,
    capacity        INTEGER     NOT NULL DEFAULT 0,
    state           TEXT        NOT NULL DEFAULT 'active',
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at    TIMESTAMPTZ,
    UNIQUE (organization_id, user_id),
    CONSTRAINT organization_member_state_check CHECK (state IN ('active', 'disabled'))
);

CREATE INDEX idx_member_user ON organization_member (user_id, state);
CREATE INDEX idx_member_org  ON organization_member (organization_id, state);

INSERT INTO organization (id, name, slug, default_preset_code, created_by, created_at)
SELECT '00000000-0000-0000-0000-000000000042',
       'nowtask',
       'nowtask',
       'kanban',
       (SELECT id FROM app_user ORDER BY created_at, id LIMIT 1),
       (SELECT min(created_at) FROM app_user)
WHERE EXISTS (SELECT 1 FROM app_user);

INSERT INTO organization_role (id, organization_id, code, name, position, protected)
SELECT md5('role:00000000-0000-0000-0000-000000000042:' || r.code)::UUID,
       '00000000-0000-0000-0000-000000000042',
       r.code, r.name, r.position, r.protected
FROM (VALUES
    ('admin',   'Administrator', 0, TRUE),
    ('manager', 'Manager',       1, FALSE),
    ('member',  'Member',        2, FALSE),
    ('guest',   'Guest',         3, FALSE)
) AS r(code, name, position, protected)
WHERE EXISTS (SELECT 1 FROM organization WHERE id = '00000000-0000-0000-0000-000000000042');

INSERT INTO organization_role_permission (role_id, permission)
SELECT r.id, p
FROM organization_role r
CROSS JOIN unnest(ARRAY[
    'tasks.create_edit', 'tasks.comment', 'tasks.delete', 'tasks.status_outside_flow',
    'fields.manage', 'fields.view_protected', 'automations.manage', 'automations.run',
    'members.invite', 'members.manage', 'roles.manage', 'projects.manage', 'settings.manage',
    'data.export', 'apikeys.manage', 'integrations.manage', 'audit.read', 'org.manage']) AS p
WHERE r.id = md5('role:00000000-0000-0000-0000-000000000042:admin')::UUID;

INSERT INTO organization_role_permission (role_id, permission)
SELECT r.id, p
FROM organization_role r
CROSS JOIN unnest(ARRAY[
    'tasks.create_edit', 'tasks.comment', 'tasks.delete', 'tasks.status_outside_flow',
    'fields.manage', 'fields.view_protected', 'automations.manage', 'automations.run',
    'members.invite', 'members.manage', 'projects.manage', 'settings.manage',
    'data.export', 'integrations.manage', 'audit.read']) AS p
WHERE r.id = md5('role:00000000-0000-0000-0000-000000000042:manager')::UUID;

INSERT INTO organization_role_permission (role_id, permission)
SELECT r.id, p
FROM organization_role r
CROSS JOIN unnest(ARRAY['tasks.create_edit', 'tasks.comment', 'automations.run']) AS p
WHERE r.id = md5('role:00000000-0000-0000-0000-000000000042:member')::UUID;

INSERT INTO organization_role_permission (role_id, permission)
SELECT r.id, p
FROM organization_role r
CROSS JOIN unnest(ARRAY['tasks.comment']) AS p
WHERE r.id = md5('role:00000000-0000-0000-0000-000000000042:guest')::UUID;

INSERT INTO organization_member (id, organization_id, user_id, role_id, capacity, state, joined_at)
SELECT gen_random_uuid(),
       '00000000-0000-0000-0000-000000000042',
       u.id,
       r.id,
       u.capacity,
       CASE WHEN u.pending THEN 'disabled' ELSE 'active' END,
       u.created_at
FROM app_user u
JOIN organization_role r
  ON r.organization_id = '00000000-0000-0000-0000-000000000042'
 AND r.code = u.role;

ALTER TABLE custom_field ADD COLUMN required_permission TEXT;

UPDATE custom_field SET required_permission = 'fields.view_protected'
WHERE restricted_to_role IS NOT NULL;

ALTER TABLE api_key ADD COLUMN role_id UUID REFERENCES organization_role (id) ON DELETE RESTRICT;

UPDATE api_key
SET role_id = md5('role:00000000-0000-0000-0000-000000000042:admin')::UUID;

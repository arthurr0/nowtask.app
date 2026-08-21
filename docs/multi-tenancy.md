# nowtask multi-tenancy

A design document. It describes moving from one company ("nowtask") to many independent
organizations in a single installation. Binding for the `identity`, `workspace`, `tasks`,
`automation`, `analytics`, `integrations`, `exports` and `app` modules.

Migration ranges for this area: **V40 to V49**, plus one step in `V64` explained in point 6. The
`V3` to `V39` ranges belong to `docs/api-contract.md`. `V3` to `V6` already exist in the repository.

---

## 1. Starting state

Facts read from the code, not assumptions:

- `app_user.role` is global (`admin|manager|member|guest`, the `app.nowtask.shared.RoleId` enum).
  The role does not depend on context, so an administrator is an administrator everywhere.
- `app_user.email` has a `UNIQUE` with no scope at all.
- `project.code` has a global `UNIQUE`. `task.task_key` has a global `UNIQUE`.
- `saved_view.code` has a global `UNIQUE` (`V5` dropped `NOT NULL`, kept `UNIQUE`).
- `workspace_settings` is a single row with the fixed identifier
  `00000000-0000-0000-0000-000000000001`, and `WorkspaceConfigService.updateSettings` does
  `UPDATE workspace_settings SET column = ?` **without a `WHERE`**.
- `custom_field.project_id` and `automation_rule.project_id` are nullable and `NULL` means
  "the whole workspace". Once organizations exist, `NULL` stops having an unambiguous meaning,
  because there will be many workspaces.
- The data access layer is mixed: JPA (`TaskRepository`, `AppUserRepository`, the `automation`
  repositories) and raw SQL through `JdbcClient` (`WorkspaceService`, `WorkspaceConfigService`,
  `SavedViewQueries`, `MetricsService`, `UserDirectoryService`, `AdminController`). That settles the
  choice of isolation mechanism, see point 4.
- `spring.jpa.open-in-view: false`, `ddl-auto: validate`. Migrations run through Flyway on the same
  database account as the application (`NOWTASK_DB_USER`).
- Frontend: `WorkspaceStore` (`frontend/src/app/data/workspace.store.ts`) holds one
  `bootstrapSignal`, has the guard `if (this.ready() && !force) return;` and **has no `clear()`
  method**. `AuthService.logout()` clears no data store.

---

## 2. Data model

### 2.1 Organization

```sql
CREATE TABLE organization (
    id                   UUID PRIMARY KEY,
    name                 TEXT        NOT NULL,
    slug                 TEXT        NOT NULL UNIQUE,
    sso_domain           TEXT UNIQUE,
    default_preset_code  TEXT        NOT NULL DEFAULT 'kanban',
    state                TEXT        NOT NULL DEFAULT 'active',
    created_by           UUID        NOT NULL REFERENCES app_user (id),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    suspended_at         TIMESTAMPTZ,
    CONSTRAINT organization_state_check CHECK (state IN ('active', 'suspended')),
    CONSTRAINT organization_slug_check  CHECK (slug ~ '^[a-z0-9]([a-z0-9-]{1,38}[a-z0-9])$')
);
```

`slug` is used for invitation addresses and for confirming organization deletion. We do not use it
as a subdomain, see point 5.

`sso_domain` is the company mail domain (`nowtask.app`). If it is set and the identity provider
confirms `email_verified`, an account signing in through OIDC gets membership in that organization
automatically. The column is `UNIQUE`, because one domain cannot point at two companies.

### 2.2 Membership with a role defined by the organization

The role moves from `app_user` to the membership, and the role itself stops being a constant in the
code. Every organization defines its own roles, because a five person agency and a company with
three departments do not share one division of labour. This is the change with no way back without
data loss, because `app_user.role` disappears.

```sql
CREATE TABLE organization_role (
    id              UUID        PRIMARY KEY,
    organization_id UUID        NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
    code            TEXT        NOT NULL,
    name            TEXT        NOT NULL,
    position        INTEGER     NOT NULL DEFAULT 0,
    protected       BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, code)
);

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
CREATE INDEX idx_role_org    ON organization_role (organization_id, position);
```

`ON DELETE RESTRICT` on `role_id` is deliberate: a role still held by somebody cannot be deleted,
the members have to be moved off it first. That is a clearer error than silently demoting people.

Consequences:

- **`RoleId` disappears from `shared`.** So do `CHECK (role IN (...))` on the columns, `hasRole(...)`
  in `SecurityConfig` and `RoleId.seesProtectedFields()`. What replaces them is point 2.2.1.
- `capacity` (today `app_user.capacity`) is a team quantity, so it moves to the membership.
  `UserDto.capacity` in the API does not change shape, the source changes.
- `UserDto.role` stops being one of four fixed strings and becomes `{ id, code, name }`, because the
  frontend can no longer translate a role through a dictionary. The name comes from the
  organization, so it is not translatable and is displayed as entered.
- `AppUserDetailsService.loadUserByUsername` used to grant `roles(user.getRole().name())`. After the
  change, authentication does not know the organization yet, so **authorities disappear from
  authentication** and are rebuilt per request, see point 5.3.
- An account without any active membership is a valid state (a fresh signup, removal from the last
  organization). Such an account signs in and sees only the "create an organization or ask for an
  invitation" screen.

### 2.2.1 The permission catalog

Roles are data, permissions are not. The catalog is a fixed enum in `shared`, because every one of
its entries corresponds to a real check in the code, and a permission nobody checks is a lie told to
the administrator. Adding a permission means a code change, defining a role does not.

The starting point is `Permissions.ALL` from `identity`, today a static matrix that
`GET /api/organization/permissions` only displays and nothing enforces. Those eleven entries become real
permissions, plus seven that enforcement needs and the matrix never had:

| Permission | Replaces / guards |
| --- | --- |
| `tasks.create_edit` | `perm.createEdit` |
| `tasks.comment` | `perm.comment` |
| `tasks.delete` | `perm.deleteTasks`, `POST /api/tasks/bulk/delete` |
| `tasks.status_outside_flow` | `perm.statusOutsideFlow` |
| `fields.manage` | `perm.manageFields` |
| `fields.view_protected` | `perm.viewProtected`, `TaskService.visibleCustomFields` |
| `automations.manage` | `perm.manageAutomations` |
| `automations.run` | `perm.runRules` |
| `members.invite` | `perm.invite` |
| `data.export` | `perm.export`, the whole `exports` module |
| `apikeys.manage` | `perm.apiKeys`, `ApiKeyController` |
| `members.manage` | editing and removing members, changing their role |
| `roles.manage` | creating, editing and deleting the organization roles |
| `projects.manage` | projects, statuses, transitions, epics, milestones |
| `settings.manage` | `workspace_settings` |
| `integrations.manage` | `/api/integrations/**` |
| `audit.read` | `GET /api/organization/audit` |
| `org.manage` | renaming and deleting the organization, `sso_domain` |

**The `conditional` value disappears.** Today the matrix gives `manager` a `conditional` on
`perm.statusOutsideFlow` and `perm.invite`, which corresponds to nothing in the code, because
nothing checks either. A permission is held or it is not. The nuance that `conditional` was trying
to express belongs to `status_transition.requirement`, which already exists and works.

**The last administrator invariant** stops being "at least one account with `role = admin`", because
`admin` no longer exists as a concept. It becomes: **an organization must always have at least one
active member whose role grants both `members.manage` and `roles.manage`.** Checked in three places,
each of which can break it: removing a member, changing a member's role, and taking a permission
away from a role. `MemberService.adminCount()` is replaced by that query.

### 2.2.2 Role templates on organization creation

An organization created with no roles is unusable, and forcing whoever creates it to define a
permission model before their first task is a bad first five minutes. `V40` therefore seeds four
roles into every organization, reproducing exactly what the matrix grants today:

| Code | Permissions | `protected` |
| --- | --- | --- |
| `admin` | all 18 | yes |
| `manager` | everything except `apikeys.manage`, `roles.manage`, `org.manage` | no |
| `member` | `tasks.create_edit`, `tasks.comment`, `automations.run` | no |
| `guest` | `tasks.comment` | no |

`protected` means the role cannot be deleted and cannot have `members.manage` or `roles.manage`
taken away. It is a second net under the invariant above, so that an organization cannot be locked
out by editing every role. Everything else, the name, the remaining permissions, the position, is
editable, and any number of further roles can be added.

The codes are unique per organization, not globally, so one company can rename `manager` to
`Team lead` and another can delete it entirely.

### 2.3 Global identity, not per company

One account per email address across the whole installation. `app_user.email UNIQUE` stays.

Rejected: a separate account per organization (`UNIQUE (organization_id, email)`). It would force a
separate password and a separate session per company, would break the organization switcher into a
sign out and sign in, and with corporate login would require picking an organization before
authenticating. The price of going global: an organization administrator cannot change the name or
password of a person who also belongs to another company. We accept that, `organization_member`
controls only the role, the state and the allocation.

### 2.4 The `organization_id` column in domain tables

24 tables get it. `app_user` does not, because the account is global.

| Table | Scope today | After the change |
| --- | --- | --- |
| `team` | none | `organization_id NOT NULL` |
| `team_member` | through `team` | `organization_id NOT NULL` (denormalized for RLS) |
| `project` | none | `organization_id NOT NULL`, `UNIQUE (organization_id, code)` |
| `status_def` | `project_id` | `organization_id NOT NULL` |
| `status_transition` | through `status_def` | `organization_id NOT NULL` |
| `epic` | `project_id` | `organization_id NOT NULL` |
| `task` | `project_id` | `organization_id NOT NULL`, `UNIQUE (organization_id, task_key)` |
| `task_label` | through `task` | `organization_id NOT NULL` |
| `subtask` | through `task` | `organization_id NOT NULL` |
| `task_relation` | through `task` | `organization_id NOT NULL` |
| `task_comment` | through `task` | `organization_id NOT NULL` |
| `task_history` | through `task` | `organization_id NOT NULL` |
| `task_watcher` | through `task` | `organization_id NOT NULL` |
| `task_key_sequence` | `project_id` | `organization_id NOT NULL` |
| `automation_rule` | `project_id` (NULL = all) | `organization_id NOT NULL`, `project_id` still nullable |
| `automation_run` | through `automation_rule` | `organization_id NOT NULL` |
| `custom_field` | `project_id` (NULL = all) | `organization_id NOT NULL`, `UNIQUE (organization_id, project_id, field_key)` with `NULLS NOT DISTINCT` |
| `saved_view` | none | `organization_id NOT NULL`, `UNIQUE (organization_id, code)` |
| `api_key` | none | `organization_id NOT NULL`, `owner_id` has to have membership in that organization |
| `milestone` | `project_id` | `organization_id NOT NULL` |
| `burndown_point` | `project_id` | `organization_id NOT NULL` |
| `throughput_week` | `project_id` | `organization_id NOT NULL` |
| `workspace_settings` | a single row | `organization_id NOT NULL UNIQUE`, one row per organization |
| `audit_event` (`V31`) | none | `organization_id NOT NULL` |

**The list is moving.** Agents working in the `V3` to `V39` range keep adding tables. While this
document was being written, `V30__api_key_auth.sql` (six columns on `api_key`, including `owner_id`
and `token_hash`) and `V31__audit_event.sql` (the `audit_event` table, required by
`GET /api/organization/audit` from `docs/api-contract.md`) arrived. Before `V41` is written the tables in
the database have to be recounted. That is exactly what `TenantSchemaTest` from point 4.6 is for: a
table created after this document that did not get an `organization_id` fails the build instead of
being silently skipped.

`api_key.token_hash` has a unique index with no scope and it stays that way. An API key is random,
so a collision between companies is impossible, and global uniqueness lets a key be recognized
without knowing the organization, which is needed because it is the key that determines the
organization (point 5.1). `api_key.owner_id` points at `app_user`, so a rule is added: the key owner
has to have an active membership in the key's organization, and removing the membership invalidates
the key (`revoked_at`).

`audit_event.api_key_id` and `actor_id` refer to tables with different scopes (`api_key` is
organizational, `app_user` global). The RLS policy on `audit_event` relies solely on its own
`organization_id`.

Denormalization in child tables (`subtask`, `task_label`, `automation_run`, ...) is deliberate.
Without it the RLS policy would have to do an `EXISTS` to the parent for every row, which costs on
lists and exports, and additionally creates a dependency of one policy on the parent's policy.
Consistency is enforced by the trigger described in point 4.4.

`custom_field` with `NULLS NOT DISTINCT` in the unique key requires PostgreSQL 15 or newer. We have
18, so it is available. Without it two fields with the same key and `project_id IS NULL` would pass
validation.

### 2.5 Tables without `organization_id`

`app_user` (a global account), `organization`, `organization_member`, `organization_invite`,
`email_verification`, `flyway_schema_history`.

`organization` and `organization_member` have their own policies based on user identity, not on the
active organization, because they have to be read before an organization is selected.

---

## 3. Choosing the isolation strategy

**I choose an `organization_id` column in a shared schema, reinforced with RLS policies in
PostgreSQL.**

### Why not separate schemas

One schema per organization (`org_a.task`, `org_b.task`) gives neat isolation, but:

- Flyway would have to run every migration N times, once per schema, in one transaction or with
  separate history tracking per schema. With 500 organizations and a migration changing `task` we
  get 500 `ALTER TABLE`s in the application startup window. Startup stops being predictable, and a
  partial failure leaves schemas on different versions.
- Creating an organization stops being a single `INSERT` and becomes a DDL operation. Onboarding
  (`docs/onboarding.md`) creates an organization inside the wizard, in one HTTP request. DDL under
  load takes locks and can get stuck on autovacuum.
- `search_path` per connection plus the HikariCP pool is a known trap: a connection returned to the
  pool with `search_path` set will serve the next request in someone else's schema. The leak risk is
  higher than with RLS, where forgetting the setting gives emptiness rather than someone else's
  data.
- Administrative queries and metrics for the whole installation require `UNION ALL` across N schemas
  or `dblink`. The system catalog swells: 25 tables times N organizations.

We come back to this only when a single customer contractually requires data separation. Then we
move their data to a separate installation, not to a separate schema.

### Why not separate databases

The best isolation and the best backup story (restoring one company to a point in time without
touching the rest), but:

- A separate connection pool per database. With 200 organizations and 5 connections per pool that is
  1000 connections to PostgreSQL, meaning a forced PgBouncer and all its baggage (transaction mode
  breaks `SET LOCAL`, prepared statements, cursors).
- Creating a database during onboarding means `CREATE DATABASE`, an operation outside a transaction,
  irreversible when the wizard fails halfway.
- Migrations: N databases times every migration, plus tracking versions of databases drifting apart.
- Cost: a managed PostgreSQL instance billed per database or per storage per database is many times
  more expensive than one database with a discriminator column.

That is the right model for a deployment in a customer's infrastructure (one installation, one
company), and that is exactly how it is served: an on-premise installation simply has one
organization.

### Why a column

- A one-off, deterministic migration, the same number of `ALTER TABLE`s regardless of the number of
  companies.
- Creating an organization is an `INSERT` in a transaction with the rest of the wizard, so
  abandoning the wizard rolls itself back.
- Backups: one database, one `pg_dump`. Restoring one company requires a selective export
  (`COPY (SELECT ... WHERE organization_id = ?) TO ...`) and that is a real downside. We cover it
  with the `exports` tool described in point 8, so that we do not discover it at the first customer
  request.
- The leak risk is the highest of the three variants and that is exactly why point 4 does not leave
  it to the developer.

**This is a decision with no easy way back.** After adding `organization_id` to 24 tables and
building RLS policies on top of it, going back to schemas or databases means rewriting the data
access layer and migrating data, not changing configuration.

---

## 4. Enforcing isolation

### 4.1 The three mechanisms considered

| Mechanism | What it covers | What it does not cover | Verdict |
| --- | --- | --- | --- |
| Hibernate filter (`@FilterDef` + `enableFilter` per session) | loading JPA entities and collections | everything through `JdbcClient`, native `@Query`, `count(*)` in subqueries, migrations, background jobs | rejected |
| A repository layer taking the organization context | whatever goes through it | every query written alongside it, and the author of new code does not know they have to | rejected as the only line of defense |
| RLS policies in PostgreSQL | every query from the application connection, regardless of the layer | queries from the table owner account (Flyway) | **chosen** |

The Hibernate filter is out not for theoretical reasons, but because in this repository most reads
go through raw SQL. `WorkspaceService`, `WorkspaceConfigService`, `SavedViewQueries`,
`MetricsService`, `UserDirectoryService.findTeams` and `AdminController.apiKeys` use `JdbcClient`. A
Hibernate filter will not see any of them.

The repository layer stays, but as a convenience and a second level, not as a guarantee. The
guarantee is the database.

The best illustration: `WorkspaceConfigService.updateSettings` runs
`UPDATE workspace_settings SET date_format = ?` without a `WHERE`. Under a Hibernate filter that
query would change the settings of every company in the installation. Under RLS it changes exactly
one row, the one belonging to the active organization, and that code needs no fixing.

### 4.2 Database accounts

Two accounts, because a table owner bypasses RLS.

| Account | Role | Permissions |
| --- | --- | --- |
| `nowtask` | schema owner, used by Flyway | DDL, DML, bypasses RLS by virtue of ownership |
| `nowtask_app` | used by the application pool | `SELECT/INSERT/UPDATE/DELETE` on tables, `USAGE` on the schema, **no** `BYPASSRLS`, no `SUPERUSER`, no table ownership |

We deliberately do **not** use `FORCE ROW LEVEL SECURITY`. If we did, the policies would cover the
owner too, and then migration `V42` (filling in `organization_id`) could not update a single row,
because at migration time `app.organization_id` is unset and the policy cuts everything off.

The deployment change: `spring.flyway.user` and `spring.flyway.password` get the `nowtask`
credentials, `spring.datasource` gets `nowtask_app`. `docker-compose.yml` gains the
`NOWTASK_APP_DB_USER` and `NOWTASK_APP_DB_PASSWORD` variables.

### 4.3 Setting the context

Two session variables, both set locally for the transaction:

- `app.user_id`, the identifier of the signed-in account, always set when there is a session,
- `app.organization_id`, the active organization, set when one is selected.

They are set by `OrganizationContextTransactionListener` (the `app` module), hooked in as a
`TransactionSynchronization` at the start of every transaction:

```java
jdbc.sql("SELECT set_config('app.user_id', ?, true), set_config('app.organization_id', ?, true)")
    .params(userId == null ? "" : userId.toString(), orgId == null ? "" : orgId.toString())
    .query(String.class)
    .list();
```

The third argument `true` means "local to the transaction", so PostgreSQL restores the value itself
on `COMMIT` and `ROLLBACK`. A connection going back to the HikariCP pool carries no context.

`spring.jpa.open-in-view` is already off and all services are `@Transactional`, so there are no
reads outside a transaction. A controller that runs `JdbcClient` without a transaction gets
autocommit, an empty context and zero rows. That is meant to be a loud failure, not a silent one.

### 4.4 The shape of the policies

Domain tables (the pattern for all 23):

```sql
ALTER TABLE task ADD COLUMN organization_id UUID NOT NULL
    DEFAULT nullif(current_setting('app.organization_id', true), '')::UUID;
ALTER TABLE task ADD CONSTRAINT task_org_fk
    FOREIGN KEY (organization_id) REFERENCES organization (id) ON DELETE CASCADE;
ALTER TABLE task ENABLE ROW LEVEL SECURITY;

CREATE POLICY task_tenant ON task
    USING      (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID)
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID);
```

Three things at once:

- `USING` cuts off reads of other companies' rows,
- `WITH CHECK` cuts off writes of other companies' rows, including
  `UPDATE ... SET organization_id = <someone else's>`,
- `DEFAULT` makes an `INSERT` that forgot the column get the right value anyway. Without it every
  existing `INSERT` in the repository would have to be fixed by hand.

`nullif(..., '')` is necessary: `current_setting('x', true)` returns `NULL` when the setting is
missing, but after `set_config(..., '', true)` it returns an empty string, and `''::UUID` raises an
error. With this construct a missing context gives `NULL = NULL`, that is `NULL`, that is a rejected
row. The default behavior is closed.

Denormalization consistency is enforced by a single trigger installed once per child table:

```sql
CREATE FUNCTION assert_same_org() RETURNS TRIGGER AS $$
DECLARE
    parent_org UUID;
BEGIN
    EXECUTE format('SELECT organization_id FROM %I WHERE id = $1', TG_ARGV[0])
        INTO parent_org USING NEW.task_id;
    IF parent_org IS DISTINCT FROM NEW.organization_id THEN
        RAISE EXCEPTION 'organization_id does not match parent %', TG_ARGV[0];
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```

We install it on `subtask`, `task_label`, `task_relation`, `task_comment`, `task_history`,
`task_watcher`, `automation_run`, `team_member`. The cost is one extra primary key lookup on write.

Identity tables, keyed by the user rather than the organization:

```sql
ALTER TABLE organization ENABLE ROW LEVEL SECURITY;
CREATE POLICY organization_visible ON organization
    USING (EXISTS (
        SELECT 1 FROM organization_member m
        WHERE m.organization_id = organization.id
          AND m.user_id = nullif(current_setting('app.user_id', true), '')::UUID
          AND m.state = 'active'));

ALTER TABLE organization_member ENABLE ROW LEVEL SECURITY;
CREATE POLICY organization_member_visible ON organization_member
    USING (user_id = nullif(current_setting('app.user_id', true), '')::UUID
        OR organization_id = nullif(current_setting('app.organization_id', true), '')::UUID);
```

There is no recursion: the `organization` policy refers to `organization_member`, and the
`organization_member` policy does not refer to `organization`.

### 4.5 Three tables without RLS and the rule that guards them

`app_user`, `organization_invite` and `email_verification` have to be readable before an
organization is chosen (signing in by email address, opening an invitation link, confirming an
address), so no sensible policy based on `app.organization_id` can be written for them.

Instead:

1. Only the `identity` module has access to them. An ArchUnit test fails the build when the string
   `app_user`, `organization_invite` or `email_verification` appears in an SQL literal outside the
   `app.nowtask.identity` package.
2. `UserDirectory.findAll()` and `findActive()` stop reading `app_user` on their own and always do a
   `JOIN organization_member`. Since `organization_member` has RLS, the join is automatically
   limited to the active organization. There is no `WHERE` to remember.
3. `AppUserRepository.findAllByOrderByPendingAscNameAsc()` **disappears**. Only
   `findByEmailIgnoreCase`, used by authentication and invitation acceptance, remains.

### 4.6 How we detect a query that forgot the filter

Four independent nets, cheapest first:

**A. The schema test (`TenantSchemaTest`).** It reads `information_schema` and `pg_class`, then
checks for every table outside the allowlist of six global tables: the `organization_id` column
exists, is `NOT NULL`, has a `DEFAULT` with `current_setting`, has a foreign key to `organization`,
`relrowsecurity = true`, and exactly one policy with `USING` and `WITH CHECK` exists. A new table
added by anyone without those six things fails the build. This is the most important of these tests,
because it works on tables that do not exist yet.

**B. The leak test (`TenantLeakTest`).** It sets up two organizations with a twin data set (the same
status names, the same task titles, different identifiers), then through `MockMvc` calls every
method annotated `@GetMapping` found by `RequestMappingHandlerMapping` as a member of organization
A and checks that the response contains no identifier belonging to B. A new controller is covered by
the test without adding a case.

**C. The runtime safeguard (the `dev` and `test` profiles).** We wrap the `DataSource` in a proxy
that, before running a statement touching a table from the protected list, checks whether
`current_setting('app.organization_id', true)` is non-empty. A missing context ends in an exception
with the query name in the message, not an empty list on the screen. Disabled in production, because
there the defense is the policy itself.

**D. Background jobs.** The job computing `burndown_point` and `throughput_week` has to iterate over
organizations and set `app.organization_id` for each one separately. A job that forgets will write
nothing, because `WITH CHECK` rejects an `INSERT` with `NULL`. The failure is visible from the first
run. That is a deliberate property, not a side effect.

What these nets do not catch: a query that correctly limits itself to the organization but not to
the project. That is a separate class of bugs and it is already in the code today, see point 7.

---

## 5. The organization context in a request

### 5.1 Where it comes from

**From the server session.** After signing in, `activeOrganizationId` lands in the session. It is
chosen in this order: the most recently used organization from `organization_member.last_seen_at`,
then the only available one, then none (the user lands on the selection screen).

**The `X-Org-Id` header as a per-request override.** The frontend always sends it, filled with the
identifier of the organization the browser tab is currently showing. The rules:

- no header: the organization from the session applies,
- the header points at an organization with an active membership: it applies, **the session does not
  change**,
- the header points at anything else: `403`.

Why: two browser tabs open on two different companies stop affecting each other. Without it,
switching in one tab silently changes the data shown in the other, and the user clicks a task of
company A and modifies a task of company B.

**Subdomain: rejected.** `nowtask.nowtask.app` looks good and has one real advantage (isolation of
`localStorage` and cookies by the browser), but it costs a wildcard certificate, a wildcard DNS
entry, a separate `Redirect URI` in Keycloak per organization or one shared with a redirect, and in
a local environment it requires poking at `/etc/hosts`. We are not buying that for a mechanism the
session and a header handle entirely. The address takes the form `/board`, `/tasks/NOW-172`, without
an organization segment, and the switcher changes the content, not the URL.

**API keys.** `api_key.organization_id` is the source of context for requests authenticated with a
key. A key cannot switch organizations, `X-Org-Id` is ignored for it.

### 5.2 Switching

`POST /api/orgs/{id}/switch`:

1. checks the active membership, returns `403` when it is missing,
2. calls `request.changeSessionId()` (session fixation on a permission scope change),
3. stores `activeOrganizationId` in the session and `last_seen_at` on the membership,
4. returns the full `BootstrapDto` of the new organization so that the frontend does not make a
   second round trip.

The session is **not invalidated**, the user stays signed in. Invalidating the session on every
switch would throw all other tabs onto the login screen.

### 5.3 Role and permissions

`AppUserDetailsService` stops granting authorities, because at authentication time the organization
is unknown, and with roles defined per organization the same account can be an administrator in one
company and a guest in another. Authorities are rebuilt on every request from the active membership
and exposed through `shared`:

```java
public record OrganizationContext(
        UUID userId,
        UUID organizationId,
        UUID roleId,
        String roleCode,
        Set<Permission> permissions) {

    public boolean can(Permission permission) {
        return permissions.contains(permission);
    }

    public void require(Permission permission) {
        if (!can(permission)) {
            throw new ForbiddenException("ROLE_FORBIDDEN", permission.code());
        }
    }
}
```

`Permission` is an enum in `shared` holding the catalog from point 2.2.1. The holder
(`ThreadLocal`) also lives in `shared`, because `shared` depends on nothing and every module needs
it. Context resolution (the servlet filter, session access, setting the database variables) lives in
`app`. The organization tables and endpoints live in `identity`.

`OrganizationContextFilter` does two things per request: it fills the holder, and it replaces the
authorities on the `Authentication` with one `PERM_<code>` per permission of the active role. That
keeps declarative rules working in `SecurityConfig`, only they stop naming roles:

```java
.requestMatchers("/api/integrations/**").hasAuthority("PERM_INTEGRATIONS_MANAGE")
.requestMatchers(HttpMethod.GET, "/api/organization/audit").hasAuthority("PERM_AUDIT_READ")
.requestMatchers("/api/organization/roles/**").hasAuthority("PERM_ROLES_MANAGE")
```

The filter runs after authentication and before `AuthorizationFilter`, in the same place
`ApiKeyAuthenticationFilter` sits today. Checks that depend on the object being touched rather than
on the path stay in the services and use `context.require(...)`.

The `custom_field.restricted_to_role` column becomes `custom_field.required_permission`, holding a
permission code rather than a role name. That is what makes it survive the change: a role name is
per organization and can be deleted, a permission code is global and stable.
`TaskService.visibleCustomFields(task, viewerRole)` becomes
`visibleCustomFields(task, context)` and asks `context.can(...)` for the field's
`required_permission`. The migration maps every non-null `restricted_to_role` to
`fields.view_protected`, which reproduces today's behavior exactly, because the current code
compares nothing to the stored value and only calls `seesProtectedFields()`.

**API keys.** A key gets `api_key.role_id` alongside `organization_id`, so a key carries the
permissions of a role rather than those of its owner. Removing the owner's membership revokes the
key (point 2.4), but while it lives its permissions do not follow the owner's role changes.
`ApiKeyScope` keeps narrowing on top of that: the effective permission set is the intersection of
the role's permissions and the key's scopes.

### 5.4 No access

| Situation | Response | Frontend behavior |
| --- | --- | --- |
| No session | `401` | the existing `authInterceptor` moves to `/login` |
| A session exists, no membership at all | `409` with `code: "NO_ORGANIZATION"` | move to `/orgs/new` |
| `X-Org-Id` points at a foreign organization | `403` with `code: "ORG_FORBIDDEN"` | refresh the organization list and switch to the first available one |
| Membership revoked mid-session | `403` with `code: "ORG_ACCESS_REVOKED"` | a message, clearing the stores, reloading the list |
| Organization suspended (`state = 'suspended'`) | `403` with `code: "ORG_SUSPENDED"` | an informational screen, only signing out and switching |
| Role too low for the operation | `403` with `code: "ROLE_FORBIDDEN"` | a toast message, no reload |

The `code` field does not fit today's error shape `{ status, message, at }`. That is a change to
`docs/api-contract.md`, listed in point 9.

---

## 6. Migrating existing data

The order matters. Every step is a separate migration so that the deployment can be stopped between
them.

### V40, organization identity, roles and permissions

1. `CREATE TABLE organization`, `organization_role`, `organization_role_permission`,
   `organization_member` (the shape from point 2.2).
2. Inserting the default organization with the fixed identifier
   `00000000-0000-0000-0000-000000000042`, name `nowtask`, slug `nowtask`,
   `default_preset_code = 'kanban'`, `created_by` set to the oldest account. A fixed identifier so
   that environments stay comparable.
3. Seeding the four role templates from point 2.2.2 into that organization, with deterministic
   identifiers derived from the organization and the code, so a re-run lands on the same rows.
4. Migrating the memberships, mapping the old `app_user.role` string onto the seeded role of the
   same code:

```sql
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
```

The join is inner on purpose. `app_user.role` has a `CHECK` limiting it to the four codes, so every
row matches, and if one does not, the migration fails loudly instead of creating a member with no
role.

5. `custom_field.required_permission TEXT`, filled with `'fields.view_protected'` wherever
   `restricted_to_role IS NOT NULL`. The old column stays until `V64`.
6. `api_key.role_id`, filled with the `admin` role of the default organization, because that is what
   an unscoped key meant before this change.

`app_user.role`, `capacity`, `pending` and `invited_on` stay untouched for now. At this point the
application works exactly as it did before the migration.

**Reversible.** `DROP TABLE organization_member, organization_role_permission, organization_role,
organization`, `DROP COLUMN custom_field.required_permission, api_key.role_id`.

### V41, the columns

`ALTER TABLE ... ADD COLUMN organization_id UUID` (without `NOT NULL`, without `DEFAULT`) on all 24
tables. `organization_role`, `organization_role_permission` and `organization_member` are not on the
list: the first two carry the organization already, the third is an identity table (point 2.5). Adding a column without a default is a catalog-only change, it does not rewrite the table,
so it is fast regardless of the number of tasks.

**Reversible.** `DROP COLUMN`.

### V42, filling in

One `UPDATE` per table, all with the same constant, because there is exactly one organization in the
database:

```sql
UPDATE task SET organization_id = '00000000-0000-0000-0000-000000000042';
```

This is the longest step, because it rewrites every row. On demo data that is nothing, on a real
installation it is worth splitting into `WHERE organization_id IS NULL LIMIT` batches. Writing a
constant instead of joining with `project` is correct here only because there is one organization.
When this step has to be repeated after another company is added, it has to join through the parent.

**Reversible.** `UPDATE ... SET organization_id = NULL`.

### V43, constraints and indexes

This is where it stops being reversible.

1. `SET NOT NULL` on all 24 columns.
2. `ALTER COLUMN organization_id SET DEFAULT nullif(current_setting('app.organization_id', true), '')::UUID`.
3. Foreign keys to `organization (id) ON DELETE CASCADE`.
4. Rebuilding the unique keys. **Irreversible without losing the guarantee:**

```sql
ALTER TABLE project    DROP CONSTRAINT project_code_key;
ALTER TABLE project    ADD CONSTRAINT project_org_code_key UNIQUE (organization_id, code);
ALTER TABLE task       DROP CONSTRAINT task_task_key_key;
ALTER TABLE task       ADD CONSTRAINT task_org_key_key     UNIQUE (organization_id, task_key);
ALTER TABLE saved_view DROP CONSTRAINT saved_view_code_key;
ALTER TABLE saved_view ADD CONSTRAINT saved_view_org_code_key UNIQUE (organization_id, code);
ALTER TABLE custom_field DROP CONSTRAINT custom_field_project_id_field_key_key;
ALTER TABLE custom_field ADD CONSTRAINT custom_field_org_project_key_key
    UNIQUE NULLS NOT DISTINCT (organization_id, project_id, field_key);
ALTER TABLE workspace_settings ADD CONSTRAINT workspace_settings_org_key UNIQUE (organization_id);
ALTER TABLE task_key_sequence DROP CONSTRAINT task_key_sequence_pkey;
ALTER TABLE task_key_sequence ADD PRIMARY KEY (organization_id, project_id);
```

Once the global `UNIQUE` is dropped from `project.code` and `task.task_key`, two companies can have
a `NOW` project and a `NOW-172` task. **Going back to global uniqueness is impossible without
renumbering tasks by hand.**

5. Indexes led by the organization, because every read starts with it:

```sql
CREATE INDEX idx_task_org_status   ON task (organization_id, status_id);
CREATE INDEX idx_task_org_assignee ON task (organization_id, assignee_id);
CREATE INDEX idx_task_org_sprint   ON task (organization_id, sprint_code);
CREATE INDEX idx_task_org_due      ON task (organization_id, due_date);
CREATE INDEX idx_status_org_project ON status_def (organization_id, project_id, position);
CREATE INDEX idx_rule_org          ON automation_rule (organization_id, position);
CREATE INDEX idx_run_org_rule      ON automation_run (organization_id, rule_id, created_at DESC);
CREATE INDEX idx_comment_org_task  ON task_comment (organization_id, task_id, created_at);
CREATE INDEX idx_history_org_task  ON task_history (organization_id, task_id, created_at DESC);
```

The old `idx_task_status`, `idx_task_assignee`, `idx_task_due`, `idx_task_sprint`,
`idx_comment_task`, `idx_history_task`, `idx_run_rule` indexes are dropped, because the
`organization_id` prefix makes them redundant.

### V44, database accounts and policies

1. `CREATE ROLE nowtask_app LOGIN PASSWORD ...` (the password from the Flyway `${appPassword}`
   parameter), `GRANT USAGE ON SCHEMA public`, `GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES`,
   `ALTER DEFAULT PRIVILEGES` for future tables.
2. `ENABLE ROW LEVEL SECURITY` and a policy on each of the 24 tables plus on `organization` and
   `organization_member`.
3. The `assert_same_org` trigger on the eight child tables.

**Irreversible in practice:** from this moment the application connects with a different account and
rolling back requires changing the deployment configuration at the same time. The SQL itself can be
reverted (`DROP POLICY`, `DISABLE ROW LEVEL SECURITY`).

After this migration `spring.datasource` has to be switched to `nowtask_app`. An application started
with the old credentials will work (the owner bypasses RLS), so this is a place where a silent
mistake is easy. `TenantSchemaTest` additionally checks that `current_user <> 'nowtask'` in the
production profile.

### V64 (the onboarding range), cleaning up `app_user` and the old role column

Irreversible.

```sql
ALTER TABLE app_user DROP COLUMN role;
ALTER TABLE app_user DROP COLUMN capacity;
ALTER TABLE app_user DROP COLUMN pending;
ALTER TABLE app_user DROP COLUMN invited_on;
ALTER TABLE custom_field DROP COLUMN restricted_to_role;
```

`pending` and `invited_on` go away, because an invitation is a relation between a person and an
organization, not a property of an account. They are replaced by `organization_invite` from
`docs/onboarding.md`. `custom_field.restricted_to_role` goes away because `required_permission`
(point 5.3) replaced it in `V40` and both columns have been written since.

**Why this step is not in the V40 to V49 range.** Any account still carrying `pending = TRUE` has to
be moved into `organization_invite` before the column disappears, and that table is only created in
`V60`. Such accounts are produced by `MemberService.invite`, so an installation that has ever
invited anyone has them. Flyway runs migrations in numeric order, so `DROP COLUMN pending` has to
have a number higher than `V60`. This one step deliberately steps outside its area's range.

Run it only after deploying the `identity` changes that stop reading those columns (`AppUser`,
`AppUserDetailsService`, `UserDirectoryService.toView`) and the `workspace` change that stops
reading `restricted_to_role`.

The V45 to V49 range stays free as this area's reserve.

### Deployment order relative to the code

| Step | Migration | Code |
| --- | --- | --- |
| 1 | V40 to V42 | unchanged, the application works as it does today |
| 2 | none | deploying the organization context and the permission enum, `identity` reads the role from the membership and still writes to `app_user.role` too |
| 3 | V43, V44 | switching `spring.datasource` to `nowtask_app` |
| 4 | V60 to V63 | invitations and address verification, moving the pending account |
| 5 | none | removing the reads of `app_user.role/capacity/pending/invited_on` and of `custom_field.restricted_to_role` |
| 6 | V64 | none |

---

## 7. Impact on the backend modules

Every module listed exists in `backend/settings.gradle.kts`.

| Module | What is added | What has to be fixed | Risk |
| --- | --- | --- | --- |
| `shared` | `Permission` (the enum from point 2.2.1), `OrganizationContext` (a record plus a `ThreadLocal` holder), `ForbiddenException`, `OrganizationEvents` (`OrganizationCreated`, `MemberJoined`, `MemberRemoved`, `RoleChanged`) | **`RoleId` is deleted**, every module importing it has to move to `Permission`; `StatusCategory` unchanged | **high**, `RoleId` reaches into four modules |
| `identity` | the `Organization`, `OrganizationRole`, `OrganizationMember`, `OrganizationInvite` entities, `OrganizationService`, `RoleService`, `OrgController`, `RoleController`, extending `UserDirectory` with `currentMembership()` and `organizations()` | `AppUser` loses four fields; `AppUserDetailsService` stops granting authorities; `UserDirectoryService.findAll/findActive` join `organization_member`; `AppUserRepository.findAllByOrderByPendingAscNameAsc` disappears; **`Permissions.ALL` stops being a static matrix** and `GET /api/organization/permissions` starts returning the catalog plus the organization's roles; `MemberService.adminCount()` is replaced by the invariant query from point 2.2.1 | **high**, this is where the whole role model changes |
| `app` | `OrganizationContextFilter` (fills the holder and rebuilds the request authorities), `OrganizationContextTransactionListener`, a second data source for Flyway, `BootstrapController` returns the organization, the organization list and the caller's permissions | `SecurityConfig` drops both `hasRole("ADMIN")` rules for `hasAuthority("PERM_...")`, and lets `/api/auth/signup`, `/api/invites/**`, `/api/orgs` through when signed in without an organization | **high**, this is where authorization changes shape |
| `workspace` | `organization_id` in writes, `projectId` as a read parameter | `WorkspaceService.statuses()`, `transitions()`, `epics()`, `customFields()`, `milestones()` **do not filter by project even today**; `nextStatusPosition()` computes `MAX(position)` across the whole table; `createEpic` and `createCustomField` do the same; `updateSettings` does an `UPDATE` without a `WHERE` (only correct under RLS); `defaultProject()` throws `NotFoundException` for an organization without a project | **high**, two scope levels at once (organization and project) |
| `tasks` | `organization_id` in the entities, the task key assigned from `task_key_sequence` per organization and project, `visibleCustomFields` asks the context for `required_permission`, `tasks.delete` and `tasks.status_outside_flow` become real checks | the current sprint sits in `workspace_settings.current_sprint`, a new organization starts without one and new tasks get no sprint code until it is set; `TaskRepository.findByKey` has to hit the (organization, key) pair, because the key stops being globally unique | **high** |
| `automation` | `organization_id` on `automation_rule` and `automation_run` | `TaskEventListener` receives events from `tasks` by a text key; the key stops being globally unique, so the records in `shared.events.TaskEvents` have to get an `organizationId`; a rule engine running asynchronously has to set the context itself before writing `automation_run` | **high**, a module boundary without a dependency on `tasks` |
| `analytics` | filtering metrics by organization | `MetricsService.burndown()` and `throughput()` read whole tables without a `WHERE`; under RLS they will start returning organization data, but still not project or sprint data | medium |
| `integrations` | `organization_id` on integrations and notifications (the tables do not exist yet, they will be created in the V20 to V29 range) | an outgoing webhook must not reveal another company's identifiers; sending mail has to take the sender from the organization settings | medium |
| `exports` | export always within the organization boundary; additionally a full organization export, see point 8 | the `Content-Disposition` header should contain the organization slug so that two `tasks.csv` files from different companies can be told apart | low |

The change in `shared.events.TaskEvents` is binding for two modules at once:

```java
public record TaskStatusChanged(
        UUID organizationId,
        String taskKey,
        String fromStatusCode,
        String toStatusCode,
        UUID actorId,
        Instant at) { }
```

Likewise `TaskAssigned`, `TaskLabelAdded`, `TaskCreated`, `TaskDeleted`. Without it, `automation`
receiving an event on another thread does not know which organization to look for the task in, and
`task_key` has stopped being globally unique.

---

## 8. Backups and deleting an organization

Choosing a shared database takes away the option of restoring one company from a database backup, so
that has to be replaced with something explicit.

- **Organization export.** `GET /api/orgs/current/export` (the `admin` role) produces a ZIP archive
  with CSV files for all 24 tables, each with `WHERE organization_id = ?`, plus a `manifest.json`
  with the schema version from `flyway_schema_history`. The `exports` module. This is the answer to
  "we want our data" and to "restore us to last week's state" (a human does the restore, by
  importing into an empty organization).
- **Deleting an organization.** `DELETE /api/orgs/current` with `{ confirmSlug }` in the body. Soft
  for 30 days: `state = 'suspended'`, `suspended_at = now()`, access cut off for everyone. After 30
  days a background job runs `DELETE FROM organization WHERE ...`, and the cascades take the 24
  tables with them. **Irreversible.** Deletion requires two administrators or one administrator and
  a 24 hour delay, because one click cannot erase a company.
- The `ON DELETE CASCADE` from `organization` to 24 tables means a single `DELETE` takes everything.
  Worth remembering when writing tests, because accidentally deleting an organization in an
  integration test leaves no trace.

---

## 9. Impact on the frontend

### 9.1 The organization switcher

The spot is already prepared visually: `frontend/src/app/features/shell/shell.html`, lines 3 to 10,
contain the side panel header with the logo, `t('app.name')`, the `t('app.workspace')` subtitle and
a `chevron-down` icon, but it is a plain `div` with no action. We turn it into a button that opens a
menu (`ui/menu.ts` exists).

The menu layout, top to bottom:

```
┌─────────────────────────────────┐
│ NOWTASK                 ✓       │   active, marker on the right
│ artur@nowtask.app · Admin       │
├─────────────────────────────────┤
│ Kontrahent sp. z o.o.           │   the other memberships
│ Guest                           │
├─────────────────────────────────┤
│ + Create an organization        │   leads to /orgs/new
│ ⚙ Organization settings         │   visible to admin and manager
└─────────────────────────────────┘
```

Below it, in the `nav.projects` section (lines 41 to 52), the project list stops being a set of
`span`s and becomes a real project selector with an `activeProjectId` signal. Today no such signal
exists anywhere in the application, and five templates (`board.html:3`, `list.html:3`,
`automations.html:3`, `task-detail.html:4`, `timeline.html:3`) read `store.projects()[0].name`
hardcoded. For a freshly created organization without a project that is `undefined`. It has to be
fixed together with the switcher, because onboarding creates an "organization without a project"
state.

### 9.2 What the data layer does on a switch

`WorkspaceStore` gets:

```ts
private orgSignal = signal<OrgDto | null>(null);
readonly organization = computed(() => this.orgSignal());
readonly organizations = signal<OrgMembershipDto[]>([]);

clear(): void
async switchTo(orgId: string): Promise<void>
```

`switchTo`:

1. `POST /api/orgs/{id}/switch`, the response is a ready `BootstrapDto`,
2. `clear()` on `WorkspaceStore` and on all five stores from
   `frontend/src/app/data/feature.stores.ts` (`RulesStore`, `MetricsStore`, `TimelineStore`,
   `AdminStore`, `SettingsStore`, `TaskDetailStore`),
3. inserting the new `bootstrapSignal`, resetting `activeProjectId` to the first project of the new
   organization,
4. `router.navigate(['/app/board'])`, because the current route may have contained a task key from
   the previous company.

Two existing problems to remove along the way:

- the `if (this.ready() && !force) return;` guard in `WorkspaceStore.load()` will block a reload
  after a switch if someone calls `load()` instead of `load(true)`. `clear()` has to reset
  `bootstrapSignal` so that `ready()` returns `false`.
- `RulesStore.runsSignal` holds the run log in a map keyed by rule identifier and has no
  invalidation at all. Without `clear()` it will show the previous company's log after a switch.
- `AuthService.logout()` clears nothing today. It has to call `clear()` on every store, otherwise
  after signing out and signing in on another account the first render will show someone else's data
  before `GET /api/bootstrap` comes back.

### 9.3 The header and the interceptor

`frontend/src/app/core/auth.interceptor.ts` adds `X-Org-Id` from
`WorkspaceStore.organization()?.id` to every request to `/api/`, except `/api/auth/login`,
`/api/auth/signup`, `/api/meta` and `/api/invites/**`. The same interceptor handles `403`:

| `code` in the response | Reaction |
| --- | --- |
| `NO_ORGANIZATION` | `router.navigate(['/orgs/new'])` |
| `ORG_FORBIDDEN`, `ORG_ACCESS_REVOKED` | `clear()` on every store, `GET /api/orgs`, switch to the first available one or `/orgs/new` |
| `ORG_SUSPENDED` | `router.navigate(['/orgs/suspended'])` |
| `ROLE_FORBIDDEN` | `ToastService`, no navigation |

### 9.4 Local preferences

`PrefsService` (`frontend/src/app/core/prefs.service.ts`) saves choices in `localStorage`. The split:

- global for the account, unchanged: theme, accent, density, radii, language,
- organization dependent, with a `nowtask.<orgId>.` key prefix: the active project, the most
  recently used view, list column settings, filter state.

Without the prefix, switching companies would leave a filter on an epic that does not exist in the
new company, and the list would come out empty with no explanation.

### 9.5 Routes

Besides the existing tree protected by `authGuard`, `frontend/src/app/app.routes.ts` gains:

```
signup                 Signup            no guard
invite/:token          InviteLanding     no guard
orgs/new               CreateOrg         authGuard, no orgGuard
orgs/suspended         OrgSuspended      authGuard, no orgGuard
onboarding             Onboarding        authGuard + orgGuard
```

The new `orgGuard` lets through only when `WorkspaceStore.organization()` is set, otherwise it moves
to `/orgs/new`. We apply it to the whole `Shell` branch. In addition `authGuard` should remember
`returnUrl`, which it does not do today, because after signing in from an invitation link the user
has to come back to the invitation.

### 9.6 Types and dictionaries

`BootstrapDto` (`frontend/src/app/core/api-types.ts`) grows by two fields:

```ts
export interface BootstrapDto {
  organization: OrgDto;
  organizations: OrgMembershipDto[];
  currentUser: UserDto;
  ...
}

export interface OrgDto {
  id: string;
  name: string;
  slug: string;
  ssoDomain: string | null;
  defaultPresetCode: string;
  memberCount: number;
  myRole: RoleId;
}

export interface OrgMembershipDto {
  id: string;
  name: string;
  slug: string;
  role: RoleId;
  lastSeenAt: string | null;
}
```

`pl.ts` is the source of the `TranslationKey` type, so every new key has to be added to `pl.ts`,
`en.ts` and `de.ts` at the same time, otherwise the build does not compile. The new key space:
`org.*`. Along the way `'app.workspace': 'nowtask'` stops being a translation key, because a company
name is data, not interface text. The key disappears and `store.organization()?.name` takes its
place.

---

## 10. Endpoints

The format follows `docs/api-contract.md`.

```
GET    /api/orgs                        -> OrgMembershipDto[]
POST   /api/orgs                        {name, slug?, presetCode?} -> BootstrapDto
GET    /api/orgs/current                -> OrgDto
PATCH  /api/orgs/current                {name?, slug?, ssoDomain?, defaultPresetCode?} -> OrgDto
DELETE /api/orgs/current                {confirmSlug} -> 204
POST   /api/orgs/current/leave          -> 204
GET    /api/orgs/current/export         -> a ZIP of CSVs, Content-Disposition
GET    /api/orgs/slug-available?slug=   -> {available: boolean, suggestion?: string}
POST   /api/orgs/{id}/switch            -> BootstrapDto

GET    /api/organization/permissions           -> {catalog: PermissionDto[], roles: RoleDto[]}
GET    /api/organization/roles                 -> RoleDto[]
POST   /api/organization/roles                 {code, name, permissions[]} -> RoleDto
PATCH  /api/organization/roles/{id}            {name?, permissions?, position?} -> RoleDto
DELETE /api/organization/roles/{id}            {reassignTo} -> 204
```

`RoleDto` is `{id, code, name, position, protected, permissions: string[], memberCount}`.
`PermissionDto` is `{code, group}`, the catalog from point 2.2.1, identical for every organization.

`DELETE /api/organization/roles/{id}` requires `reassignTo`, the identifier of the role its members move
to, because `organization_member.role_id` is `ON DELETE RESTRICT`. Deleting a role with no members
still requires the field, and ignores it. A `protected` role cannot be deleted at all.

All five paths require `PERM_ROLES_MANAGE`, except `GET /api/organization/permissions`, which every member
may read, because the interface shows the caller their own permissions.

`GET /api/orgs` is the only endpoint available when signed in without a selected organization,
alongside `POST /api/orgs` and `/api/auth/*`. The rest of `/api/**` answers `409 NO_ORGANIZATION`.

`POST /api/orgs` creates in one transaction: the organization, the four role templates from point
2.2.2, the creator's membership with the `admin` role, a `workspace_settings` row, three built-in views (`view.atRisk`, `view.unassigned`,
`view.automated`) and switches the session to the new organization. It does not create a project,
because that is a separate wizard step (`docs/onboarding.md`).

There is no `GET /api/orgs/{id}` or `PATCH /api/orgs/{id}`. Only the active organization can be
managed, through `/api/orgs/current`. That way Spring's mapping has no ambiguity between the literal
`current` and the `{id}` template, and authorization has a single place.

Members, teams, invitations and API keys are still managed by the existing `/api/organization/*` paths from
`docs/api-contract.md`. They operate on the active organization. **We do not duplicate them under
`/api/orgs/{id}/members`.**

Changed behavior of existing paths, without a change of shape:

| Path | Was | Is |
| --- | --- | --- |
| `GET /api/bootstrap` | everything in the installation | everything in the active organization, plus `organization` and `organizations` |
| `GET /api/organization/members` | all accounts | members of the active organization, `role` an object from the membership |
| `GET /api/organization/permissions` | a static matrix of four roles | the permission catalog plus the organization's roles, editable |
| `GET /api/organization/api-keys` | all keys | keys of the active organization |
| `GET /api/workspace/*` | the whole installation | the active organization |
| `GET /api/tasks/{key}` | a globally unique key | a key unique within the organization |

There are no path collisions: `docs/api-contract.md` uses neither the `/api/orgs` nor the
`/api/invites` prefix.

---

## 11. Changes required in `docs/api-contract.md`

I am not modifying that file. Below is the list of what has to be added to it for the design to be
consistent.

1. **Error shape.** `{ status, message, at }` gains an optional `code: string`. Without it the
   frontend cannot tell a `403` caused by revoked membership from a `403` caused by too low a role,
   and those two cases call for completely different reactions. The list of codes is in point 5.4.
2. **A new `409` status.** Today the contract lists `400`, `404` and `422`. `409 NO_ORGANIZATION` is
   needed, because it is neither a data error nor a broken domain rule, but a missing context that
   the user can fix with one action.
3. **The `X-Org-Id` header.** To be added to the shared rules next to `X-XSRF-TOKEN`.
4. **`BootstrapDto`.** Two new fields: `organization`, `organizations`.
5. **`UserDto.role`.** It is the role in the active organization, not a global role, **and its shape
   changes** from one of four fixed strings to `{id, code, name}`. The name is defined by the
   organization, so the frontend stops translating it and displays it as entered. Every dictionary
   entry of the form `role.admin` disappears from the three i18n files.
6. **`GET /api/workspace/statuses|transitions|custom-fields|milestones|epics`.** Add the required
   `?projectId=` parameter. Today those endpoints return the whole installation, which is already
   wrong with the three projects in the demo data (`NOW`, `MOB`, `DS`), and with many companies it
   becomes a leak. It changes the response shape only in so far as the lists get shorter.
7. **`shared.events.TaskEvents`.** All five records get `UUID organizationId` as their first field.
   The contract describes the boundary of `automation` and `integrations` towards `tasks`, so this
   change belongs to the contract.
8. **Migration numbering.** Add a table row: `V40` to `V49`, organizations and data isolation.
9. **The "corporate login" section.** Clarify that the return from OIDC links an account by email
   address only when `email_verified = true` in the token, and that assignment to an organization
   goes through `organization.sso_domain`.
10. **A new "Roles and permissions" section.** The five paths from point 10, `RoleDto`,
    `PermissionDto`, and the `ROLE_FORBIDDEN` code carrying the missing permission in `detail`.

---

## 12. Open questions for a human to settle

I am not guessing these, because each one is a product or legal decision, not a technical one.

1. **Can one account be an administrator of any number of organizations, or is there a limit on
   companies created per account?** Without a limit a public installation is open to one script
   creating thousands of empty organizations. I suggest a limit of 3 for an unverified account and
   no limit after address verification, but that is a product decision.
2. **What happens to tasks assigned to a person being removed from an organization?** Today
   `task.assignee_id` has `ON DELETE SET NULL`, but we remove the membership, not the account, so
   the cascade will not fire. Options: clear the assignment, reassign to an administrator, or leave
   it and show the person as a "former member". This affects workload metrics.
3. **Can `sso_domain` be claimed by the first organization that declares it?** The column is
   `UNIQUE`, so the first company to enter `gmail.com` takes over everyone signing up with that
   address. A list of public domains to reject is needed, or a domain verification procedure through
   a DNS entry.
4. **Retention of a deleted organization.** I put down 30 days, but that number is out of thin air
   and should follow from a privacy policy, which the repository does not have.
5. **Can a member be given permissions outside their role?** The model says no: a permission is held
   through a role and only through a role. The alternative, a per member grant list, doubles every
   authorization query and makes "who can do what" unanswerable from the roles screen. If a company
   needs an exception, it creates a role for it. Worth confirming before the screens are built.
6. **Is the permission catalog closed?** Adding an entry means a code change, because a permission
   nobody checks is a lie told to the administrator. That is a deliberate limit on how far a company
   can shape its own model, and it should be a conscious decision, not a discovery.

# Implementation plan: organizations, presets, onboarding

The order of work for whoever writes the code based on `docs/multi-tenancy.md`,
`docs/work-presets.md` and `docs/onboarding.md`. The document says what comes after what, what can
be done in parallel and where a silent mistake is easy to make.

---

## 1. Starting constraints

**Occupied areas.** At the time this plan was written, other agents are working in `backend/tasks`,
`backend/workspace` and `frontend/src/app/ui`. The plan is arranged so that the first two phases do
not touch those directories at all.

**Occupied migration numbers.** `docs/api-contract.md` assigned the ranges from `V3` to `V39`. The
repository already holds `V3__task_watchers.sql`, `V4__task_key_sequence.sql`,
`V5__saved_view_query.sql` and `V6__workspace_settings.sql`. Our numbers start at `V40`.

**The baseline state we refer to.** 25 tables: 21 from `V1`, plus `task_watcher` (`V3`),
`task_key_sequence` (`V4`), `workspace_settings` (`V6`) and `audit_event` (`V31`). `V3` dropped the
`task.watcher_count` column, `V30` extended `api_key` with six columns.
The baseline changes as work goes on, because agents in the `V3` to `V39` range keep adding
migrations. Before phase 1 starts, the table list has to be recounted and compared with the table in
`docs/multi-tenancy.md`, point 2.4.

A note for whoever works in `backend/tasks`: `TaskService.detail()` calls `task.getWatcherCount()`
today, and `V3` dropped that column. This is an existing mismatch, independent of this plan, but it
will hit the same class on the first run with `ddl-auto: validate`.

---

## 2. Migration range assignment

| Range | Area | Document |
| --- | --- | --- |
| `V3` to `V39` | taken by `docs/api-contract.md` | do not touch |
| `V40` to `V49` | organizations and data isolation | `docs/multi-tenancy.md` |
| `V50` to `V59` | work form presets, sprints, field options | `docs/work-presets.md` |
| `V60` to `V69` | onboarding, invitations, address verification | `docs/onboarding.md` |
| `V70` and beyond | free | not applicable |

Detailed breakdown:

| Number | Contents | Reversible |
| --- | --- | --- |
| `V40` | `organization`, `organization_member`, the default organization, role migration | yes |
| `V41` | `organization_id` (without `NOT NULL`) on 24 tables | yes |
| `V42` | filling in `organization_id` | yes |
| `V43` | `NOT NULL`, `DEFAULT`, foreign keys, rebuilt unique keys, indexes | **no** |
| `V44` | the `nowtask_app` role, RLS, policies, consistency triggers | in SQL yes, in deployment no |
| `V45` to `V49` | isolation area reserve | not applicable |
| `V50` | preset columns on `project` | yes |
| `V51` | `sprint`, `task.sprint_id`, migration from `task.sprint_code` | yes |
| `V52` | `custom_field_option`, normalizing values in `task.custom` | **no** |
| `V53` | `saved_view.project_id`, `saved_view.origin` | yes |
| `V54` | `task.milestone_id` | yes |
| `V55` | `preset_change`, `preset_change_task` | yes |
| `V56` | dropping `task.sprint_code` | **no** |
| `V57` to `V59` | preset area reserve | not applicable |
| `V60` | `organization_invite` | yes |
| `V61` | `email_verification`, `app_user.email_verified_at`, `state`, `oidc_subject` | yes |
| `V62` | `onboarding_progress` | yes |
| `V63` | moving the pending account from demo data into an invitation, `email_verified_at` for demo accounts | yes |
| `V64` | dropping `app_user.role`, `capacity`, `pending`, `invited_on` | **no** |
| `V65` to `V69` | onboarding area reserve | not applicable |

**Why `V64` and not `V45`.** Flyway runs migrations in numeric order. Dropping `app_user.pending`
requires the `hanna@kontrahent.pl` account to be moved into `organization_invite` first, and that
table is created in `V60`. If the cleanup were numbered `V45`, it would run before `V60` and delete
the data. This one step deliberately steps outside its own area's range.

**A rule for all streams.** A migration number is reserved by creating an empty file with the target
name in the first commit of the task, before any SQL is written. Two migrations with the same number
stop the application from starting for everyone.

---

## 3. Phases

### Phase 1: the isolation foundation

Modules: `shared`, `identity`, `app`. Migrations: `V40`, `V41`, `V42`.
**Touches neither `backend/tasks`, nor `backend/workspace`, nor `frontend/`.**

1. `shared`: `OrganizationContext` (a record with `userId`, `organizationId`, `role`),
   `OrganizationContextHolder` (`ThreadLocal`), `OrganizationEvents`.
2. `identity`: the `Organization` and `OrganizationMember` entities, repositories,
   `OrganizationService`, extending the `UserDirectory` interface with `currentMembership()`
   and `organizations()`.
3. `identity`: `UserDirectoryService.findAll()` and `findActive()` join `organization_member`.
   `AppUserRepository.findAllByOrderByPendingAscNameAsc()` disappears.
4. `app`: `OrganizationContextFilter` (resolving the context from the session or from `X-Org-Id`),
   `OrganizationContextTransactionListener` (`set_config` at the start of a transaction),
   `SecurityConfig` lets `GET /api/orgs` and `POST /api/orgs` through without a selected
   organization.
5. `app`: `OrgController` with the eight paths from `docs/multi-tenancy.md`, point 10.
   The controller lives in `identity`, registration happens in `app` through package scanning as it
   does today.
6. `app`: `BootstrapController` adds `organization` and `organizations`.
7. Migrations `V40` to `V42`.

At the end of the phase the application works exactly as it did before it, with one organization,
and additionally exposes `/api/orgs`. `app_user.role` is still read, but `identity` prefers the
value from the membership when one exists.

**Exit gate:** `TenantSchemaTest` passes for `V40` to `V42` in the version that only checks column
existence (without RLS, because it does not exist yet). Manual test: two organizations in the
database, `POST /api/orgs/{id}/switch` changes `GET /api/bootstrap`.

### Phase 2: enforcing isolation

Modules: `app`, deployment configuration. Migrations: `V43`, `V44`.
**Still touches neither `tasks`, nor `workspace`, nor the frontend.**

1. Migration `V43` (constraints, unique keys, indexes).
2. Migration `V44` (the `nowtask_app` role, RLS, policies, triggers).
3. `application.yml`: separating `spring.flyway.user` from `spring.datasource.username`.
   `docker-compose.yml`: `NOWTASK_APP_DB_USER`, `NOWTASK_APP_DB_PASSWORD`.
4. `TenantSchemaTest` in its full version (six conditions per table, point 4.6 A
   in `docs/multi-tenancy.md`).
5. `TenantLeakTest` (two twin organizations, walking every `@GetMapping`).
6. A runtime safeguard for the `dev` and `test` profiles.
7. An ArchUnit test blocking `app_user` in SQL outside `identity`.

This is the phase after which **there is no way back** without migrating data. It should be a
separate, deliberate deployment, not tacked onto another change.

**Exit gate:** `TenantLeakTest` passes for all existing controllers. Starting the application on the
`nowtask` account (the owner) in the production profile ends with a refusal to start.

### Phase 3: context in the domain modules

Modules: `workspace`, `tasks`, `automation`, `analytics`, `integrations`, `exports`.
No migrations.

**Start condition: the agents working in `backend/tasks` and `backend/workspace` today have to
finish and merge their changes.** This phase touches exactly the same files they do.

1. `shared.events.TaskEvents`: all five records get a `UUID organizationId`.
   This is a change that breaks compilation of `tasks` and `automation` at once, so it goes in one
   commit.
2. `tasks`: `TaskRepository.findByKey(String)` becomes
   `findByOrganizationIdAndKey(UUID, String)`. Key assignment from `task_key_sequence` within the
   boundary of an organization and a project.
3. `automation`: `TaskEventListener` uses `organizationId` from the event. An engine performing
   actions on another thread (`@Async`, `@TransactionalEventListener`) **has to set the context
   itself**, because a `ThreadLocal` does not cross a thread boundary.
4. `workspace`: `projectId` as a parameter of every configuration read.
   `nextStatusPosition()`, `createEpic()`, `createCustomField()` compute `MAX(position)` within the
   project, not the whole table.
5. `analytics`: `MetricsService.burndown()` and `throughput()` get a `projectId`.
6. `exports`: the organization slug in `Content-Disposition`.

**Exit gate:** `TenantLeakTest` passes after adding a second project to each of the two test
organizations. That catches confusing organization scope with project scope.

### Phase 4: frontend, the organization switcher

Directories: `frontend/src/app/core`, `data`, `features/shell`, `features/login`.
**Start condition: the stream working in `frontend/src/app/ui` has to finish**, because the switcher
uses `ui/menu.ts`.

1. `core/api-types.ts`: `OrgDto`, `OrgMembershipDto`, extending `BootstrapDto`.
2. `data/workspace.store.ts`: `organization`, `organizations`, `activeProjectId`, `clear()`,
   `switchTo()`. The `if (this.ready() && !force) return;` guard has to cooperate with `clear()`.
3. `data/feature.stores.ts`: `clear()` on all six stores.
   Without it `RulesStore.runsSignal` will show the previous company's log.
4. `core/auth.service.ts`: `logout()` clears the stores.
5. `core/auth.interceptor.ts`: the `X-Org-Id` header, handling `403` by the `code` field.
6. `core/auth.guard.ts`: `returnUrl`. A new `orgGuard`.
7. `features/shell/shell.html`: the switcher in place of today's dead header (lines 3 to 10), a real
   project selector in place of the list of `span`s (lines 41 to 52).
8. **Five templates with `store.projects()[0].name`** (`board.html:3`, `list.html:3`,
   `automations.html:3`, `task-detail.html:4`, `timeline.html:3`) stop assuming a project exists.
   This is a precondition for phase 6, because onboarding creates an "organization without a
   project" state.
9. `core/prefs.service.ts`: a `nowtask.<orgId>.` prefix for organization dependent preferences.
10. A new `org.*` key space in `pl.ts`, `en.ts`, `de.ts`. Removing `app.workspace`.

**Exit gate:** switching organizations leaves no data from the previous company on the screen,
including in the rule log and in the checklist.

### Phase 5: presets

Modules: `workspace` (main), `tasks`, `automation`, `analytics`, frontend.
Migrations: `V50` to `V56`.

Order within the phase:

1. `V50` to `V54` (schema) plus `PresetCatalog` in `workspace` with three definitions and a test
   checking that every preset has statuses in all three categories (the condition from
   `docs/work-presets.md`, point 6.2).
2. `GET /api/presets`, `GET /api/presets/{code}`, `POST /api/projects` applying a preset.
   Up to this point a preset is only a "starter set" and there is no preset change yet.
3. Enforcing `requirement` (ten codes) and `wip_limit` in `tasks`.
4. Sprints: `V51` is already there, `SprintService` is added, six `/api/sprints` paths, the
   `SprintStarted` and `SprintCompleted` events. `TaskService.CURRENT_SPRINT` disappears.
5. `V56` (dropping `task.sprint_code`).
6. `V55` plus the preset change algorithm: preview, apply, undo.
7. Frontend: the preset change wizard, the screen from `docs/work-presets.md`, point 6.3.

Steps 2 and 3 can be done in parallel by two people. Step 6 requires all the previous ones.

**Exit gate:** a test running the demo project through all six preset pairs (scrum to kanban, scrum
to waterfall, kanban to scrum, and so on), each time checking that the task count matches, that none
was left without a status, and that rules and views point at existing statuses.

### Phase 6: onboarding

Modules: `identity`, `integrations`, `app`, frontend. Migrations: `V60` to `V63`.

**Start condition:** phase 5 steps 1 and 2, because wizard step 3 creates a project with a preset.
And phase 4 point 8, because the wizard passes through the "organization without a project" state.

1. `V60` to `V62` (schema), `V63` (demo data).
2. `identity`: `InviteService`, `SignupService`, `EmailVerificationService`, `OnboardingService`.
3. `integrations`: three mail templates, listeners for `InviteIssued` and
   `EmailVerificationRequested`.
4. `app`: `SecurityConfig` lets five new public paths through, rate limiting.
5. OIDC: a realm import file in `infra/keycloak/` (the directory exists and is empty),
   `ClientRegistration` in `application.yml`, `OidcUserService` with account linking rules.
6. Frontend: `signup`, `invite/:token`, `orgs/new`, `onboarding`, the checklist, the tour, fixes to
   the login screen (removing the hardcoded credentials).

**Exit gate:** both flows go from zero to a board in the `docker compose` environment, with the mail
received in Mailpit.

### Phase 7: cleanup

Migration `V64`. Removing the reads of `app_user.role`, `capacity`, `pending`, `invited_on` from
`AppUser`, `AppUserDetailsService` and `UserDirectoryService.toView`, and only then the migration.

---

## 4. What can be done in parallel

```
Phase 1  ─────────────►  Phase 2  ─────────────►  Phase 3  ──┬──►  Phase 5  ──┬──►  Phase 6  ──►  Phase 7
                                                             │                │
                              Phase 4  ─────────────────────────────────────► ┘
                              (starts once the work in frontend/src/app/ui is finished)
```

| Pair of phases | In parallel | Why |
| --- | --- | --- |
| 1 and 2 | no | 2 assumes the columns from 1 exist and are filled in |
| 2 and 4 | yes | disjoint directories, the frontend does not depend on RLS |
| 3 and 4 | yes | the only point of contact is `BootstrapDto`, which is created in phase 1 |
| 3 and 5 | no | phase 5 rewrites the same files in `workspace` |
| 5 and 6 | partly | phase 6 needs `POST /api/projects` with a preset, the rest is independent |
| 5 step 2 and 5 step 3 | yes | the preset catalog versus requirement enforcement, different files |

A sensible split into three streams working in parallel after phase 2:

- **Agent A:** phase 3 (domain modules), then phase 5 (presets, backend).
- **Agent B:** phase 4 (frontend, the switcher), then phase 6 point 7 (frontend, onboarding).
- **Agent C:** phase 6 points 1 to 6 (onboarding backend), starting after phase 5 step 2.

The migration ranges of the three streams are disjoint, so they will not collide on numbers.

---

## 5. Risky spots

Ordered from the most dangerous.

### R1. The application stays on the database owner account

After `V44` the RLS policies exist, but the `nowtask` account bypasses them because it owns the
tables. An application started with the old credentials **works correctly and has no isolation**.
Nothing breaks, nothing raises an alarm, company data is mutually accessible.

Safeguard: a `current_user` check at startup in the production profile and a refusal to start, plus
an assertion in `TenantSchemaTest`. This has to be built together with `V44`, not later.

### R2. Roles disappearing from `GrantedAuthority`

`AppUserDetailsService` stops calling `roles(user.getRole().name())`. Every
`@PreAuthorize("hasRole('ADMIN')")` annotation or `.hasRole(...)` in the security chain stops
working and **lets through instead of blocking**, if the rule was written as `permitAll` with the
condition in the method.

There is not a single such annotation in the repository today, but the agents working in `tasks` and
`workspace` may add them before phase 1 merges. They have to be warned before phase 1 starts, not
after.

Safeguard: a test that walks every controller method and fails the build when it finds a `hasRole`
or `hasAuthority` referring to domain roles.

### R3. `task_key` stops being globally unique

`V43` drops the `UNIQUE` from `task.task_key`. Four places assume global uniqueness today:

- `TaskRepository.findByKey(String)` and `findByKeyIn(List<String>)`,
- `automation_run.task_key` (deliberately without a foreign key, a module boundary),
- `shared.events.TaskEvents`, all five records,
- `GET /api/rules/touching/{key}` and `POST /api/rules/{id}/run`.

Missing any one of them gives a bug that only shows up once two companies have a project with the
same short code. That will not happen by accident in test data, so `TenantLeakTest` has to set up
**the same project code in both organizations**.

### R4. The context does not reach the rule engine thread

`OrganizationContextHolder` is a `ThreadLocal`. A rule engine reacting to events in
`@TransactionalEventListener(phase = AFTER_COMMIT)` or in `@Async` gets an empty context, and then
`set_config` sets an empty value, RLS cuts everything off, and the rule silently does nothing and
writes an `automation_run` with outcome `error`.

That is a visible failure, not a leak, so this behavior is acceptable as a first approximation. The
target: `organizationId` from the event (R3) set on entry into the handler.

### R5. Changing a preset on a large project

Applying a preset is a single transaction with `UPDATE task SET status_id = ...` on every task in
the project plus an entry in `task_history` for every moved task. With 50 thousand tasks that is a
long transaction holding locks on `task`, during which the board is unavailable for writes.

Safeguard: the preview returns `taskCount`, and above a threshold (5000 is my suggestion) the
interface warns and suggests doing it outside working hours. Splitting into batches is out of the
question, because a partially changed preset means a project where some tasks have statuses that no
longer exist.

### R6. Two scope levels at once

`WorkspaceService.statuses()`, `transitions()`, `epics()`, `customFields()`, `milestones()` **do not
filter by project today**. After phase 2 RLS will limit them to the organization and everything will
look correct in tests with a single project. The bug will only show up with a second project, that
is after presets are deployed.

Safeguard: test data from phase 3 on always has **two projects per organization**. The demo data has
three (`NOW`, `MOB`, `DS`), but statuses, epics and milestones exist only for `NOW`, so today's demo
data will not catch it. Statuses have to be added to `MOB`.

### R7. An organization without a project

`WorkspaceService.defaultProject()` throws `NotFoundException("No project defined")`, and five
frontend templates read `store.projects()[0].name`. The onboarding wizard passes through this state
between step 1 and step 3. Without the fix from phase 4 point 8 the wizard crashes the application
halfway through.

### R8. `DemoPasswordInitializer` on an installation with signup

Closed. The component and the whole demo password mechanism have been removed from the repository,
so there is no longer a path where an account created through OIDC picks up a shared password.

### R9. Migration number collision

Agents in the `V3` to `V39` ranges work in parallel. Numbers from `V40` up are ours, but a file with
the same name created by two people on our team stops the application from starting for everyone.
Reserve the number with an empty file in the first commit, point 2.

### R10. Three i18n dictionaries

`pl.ts` is the source of the `TranslationKey` type, and `en.ts` and `de.ts` have to hold exactly the
same set of keys, otherwise the build does not compile. We add six key spaces: `org.*`, `preset.*`,
`signup.*`, `invite.*`, `onboarding.*`, `tour.*`, plus `mail.*` on the backend side. That is around
150 keys times three files, added across four different phases by three streams.

Safeguard: always add keys to the three files in a single commit. The German and English
translations can start as a copy of the Polish one, as long as the key exists.

### R11. Migration `V42` rewrites every row

`UPDATE task SET organization_id = ...` without a `WHERE` rewrites the whole table. On demo data
that is seconds. On a real installation with hundreds of thousands of tasks it is a window in which
`task` is locked for writes and Flyway is holding up application startup.

Safeguard: on an installation larger than demo, split it into `WHERE organization_id IS NULL`
batches with a `LIMIT`, run outside Flyway, and leave `V42` as a check that no `NULL` remains.

### R12. `NULLS NOT DISTINCT` requires PostgreSQL 15

`custom_field` and `saved_view` use `UNIQUE NULLS NOT DISTINCT`. `docker-compose.yml` has
`postgres:18-alpine`, so that is fine, but a developer running the database locally from an older
installation will get a syntax error in `V43` whose message will not point at the cause.

Safeguard: a server version check in `V40` with a readable `RAISE EXCEPTION`.

---

## 6. Quality gates

Tests that have to exist for a phase to count as finished. The repository has not a single test
today, so this is also the first opportunity to start writing them.

| Test | Phase | What it checks |
| --- | --- | --- |
| `TenantSchemaTest` | 1, full in 2 | six conditions on every table outside the allowlist |
| `TenantLeakTest` | 2, extended in 3 | no identifiers of organization B in responses for A |
| `TenantWriteTest` | 2 | `INSERT` and `UPDATE` with someone else's `organization_id` ends in a database error |
| `ArchUnit: app_user` | 2 | no `app_user`, `organization_invite`, `email_verification` in SQL outside `identity` |
| `ArchUnit: hasRole` | 2 | no `hasRole` referring to domain roles |
| `PresetCatalogTest` | 5 | every preset has statuses in all three categories |
| `PresetMigrationTest` | 5 | six preset pairs, matching task counts, no orphans |
| `TransitionRequirementTest` | 5 | ten requirement codes, each one blocks and lets through |
| `InviteFlowTest` | 6 | six invitation states from `docs/onboarding.md`, point 4 |
| `SignupFlowTest` | 6 | five validation cases from step 0 |

`TenantLeakTest` is the most important one on this list. It should be created in phase 2 and extended
with every new controller, because it is the only net that catches a leak in code that does not
exist yet.

---

## 7. Collected changes to `docs/api-contract.md`

The three design documents list 36 contract changes between them. I am not modifying that file, but
someone has to merge them before phase 1 starts, because four of them change things marked today as
"do not change the shape".

Four changes in the "Exists (do not change the shape)" section:

| Change | From | Weight |
| --- | --- | --- |
| `BootstrapDto` gets `organization`, `organizations`, `onboarding` | multi-tenancy, onboarding | required |
| `UserDto.role` becomes the role in the organization, `emailVerified` is added | multi-tenancy, onboarding | required |
| `GET /api/workspace/*` gets a required `?projectId=` | presets, multi-tenancy | required |
| `GET /api/admin/members` returns organization members, not all accounts | multi-tenancy | required |

Changes in the shared rules:

- a `code` field in the error shape `{ status, message, at }`,
- codes `409` and `429` alongside today's `400`, `404`, `422`,
- the `X-Org-Id` header alongside `X-XSRF-TOKEN`,
- the migration numbering table grows by three rows (`V40` to `V49`, `V50` to `V59`,
  `V60` to `V69`).

Full lists: `docs/multi-tenancy.md` point 11 (nine items), `docs/work-presets.md` point 9 (seventeen
items), `docs/onboarding.md` point 9 (eleven items).

---

## 8. The order of production deployments

Phases do not map one to one onto deployments. The minimum number of separate deployments is five:

| Deployment | Contains | Reversible |
| --- | --- | --- |
| 1 | phase 1 (`V40` to `V42`, code with no behavior change) | yes, rolling the code back is enough |
| 2 | phase 2 (`V43`, `V44`, switching the database account) | **no** |
| 3 | phases 3 and 4 | yes |
| 4 | phase 5 (`V50` to `V56`) | partly, `V52` and `V56` are not |
| 5 | phases 6 and 7 (`V60` to `V64`) | **no** for `V64` |

Deployment 2 is the only one that has to have a planned window and a prepared rollback procedure
based on restoring a database backup, not on reverting migrations.

Between deployment 1 and 2 the application works correctly on one organization without isolation at
the database level. That is an acceptable intermediate state only because there is no public signup
until deployment 5, so there is physically one organization.

---

## 9. What this plan does not settle

Questions from the three design documents that need a human decision before the corresponding phase
starts:

| Question | Blocks phase | Document |
| --- | --- | --- |
| Limit on organizations created by one account | 6 | multi-tenancy, point 12.1 |
| What happens to the tasks of a person removed from an organization | 3 | multi-tenancy, point 12.2 |
| Domain ownership verification for `sso_domain` | 6 | multi-tenancy, point 12.3 |
| Retention of a deleted organization | 2 | multi-tenancy, point 12.4 |
| Whether presets should be editable by an organization | 5 | presets, point 10.1 |
| The undo window for a preset change | 5 | presets, point 10.2 |
| `completed_at` when mapping to the `done` category | 5 | presets, point 10.3 |
| Signup mode: open, invitation only, corporate only | 6 | onboarding, point 10.1 |
| Whether address verification blocks the wizard | 6 | onboarding, point 10.2 |
| Terms of service and privacy policy | 6 | onboarding, point 10.3 |
| The default role when joining through `sso_domain` | 6 | onboarding, point 10.4 |

Phases 1 to 4 can be done in full without any of these decisions.

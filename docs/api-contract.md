# nowtask API contract

A document binding for the backend and the frontend. The backend implements exactly these paths and
shapes, the frontend codes against them. Changing the contract requires updating this file.

Shared rules:

- Everything under `/api`, session in a cookie, CSRF in the `X-XSRF-TOKEN` header.
- Errors: `{ status, message, at }`. 404 does not exist, 422 broken domain rule, 400 bad input.
- Dates: `LocalDate` as `YYYY-MM-DD`, timestamps as ISO 8601 with an offset.
- Identifiers are UUIDs. Tasks are additionally addressed by a text key (`NOW-172`).

## Exists (do not change the shape)

```
GET    /api/meta
POST   /api/auth/login          {email,password} -> UserDto
POST   /api/auth/logout
GET    /api/auth/me             -> UserDto
GET    /api/bootstrap           -> BootstrapDto
GET    /api/tasks               -> TaskDto[]
GET    /api/tasks/{key}         -> TaskDetailDto
PATCH  /api/tasks/{key}         -> TaskDto
GET    /api/tasks/{key}/comments, /history
POST   /api/tasks/{key}/comments
POST   /api/tasks/{key}/subtasks/{id}/toggle
POST   /api/tasks/bulk/assign, /bulk/status, /bulk/delete
GET    /api/rules, /api/rules/{id}, /api/rules/{id}/runs, /api/rules/touching/{key}
POST   /api/rules/{id}/toggle
GET    /api/metrics/overview
GET    /api/timeline
GET    /api/admin/members, /teams, /permissions, /api-keys
GET    /api/workspace/statuses, /transitions, /custom-fields, /milestones
```

## To add: tasks

```
POST   /api/tasks                          NewTask -> TaskDto
DELETE /api/tasks/{key}
POST   /api/tasks/{key}/subtasks           {title, assigneeId?} -> SubtaskDto
PATCH  /api/tasks/{key}/subtasks/{id}      {title?, done?, assigneeId?} -> SubtaskDto
DELETE /api/tasks/{key}/subtasks/{id}
POST   /api/tasks/{key}/labels             {label} -> string[]
DELETE /api/tasks/{key}/labels/{label}     -> string[]
POST   /api/tasks/{key}/relations          {kind, taskKey} -> RelationDto[]
DELETE /api/tasks/{key}/relations/{kind}/{taskKey}
PUT    /api/tasks/{key}/custom/{fieldKey}  {value} -> TaskDetailDto
POST   /api/tasks/{key}/watch              -> {watching: boolean}
```

`NewTask`: `{ title, description?, statusId?, priority?, assigneeId?, reviewerId?, dueDate?,
estimate?, epicId?, labels?[], custom? }`. The key is assigned by the backend from the project
prefix.

`PATCH /api/tasks/{key}` additionally accepts `reviewerId`, `epicId`, `startDate`, `endDate`.
Passing `null` clears the field; omitting the key leaves it unchanged. The conventional zero UUID is
gone, we use an explicit `null` in JSON.

## To add: search, filters, views

```
GET    /api/tasks?query=&statusId=&assigneeId=&label=&priority=&epicId=&dueBefore=&unassigned=
                 &automated=&sprint=&groupBy=&sort=&page=&size=
```

**Always** returns the envelope `{ items: TaskDto[], total, page, size, groups?: { key, label, count }[] }`,
including when no parameters are given. No filters means the current sprint without paging.
Filtering, sorting and grouping happen entirely in SQL.

`sort` accepts: `key`, `title`, `status`, `priority`, `dueDate`, `estimate`, `created`, `updated`.
A `-` prefix reverses the order. An unknown value ends in a 400.

`groupBy` accepts: `status`, `assignee`, `priority`, `epic`, `label`.

Response codes across the API: 201 on resource creation, 204 on deletion, 200 on modification.
Clearing a field is an explicit `null` in JSON. The conventional zero UUID is no longer used
anywhere.

```
GET    /api/views                -> SavedViewDto[]
POST   /api/views                {name, query} -> SavedViewDto
PATCH  /api/views/{id}           {name?, query?} -> SavedViewDto
DELETE /api/views/{id}
```

`SavedViewDto`: `{ id, code, name, query, shared, ownerId, count }`, where `code` and `name` are
disjoint: built-in views have a `code` and an empty `name`, user views the other way round. `query`
is an object with the same keys as the `GET /api/tasks` parameters, plus `columns`: an ordered list
of visible task list columns (`labels`, `assignee`, `priority`, `due`, `estimate`). The order of the
array is the order of the columns, an omitted code means a hidden column, a missing field means the
default layout. ID and title are always visible and do not appear in this list. Saving a view
validates the codes: an unknown one ends in a 400, a repeated one is skipped. `columns` does not
affect `GET /api/tasks`. Trying to delete a built-in view ends in a 422.

`TransitionDto` carries `id`, needed for deleting a transition.

```
GET    /api/search?q=            -> { tasks: TaskDto[], rules: RuleDto[], people: UserDto[], views: SavedViewDto[] }
```

The search behind ⌘K. Limit of 5 items per category.

## To add: automation rules

```
POST   /api/rules                NewRule -> RuleDto
PATCH  /api/rules/{id}           {name?, summary?, scopeLabel?, projectId?, trigger?, conditions?, actions?} -> RuleDto
DELETE /api/rules/{id}
POST   /api/rules/{id}/run       {taskKey} -> RunDto      // manual run on a given task
GET    /api/rules/runs           ?ruleId=&outcome=&page=  -> { items: RunDto[], total }
GET    /api/rules/catalog        -> { triggers[], conditionFields[], operators[], actions[] }
```

`catalog` describes what the builder can offer in its selects: for every trigger, condition field
and action it gives `kind`, `labelKey`, the expected value type (`none|text|number|status|
priority|user|label|duration|channel|url`) and an optional list of allowed values.

Validation on the API side: at most three levels of condition group nesting, rejected with a 422 and
the identifier of the offending group.

**The rule engine really has to perform the actions.** A listener for events from the `tasks` module
checks conditions and performs actions: status change, assignment, adding a label, setting a due
date, adding a watcher, notifying a channel, a webhook, archiving. Every run writes an entry to the
log with outcome `ok|skipped|error` and a reason.

Rules fire after the change that triggered them is committed and work in their own transaction. This
way a failed action ends as an `error` entry in the log rather than as an error in the operation a
human performed. Events carry the name of the rule that caused them, and the engine does not pick
such events up again, so one rule's action does not trigger the next one.

## To add: workspace configuration

```
POST   /api/workspace/statuses            {code,label,category,wipLimit?,position} -> StatusDto
PATCH  /api/workspace/statuses/{id}
DELETE /api/workspace/statuses/{id}       // 422 when the status has tasks
POST   /api/workspace/statuses/reorder    {ids: []} -> StatusDto[]
POST   /api/workspace/transitions         {fromStatus,toStatus,requirement?} -> TransitionDto
DELETE /api/workspace/transitions/{id}
POST   /api/workspace/custom-fields       {name,fieldKey,type,scopeLabel,restrictedToRole?} -> CustomFieldDto
PATCH  /api/workspace/custom-fields/{id}  // fieldKey is immutable
DELETE /api/workspace/custom-fields/{id}
GET    /api/workspace/settings            -> WorkspaceSettingsDto
PATCH  /api/workspace/settings            {dateFormat?,timeFormat?,firstDayOfWeek?,timeZone?,currency?,allowUserOverride?,blockDisallowedDrag?}
GET    /api/workspace/epics, POST, PATCH, DELETE
```

Status transitions have to be enforced: `PATCH /api/tasks/{key}` with a status outside an allowed
transition returns a 422 when `blockDisallowedDrag` is on.

## To add: people and access

```
POST   /api/admin/invites            {email, role} -> UserDto        // pending account
DELETE /api/admin/invites/{id}
PATCH  /api/admin/members/{id}       {role} -> UserDto
DELETE /api/admin/members/{id}
POST   /api/admin/teams              {name} -> TeamDto
PATCH  /api/admin/teams/{id}
DELETE /api/admin/teams/{id}
POST   /api/admin/teams/{id}/members {userId}
DELETE /api/admin/teams/{id}/members/{userId}
POST   /api/admin/api-keys           {label, scopes?, expiresInDays?} -> {key, view: ApiKeyDto}
DELETE /api/admin/api-keys/{id}
GET    /api/admin/audit              ?page=&size= -> { items: AuditDto[], total }
GET    /api/auth/api-key             -> {prefix, label, scopes[], owner: UserDto}
```

`ApiKeyDto`: `{ id, prefix, label, scopes[], ownerId, createdAt, lastUsedAt, expiresAt, revokedAt,
state }`, where `state` is `active | expired | revoked`. The full key is returned once, at creation;
only its hash is stored in the database.

`GET /api/auth/api-key` works only under key authentication and lets an agent learn its own
permissions without access to `/api/admin/**`. For a session it returns a 404.

`AuditDto`: `{ id, at, actorId, apiKeyId, actorLabel, action, subject, detail }`. The event log
records administrative operations, configuration changes and every agent request and denial.

### API key authentication

Requests with an `Authorization: Bearer nt_...` header **do not require a CSRF token**, because they
carry no cookies. Requests with a session cookie still require one. An invalid key ends in a 401
with `reason: "invalid_api_key"` and never silently falls back to the session.

Scopes: `tasks:read`, `tasks:write`, `tasks:delete`, `rules:read`, `rules:run`, `rules:write`,
`metrics:read`, `workspace:read`. Omitting `scopes` at creation grants only the four read ones.
`tasks:delete` is never granted by default. `/api/admin/**` paths are closed to keys regardless of
scopes, and a path not described in the rules is closed by default.

A denial caused by a missing scope returns a 403 with `requiredScope` and `grantedScopes`.

Changes made with a key store `rule_name` in task history in the form
`agent:<key name> (<prefix>)`, so in the interface they are distinguishable from rule actions.

### The AI agents screen

```
GET    /api/agents                   ?activity= -> { agents: AgentDto[], activity: AgentActivityDto[] }
```

`AgentDto`: `{ id, prefix, label, scopes[], lastUsedAt, expiresAt, state }`. This is the same set of
keys as `/api/admin/api-keys`, without `ownerId` and without creation fields, so that the agents
screen can be available to every signed-in role. Key management stays in `/api/admin/api-keys` and
still requires an administrator.

`AgentActivityDto`: `{ id, at, agent, action, method, path, status }`, where `action` is
`agent.task.*`, `agent.rule.*`, `agent.request.*` or `agent.denied`. These are event log entries
that have an `api_key_id`, that is agent traffic only, without human operations. `activity` returns
the 20 most recent entries by default, at most 200.

The path is closed to API keys: an agent cannot read the agent list or anyone else's activity.

## To add: account signup

```
POST   /api/auth/signup              {name, email, password} -> UserDto
```

An open path, without authentication but with a CSRF token, so the frontend first fetches
`GET /api/meta`. Returns `201`, creates an account with the `member` role (`pending = false`) and
opens a session immediately, changing the session identifier so that it cannot be fixated.

Error codes: `409` address taken, `400` password shorter than 10 characters or an invalid address,
`422` password from the most common list.

Without organizations (`docs/multi-tenancy.md`) whoever signs up enters the only workspace in the
installation. Address verification, the wizard and `suggestOrg` from `docs/onboarding.md` wait for
organizations.

## To add: navigation personalization

```
GET    /api/me/navigation            -> NavItemDto[]
PUT    /api/me/navigation            {items: NavItemDto[]} -> NavItemDto[]
```

`NavItemDto`: `{ code, hidden }`, where `code` is one of the items in the "Navigation" section of the
side panel: `overview`, `my-tasks`, `board`, `list`, `timeline`, `automations`, `agents`, `reports`.
The order of the array is the order of the items in the panel. The setting is personal, stored per
user.

`PUT` accepts a list in any order and with any subset of codes: the backend removes duplicates,
appends the missing codes at the end as visible, and returns the normalized list. An unknown code
ends in a 400.

`GET /api/bootstrap` carries the same list in the `navigation` field, so the side panel needs no
separate request at startup.

## In-app notifications

```
GET    /api/notifications            ?unreadOnly= -> { items: NotificationDto[], unread }
POST   /api/notifications/{id}/read
POST   /api/notifications/read-all
```

`NotificationDto`: `{ id, at, kind, titleKey, params, taskKey?, read }`. The bell in the header shows
the unread count and the list on click. Rules and assignments create entries.

Notifications are personal: the list returns the 50 most recent entries of the signed-in person,
`unreadOnly=true` narrows it to unread ones, and `read-all` responds with
`{ read: <number marked> }`. Trying to mark someone else's entry ends in a `404`. API keys have no
access to this path.

## Export and integrations

```
GET    /api/export/tasks?format=csv|xlsx&<the same filters as /api/tasks>
GET    /api/export/rule-runs?format=csv&ruleId=
GET    /api/integrations             -> IntegrationDto[]
POST   /api/integrations             {kind, name, config} -> IntegrationDto
PATCH  /api/integrations/{id}
DELETE /api/integrations/{id}
POST   /api/integrations/{id}/test   -> {ok, detail}
```

Export returns a real file with a `Content-Disposition` header and contains exactly the columns and
rows visible after the filters are applied.

`IntegrationDto.kind`: `webhook` or `email`. A webhook configuration is an address, a secret and
events. The "notify channel" rule action sends a request to the integration with the given name and
records the result.

Task export gives the columns `Key`, `Title`, `Status`, followed by the list columns from `columns`
in the same order. It takes all rows matching the filters, not just the current page, with a hard
limit of 10,000. CSV comes out in UTF-8 with a BOM so that Excel does not get the encoding wrong.
`rule-runs` exists in CSV only, another format ends in a `422`.

`IntegrationDto.config` for a webhook is `{ url, secret?, events? }`, for mail `{ to, events? }`.
`events` is a subset of `taskCreated`, `taskStatusChanged`, `taskAssigned`, `ruleNotify`; an empty
list or a missing field means all events. An unknown event ends in a `422`, a repeated integration
name in a `409`.

A webhook receives a `POST` with the body `{ event, at, integration, taskKey, message }`, an
`X-Nowtask-Event` header and, when a secret is set, `X-Nowtask-Signature: sha256=<HMAC of the body>`.
The timeout is 5 seconds, and the result of every attempt lands in the integration delivery log.
Event delivery happens off the request thread, so an unresponsive recipient does not slow the
application down.

`/api/integrations` requires the administrator role and is not available to API keys.
`/api/export/**` is available to API keys under the `tasks:read` scope.

## To add: corporate login

Corporate login is delivered through OIDC with Keycloak run in `docker compose`. A button on the
login screen leads to `/oauth2/authorization/nowtask`, the return creates or links an account by
email address. Passkeys are left for later and disappear from the screen until there is an
implementation.

## Split of work across backend modules

| Area | Module |
| --- | --- |
| tasks, subtasks, labels, links, search, filters | `tasks` |
| statuses, transitions, custom fields, epics, workspace settings, views | `workspace` |
| people, roles, teams, invitations, API keys, event log, OIDC | `identity` |
| rules, execution engine, run log | `automation` |
| notifications, integrations, webhooks, mail | `integrations` |
| export | `exports` |
| metrics | `analytics` |

Boundaries unchanged: `automation` and `integrations` do not depend on `tasks`, communication goes
through events in `shared.events`. New events are added to `shared`.

## Migration numbering

So that parallel work does not collide, every area gets its own range:

| Range | Area |
| --- | --- |
| `V3` to `V9` | tasks and workspace |
| `V10` to `V19` | rules and the execution engine |
| `V20` to `V29` | notifications, integrations, export |
| `V30` to `V39` | people, access, event log, OIDC |

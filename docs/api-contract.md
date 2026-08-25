# nowtask API contract

A document binding for the backend and the frontend. The backend implements exactly these paths and
shapes, the frontend codes against them. Changing the contract requires updating this file.

Shared rules:

- Everything under `/api`, session in a cookie, CSRF in the `X-XSRF-TOKEN` header, the active
  organization in the `X-Org-Id` header.
- Errors: `{ status, message, at, code?, detail? }`. 404 does not exist, 422 broken domain rule,
  400 bad input, 409 conflict or missing context, 429 rate limit with a `Retry-After` header.
- Open paths (`/api/auth/signup`, `/api/auth/verify-email`, `/api/auth/forgot-password`,
  `/api/auth/reset-password`, `/api/invites/**`) work without a session but still require
  `X-XSRF-TOKEN` on state changing methods.
- `Accept-Language` picks the language of an outgoing mail. The frontend sends the language chosen
  in the interface, so the message matches what the person sees in the application.
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
GET    /api/organization/members, /teams, /permissions, /api-keys
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
of visible task list columns (`status`, `labels`, `assignee`, `priority`, `due`, `estimate`). The
order of the array is the order of the columns, an omitted code means a hidden column, a missing
field means the default layout. ID and title are always visible and do not appear in this list.
Saving a view validates the codes: an unknown one ends in a 400, a repeated one is skipped.
`columns` does not affect `GET /api/tasks`. Trying to delete a built-in view ends in a 422.

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
GET    /api/workspace/task-fields         -> TaskFieldSettingDto[]
PATCH  /api/workspace/task-fields         {projectId?, fields: {key: true|false|null}} -> TaskFieldSettingDto[]
GET    /api/workspace/task-views          -> TaskViewSettingDto[]
PATCH  /api/workspace/task-views          {projectId?, views: {code: true|false|null}} -> TaskViewSettingDto[]
GET    /api/workspace/epics, POST, PATCH, DELETE
```

Status transitions have to be enforced: `PATCH /api/tasks/{key}` with a status outside an allowed
transition returns a 422 when `blockDisallowedDrag` is on.

### Task fields on and off

`TaskFieldSettingDto` is `{fieldKey, projectId, enabled}` and it says which optional task fields the
organization uses. `projectId = null` is the default for the whole organization, a row with a
`projectId` overrides it for that one project. A field with no row anywhere is on. `GET /api/bootstrap`
carries the same list as `taskFieldSettings`, so the interface knows the answer without another call.

`fieldKey` is one of `description`, `priority`, `assignee`, `reviewer`, `dueDate`, `estimate`,
`epic`, `labels`, `sprint`, or `custom:{fieldKey}` for a custom field. Title, status and project are
always required and have no setting.

In `PATCH` a `true` or `false` writes a setting at the chosen level, `null` removes it, so a project
goes back to inheriting from the organization and the organization goes back to the default. The
call requires `fields.manage`, an unknown key ends in a 422.

A disabled field disappears from the task form, the task detail, the list columns and the filters.
`POST /api/tasks` and `PATCH /api/tasks/{key}` return a 422 when they carry a value for a field that
is off in the project of that task, and so do `POST /api/tasks/{key}/labels` and
`PUT /api/tasks/{key}/custom/{fieldKey}`. An empty value (`null`, an empty string, an empty list) is
ignored rather than rejected, so a client that always sends the whole task does not break.

## To add: people and access

```
PATCH  /api/organization/members/{id}       {role} -> UserDto
DELETE /api/organization/members/{id}
POST   /api/organization/teams              {name} -> TeamDto
PATCH  /api/organization/teams/{id}
DELETE /api/organization/teams/{id}
POST   /api/organization/teams/{id}/members {userId}
DELETE /api/organization/teams/{id}/members/{userId}
POST   /api/organization/api-keys           {label, scopes?, expiresInDays?} -> {key, view: ApiKeyDto}
DELETE /api/organization/api-keys/{id}
GET    /api/organization/audit              ?page=&size= -> { items: AuditDto[], total }
GET    /api/auth/api-key             -> {prefix, label, scopes[], owner: UserDto}
```

`ApiKeyDto`: `{ id, prefix, label, scopes[], ownerId, createdAt, lastUsedAt, expiresAt, revokedAt,
state }`, where `state` is `active | expired | revoked`. The full key is returned once, at creation;
only its hash is stored in the database.

`GET /api/auth/api-key` works only under key authentication and lets an agent learn its own
permissions without access to `/api/organization/**`. For a session it returns a 404.

`AuditDto`: `{ id, at, actorId, apiKeyId, actorLabel, action, subject, detail }`. The event log
records administrative operations, configuration changes and every agent request and denial.

### API key authentication

Requests with an `Authorization: Bearer nt_...` header **do not require a CSRF token**, because they
carry no cookies. Requests with a session cookie still require one. An invalid key ends in a 401
with `reason: "invalid_api_key"` and never silently falls back to the session.

Scopes: `tasks:read`, `tasks:write`, `tasks:delete`, `rules:read`, `rules:run`, `rules:write`,
`metrics:read`, `workspace:read`. Omitting `scopes` at creation grants only the four read ones.
`tasks:delete` is never granted by default. `/api/organization/**` paths are closed to keys regardless of
scopes, and a path not described in the rules is closed by default.

A denial caused by a missing scope returns a 403 with `requiredScope` and `grantedScopes`.

Changes made with a key store `rule_name` in task history in the form
`agent:<key name> (<prefix>)`, so in the interface they are distinguishable from rule actions.

### The AI agents screen

```
GET    /api/agents                   ?activity= -> { agents: AgentDto[], activity: AgentActivityDto[] }
```

`AgentDto`: `{ id, prefix, label, scopes[], lastUsedAt, expiresAt, state }`. This is the same set of
keys as `/api/organization/api-keys`, without `ownerId` and without creation fields, so that the agents
screen can be available to every signed-in role. Key management stays in `/api/organization/api-keys` and
still requires an administrator.

`AgentActivityDto`: `{ id, at, agent, action, method, path, status }`, where `action` is
`agent.task.*`, `agent.rule.*`, `agent.request.*` or `agent.denied`. These are event log entries
that have an `api_key_id`, that is agent traffic only, without human operations. `activity` returns
the 20 most recent entries by default, at most 200.

The path is closed to API keys: an agent cannot read the agent list or anyone else's activity.

## Account signup and address verification

```
POST   /api/auth/signup              {name, email, password} -> SignupResultDto
POST   /api/auth/verify-email        {token} -> UserDto
POST   /api/auth/resend-verification -> 204
POST   /api/auth/forgot-password     {email} -> 204
POST   /api/auth/reset-password      {token, password} -> 204
```

`POST /api/auth/forgot-password` always answers `204`, whether the address is known or not, so the
endpoint cannot be used to check who has an account. A mail goes out only for an active account with
a password, at most three times an hour. The link is valid for one hour.

`POST /api/auth/reset-password` answers `422` with `TOKEN_INVALID`, `TOKEN_USED` or `TOKEN_EXPIRED`,
and `400` when the password is shorter than ten characters. A successful reset closes every other
open link of that account and sends a confirmation mail.

`POST /api/auth/signup` and `POST /api/auth/verify-email` are open paths, without authentication but
with a CSRF token, so the frontend first fetches `GET /api/meta`. Signup returns `201`, creates the
account and opens a session immediately, changing the session identifier so that it cannot be
fixated. The account belongs to no organization yet, so the next step is `POST /api/orgs`.

`SignupResultDto`: `{ user: UserDto, suggestOrg: { id, name, slug } | null }`. A non-empty
`suggestOrg` means the address domain matches an `organization.sso_domain` and the interface shows
the intermediate screen from `docs/onboarding.md`, point 3, step 0.

Error codes: `409 EMAIL_TAKEN`, `400` password shorter than 10 characters or an invalid address,
`422 PASSWORD_TOO_COMMON`, `422 EMAIL_DISPOSABLE`, `429` when the rate limit is exceeded.
Verification: `422 TOKEN_EXPIRED`, `422 TOKEN_USED`, `422 TOKEN_INVALID`.

Whether signup is open is decided by `nowtask.signup.mode` (`open`, `invite-only`, `sso-only`,
`open` by default). The other two modes answer `422 SIGNUP_INVITE_ONLY` or `422 SIGNUP_SSO_ONLY`.

Address verification does not block the wizard. It blocks sending invitations
(`422 EMAIL_NOT_VERIFIED`).

## Account settings

Everything a person can change about their own account. The whole `/api/account` path works without
an organization, so it stays reachable after the last membership is gone. API keys have no access.

```
PATCH  /api/account/profile           {name?, shortName?, initials?} -> UserDto
POST   /api/account/password          {currentPassword, newPassword} -> 204
POST   /api/account/email             {email, password} -> EmailChangeDto
GET    /api/account/email-change      -> EmailChangeDto | 204
DELETE /api/account/email-change      -> 204
GET    /api/account/sessions          -> SessionDto[]
DELETE /api/account/sessions/{id}     -> 204
POST   /api/account/sessions/revoke-others -> { closed }
POST   /api/account/leave             -> 204
POST   /api/account/delete            {password} -> 204
POST   /api/auth/confirm-email-change {token} -> UserDto
```

`PATCH /api/account/profile` recomputes the short name and the initials from `name`, unless the body
carries them explicitly. Initials are cut to three characters.

`POST /api/account/password` checks the current password, applies the same rules as signup, and
closes every other session of that account. Error codes: `422 PASSWORD_INVALID`,
`422 PASSWORD_REUSED`, `422 PASSWORD_TOO_COMMON`, `422 PASSWORD_NOT_SET` for an account that signs in
without a password, `400` when the new password is shorter than ten characters.

Changing the address is a two-step flow. `POST /api/account/email` verifies the password and sends a
confirmation link to the **new** address, valid for 24 hours, at most three times an hour. The old
address stays in force until `POST /api/auth/confirm-email-change` is called, which is an open path
like the other token endpoints. Confirming closes every session of that account, so the next sign-in
uses the new address, and sends a notice to the previous one. Error codes: `409 EMAIL_TAKEN`,
`422 EMAIL_UNCHANGED`, `422 EMAIL_DISPOSABLE`, `422 TOKEN_INVALID`, `422 TOKEN_USED`,
`422 TOKEN_EXPIRED`.

`EmailChangeDto`: `{ newEmail, requestedAt, expiresAt }`. `GET /api/account/email-change` answers
`204` when nothing is pending.

`SessionDto`: `{ id, createdAt, lastSeenAt, ip, userAgent, current }`. The list holds the sessions
seen within `server.servlet.session.timeout` (30 minutes by default); older rows are dropped by an
hourly cleanup. Every request refreshes `last_seen_at` at most once every 30 seconds, and a session
marked as revoked is closed on its next request with `401 SESSION_REVOKED`. Signing out deletes the
row of the current session.

`POST /api/account/leave` removes the membership of the active organization: assignments are dropped,
comments stay. When the person is the last one able to manage members and roles, the answer is `422`
and the membership stays. When they are the only member, the organization is closed
(`organization.state = 'deleted'`).

`POST /api/account/delete` verifies the password, leaves every organization by the same rules, and
then anonymizes the account: the name becomes `Deleted account`, the address is replaced with an
address in the `nowtask.invalid` domain, the password hash and the OIDC subject are cleared and
`app_user.state` becomes `deleted`, which blocks signing in. The row itself stays so that comments,
history and the event log keep an author.

## Invitations and joining

The invitee side, all three paths open without a session:

```
GET    /api/invites/{token}            -> InvitePreviewDto
POST   /api/invites/{token}/accept     {name?, password?} -> AcceptedInviteDto
POST   /api/invites/{token}/request-new -> 204
```

`InvitePreviewDto`: `{ organizationName, organizationSlug, invitedByName, role, maskedEmail,
expiresAt, state, accountExists, ssoAvailable }`, where `state` is
`open | expired | revoked | accepted | unknown`. An unknown token answers `200` with
`state: "unknown"` and empty fields, identical to a revoked one, so that the endpoint is not an
oracle for checking whether an invitation existed.

`POST /api/invites/{token}/accept` creates the account when there is none (`name` and `password`
required), or authenticates an existing one (`password` required), and always creates the
membership. A session for the matching address needs no body. The account created this way is
verified straight away. `AcceptedInviteDto`: `{ organizationId, organizationName, roleCode, user }`;
the frontend follows it with `GET /api/bootstrap`. Errors: `422 INVITE_EXPIRED`,
`422 INVITE_REVOKED`, `422 INVITE_ACCEPTED`, `409 ALREADY_MEMBER`, `409 EMAIL_MISMATCH`,
`409 BAD_CREDENTIALS`.

`POST /api/invites/{token}/request-new` works only for an expired invitation, once a day, and
notifies the person who issued it.

The organization side, all paths require `PERM_MEMBERS_INVITE` and a verified address:

```
GET    /api/organization/invites             ?state= -> InviteDto[]
POST   /api/organization/invites             {email, role} -> InviteDto
POST   /api/organization/invites/bulk        {emails: string[], role} -> BulkInviteResultDto
POST   /api/organization/invites/{id}/resend -> InviteDto
DELETE /api/organization/invites/{id}
```

`InviteDto`: `{ id, email, roleCode, roleName, roleId, state, invitedById, invitedByName, createdAt,
expiresAt }`. `BulkInviteResultDto`: `{ sent: InviteDto[], failed: [{ email, code, messageKey }] }`
with item codes `INVALID_EMAIL`, `ALREADY_MEMBER`, `ALREADY_INVITED`, `LIMIT_REACHED`.

An invitation is valid for 14 days, the limit is 50 per day per organization, and a reminder goes
out once, 7 days after it was issued. The reminder rotates the token, so the earlier link stops
working.

**Issuing an invitation no longer creates an account.** `GET /api/organization/members` merges members with
open invitations and returns a `UserDto` with `pending: true`, an `id` equal to the invitation
identifier and a `name` equal to the address, so the administration screen needs no rebuild.
`DELETE /api/organization/members/{id}` with such an identifier revokes the invitation.

## Onboarding

```
GET    /api/onboarding                -> OnboardingDto, or 204 when there is nothing to show
POST   /api/onboarding/start          -> OnboardingDto
PATCH  /api/onboarding                {step?, dismissed?, tourSeen?} -> OnboardingDto
```

`OnboardingDto`: `{ flow, step, checklist: [{ code, done, at }], tourSeen, completed, dismissed }`.
`flow` is `founder` or `invitee`, `step` is `orgName | preset | project | invite | done` for the
founder and `tour | done` for the invitee.

Checklist items are ticked off by listening to events, never by the API. Sending `checklist` in a
`PATCH` ends in `422 CHECKLIST_READ_ONLY`. A list that is neither finished nor hidden disappears
after 30 days.

`GET /api/bootstrap` carries the same object in the `onboarding` field.

## Work form presets

```
GET    /api/presets                   -> PresetSummaryDto[]
GET    /api/presets/{code}            -> PresetDetailDto
GET    /api/projects                  -> ProjectDto[]
POST   /api/projects                  {name, code?, presetCode?} -> ProjectDto
```

Three preset codes: `scrum`, `kanban`, `waterfall`, plus `custom` for a project without one.
`POST /api/projects` applies the preset in one transaction: statuses, transitions, custom fields with
their value lists, preset views and rules, and for waterfall also two milestones. It requires
`PERM_PROJECTS_MANAGE`. A preset rule whose trigger or action the engine does not know yet is
created as `draft`.

`PresetSummaryDto`: `{ code, statusCount, sprintsEnabled, milestonesEnabled, estimateUnit,
wipEnforced, dependencyGuard, defaultViewCode }`. `PresetDetailDto` adds the full list of statuses,
transitions, fields, view codes and rules, and is what the wizard preview shows.

`SavedViewDto` gains a third class of view: `origin` is `builtin`, `preset` or `user`. Only a
`builtin` view refuses deletion with a `422`.

## Task views on and off

`TaskViewSettingDto` is `{viewCode, projectId, enabled}` and it says which task visualisations the
organization offers. `viewCode` is one of `board`, `list`, `timeline`, `calendar`. The levels work
like the task fields: `projectId = null` is the setting for the whole organization, a row with a
`projectId` overrides it for that one project, a view with no row anywhere is on. `GET /api/bootstrap`
carries the list as `taskViewSettings`.

In `PATCH` a `true` or `false` writes a setting at the chosen level and `null` removes it, so a
project goes back to inheriting and the organization goes back to the default. The call requires
`settings.manage`, an unknown code ends in a 422, and so does a change that would leave the
organization or any active project without a single enabled view.

A view that is off disappears from the side panel, from the view tabs and from the command palette,
and its route sends the person to the first view still available. `my-tasks` is a personal shortcut,
not one of the four visualisations, so it stays reachable whatever is turned off.

## The default view of a user

```
GET    /api/me/default-view          -> {view}
PUT    /api/me/default-view          {view} -> {view}
```

`view` is one of `board`, `list`, `timeline`, `calendar`. It says which view opens on `/app`, the
setting is personal and stored per user and per organization. An unknown code ends in a 400.
`GET /api/bootstrap` carries it in the `defaultView` field. When the organization turns the chosen
view off, the interface falls back to the first view that is still available; the stored preference
stays as it was and comes back once the view is on again.

## To add: navigation personalization

```
GET    /api/me/navigation            -> NavItemDto[]
PUT    /api/me/navigation            {items: NavItemDto[]} -> NavItemDto[]
```

`NavItemDto`: `{ code, hidden }`, where `code` is one of the items in the "Navigation" section of the
side panel: `overview`, `my-tasks`, `board`, `list`, `timeline`, `calendar`, `automations`, `agents`,
`reports`.
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

Each person decides which of them reach them and how:

```
GET    /api/account/notifications  -> NotificationPrefDto[]
PUT    /api/account/notifications  NotificationPrefDto[] -> NotificationPrefDto[]
```

`NotificationPrefDto`: `{ kind, inApp, email }`, where `kind` is `assigned` or `inviteRenewal`. The
default is an entry in the app without a mail. With `email` on, the same message also goes out as a
mail through the `notification` template. An unknown `kind` ends in `422 NOTIFICATION_UNKNOWN`.
Preferences hold for the whole account, across organizations. Security mails, such as a password or
address change, are sent regardless of the settings.

## Export and integrations

```
GET    /api/export/tasks?format=csv|xlsx&<the same filters as /api/tasks>
GET    /api/export/rule-runs?format=csv&ruleId=
GET    /api/integrations             -> IntegrationDto[]
POST   /api/integrations             {kind, name, config} -> IntegrationDto
PATCH  /api/integrations/{id}
DELETE /api/integrations/{id}
POST   /api/integrations/{id}/test   -> {ok, detail}
GET    /api/integrations/github      -> GitHubStatusDto
POST   /api/integrations/github/connect  {installationId, code} -> GitHubStatusDto
DELETE /api/integrations/github      -> GitHubStatusDto
POST   /api/integrations/github/webhook  the endpoint GitHub itself calls
GET    /api/integrations/github/account  -> GitHubAccountDto
POST   /api/integrations/github/account/authorize -> {url}
POST   /api/integrations/github/account/connect   {code, state} -> GitHubAccountDto
DELETE /api/integrations/github/account  -> GitHubAccountDto
GET    /api/integrations/github/task/{taskKey} -> GitHubTaskLinkDto[]
```

Export returns a real file with a `Content-Disposition` header and contains exactly the columns and
rows visible after the filters are applied.

`IntegrationDto.kind`: `webhook`, `email` or `github`. A webhook configuration is an address, a
secret and events. The "notify channel" rule action sends a request to the integration with the given name and
records the result.

Task export gives the columns `Key`, `Title`, `Status`, followed by the list columns from `columns`
in the same order; the `status` code is skipped there because status is already a fixed column. It
takes all rows matching the filters, not just the current page, with a hard limit of 10,000. CSV
comes out in UTF-8 with a BOM so that Excel does not get the encoding wrong.
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

`IntegrationDto.config` for `github` is `{ repos?, projects?, issueRepo?, statusOnBranchCreated?,
statusOnBranchPush?, statusOnPullOpen?, statusOnReviewApproved?, statusOnReviewChangesRequested?,
statusOnPullMerged?, statusOnWorkflowFailure?, statusOnIssueClosed?, statusOnIssueReopened?,
issueClosingStatuses?, syncIssues?, commentOnPull?, commentOnPush?, commentOnIssueComment?,
commentOnReview?, commentOnWorkflow?, commentOnRelease?, commentOnBranch?, assignFromPull?,
linkCommits? }`. The handled events are push, create, delete, pull_request,
pull_request_review, pull_request_review_comment, issues, issue_comment, workflow_run, release and
installation.
Repositories are written as `owner/name`, anything else ends in a `422`; an empty `repos` covers
every repository the installation reaches. `projects` holds task key prefixes such as `NOW`, and it
binds the integration to those projects in both directions: an event only touches tasks whose key
belongs to one of them, and an issue opened from a task is created by the integration covering that
task's project. An empty `projects` covers every project, which is what an existing configuration
without the field keeps doing. The status fields hold status codes, an empty one leaves
the status alone. A `github` integration is never a target of the "notify channel" action and never
receives task events over HTTP, its `test` reports whether the installation answers.

`GitHubStatusDto` is `{ available, installUrl, connected, account, suspended, repositories, detail }`.
`available` says whether the instance has a GitHub App configured at all, `installUrl` leads to the
installation on GitHub, and after the install GitHub returns to the interface with `installation_id`
and `code` in the query, which the frontend passes to `connect`. `connect` verifies through the
OAuth code that the caller really reaches that installation, otherwise it ends in a `403`
(`GITHUB_INSTALLATION_FOREIGN`, or `GITHUB_INSTALLATION_TAKEN` when another organization holds it).

`GitHubAccountDto` is `{ available, connected, login, avatarUrl }` and covers the caller's own
GitHub account, so it needs a session but no administrator role. `authorize` returns the GitHub
consent URL and stores a one time `state` in the session; `connect` refuses with a `403`
(`GITHUB_STATE_MISMATCH`) when the returned state does not match, and with a `409` when that GitHub
account already belongs to somebody else here. A linked account decides who a GitHub comment or
review is written by; without one the work is attributed to the account that connected the
installation.

`GitHubTaskLinkDto` is `{ kind, repo, number?, ref, url, state, title, authorLogin, detail,
checkState, updatedAt }` with `kind` one of `issue`, `pull`, `commit`, `branch`, `release`,
`workflow`. Reading a task's links needs a session only, since it says nothing the task itself does
not.

Automation gains the condition field `github`, whose values are `openPull`, `mergedPull`,
`failedChecks` and `linked`, and the actions `githubComment`, `githubCloseIssue` and `githubLabel`.
A GitHub call that fails leaves the run entry with `run.githubFailed`.

`/api/integrations/github/webhook` is the only endpoint in this group open without a session, and it
authenticates the caller by `X-Hub-Signature-256` (HMAC SHA-256 of the raw body against the instance
webhook secret). A body that does not match ends in a `401` and touches nothing. The organization
comes from the installation identifier in the payload, the work runs off the request thread as the
account that connected the installation, and `X-GitHub-Delivery` is remembered for a week so a
redelivery changes nothing twice.

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
| `V40` to `V49` | organizations, roles, data isolation |
| `V50` to `V59` | work form presets, sprints, field options |
| `V60` to `V69` | onboarding, invitations, address verification |
| `V70` to `V79` | task and view settings, GitHub integration, linked GitHub accounts |

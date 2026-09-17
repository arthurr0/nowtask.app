# Architecture

One installation, many organizations, one API for people and agents. This page describes how
the three parts are built and how they fit together. For the paths and payloads see
[api-contract.md](api-contract.md).

```text
browser ──cookie session──▶ nginx (frontend image) ──/api──▶ backend ──▶ PostgreSQL
agent   ──Bearer nt_...──▶ nginx ──/mcp──▶ MCP server ──/api + Bearer──▶ backend
```

## Backend

A Gradle multi-module Spring Boot 4.1 application on Java 25. Modules talk to each other
through the interfaces in their `api` packages, never through entities, so a module can be
read on its own.

| Module | Owns |
|---|---|
| `shared` | permissions, the organization context, domain exceptions, task query and filter types, the events every module publishes |
| `identity` | users, organizations, memberships, roles, invitations, sessions, email verification, password reset, API keys, the audit log, onboarding progress |
| `workspace` | projects, statuses and transitions, custom fields, labels, epics, saved views, task field and view settings, presets |
| `tasks` | tasks, subtasks, comments, relations, watchers, history, search and filtering |
| `automation` | rules, the rule engine, the run log |
| `analytics` | dashboard metrics |
| `integrations` | in-app notifications, notification preferences, webhooks, mail, the GitHub App |
| `realtime` | the server-sent event stream |
| `exports` | CSV and XLSX export |
| `app` | Spring configuration, security, the tenant data source, Flyway migrations, the entry point |

Endpoints: `/api/auth`, `/api/account`, `/api/orgs`, `/api/organization`, `/api/onboarding`,
`/api/presets`, `/api/projects`, `/api/me`, `/api/bootstrap`, `/api/tasks`, `/api/workspace`,
`/api/rules`, `/api/views`, `/api/search`, `/api/timeline`, `/api/metrics`,
`/api/notifications`, `/api/integrations`, `/api/export`, `/api/events`, `/api/meta`.

### Organizations and row level security

Every table that holds organization data carries `organization_id`, and PostgreSQL row level
security policies compare it with the setting `app.organization_id`. The `TenantDataSource`
wrapper sets `app.user_id` and `app.organization_id` on every connection it hands out, from the
`OrganizationContext` of the current request. The application connects as `nowtask_app`, a
role that does not own the tables, so the policies apply to it; Flyway connects as the owner.

The context is resolved once per request by `OrganizationContextFilter`: from the `X-Org-Id`
header if the client sent one, otherwise from the session, otherwise the first membership of
the user. An API key carries its organization and role with it. A request that reaches a
domain endpoint without an organization is answered with `409 NO_ORGANIZATION`.

`TenantSchemaTest` in the `app` module reads `information_schema` and fails when a table lacks
the column, allows it to be null, or has no policy, with a short allowlist for the tables that
are global by design (users, sessions, invitations, verification tokens).

### Authentication

Two paths, both handled by Spring Security:

- **Session**: `POST /api/auth/login` authenticates and stores the security context in a
  session kept in PostgreSQL by Spring Session JDBC. The cookie is `HttpOnly`, `SameSite=Lax`
  and lives 30 days. Every state changing request has to echo the `XSRF-TOKEN` cookie in the
  `X-XSRF-TOKEN` header. Permissions are granted per request as `PERM_*` authorities from
  the member's role in the active organization.
- **API key**: `Authorization: Bearer nt_<prefix>_<secret>`. The key is looked up by prefix,
  the secret is compared as a SHA-256 hash, and the scopes become `SCOPE_*` authorities.
  `ApiKeyScopeRules` maps every path and method to the scope it needs and closes
  `/api/organization/**` and `/api/auth/**` to keys regardless of scope.

Roles are a set of permissions. Four templates exist (administrator, manager, member, guest)
and an organization can define its own. Endpoints check permissions, never role names.

### Events, rules and realtime

Writes in `tasks` publish Spring application events after the transaction commits. Three
listeners consume them:

- `automation` runs the rules whose trigger matches, in a new transaction, and records a run
  with `ok`, `skipped` or `error`. Actions performed by a rule publish events tagged with the
  rule's name, and the engine ignores those, so a rule cannot trigger itself or another rule
  in a loop.
- `integrations` creates in-app notifications, sends mail and delivers webhooks. Delivery
  runs on a separate thread and every attempt lands in the integration log.
- `realtime` pushes a message to every open `GET /api/events` stream of the organization, so
  the interface updates without polling.

### Database

Flyway with 39 migrations, currently at `V79`. Numbering reserves ranges so that areas
developed in parallel do not collide:

| Range | Area |
|---|---|
| V1 to V19 | base schema |
| V20 to V29 | notifications, integrations, export |
| V30 to V39 | API keys, audit, preferences |
| V40 to V49 | organizations and row level security |
| V50 to V59 | presets and custom fields |
| V60 to V69 | onboarding, invitations, account self-service |
| V70 onwards | later changes in order |

Migrations run as the owner role with `out-of-order: true`, so a lower number added later
still applies. An applied migration is never edited.

## Frontend

Angular 22 with standalone components, signals and zoneless change detection, styled with
Tailwind 4 on top of the tokens in `src/styles.css`.

```text
frontend/src/app/
├── core/       session, guards, interceptor, theme, preferences, i18n, models, dates
├── data/       WorkspaceStore and the feature stores, the only code that calls the API
├── ui/         the design system: fields, dialogs, menus, command palette, icons, logo
└── features/   landing, login, signup, onboarding, shell, board, list, timeline, calendar,
                views, task-detail, task-composer, dashboard, automations, agents,
                settings, organization, system
```

Routes split into public pages (`/`, `/login`, `/signup`, `/forgot-password`,
`/reset-password`, `/invite/:token`, `/verify-email`) and the application under `/app`,
guarded by `authGuard`, `orgGuard` (no membership sends you to `/orgs/new`) and
`onboardingGuard` (a founder who has not finished the wizard goes to `/onboarding`).

`WorkspaceStore` is the single entry point to the API and keeps state in signals; components
read signals and call store methods, nothing else touches `HttpClient`. `GET /api/bootstrap`
loads everything a session needs in one round trip, and the event stream keeps it current.

Theme, accent, density and radius are `data-*` attributes on `<html>`, backed by CSS
variables. `PrefsService` saves them in `localStorage` under `nowtask.prefs`, and a script in
`index.html` restores them before the first paint so nothing flashes. The design system page
at `/app/system` is compiled into development builds only.

The interface speaks Polish, English and German. The dictionaries in `core/i18n` hold about
1,240 keys each, `I18nService.t()` is called straight from templates, and switching the
language re-renders without a reload. Adding a language is a new dictionary file plus an entry
in `DICTIONARIES` and `LANGUAGES`.

## MCP server

A separate TypeScript process in `mcp/`, built on `@modelcontextprotocol/sdk`:

```text
mcp/src/
├── index.ts      entry point, transport selection, HTTP session handling
├── config.ts     environment variables and their validation
├── client.ts     HTTP client for the API, Bearer header, error mapping
├── server.ts     the MCP server instance
├── tools.ts      31 tools with their input schemas and required scopes
├── resources.ts  the nowtask://workspace and nowtask://task/{key} resources
└── scopes.ts     permission scopes and client side checks
```

It has no database access and no knowledge of browser sessions. Everything goes through
`/api/**` with an API key, so an agent cannot do anything the key's scopes do not allow, and
every write it makes is attributed to the key in task history and in the audit log. Over
stdio the key comes from the environment; over Streamable HTTP each client brings its own in
the `Authorization` header and the server binds it to the session. The frontend nginx exposes
the server under `/mcp`, so an agent connects to the same address as the interface.

[ai-agents.md](ai-agents.md) covers keys, scopes and every client; [mcp/README.md](../mcp/README.md)
covers running the server.

## Container images

| Image | Base | Runs as | Health check |
|---|---|---|---|
| backend | `eclipse-temurin:25-jre` | uid 10001 | `GET /actuator/health/readiness` |
| frontend | `nginx:1.29-alpine` | nginx | `GET /` |
| mcp | `node:22-alpine` | `node` | `GET /health` on 8765 |

The frontend nginx resolves the `backend` and `mcp` service names per request rather than at
startup, so the container comes up regardless of the order the services start in.

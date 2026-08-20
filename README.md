<div align="center">

<a href="https://nowtask.app">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset=".github/brand/logo-horizontal-inverse.svg">
    <img src=".github/brand/logo-horizontal.svg" alt="nowtask" width="232">
  </picture>
</a>

<h3>Now, not someday.</h3>

<p>
  Task management for teams: configurable statuses and fields, an automation engine,<br>
  board, list, timeline and dashboard views, dark and light theme, interface in three languages.
</p>

<p>
  <a href="LICENSE"><img alt="License BSL 1.1" src="https://img.shields.io/badge/license-BSL%201.1-55585f?style=flat-square&labelColor=1d1f23"></a>
  <img alt="Angular 22" src="https://img.shields.io/badge/Angular-22-55585f?style=flat-square&labelColor=1d1f23&logo=angular&logoColor=ecedef">
  <img alt="Spring Boot 4.1" src="https://img.shields.io/badge/Spring%20Boot-4.1-55585f?style=flat-square&labelColor=1d1f23&logo=springboot&logoColor=ecedef">
  <img alt="Java 25 LTS" src="https://img.shields.io/badge/Java-25%20LTS-55585f?style=flat-square&labelColor=1d1f23&logo=openjdk&logoColor=ecedef">
  <img alt="PostgreSQL 18" src="https://img.shields.io/badge/PostgreSQL-18-55585f?style=flat-square&labelColor=1d1f23&logo=postgresql&logoColor=ecedef">
  <img alt="MCP 31 tools" src="https://img.shields.io/badge/MCP-31%20tools-55585f?style=flat-square&labelColor=1d1f23">
</p>

<p>
  <img alt="CI backend" src="https://img.shields.io/github/actions/workflow/status/arthurr0/nowtask.app/ci-backend.yml?branch=master&style=flat-square&label=backend&labelColor=1d1f23&color=55585f">
  <img alt="CI frontend" src="https://img.shields.io/github/actions/workflow/status/arthurr0/nowtask.app/ci-frontend.yml?branch=master&style=flat-square&label=frontend&labelColor=1d1f23&color=55585f">
  <img alt="CI mcp" src="https://img.shields.io/github/actions/workflow/status/arthurr0/nowtask.app/ci-mcp.yml?branch=master&style=flat-square&label=mcp&labelColor=1d1f23&color=55585f">
</p>

<p>
  <a href="#running"><b>Running</b></a> &nbsp;·&nbsp;
  <a href="#ai-agents"><b>AI agents</b></a> &nbsp;·&nbsp;
  <a href="#how-it-is-organized"><b>Architecture</b></a> &nbsp;·&nbsp;
  <a href="#documentation"><b>Documentation</b></a> &nbsp;·&nbsp;
  <a href="#license"><b>License</b></a>
</p>

</div>

---

AI agents work here on equal terms with people. The repository holds an MCP server that gives an
agent 31 tools over the same API the interface uses: from searching and editing tasks, through
comments and bulk operations, to automation rules and metrics. An agent authenticates with an API
key with assigned scopes, and every write it makes stays in the task history and in the event log.
Details below, in [AI agents](#ai-agents).

## Status

The project is under construction and has no release yet. It works as a whole: the frontend talks
to the backend, the backend keeps data in Postgres, `docker compose up --build` brings everything
up at once.

| Missing | Where the design lives |
| --- | --- |
| **Tests** | not a single one, despite Vitest being configured on the frontend and a `src/test` module in Gradle |
| **Multi-tenancy** | an installation serves one organization, the target design is in `docs/multi-tenancy.md` |
| **Onboarding and work presets** | designed in `docs/onboarding.md` and `docs/work-presets.md`, not written yet |
| **Corporate login through OIDC** | designed in `docs/onboarding.md`, not in the code yet |

## Stack

| Layer     | Technology                                                                  |
| --------- | --------------------------------------------------------------------------- |
| Frontend  | Angular 22, standalone components, signals, zoneless, Tailwind 4             |
| Backend   | Spring Boot 4.1, Java 25 LTS, Spring Data JPA, JdbcClient, Flyway            |
| Database  | PostgreSQL 18                                                               |
| MCP       | TypeScript, @modelcontextprotocol/sdk, stdio and Streamable HTTP transports  |
| Packaging | Docker images (nginx, JRE, Node) plus compose                               |

## Running

### Everything in Docker

```bash
cp .env.example .env
docker compose up --build
```

| Service | Address |
| --- | --- |
| Frontend | http://localhost:8080 |
| Backend | http://localhost:8081/api/meta |
| MCP server for agents | http://localhost:8080/mcp, directly also http://localhost:8765/mcp |
| Mailpit inbox, which catches outgoing mail | http://localhost:8025 |
| Postgres | localhost:5433 (`nowtask` / `nowtask`), port shifted so it does not clash with a local installation |

Nginx forwards `/api/` to the backend container, so the frontend needs no separate address configuration.

### Separately, for working on the code

```bash
docker compose -f docker-compose.dev.yml up -d    # Postgres alone on port 5433

cd backend && ./gradlew :app:bootRun               # http://localhost:8081
cd frontend && pnpm install && pnpm start          # http://localhost:4200, proxies /api to 8081
```

<details>
<summary><b>Demo accounts</b></summary>

<br>

The `V2__demo_data.sql` migration sets up a workspace with tasks, rules and history. Demo accounts
get a shared password from `NOWTASK_DEMO_PASSWORD` at startup. An empty value turns this mechanism
off, and that is the default outside `docker-compose.yml`.

| Address | Role |
| --- | --- |
| `artur@nowtask.app` | admin |
| `marta@nowtask.app` | manager |
| `piotr@nowtask.app` | member |
| `ola@nowtask.app` | member |
| `jakub@nowtask.app` | member |

</details>

## AI agents

The MCP server in `mcp/` connects an agent to the same API the interface uses. There is no separate
copy of the data, no export and no synchronization: the agent sees the same tasks as the team and
changes them under the same rules.

### What an agent gets

| Group | Tools | Scope |
| --- | --- | --- |
| Read | search with filters, task details, comments, history, timeline, workspace configuration | `tasks:read`, `workspace:read` |
| Write | creating and changing tasks, statuses, comments, labels, links, custom fields | `tasks:write` |
| Subtasks and bulk operations | subtasks, assignment and status change for up to 100 tasks per call | `tasks:write` |
| Automation and metrics | rule list, run log, manual rule run, enabling and disabling, dashboard metrics | `rules:read`, `rules:run`, `rules:write`, `metrics:read` |
| Deletion | permanent deletion of a task or subtask, irreversible | `tasks:delete` |

31 tools in total, prefixed with `nowtask_`, plus two resources: `nowtask://workspace` and
`nowtask://task/{key}`. The full list with required scopes is in `docs/ai-agents.md`.

In the application this maps to the **AI agents** screen (`/app/agents`), an item in the side
navigation next to Automations. The wizard has two variants: connecting without installation, where
the client connects over HTTP to this instance's server, and running the server yourself. Both walk
you through step by step: the API key is created on the spot and drops into the configuration
together with the instance address, the ready snippet can be copied or downloaded as a file, and the
last step shows whether the key has been used yet. Picking a client switches the content between the
`claude mcp add` command, an entry for Claude Desktop and Cursor, and a Docker variant.

Further down the same screen are the connected agents together with key revocation, the tool list
with required scopes, a scope table with permission boundaries, and recent agent activity from
`GET /api/agents`. Every signed-in role can see the screen, but only an administrator creates and
revokes keys.

### Connecting without installation

The MCP server runs alongside the instance and nginx exposes it under `/mcp`, so an agent needs
neither the repository nor Node. The client brings the key in a header, a separate one for each
session:

```bash
claude mcp add --transport http nowtask http://localhost:8080/mcp \
  --header "Authorization: Bearer nt_xxxxxxxx_..."
```

Clients configured through a file get the same entry as `{"type": "http", "url": …, "headers": …}`.
The server validates the key against the API before opening a session, so `initialize` without the
header or with a revoked key ends in a 401. Two agents on one process have their own scopes and
separate traces in task history.

You generate a key in AI agents → New key. The full key is shown once, only the SHA-256 hash stays
in the database.

<details>
<summary><b>Running the server yourself</b></summary>

<br>

If you prefer to keep the server local, run it over `stdio`:

```bash
git clone https://github.com/arthurr0/nowtask.app.git
cd nowtask.app/mcp && npm install && npm run build
```

```json
{
  "mcpServers": {
    "nowtask": {
      "command": "node",
      "args": ["/path/to/nowtask.app/mcp/dist/index.js"],
      "env": {
        "NOWTASK_API_URL": "http://localhost:8081",
        "NOWTASK_API_KEY": "nt_xxxxxxxx_..."
      }
    }
  }
}
```

</details>

### Where an agent's permissions end

Scopes are disjoint and do not contain one another: `tasks:write` grants no right to delete, and
`tasks:delete` has to be granted explicitly. They are checked by the API, not by the MCP server, so
going around it with a custom client gains nothing. Beyond scopes there are hard boundaries:
`/api/admin/**` and `/api/auth/**` are closed to keys, workspace configuration is read only, and a
path outside the list is closed by default.

Every write made with a key leaves a trace. In task history the entry has `ruleName` in the form
`agent:<key name> (<prefix>)`, so it is clear which agent made the change. The event log records the
request together with the method, path and response code, and denials caused by a missing scope are
stored as `agent.denied`.

> Running the server, environment variables and troubleshooting: `mcp/README.md`.<br>
> Keys, scopes and good practices: `docs/ai-agents.md`.

## How it is organized

<details open>
<summary><b>Backend</b></summary>

<br>

Gradle, multi-module. Modules talk to each other through interfaces from `api` packages, not through
entities.

```
backend/
├── shared/        domain exceptions, shared types and roles
├── identity/      users, teams, session, API keys, audit log
├── workspace/     projects, statuses, transitions, custom fields, labels, saved views
├── tasks/         tasks, subtasks, comments, relations, history, watchers
├── automation/    rule engine: triggers, conditions, actions, run log
├── analytics/     dashboard metrics: burndown, throughput, workload
├── integrations/  in-app notifications, outgoing channels: webhooks and mail
├── exports/       exporting tasks and rule runs to CSV and XLSX
└── app/           Spring configuration, security, migrations, entry point
```

Endpoints: `/api/auth`, `/api/me`, `/api/bootstrap`, `/api/tasks`, `/api/workspace`, `/api/rules`,
`/api/views`, `/api/search`, `/api/timeline`, `/api/metrics`, `/api/notifications`,
`/api/integrations`, `/api/export`, `/api/admin`. The contract is described in
`docs/api-contract.md` and is binding for both sides.

Authentication has two paths: a cookie session with CSRF protection for the browser, and an
`Authorization: Bearer nt_...` API key for agents and integrations. A key has permission scopes,
only the SHA-256 hash stays in the database, and every write request made with a key lands in the
audit log.

</details>

<details>
<summary><b>Database schema</b></summary>

<br>

Flyway, 29 tables. Migration numbering has reserved ranges so that areas developed in parallel do
not collide on numbers:

| Range | Area |
| --- | --- |
| V1 to V19 | base schema and current changes |
| V20 to V29 | notifications, integrations, export |
| V30 to V39 | API keys, audit, preferences |
| V40 to V49 | multi-tenancy |
| V50 to V59 | work form presets |
| V60 to V69 | onboarding and invitations |

</details>

<details open>
<summary><b>Frontend</b></summary>

<br>

```
frontend/src/app/
├── core/           session, guard, interceptor, theme, preferences, i18n, models, date formatting
├── data/           workspace.store.ts and feature.stores.ts, the only place that calls the API
├── ui/             design system: fields, dialogs, menus, command palette, notifications, icons
└── features/       landing, login, signup, shell, board, list, timeline, task-detail,
                    task-composer, dashboard, automations, agents, settings, admin, system
```

Routes split into public ones (`/` landing, `/login`, `/signup`) and the application under `/app`
behind `authGuard`. `WorkspaceStore` is the only data access point and keeps state in signals.

All colors, radii and density live in CSS variables in `src/styles.css`. The theme switches the
`data-theme` attribute on `<html>`, and accent, density and rounding work the same way.
`PrefsService` saves the choices in `localStorage`, and a script in `index.html` restores them
before the first render so there is no flash. The **Settings → Appearance** screen controls this
live.

Translations live in `core/i18n/{pl,en,de}.ts`, around 740 keys per language. `I18nService` exposes
a `t()` function called directly in templates, so switching the language refreshes the views without
a reload and without a separate build. Adding a language: a new dictionary file, an entry in
`DICTIONARIES` and in `LANGUAGES`.

Brand assets live in `frontend/public/brand`: the mark, the horizontal logo in both variants, the
favicon set and the OG image. The `ui-logo` component draws the same mark from the theme variables,
so it follows the theme without a second file.

</details>

<details>
<summary><b>Notifications and integrations</b></summary>

<br>

Assigning a task creates a notification for the assignee, and the bell in the header shows the
unread count and the list on click. Outgoing channels are separate, configured in
**Administration → Integrations**: a webhook receives a `POST` with an `X-Nowtask-Signature` header
(HMAC SHA-256 of the body, when you set a secret), a mail integration sends a message over SMTP.
A channel can be named in the "Notify channel" rule action. Every delivery attempt lands in the
integration log together with its result, and delivery happens off the request thread, so a silent
recipient does not slow the application down.

Locally Mailpit from `docker-compose.yml` catches mail, so nothing leaves the machine. Outside
Docker mail is disabled until you set `NOWTASK_MAIL_HOST`.

</details>

<details>
<summary><b>Export</b></summary>

<br>

A button on the task list downloads CSV or XLSX with exactly the rows and columns left after
filtering, with a limit of 10,000 rows. `GET /api/export/rule-runs?format=csv` gives the rule run
log. CSV comes out in UTF-8 with a BOM so that Excel does not get the encoding wrong.

</details>

<details>
<summary><b>MCP server</b></summary>

<br>

```
mcp/src/
├── index.ts      entry point, transport selection, HTTP session handling
├── config.ts     environment variables and their validation
├── client.ts     HTTP client for the nowtask API, Bearer header, error mapping
├── server.ts     MCP server instance
├── tools.ts      31 tools with their input schemas and required scopes
├── resources.ts  the nowtask://workspace and nowtask://task/{key} resources
└── scopes.ts     permission scopes and client side checks
```

A separate process, a separate Docker image, no access to the database or to browser sessions. All
traffic goes through the public `/api/**` with an API key, so an agent does not bypass the rules
that apply to the interface. The user facing description is above, in [AI agents](#ai-agents).

</details>

## Documentation

| File | What it describes |
| --- | --- |
| `docs/api-contract.md` | the API contract, binding for backend and frontend |
| `docs/ai-agents.md` | AI agent access, API keys and permission scopes |
| `docs/multi-tenancy.md` | the design for moving to multiple organizations |
| `docs/work-presets.md` | the design for presets: sprint, kanban, waterfall |
| `docs/onboarding.md` | the design for creating an organization and joining from an invitation |
| `docs/implementation-plan.md` | the order of work for the three areas above |

## License

Business Source License 1.1, full text in [LICENSE](LICENSE).

In short: the code is open, you can read it, change it and run it yourself. Production use is free
for up to ten users in total. Above that threshold, and also when offering nowtask to third parties
as a hosted service, a commercial license is required: kontakt@nowtask.app. Every version switches
automatically to Apache 2.0 on 20 August 2029.

<div align="center">
<br>
<picture>
  <source media="(prefers-color-scheme: dark)" srcset=".github/brand/logo-horizontal-inverse.svg">
  <img src=".github/brand/logo-horizontal.svg" alt="nowtask" width="132">
</picture>

<sub>An open source project. The official instance is run by nowtask.</sub>
</div>

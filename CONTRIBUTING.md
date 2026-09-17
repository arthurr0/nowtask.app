# Contributing to nowtask

Thanks for taking the time. nowtask is a small project with a clear scope, so the fastest way
to get a change merged is to open an issue first and agree on the shape of it before you write
code.

## Development setup

You need Java 25, Node 24 with pnpm 11 for the frontend, Node 22 or newer for the MCP server,
and Docker or Podman for PostgreSQL and the backend tests. The Gradle wrapper downloads Gradle
itself.

```bash
git clone https://github.com/arthurr0/nowtask.app.git
cd nowtask.app
docker compose -f docker-compose.dev.yml up -d        # PostgreSQL 18 on port 5433
cd backend && ./gradlew :app:bootRun                   # http://localhost:8081
cd frontend && pnpm install && pnpm start              # http://localhost:4200, proxies /api
cd mcp && npm install && npm run build                 # the MCP server, see mcp/README.md
```

`docker compose up --build` starts the whole stack instead, with Mailpit catching every mail
at <http://localhost:8025>. [docs/install.md](docs/install.md) covers both paths in detail.

### Commands

| Part | Command | What it does |
|---|---|---|
| backend | `./gradlew build` | compiles every module and runs the tests |
| backend | `./gradlew test` | the tests alone, PostgreSQL comes from Testcontainers |
| backend | `./gradlew :integrations:test` | renders every mail template and leaves previews in `backend/integrations/build/mail-preview` |
| frontend | `pnpm exec prettier --check .` | formatting, the same check CI runs |
| frontend | `pnpm exec ng build --configuration production` | the production build with the bundle budgets |
| mcp | `npm run typecheck` | `tsc --noEmit` |
| mcp | `npm run build` | compiles to `mcp/dist` |

Run the checks for the part you touched before you open a pull request. CI runs the same
commands, plus a container image build for each part.

## Project layout

```text
backend/           Gradle multi-module Spring Boot application
  shared/          domain exceptions, permissions, shared types, events
  identity/        users, organizations, roles, sessions, API keys, audit log
  workspace/       projects, statuses, custom fields, labels, saved views, presets
  tasks/           tasks, subtasks, comments, relations, history, watchers
  automation/      rule engine: triggers, conditions, actions, run log
  analytics/       dashboard metrics
  integrations/    notifications, webhooks, mail, the GitHub App
  realtime/        server-sent events
  exports/         CSV and XLSX export
  app/             Spring configuration, security, migrations, entry point
frontend/          Angular 22 interface
mcp/               the MCP server for AI agents
docs/              documentation and the design records
brand/             logo, favicon, palette
```

[docs/architecture.md](docs/architecture.md) explains how the modules talk to each other, how
the frontend is organized and how the database is scoped per organization.
[docs/api-contract.md](docs/api-contract.md) is the contract between the backend and the
frontend; a change to a path or a payload shape starts there.

## Coding rules

These keep the diff readable and the codebase consistent.

- **No comments.** Not in Java, TypeScript, Kotlin build scripts, YAML, SQL, nginx or the
  Dockerfiles. Name things so the code reads without them, and put the explanation in `docs/`
  or in the commit message where it belongs. Existing migrations under
  `backend/app/src/main/resources/db/migration` are the one place where a comment stays,
  because editing an applied migration changes its Flyway checksum.
- **English only**, everywhere: code, identifiers, log lines, API messages, interface strings,
  documentation and commit messages. The three interface languages live in
  `frontend/src/app/core/i18n` and in the mail bundles, nowhere else.
- Java follows the formatting already in the tree: four spaces, 120 columns, one class per
  file, records for values, constructor injection. No Lombok.
- TypeScript passes `tsc` and Prettier with the configuration in `frontend/` and `mcp/`.
  Standalone components, signals, `inject()`, no `any` unless there is genuinely nothing
  better.
- Every interface string goes through `i18n.t()` and is added to all three dictionaries.
- Every database change is a new Flyway migration in the range reserved for its area, see
  [docs/api-contract.md](docs/api-contract.md#migration-numbering). Never edit an applied
  migration.
- Every table that holds organization data carries `organization_id` and a row level security
  policy. `TenantSchemaTest` fails the build when one is missing.
- Error messages are lowercase, start with the thing that failed, and say what to do next when
  that is knowable.
- New behaviour comes with a test. New user visible behaviour comes with a documentation page
  or a section in an existing one, and an entry in `CHANGELOG.md`.

## Commit messages

Conventional commits:

```text
feat(tasks): add a due date filter to saved views
fix(identity): reject an invite that was already accepted
docs(install): describe the reverse proxy headers
```

Types in use: `feat`, `fix`, `perf`, `docs`, `refactor`, `test`, `build`, `ci`, `chore`. The
scope is the module or the area, for example `tasks`, `identity`, `frontend`, `mcp`, `docs`.
Write the subject in the imperative, under 72 characters, no trailing period. Put the
reasoning in the body if it is not obvious from the diff.

Do not add trailers naming tools or generators.

## Pull requests

Before you open one:

- [ ] the checks for every part you touched pass
- [ ] the branch is rebased on `master` and the history is tidy
- [ ] commits follow the message style above
- [ ] no comments were added to any file
- [ ] every interface string exists in `pl`, `en` and `de`
- [ ] documentation under `docs/` is updated for any user visible change
- [ ] `CHANGELOG.md` has an entry under the unreleased heading for anything a user would notice
- [ ] screenshots are attached if the change touches the interface

Keep a pull request to one topic. A refactor and a feature in the same branch take much longer
to review than the two of them separately.

## Reporting bugs and asking for features

Use the issue forms. The bug form asks for the version from `GET /api/meta`, how you installed
nowtask, which part is involved and the relevant log lines, because without those the first
reply is always the same three questions.

Security problems do not go in an issue. See [SECURITY.md](SECURITY.md).

## Code of conduct

This project follows the [Contributor Covenant](CODE_OF_CONDUCT.md). By participating you
agree to it.

## License

Contributions are accepted under the [Business Source License 1.1](LICENSE) that covers the
project, including its change to Apache 2.0 on the change date.

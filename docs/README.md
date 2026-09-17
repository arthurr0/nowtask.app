# nowtask documentation

nowtask is task management for teams with an API that AI agents use on equal terms with
people. This is the full documentation, one topic per page.

## Start here

| Page | What it covers |
|---|---|
| [Install](install.md) | Published images, building from source, running the parts on the host, reverse proxy, backups |
| [Configuration](configuration.md) | Every environment variable, the two database roles, mail, the GitHub App, security defaults |
| [Architecture](architecture.md) | The backend modules, row level security, authentication, events and rules, the frontend, the MCP server |

## Using nowtask

| Page | What it covers |
|---|---|
| [AI agents](ai-agents.md) | API keys, scopes, connecting Claude Code, Claude Desktop, Cursor, Codex, Gemini CLI and VS Code, the tool list, the trace an agent leaves |
| [Integrations](integrations.md) | In-app notifications, webhooks, Slack and Discord, mail, the GitHub App |
| [MCP server](../mcp/README.md) | Running the server over stdio or HTTP, its variables, error handling, troubleshooting |

## Reference

| Page | What it covers |
|---|---|
| [API contract](api-contract.md) | Every path and payload, binding for the backend and the frontend, migration numbering |
| [Release process](release-process.md) | How a release is cut, what it produces, how to verify it |

## Design records

These were written before the code, as the specification the implementation followed. They
describe the reasoning and the alternatives in more depth than the pages above, and they are
kept as the record of why things are the way they are. Where a record and the code disagree,
the code and the pages above are right.

| Page | What it covers |
|---|---|
| [Multi-tenancy](multi-tenancy.md) | Moving from one company to many organizations in one installation |
| [Work presets](work-presets.md) | Scrum, kanban and waterfall presets and how a project switches between them |
| [Onboarding](onboarding.md) | Creating an organization and joining from an invitation, screen by screen |
| [Implementation plan](implementation-plan.md) | The order the three areas above were built in |

## Conventions used in these pages

- Commands that start with `docker compose` are run from the repository root; commands that
  start with `./gradlew`, `pnpm` or `npm` from `backend/`, `frontend/` or `mcp/`.
- Placeholders are written like `nowtask.example.com`, `nt_9f2c...` and `NOW-12`, and are
  never real values.
- Permission names are written as they appear in the code, for example `TASKS_DELETE`; the
  interface shows their translated labels.

Images referenced by the README and these pages live in `docs/images/`.

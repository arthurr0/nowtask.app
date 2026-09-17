# Changelog

All notable changes to nowtask are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] - 2026-09-17

First release. Everything below is new.

### Added

- Organizations: a single installation serves many independent organizations. Every domain
  table carries `organization_id`, PostgreSQL row level security enforces the boundary under
  the application, and the backend connects as a role that does not own the tables. A person
  can belong to several organizations and switch between them in the shell.
- Accounts: signup with email verification, sign in with a cookie session that survives a
  restart through Spring Session JDBC, password reset, email change with confirmation, account
  deletion, and a signup mode setting of `open`, `invite-only` or `sso-only`.
- Roles and permissions: four built-in roles (administrator, manager, member, guest), custom
  roles composed from eighteen permissions, invitations with reminders, and an organization
  panel for members, teams, roles, invitations and the audit log.
- Onboarding: creating an organization from a preset (scrum, kanban or waterfall), a guided
  wizard, a checklist that tracks the first steps, and an invitation landing page.
- Projects and workspace configuration: statuses grouped into not started, in flight and
  done, allowed transitions, custom fields of type text, number, date, select, person and
  url, protected fields visible only with a permission, labels, epics and sprints.
- Tasks: keys per project, subtasks, comments, relations, watchers, history of every change,
  bulk assignment and status change, and a composer that opens as a panel, a dialog or a
  page according to the user's preference.
- Views: board, list, timeline and calendar over the same data, a saved view builder with
  nested filter groups, grouping and sorting, shared views, per-user default view, and a
  search across everything with a command palette.
- Automation: rules with a trigger (task created, assigned or status changed, or run by
  hand), nested conditions on status, assignee, priority, labels, due dates, estimates and
  custom fields, and actions that set status, priority or due date, assign a person or a
  reviewer, add a label, comment, notify a channel, or comment on, label or close the linked
  GitHub issue. Every run lands in a log with its result.
- Dashboard and reports: overview and report pages with burndown, throughput and workload
  metrics.
- Notifications: an in-app bell with the unread count, per-user notification preferences,
  and mail for assignment and account events.
- Integrations: outgoing webhooks signed with HMAC-SHA256 that detect Slack and Discord
  endpoints and format the payload for them, mail over SMTP, and a delivery log for every
  attempt.
- GitHub App integration: one app per instance installed per organization, task keys
  recognised in branch names, commit messages, pull request titles, issue titles and release
  notes, events that become comments and status changes, issues opened from tasks and kept
  in sync, a GitHub section on the task with its pull requests, issues, commits, branches, CI
  runs and releases, and personal account linking so a review lands as the reviewer.
- Transactional mail: address confirmation, invitation and reminder, welcome, join notice,
  password reset and change confirmation, rendered from Thymeleaf templates as HTML with a
  plain text alternative in Polish, English and German, in light and dark.
- Export: CSV and XLSX of the filtered task list up to 10,000 rows, and CSV of the rule run
  log.
- Realtime: a server-sent event stream at `/api/events` that keeps every open view current
  without polling.
- Interface: Angular 22 with standalone components, signals and zoneless change detection,
  Tailwind 4, light and dark themes, five accents, density and radius settings, and a full
  interface in Polish, English and German.
- API keys and AI agents: `nt_` keys with disjoint scopes (`tasks:read`, `tasks:write`,
  `tasks:delete`, `workspace:read`, `rules:read`, `rules:run`, `rules:write`,
  `metrics:read`), only the SHA-256 hash stored, an AI agents screen that generates the
  client configuration for Claude Code, Claude Desktop, Cursor, Codex CLI, Gemini CLI and VS
  Code, and an audit trail of every write made with a key.
- MCP server: 31 tools prefixed `nowtask_` and two resources, over stdio for a local client
  or Streamable HTTP for a shared deployment where each client brings its own key, exposed by
  the frontend under `/mcp`.
- Deployment: container images for the backend, the frontend and the MCP server published to
  `ghcr.io/arthurr0`, a compose file that builds everything from source with PostgreSQL 18
  and Mailpit, and a production compose file that runs the published images.
- Documentation covering install, configuration, architecture, integrations, AI agents, the
  API contract and the release process, plus the design records for organizations, presets
  and onboarding.

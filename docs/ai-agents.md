# AI agents in nowtask

nowtask exposes itself to AI agents through an MCP (Model Context Protocol) server. An agent gets no
access to the database or to browser sessions: it talks to the same `/api/**` API as the frontend,
authenticating with an API key that carries assigned permission scopes.

```
MCP client  ──stdio / Streamable HTTP──▶  MCP server (mcp/)  ──HTTP + Bearer──▶  nowtask API
```

Server code and startup details: [`../mcp/README.md`](../mcp/README.md).

## 1. Generate an API key

Keys are created by a workspace administrator. Other roles get a 403 from the endpoint.

**In the interface:** the **AI agents** screen → **New key** (the same dialog is in Organization →
API keys). The agents screen walks through the whole setup in four steps: building the server, the
key, client configuration with the instance address and a fresh key already filled in, and finally a
view of whether the key has been used. The configuration can be copied or downloaded as a file, and
picking a client switches between the `claude mcp add` command, an entry for Claude Desktop and
Cursor, and a Docker variant.

In the key creation dialog give it a name (it will be visible in task history and in the event log,
so name the key after the agent, for example "Team assistant"), select the scopes and optionally the
number of days it stays valid.

**The full key is shown exactly once.** Only the SHA-256 hash and the prefix stay in the database,
so reading the key after closing the dialog is impossible. A lost key is revoked and a new one
generated.

The same from the command line, if you prefer (requires a signed-in session and a CSRF token):

```bash
curl -b jar.txt -H "X-XSRF-TOKEN: $XSRF" -H "Content-Type: application/json" \
  -X POST http://localhost:8081/api/organization/api-keys \
  -d '{"label":"Team assistant","scopes":["tasks:read","tasks:write","workspace:read"],"expiresInDays":90}'
```

Response:

```json
{
  "key": "nt_fbe17d9e_arUXp7uv...",
  "view": { "id": "...", "prefix": "nt_fbe17d9e", "label": "Team assistant",
            "scopes": ["tasks:read","tasks:write","workspace:read"],
            "expiresAt": "2026-11-18T…Z", "state": "active" }
}
```

Revoking a key: `DELETE /api/organization/api-keys/{id}`. The key stops working immediately, the entry
stays on the list with state `revoked` so that it is visible what existed and when.

Without `scopes` a key gets a read only set:
`tasks:read`, `workspace:read`, `rules:read`, `metrics:read`.

## 2. Connect an MCP client

### Without installation, over HTTP (recommended)

The MCP server runs alongside the instance and nginx exposes it under `/mcp`, so there is nothing to
download or build. The client brings the key in a header:

```bash
claude mcp add --transport http nowtask https://your-instance/mcp \
  --header "Authorization: Bearer nt_fbe17d9e_arUXp7uv..."
```

A client configured through a file gets the same entry in JSON form:

```json
{
  "mcpServers": {
    "nowtask": {
      "type": "http",
      "url": "https://your-instance/mcp",
      "headers": { "Authorization": "Bearer nt_fbe17d9e_arUXp7uv..." }
    }
  }
}
```

The server validates the key against the API before opening a session: an `initialize` request
without the header or with a revoked key ends in a 401. The key is bound to the session, so two
agents connected to the same process work with their own scopes and leave separate traces in task
history.

The **AI agents** screen in the application generates this command and this JSON with the instance
address and a freshly created key filled in, so in practice copying the snippet is enough.

### stdio (server on your own machine)

```json
{
  "mcpServers": {
    "nowtask": {
      "command": "node",
      "args": ["/path/to/task-board/mcp/dist/index.js"],
      "env": {
        "NOWTASK_API_URL": "http://localhost:8081",
        "NOWTASK_API_KEY": "nt_fbe17d9e_arUXp7uv..."
      }
    }
  }
}
```

The path has to be absolute and `mcp/dist` has to be built (`cd mcp && npm install && npm run
build`). MCP clients usually do not inherit shell environment variables, so the key has to be in the
`env` section.

### Your own HTTP server

The same mode can be hosted yourself, for example for a team on an internal network:

```bash
docker compose up -d mcp
```

The service listens on port 8765 and has no key of its own, so access to the port alone gains
nobody anything: without an `Authorization` header the server rejects `initialize`. The
`NOWTASK_API_KEY` variable in the process environment is optional and works as a fallback key for
clients that do not send the header. **If you set it, anyone who can reach the port operates with
that key's permissions**, so leave it empty everywhere except a closed network.

When exposing the server under your own domain, add its host to `NOWTASK_MCP_ALLOWED_HOSTS`, because
the DNS rebinding protection checks the `Host` header. Nginx from `docker-compose.yml` does this for
you by passing the server its own address.

## 3. Permission scopes

| Scope | What it opens |
| --- | --- |
| `tasks:read` | reading tasks, comments, history, the timeline and search |
| `tasks:write` | creating and changing tasks, subtasks, labels, links, comments, custom fields |
| `tasks:delete` | permanently deleting tasks and subtasks |
| `rules:read` | reading automation rules and their run log |
| `rules:run` | manually running a rule on a given task |
| `rules:write` | enabling and disabling rules |
| `metrics:read` | reading workspace metrics |
| `workspace:read` | reading statuses, epics, people and the rest of the workspace configuration |

Scopes are disjoint and do not contain one another: `tasks:write` grants no right to delete.
`tasks:delete` has to be granted explicitly and is not granted by default.

Permissions are checked on the API side, not in the MCP server. A key request without the required
scope ends in a 403 with `requiredScope` and `grantedScopes` fields in the response body.

Beyond scopes there are hard boundaries that no scope unlocks:

- `/api/organization/**` is closed to API keys. An agent cannot generate itself a key, read the key list,
  change roles or read the event log.
- `/api/auth/login` and `/api/auth/logout` are closed to keys. An agent cannot open a session.
- Workspace configuration (`/api/workspace/**`, `/api/views`) is read only for keys. An agent cannot
  delete a status or a custom field.
- A path outside the list above is closed by default, not open by default.

## 4. Tools and required scopes

| Tool | Scope | Notes |
| --- | --- | --- |
| `nowtask_whoami` | - | key identity and the list of granted scopes |
| `nowtask_workspace` | `workspace:read` | identifier dictionary: statuses, people, epics |
| `nowtask_tasks_search` | `tasks:read` | filters, sorting, paging |
| `nowtask_task_get` | `tasks:read` | task details with subtasks and links |
| `nowtask_task_comments` | `tasks:read` | |
| `nowtask_task_history` | `tasks:read` | |
| `nowtask_task_create` | `tasks:write` | |
| `nowtask_task_update` | `tasks:write` | an omitted field stays unchanged, `null` clears it |
| `nowtask_task_set_status` | `tasks:write` | accepts `statusCode` or `statusId` |
| `nowtask_task_add_comment` | `tasks:write` | a comment cannot be changed or deleted later |
| `nowtask_task_add_label` | `tasks:write` | |
| `nowtask_task_remove_label` | `tasks:write` | |
| `nowtask_task_add_relation` | `tasks:write` | |
| `nowtask_task_remove_relation` | `tasks:write` | |
| `nowtask_task_set_custom_field` | `tasks:write` | |
| `nowtask_task_toggle_watch` | `tasks:write` | a toggle, not a setter |
| `nowtask_subtask_add` | `tasks:write` | |
| `nowtask_subtask_update` | `tasks:write` | |
| `nowtask_subtask_toggle` | `tasks:write` | |
| `nowtask_subtask_delete` | `tasks:delete` | **irreversible** |
| `nowtask_task_delete` | `tasks:delete` | **irreversible**, also removes comments and history |
| `nowtask_tasks_bulk_assign` | `tasks:write` | up to 100 tasks in one call |
| `nowtask_tasks_bulk_status` | `tasks:write` | up to 100 tasks in one call |
| `nowtask_rules_list` | `rules:read` | |
| `nowtask_rule_get` | `rules:read` | |
| `nowtask_rule_runs` | `rules:read` | |
| `nowtask_rule_run` | `rules:run` | the rule really does perform its actions on the task |
| `nowtask_rule_toggle` | `rules:write` | changes workspace behavior for everyone |
| `nowtask_metrics_overview` | `metrics:read` | |
| `nowtask_timeline` | `tasks:read` | |
| `nowtask_search` | `tasks:read` | general search, 5 hits per category |

Resources: `nowtask://workspace` (`workspace:read`) and `nowtask://task/{key}` (`tasks:read`).

## 5. The trace an agent leaves

Every change made with a key is recognizable in two places.

**Task history** (`GET /api/tasks/{key}/history`): the entry has a `ruleName` field in the form
`agent:<key name> (<prefix>)`, for example `agent:Team assistant (nt_fbe17d9e)`. The `actorId` field
points at the account the key was issued for. Entries made by a human have an empty `ruleName`, so
telling them apart is unambiguous.

**Event log** (`GET /api/organization/audit`, for signed-in humans only): every write request made with a
key goes into the log together with the key identifier, method, path and response code. Denials
caused by a missing scope are recorded too, as `agent.denied`, so attempts to step outside the
permissions are visible. Operations on the keys themselves also land in the log: `api-key.created`
and `api-key.revoked`.

The time a key was last used is refreshed on every successful authentication and visible on the key
list as `lastUsedAt`. A key that has not been used for months can safely be revoked.

## 6. Security

**What not to grant an agent**

- `tasks:delete`, until there is a concrete reason. Deletion is irreversible, there is no trash bin,
  and an agent that "tidies up the board" can do damage faster than anyone manages to react. A
  closed task belongs in the "done" status, not in the bin.
- `rules:write`, if the agent is not meant to manage automation. Disabling a rule changes workspace
  behavior for the whole team and is not visible on the board.
- `rules:run` for an agent that only reads and reports. Running a rule manually really does perform
  its actions, including webhooks and outbound notifications.

**Good practices**

- A separate key per agent and per environment. The key name appears in task history, so the entry
  shows who is at fault.
- Always set `expiresInDays`. A key with no expiry lives until someone forgets about it for good.
- Start with the default set (read only), add `tasks:write` when the agent is genuinely meant to
  work, and stop there.
- A key is issued for the account of the administrator who creates it, but scopes cut permissions
  regardless of that account's role. Even so, do not issue keys from an account you would rather not
  see in the `actorId` field on changes.
- A key in an MCP client configuration sits in a file on disk. Treat it like a password: do not
  commit it to a repository, do not paste it into tickets.
- In Streamable HTTP mode listen on `127.0.0.1` unless you have your own authentication in front of
  the server. An unprotected port is an API key handed to everyone who can reach it.
- Revoking a key takes effect immediately and needs no backend restart. It is the first thing to do
  when an agent starts doing something unexpected.

**Why key requests need no CSRF token**

The key travels in the `Authorization` header, not in a cookie, so a browser will not add it by
itself to a cross-site request. CSRF protection is skipped only for requests that carry an
`Authorization: Bearer nt_...` header. A request with just the session cookie still has to have
`X-XSRF-TOKEN`, so the frontend loses no protection.

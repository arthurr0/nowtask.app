# nowtask MCP server

A separate TypeScript process that exposes nowtask to AI agents over the MCP protocol.
It talks to the backend over plain HTTP, authenticating with an API key
(`Authorization: Bearer nt_...`). It has no database access and knows nothing about browser sessions.

Two transports are supported:

- **stdio**: the MCP client starts the process and talks to it over standard input and output.
  This is the default mode for local clients such as Claude Desktop or Claude Code.
- **Streamable HTTP**: the server listens on a port and accepts requests at `/mcp`.
  This is the mode for shared deployments and for `docker compose`.

## Requirements

- Node.js 20 or newer (the Docker image uses 22).
- A running nowtask backend.
- An API key generated in the application: **AI agents → New key**. The same screen walks you
  step by step through connecting a client and fills in the configuration with the instance
  address and a fresh key. The full key is shown **only once**, only a cryptographic hash is
  stored in the database.

## Running

```bash
cd mcp
npm install
npm run build
```

### stdio

```bash
NOWTASK_API_URL=http://localhost:8081 \
NOWTASK_API_KEY=nt_xxxxxxxx_... \
node dist/index.js
```

The process writes nothing to standard output except protocol traffic. Diagnostic messages go to
standard error so that the JSON-RPC stream stays clean.

### Streamable HTTP

```bash
NOWTASK_API_URL=http://localhost:8081 \
NOWTASK_MCP_TRANSPORT=http \
NOWTASK_MCP_HTTP_PORT=8765 \
node dist/index.js
```

In this mode the server handles many clients at once and **has no key of its own**: every client
brings its own in the `Authorization: Bearer nt_...` header on the `initialize` request. The server
validates the key against the API before opening a session and binds it to that session, so two
agents on one process work with their own permissions and leave separate traces in task history.

`NOWTASK_API_KEY` is optional in this mode and acts as a fallback key for clients that do not send
the header.

Endpoint: `POST http://127.0.0.1:8765/mcp`. The first `initialize` request opens a session, the
identifier comes back in the `mcp-session-id` header and has to be attached to every following
request. In addition, `GET /health` returns process status and the number of open sessions.

Connecting a client then comes down to a single command, with nothing to download:

```bash
claude mcp add --transport http nowtask https://your-instance/mcp \
  --header "Authorization: Bearer nt_xxxxxxxx_..."
```

### Docker

```bash
docker compose up mcp
```

The `mcp` service in `docker-compose.yml` starts in HTTP mode on port 8765 and points at the
`backend` service. There is no key to configure anywhere, because the client brings it. The
frontend nginx exposes this server under `/mcp`, so an agent connects to the same address as the
application (locally `http://localhost:8080/mcp`), and the direct port 8765 stays for diagnostics.

## Configuration

| Variable | Required | Default | Meaning |
| --- | --- | --- | --- |
| `NOWTASK_API_KEY` | stdio only | - | nowtask API key, must start with `nt_`. Optional over HTTP: clients bring their own key in the header. |
| `NOWTASK_API_URL` | no | `http://localhost:8081` | Backend address, without a trailing slash. |
| `NOWTASK_MCP_TRANSPORT` | no | `stdio` | `stdio` or `http`. |
| `NOWTASK_MCP_HTTP_HOST` | no | `127.0.0.1` | Listen interface in HTTP mode. |
| `NOWTASK_MCP_HTTP_PORT` | no | `8765` | Listen port in HTTP mode. |
| `NOWTASK_MCP_ALLOWED_HOSTS` | no | - | Additional `Host` header values accepted by the DNS rebinding protection, comma separated. |
| `NOWTASK_API_TIMEOUT_MS` | no | `15000` | Timeout for a single API request. |

On stdio a missing `NOWTASK_API_KEY` ends the process with exit code 1 and a message pointing to
where to generate a key. We do not wait with that until the first tool call. Over HTTP the process
starts without a key, and an `initialize` request without the `Authorization` header gets a 401
with the same hint.

## Tools

The full list of tools together with the scopes they require is in
[`../docs/ai-agents.md`](../docs/ai-agents.md).
In short: 31 tools prefixed with `nowtask_`, from `nowtask_tasks_search` and `nowtask_task_get`,
through writes (`nowtask_task_create`, `nowtask_task_update`, `nowtask_task_set_status`, comments,
subtasks, labels, links, custom fields, bulk operations), to automation rules, metrics and the
timeline.

Every tool declares the scope it requires in its description. When a key lacks that scope, the tool
returns a readable message (the model reads the descriptions) saying which scope has to be granted,
instead of a raw 403.

Irreversible operations (`nowtask_task_delete`, `nowtask_subtask_delete`) carry an explicit warning
in their description and a `destructiveHint` annotation.

## Resources

Besides tools the server exposes two resources:

- `nowtask://workspace`: workspace configuration: projects, statuses, people, epics, views.
- `nowtask://task/{key}`: a single task, for example `nowtask://task/NOW-172`.

Resources make sense where tools do not: an MCP client can attach a task to the conversation as an
attachment that can be quoted and whose reference is visible in the interface, without spending a
turn on a tool call. The template resource listing shows the 50 most recent tasks so that the whole
board is not pulled in.

## Error handling

| Situation | What the tool returns |
| --- | --- |
| Backend unreachable or timed out | A message with the API address and a note that the backend is not responding. No retry loop. |
| 401 | The key is invalid, revoked or expired. A new one has to be generated and the server restarted. |
| 403 with a missing scope | The name of the missing scope, the list of scopes the key has, and instructions on what to ask the administrator for. |
| 403 on a path closed to keys | A note that no scope unlocks it (applies to `/api/organization/**` and session login). |
| 404 | A note that no such task, rule or subtask exists. |
| 400 | Rejected input together with the message from the API. |
| 422 | A broken workspace rule, for example a disallowed status transition. The model is told directly so that it does not repeat the same values. |
| 5xx | A backend side error, to be reported to the user. |

## Troubleshooting

**`Configuration error: NOWTASK_API_KEY environment variable is missing`**
The MCP client did not pass the environment variables. Most clients do not inherit the shell
environment, they pass an explicit `env` section from the configuration instead. Fill it in.

**All tools return "nowtask rejected the API key (401)"**
The key was revoked, expired or comes from a different instance. Generate a new one in
Organization and restart the MCP server. A key cannot be read back after the fact, the database
only holds a hash.

**A write tool reports a missing scope even though the key has it**
The server reads scopes once at startup. After changing a key's permissions (that is, after
generating a new one) the process has to be restarted.

**HTTP mode answers 421 or rejects the request because of the `Host` header**
The DNS rebinding protection is doing its job. Add the host name you use to
`NOWTASK_MCP_ALLOWED_HOSTS`, for example `NOWTASK_MCP_ALLOWED_HOSTS=nowtask-mcp.internal:8765`.

**HTTP mode returns "Missing mcp-session-id header"**
The client skipped the `initialize` request or lost the session identifier. Open the connection
again.

**Inspector does not see the environment variables**
Inspector in command line mode passes only selected variables to the process. Provide the key
explicitly:

```bash
npx @modelcontextprotocol/inspector --cli node mcp/dist/index.js \
  -e NOWTASK_API_URL=http://localhost:8081 \
  -e NOWTASK_API_KEY=nt_xxxxxxxx_... \
  --method tools/list
```

**Agent changes do not show up as agent changes**
In task history an entry made with a key has a `ruleName` field starting with `agent:`, followed by
the key name and its prefix after the colon. The full trace, together with the method, path and
response code, is in the event log under `GET /api/organization/audit`.

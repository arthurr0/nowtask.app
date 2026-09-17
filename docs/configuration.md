# Configuration

The backend reads its configuration from environment variables with the `NOWTASK_` prefix.
`application.yml` maps them onto Spring properties, so anything a Spring Boot application
accepts can also be set the Spring way, but the variables below are the supported surface.
`.env.example` lists the same variables in the shape `docker-compose.prod.yml` expects.

## Application

| Variable | Default | Meaning |
|---|---|---|
| `NOWTASK_APP_URL` | `http://localhost:8080` | The public address of the interface. Every link in a mail is built from it, and the GitHub App callback uses it. |
| `NOWTASK_SIGNUP_MODE` | `open` | Who may create an account: `open` (anyone), `invite-only` (only through an invitation), `sso-only` (only through corporate sign-in, reserved for a future release). |

## Database

| Variable | Default | Meaning |
|---|---|---|
| `NOWTASK_DB_URL` | `jdbc:postgresql://localhost:5433/nowtask` | JDBC address of the database. |
| `NOWTASK_DB_USER` | `nowtask` | The owner role. Flyway runs the migrations as this role. |
| `NOWTASK_DB_PASSWORD` | `nowtask` | Password of the owner role. |
| `NOWTASK_APP_DB_USER` | `nowtask_app` | The runtime role. It does not own the tables, so row level security applies. |
| `NOWTASK_APP_DB_PASSWORD` | `nowtask_app` | Password of the runtime role. Migration `V44` creates the role with it on the first run. |

`docker-compose.prod.yml` additionally reads `NOWTASK_DB_NAME` (default `nowtask`) for the
database created inside the PostgreSQL container, and builds `NOWTASK_DB_URL` from it.

## Mail

| Variable | Default | Meaning |
|---|---|---|
| `NOWTASK_MAIL_HOST` | empty | SMTP host. Empty disables mail entirely: account mail is logged, mail integrations report a delivery error. |
| `NOWTASK_MAIL_PORT` | `1025` | SMTP port. Mailpit listens on 1025, a real relay usually on 587. |
| `NOWTASK_MAIL_USERNAME` | empty | SMTP login. |
| `NOWTASK_MAIL_PASSWORD` | empty | SMTP password. |
| `NOWTASK_MAIL_AUTH` | `false` | Whether to authenticate. `docker-compose.prod.yml` defaults it to `true`. |
| `NOWTASK_MAIL_STARTTLS` | `false` | Whether to require STARTTLS. `docker-compose.prod.yml` defaults it to `true`. |
| `NOWTASK_MAIL_FROM` | `nowtask@localhost` | The sender address. A relay has to allow sending as it. |
| `NOWTASK_MAIL_FROM_NAME` | `nowtask` | The name shown next to the sender address. |
| `NOWTASK_MAIL_REPLY_TO` | empty | An address for replies when the sender is a no-reply mailbox. |

Connection, read and write timeouts are fixed at ten seconds. Delivery happens off the request
thread, so a slow relay does not slow the interface down.

For Zoho Mail as an example: a paid organization sends through `smtppro.zoho.eu` (European
data centre, login at mail.zoho.eu) or `smtppro.zoho.com`, a free plan through `smtp.zoho.eu`
or `smtp.zoho.com`. The username is the full mailbox address and the password is an app
password generated under Settings, Security, App passwords, never the account password.

## GitHub App

All six are required for the GitHub integration and all are empty by default, which leaves the
integration switched off. [integrations.md](integrations.md#github) explains how to register
the app.

| Variable | Meaning |
|---|---|
| `NOWTASK_GITHUB_APP_ID` | The numeric app id from the app settings. |
| `NOWTASK_GITHUB_APP_SLUG` | The URL slug of the app, used to build the install link. |
| `NOWTASK_GITHUB_PRIVATE_KEY` | The PEM private key as GitHub hands it out. Newlines may be escaped as `\n`. |
| `NOWTASK_GITHUB_WEBHOOK_SECRET` | The secret every incoming webhook is signed with. |
| `NOWTASK_GITHUB_CLIENT_ID` | The OAuth client id of the app. |
| `NOWTASK_GITHUB_CLIENT_SECRET` | The OAuth client secret. Proves that whoever finishes an install in the browser owns the installation. |

## MCP server

The MCP server is a separate process with its own variables, documented in
[mcp/README.md](../mcp/README.md#configuration). The ones that matter for a deployment:

| Variable | Default | Meaning |
|---|---|---|
| `NOWTASK_API_URL` | `http://localhost:8081` | Address of the backend, without a trailing slash. |
| `NOWTASK_MCP_TRANSPORT` | `stdio` | `stdio` for one local client, `http` for a shared server. The images set `http`. |
| `NOWTASK_MCP_HTTP_HOST` | `127.0.0.1` | Listen interface in HTTP mode. The images set `0.0.0.0`. |
| `NOWTASK_MCP_HTTP_PORT` | `8765` | Listen port in HTTP mode. |
| `NOWTASK_MCP_ALLOWED_HOSTS` | empty | Extra `Host` header values accepted by the DNS rebinding protection, comma separated. |
| `NOWTASK_API_KEY` | empty | Required on stdio. Optional over HTTP, where it is only a fallback for a client that cannot send the `Authorization` header. |
| `NOWTASK_API_TIMEOUT_MS` | `15000` | Timeout for one API request. |

## Ports in the production compose file

| Variable | Default | Meaning |
|---|---|---|
| `NOWTASK_FRONTEND_PORT` | `8080` | Host port of the interface. The only one that has to be reachable. |
| `NOWTASK_BACKEND_PORT` | `8081` | Host port of the API. Close it on the firewall. |
| `NOWTASK_MCP_PORT` | `8765` | Host port of the MCP server. Close it on the firewall. |
| `IMAGE_TAG` | `latest` | The image tag to run. |

## Session and security defaults

These are fixed in `application.yml` and not exposed as variables:

- The session cookie is `HttpOnly`, `SameSite=Lax`, and lives 30 days. Sessions are stored
  in PostgreSQL through Spring Session JDBC, so a restart does not sign anyone out.
- CSRF protection uses a cookie named `XSRF-TOKEN` that the frontend echoes in the
  `X-XSRF-TOKEN` header. A client driving the API with a session has to do the same; an API
  key does not need it.
- Rate limits per client address: 5 signups, 5 verification resends, 5 password reset
  requests and 5 email changes an hour, 10 logins per 15 minutes, 10 password resets, 10
  password changes and 10 email change confirmations an hour, 20 invitation lookups and
  acceptances an hour. A limited request answers `429` with `Retry-After`.
- Passwords are hashed with bcrypt and have to be at least ten characters. API keys are
  stored as SHA-256 hashes.
- `/actuator/health`, `/actuator/health/**` and `/actuator/info` are open, every other
  actuator endpoint is not exposed.

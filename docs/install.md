# Install

nowtask is three containers and a PostgreSQL database: the backend (Spring Boot, port 8081),
the frontend (nginx serving the Angular build and proxying `/api`, `/api/events` and `/mcp`,
port 8080) and the MCP server (Node, port 8765). The frontend is the only thing a browser or an
agent needs to reach.

## Published images with Docker Compose

Every release publishes three images to the GitHub Container Registry:

| Image | What it runs |
|---|---|
| `ghcr.io/arthurr0/nowtask-backend` | the API, the migrations, the rule engine, mail and webhooks |
| `ghcr.io/arthurr0/nowtask-frontend` | nginx with the interface and the reverse proxy rules |
| `ghcr.io/arthurr0/nowtask-mcp` | the MCP server in Streamable HTTP mode |

Each image is tagged with the version (`0.1.0`), the minor line (`0.1`) and `latest`. Images
are built for `linux/amd64` and `linux/arm64`.

```bash
git clone https://github.com/arthurr0/nowtask.app.git
cd nowtask.app
cp .env.example .env
```

Open `.env` and set at least `NOWTASK_DB_PASSWORD`, `NOWTASK_APP_DB_PASSWORD` and
`NOWTASK_APP_URL`. Compose refuses to start without the two passwords. Then:

```bash
docker compose -f docker-compose.prod.yml up -d
```

The interface is on port 8080, the API on 8081 and the MCP server on 8765, all bound to every
interface of the host, so put a reverse proxy with TLS in front and close 8081 and 8765 on the
firewall. Only 8080 has to be reachable, because nginx inside the frontend container forwards
`/api` and `/mcp` to the other two.

To run a specific version instead of `latest`:

```bash
IMAGE_TAG=0.1.0 docker compose -f docker-compose.prod.yml up -d
```

Upgrading is `docker compose -f docker-compose.prod.yml pull` followed by `up -d`. Flyway
runs the migrations on startup, so a new backend image brings its schema with it.

## Everything from source with Docker Compose

`docker-compose.yml` builds the three images from the working tree and adds Mailpit, which
catches every outgoing mail so nothing leaves the machine:

```bash
docker compose up --build
```

| Service | Address |
|---|---|
| Interface | <http://localhost:8080> |
| API | <http://localhost:8081/api/meta> |
| MCP server | <http://localhost:8080/mcp>, directly <http://localhost:8765/mcp> |
| Mailpit inbox | <http://localhost:8025> |
| PostgreSQL | `localhost:5433`, user and password `nowtask`, moved off 5432 so it does not clash with a local server |

A new database starts empty. Create the first account at `/signup`; the verification mail
lands in Mailpit, and the wizard creates the organization and the first project from a preset.
There is no seeded data and no shared password.

## Running the parts on the host

For work on the code, run PostgreSQL alone and start each part directly:

```bash
docker compose -f docker-compose.dev.yml up -d
cd backend && ./gradlew :app:bootRun
cd frontend && pnpm install && pnpm start
cd mcp && npm install && npm run build && NOWTASK_API_KEY=nt_... node dist/index.js
```

The backend listens on 8081 and expects PostgreSQL on 5433 with the defaults from
`application.yml`. The Angular development server on 4200 proxies `/api` to 8081 through
`proxy.conf.json`. Mail is disabled until `NOWTASK_MAIL_HOST` is set, so account mail is
logged instead of sent; to see it in a browser, keep Mailpit from `docker-compose.yml`
running and set `NOWTASK_MAIL_HOST=localhost` with `NOWTASK_MAIL_PORT=1025`.

The MCP server on the host runs over stdio for a single client, which is what a local Claude
Code or Claude Desktop uses. [mcp/README.md](../mcp/README.md) has both modes.

## Reverse proxy and HTTPS

Put the frontend container behind a proxy that terminates TLS and forwards to port 8080. The
backend reads `X-Forwarded-For` and `X-Forwarded-Proto` (`forward-headers-strategy:
framework`), so the session cookie is marked secure and rate limits see the client address.
Pass the headers through and keep server-sent events unbuffered:

```nginx
server {
  listen 443 ssl http2;
  server_name nowtask.example.com;

  location / {
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_buffering off;
    proxy_read_timeout 1h;
  }
}
```

With Caddy, `reverse_proxy 127.0.0.1:8080` with `flush_interval -1` does the same. Set
`NOWTASK_APP_URL` to the public address, because every link in a mail is built from it.

The MCP endpoint is reachable at `https://nowtask.example.com/mcp` through the same proxy.
The frontend nginx rewrites the `Host` header to `mcp:8765` before forwarding, so the DNS
rebinding check in the MCP server passes without adding the public host name to
`NOWTASK_MCP_ALLOWED_HOSTS`.

## Database roles

Two PostgreSQL roles are involved and both are required:

- `NOWTASK_DB_USER` owns the schema. Flyway connects as this role to run the migrations.
- `NOWTASK_APP_DB_USER` is the role the application uses at runtime. It does not own the
  tables, so PostgreSQL row level security applies to it, and that is what keeps one
  organization from reading another. Migration `V44` creates this role with the password from
  `NOWTASK_APP_DB_PASSWORD` on the first run.

Never point the application at the owner role. It would work, and it would silently disable
the isolation between organizations.

## Health and readiness

`GET /actuator/health/readiness` on the backend answers `200` once the migrations ran and the
database is reachable; the backend image uses it as its health check. `GET /api/meta` answers
without a session and returns the version, so it is the simplest smoke test after an upgrade:

```bash
curl -fsS https://nowtask.example.com/api/meta
```

## Backups

Everything lives in the PostgreSQL database, including sessions and the audit log. The
volume is `db-data` in both compose files. A `pg_dump` of the `nowtask` database is a complete
backup; API keys are stored as hashes, so a dump does not leak them, but it does contain the
webhook secrets and the SMTP password you configured through the interface.

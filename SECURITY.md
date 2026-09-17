# Security policy

## Supported versions

| Version | Supported |
|---|---|
| 0.1.x | yes |
| older | no releases exist |

nowtask is at 0.1.x. Fixes go into the next patch release on that line, and there is no
backporting to earlier tags. Run the latest 0.1.x release before reporting a problem.

## Reporting a vulnerability

Report privately, not in a public issue.

1. Open <https://github.com/arthurr0/nowtask.app/security/advisories/new>, which is the
   "Report a vulnerability" button under the Security tab of the repository.
2. Describe what an attacker can do, the version you tested and the steps to reproduce it. A
   proof of concept, a request log or a short patch all help.
3. You get an acknowledgement within 7 days and an assessment within 14 days.

If GitHub private vulnerability reporting is unavailable to you, write to
<kontakt@nowtask.app> with the same information.

Please give a reasonable window to ship a fix before publishing. Credit is given in the
advisory and the changelog unless you ask otherwise.

## In scope

- Authentication and session handling: login, signup, email verification, password reset,
  the session cookie, the CSRF token, the rate limits on the authentication endpoints.
- Organization isolation: anything that lets a member of one organization read or change data
  of another, through the API, the MCP server, a saved view, an export or a notification.
- Roles and permissions: a permission check that can be bypassed, a role that grants more than
  its permission list, a protected custom field that leaks to someone without
  `FIELDS_VIEW_PROTECTED`.
- API keys: scope enforcement, the hard boundaries around `/api/organization/**` and
  `/api/auth/**`, key storage, the audit trail of a write made with a key.
- The MCP server: a way to reach the API without a valid key, a session that outlives its key,
  the host header check.
- Integrations: the HMAC signature on outgoing webhooks, the signature check on incoming GitHub
  webhooks, the installation ownership check, anything that lets one organization attach
  another's GitHub installation.
- The interface: cross site scripting, clickjacking, anything that lets one signed-in user act
  with another's privileges.
- The container images and the compose files: a default that grants more than the documented
  privileges.

## Out of scope

- Anyone with root on the host, or with the database credentials.
- Running the interface over plain HTTP on a public network, or setting `NOWTASK_APP_URL` to
  something other than the address users actually type.
- Mail delivered to a relay you configured without TLS, or webhooks sent to an endpoint you
  configured without a secret.
- Reports produced only by a scanner, denial of service through sheer volume, missing
  hardening headers with no exploit path, and issues in a dependency that nowtask does not
  reach.

## The trust model in short

The browser signs in with a cookie session and a CSRF token; agents and integrations sign in
with an `nt_` API key that carries scopes, and only the SHA-256 hash of the key is stored.
Every request runs inside one organization, selected by the session or the key, and PostgreSQL
row level security enforces that boundary underneath the application: the backend connects as
a role that does not own the tables and cannot see rows outside the current organization.
Every write made with a key lands in the audit log with the key's name and prefix. The GitHub
App's private key is configuration on the instance, never a row in the database.

The details are in [docs/architecture.md](docs/architecture.md) and
[docs/ai-agents.md](docs/ai-agents.md).

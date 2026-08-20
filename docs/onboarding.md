# Onboarding

A design document. Two ways into the product: **creating an organization** and **joining from an
invitation**. Screen by screen, with states and errors.

Migration ranges for this area: **V60 to V69**.

It assumes `docs/multi-tenancy.md` (organizations, memberships, context in the session) and
`docs/work-presets.md` (three presets, `POST /api/projects`).

---

## 1. Starting state

What already exists and has to be matched:

- **Session with CSRF.** `SecurityConfig` uses `CookieCsrfTokenRepository.withHttpOnlyFalse()`,
  `SessionCreationPolicy.IF_REQUIRED`, context storage in
  `HttpSessionSecurityContextRepository`. `formLogin` and `logout` are disabled, everything goes
  through `AuthController`. The frontend (`AuthService.login`) first does `GET /api/meta` to force
  the `XSRF-TOKEN` cookie, and only then `POST /api/auth/login`.
- **Demo accounts.** On every startup `DemoPasswordInitializer` iterates `users.findAll()` and gives
  a shared password (`NOWTASK_DEMO_PASSWORD`, `demo1234` by default) to every account that has
  `password_hash IS NULL` and is not `pending`.
- **Corporate login.** `docker-compose.yml` brings up Keycloak 26.4 with `--import-realm` and mounts
  `./infra/keycloak`, the backend gets `NOWTASK_OIDC_ISSUER`, `backend/app/build.gradle.kts` has
  `spring-boot-starter-oauth2-client`. **The `infra/keycloak/` directory exists but is empty**, so
  the `nowtask` realm is not created today and `/oauth2/authorization/nowtask` will not work.
- **The login screen.** `frontend/src/app/features/login/login.html`, lines 123 to 132, has two
  `disabled` buttons with no handler: `login.saml` ("Corporate login (SAML)") and `login.passkey`.
  `docs/api-contract.md` talks about OIDC, not SAML, and the passkey is meant to disappear from the
  screen until it is implemented. The `login.forgot` key leads to `<a href="#">`.
- **Routes.** `app.routes.ts` has `signup` (a signup screen hitting `POST /api/auth/signup`, an
  account with the `member` role, a session straight away). There is no `invite/:token`, `orgs/new`
  or `onboarding`.
- **`app_user.pending` and `invited_on`** handle an invitation today as a property of an account
  (`hanna@kontrahent.pl`, `pending = TRUE`). Once organizations exist, those columns disappear
  (`docs/multi-tenancy.md`, migration `V45`) and an invitation becomes a relation.

---

## 2. Data model

### 2.1 The invitation

```sql
CREATE TABLE organization_invite (
    id              UUID        PRIMARY KEY,
    organization_id UUID        NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
    email           TEXT        NOT NULL,
    role            TEXT        NOT NULL,
    token_hash      TEXT        NOT NULL UNIQUE,
    state           TEXT        NOT NULL DEFAULT 'open',
    invited_by      UUID        NOT NULL REFERENCES app_user (id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ NOT NULL,
    accepted_at     TIMESTAMPTZ,
    accepted_by     UUID REFERENCES app_user (id) ON DELETE SET NULL,
    revoked_at      TIMESTAMPTZ,
    reminder_sent_at TIMESTAMPTZ,
    CONSTRAINT invite_role_check  CHECK (role IN ('admin', 'manager', 'member', 'guest')),
    CONSTRAINT invite_state_check CHECK (state IN ('open', 'accepted', 'revoked', 'expired'))
);

CREATE UNIQUE INDEX idx_invite_open_email
    ON organization_invite (organization_id, lower(email)) WHERE state = 'open';
CREATE INDEX idx_invite_org ON organization_invite (organization_id, state);
```

The token never reaches the database in plain form. We generate 32 bytes from `SecureRandom`, encode
them base64url (43 characters), store the `sha256` in `token_hash`, and the plain token goes only
into the body of the mail. A leaked database copy gives no way into the organization.

The partial index does not allow two open invitations for the same address in the same organization.
Inviting the same person again invalidates the previous invitation and issues a new one.

Validity: **14 days**. After that a background job sets `state = 'expired'`.

`organization_invite` has no RLS policy for the same reason as `app_user`
(`docs/multi-tenancy.md`, point 4.5): it has to be read by token before a session exists and before
it is known which organization is involved. Reading by token is confined to the `identity` module,
and an ArchUnit test makes sure the table name does not appear anywhere else.

### 2.2 Address verification

```sql
CREATE TABLE email_verification (
    id          UUID        PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    email       TEXT        NOT NULL,
    token_hash  TEXT        NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ
);

ALTER TABLE app_user
    ADD COLUMN email_verified_at TIMESTAMPTZ,
    ADD COLUMN state             TEXT NOT NULL DEFAULT 'active',
    ADD COLUMN oidc_subject      TEXT UNIQUE,
    ADD CONSTRAINT app_user_state_check CHECK (state IN ('active', 'disabled'));
```

`state` on the account replaces the global part of `pending` from `docs/multi-tenancy.md` (`V45`). A
`disabled` account signs in nowhere, regardless of memberships.

`oidc_subject` stores the `sub` from the identity provider's token. We keep it so that changing the
email address in Keycloak does not create a second account. The column is `UNIQUE`.

Verification link validity: 7 days, with the option to resend.

### 2.3 Onboarding progress

```sql
CREATE TABLE onboarding_progress (
    id              UUID        PRIMARY KEY,
    organization_id UUID        NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
    user_id         UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    flow            TEXT        NOT NULL,
    step            TEXT        NOT NULL DEFAULT 'orgName',
    checklist       JSONB       NOT NULL DEFAULT '{}'::JSONB,
    tour_seen_at    TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    dismissed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, user_id),
    CONSTRAINT onboarding_flow_check CHECK (flow IN ('founder', 'invitee'))
);
```

`step` stores the wizard step the user stopped at, and it is what decides how they resume after
abandoning it. Values: `orgName`, `preset`, `project`, `invite`, `done` for `founder`; `account`,
`tour`, `done` for `invitee`.

`checklist` is a flat object `{ "<code>": "<ISO 8601>" }`, where the key is the code of a checklist
item and the value is the moment it was ticked off. A missing key means "not ticked". JSONB instead
of a table, because the checklist will change a few times over the product's life and I do not want
a migration every time an item is added.

Checklist codes:

| Code | Item | Ticked off by |
| --- | --- | --- |
| `createTask` | Create your first task | `TaskCreated` in the organization, authored by this user |
| `moveTask` | Move a task on the board | `TaskStatusChanged` with this user's `actorId` |
| `inviteMember` | Invite someone to the team | the first `organization_invite` issued by this user |
| `tryAutomation` | Enable an automation rule | `POST /api/rules/{id}/toggle` to `enabled` |
| `customizeFlow` | Adjust the statuses | any change in the project's `status_def` |

Items are ticked off by listening to `shared.events`, not by clicking the list. The checklist is
meant to show what the user actually did, not what they clicked away.

### 2.4 What we are not doing

- **We do not create an `app_user` when an invitation is issued.** Today the demo data has
  `hanna@kontrahent.pl` as a `pending = TRUE` account. After the change the account is created only
  at the moment of acceptance. `GET /api/admin/members` merges members with open invitations and for
  the latter returns a `UserDto` with `pending: true`, an `id` equal to the invitation identifier
  and a `name` equal to the email address. The DTO shape does not change, and
  `DELETE /api/admin/invites/{id}` from `docs/api-contract.md` takes exactly that `id`.
- **There is no separate "team" concept in the wizard.** Teams (`team`) are created later, in
  administration. A wizard that makes you invent a team structure up front gets abandoned.

---

## 3. Flow A: creating an organization

Five steps, two of which can be skipped. A progress bar at the top, "Step 2 of 5".

### Step 0, signup (`/signup`)

Outside the wizard, because it concerns the account, not the organization.

```
┌──────────────────────────────┬──────────────────────────────────┐
│                              │  Create an account               │
│  NOWTASK                     │                                  │
│  Now, not someday            │  Full name        [____________] │
│                              │  Email address    [____________] │
│  (left column as it is       │  Password         [__________ 👁]│
│   on the login screen today) │  ░░░░░░░░░░  strength: good      │
│                              │                                  │
│                              │  [ Create account ]              │
│                              │  ─────────  or  ─────────        │
│                              │  [ 🛡 Corporate login ]          │
│                              │                                  │
│                              │  Have an account? Sign in        │
└──────────────────────────────┴──────────────────────────────────┘
```

The layout inherits the split screen from `login.html` so that a second template does not have to be
built.

Validation and errors:

| Situation | Response | Message |
| --- | --- | --- |
| Address taken | `409` `EMAIL_TAKEN` | "An account with this address already exists. Sign in." with a link |
| Password under 10 characters | `400` | a strength meter under the field, the button inactive |
| Password from the most common list | `422` `PASSWORD_TOO_COMMON` | "This password is too common" |
| Address from a disposable domain | `422` `EMAIL_DISPOSABLE` | "Use a company address" |
| Address matches an `organization.sso_domain` | `200`, but with `{ suggestOrg: {...} }` | an intermediate screen: "Your company already uses nowtask. Ask for an invitation or create a separate organization." with two buttons |

The last row matters more than it looks. Without it five people from the same company create five
separate organizations and the product is useless from day one.

On success: the account is created, a session opens (signup signs you in straight away), the
verification mail goes out in the background, the wizard starts. **Address verification does not
block the wizard.** It blocks three things: sending invitations, creating API keys and outgoing
webhooks. That is enough to keep an account with a fake address from being used to send mail, and it
does not stop a person who wants to see the product.

### Step 1, the organization name (`/orgs/new`)

```
Step 1 of 5  ●○○○○

What is your company called?

  Name       [ nowtask_____________________ ]
  Address    nowtask.app/  [ nowtask_______ ]  ✓ free

  ℹ You can change the name and the address later in settings.

                                    [ Next → ]
```

The `slug` is proposed from the name, editable, checked live through
`GET /api/orgs/slug-available?slug=`. The step is mandatory, `Next` is inactive with an empty name
or a taken address.

The organization **is created in the database only after `Next` is clicked** (`POST /api/orgs`),
together with the creator's membership as `admin`, a `workspace_settings` row, three built-in views
and an `onboarding_progress` row with `flow = 'founder'`, `step = 'preset'`.

From that moment on, abandoning the wizard does not delete the organization. Coming back resumes
from `step`.

### Step 2, the way of working

```
Step 2 of 5  ●●○○○

How does your team work?

┌────────────────┐ ┌────────────────┐ ┌────────────────┐
│ ▦ Sprint       │ │ ▤ Agile        │ │ ▥ Waterfall    │
│   (Scrum)      │ │   (Kanban)     │ │                │
│                │ │                │ │                │
│ Fixed          │ │ A continuous   │ │ Phases with    │
│ iterations,    │ │ stream with a  │ │ sign-off and   │
│ 2 weeks        │ │ WIP limit      │ │ milestones     │
│                │ │                │ │                │
│ 6 statuses     │ │ 5 statuses     │ │ 7 statuses     │
│ points         │ │ no estimates   │ │ days, deps     │
│                │ │                │ │                │
│ [ Preview ]    │ │ [ Preview ]    │ │ [ Preview ]    │
└────────────────┘ └────────────────┘ └────────────────┘
        ○                  ● selected           ○

  Not sure yet → we will pick Agile, you can change it any time

                          [ ← Back ]   [ Next → ]
```

Agile (Kanban) is selected by default, because it is the least committing layout and the easiest one
to move from to the other two. `Preview` expands the list of statuses and transitions from
`GET /api/presets/{code}`, without leaving the step.

The choice is saved in `organization.default_preset_code` and becomes the default for every
subsequent project of that organization.

The step is **skippable in practice** (it always has a default value), but there is no "skip"
button, because clicking `Next` is just as cheap.

### Step 3, the first project

```
Step 3 of 5  ●●●○○

What are you working on?

  Project name  [ Platform______________ ]
  Short code    [ NOW ]   task keys: NOW-1, NOW-2, …

  We will apply the Agile (Kanban) layout: Backlog, Ready,
  In progress, Review, Done.

                          [ ← Back ]   [ Next → ]
```

The step is **mandatory**. Without a project `WorkspaceService.defaultProject()` throws
`NotFoundException("No project defined")` and the whole application does not start, and five
frontend templates (`board.html:3`, `list.html:3`, `automations.html:3`, `task-detail.html:4`,
`timeline.html:3`) read `store.projects()[0].name` with no guard. Until those templates tolerate an
empty list, the wizard has no right to let an organization out without a project.

`POST /api/projects` with the `presetCode` from step 2 creates the project together with the
preset's statuses, transitions, custom fields, views and rules.

The short code is derived from the name (uppercase letters without diacritics, up to 4 characters),
editable, checked for collisions within the organization.

### Step 4, inviting the team

```
Step 4 of 5  ●●●●○

Who are you bringing along?

  [ marta@nowtask.app                       ]  [ Member   ▾ ]  ✕
  [ piotr@nowtask.app                       ]  [ Member   ▾ ]  ✕
  [ + add another address                   ]

  Paste several addresses at once, separated by commas or new lines.

  ⚠ Confirm your address to send invitations.
     We sent a mail to artur@nowtask.app.  [ Resend ]

              [ Skip for now ]        [ Send invitations → ]
```

The step is **skippable**, `Skip for now` is visible and not hidden behind a small link. A one person
team is a normal case.

The verification warning only appears for an unverified address, and then the send button is
inactive. `Skip for now` always works.

The default role: `member`. The dropdown has `manager`, `member`, `guest`. The `admin` role is not in
the wizard, it is granted later in administration, with a deliberate click.

Errors on individual addresses do not block the whole thing: `POST /api/admin/invites/bulk` returns a
list of results and the interface marks in red only the ones that failed (bad format, address
already in the organization, address with an open invitation).

### Step 5, entering the board

There is no fifth wizard screen. `step = 'done'`, a redirect to `/board` with the project from step 3
and the first run of the tour (point 5).

In the side panel, above the navigation, a checklist appears:

```
┌─ Get started ────────────── 1/5 ──┐
│ ✓ Create your first task          │
│ ○ Move a task on the board        │
│ ○ Invite someone to the team      │
│ ○ Enable an automation rule       │
│ ○ Adjust the statuses             │
│                            Hide ✕ │
└───────────────────────────────────┘
```

### Abandoning halfway

| Where they stopped | What is in the database | What they see on return |
| --- | --- | --- |
| before step 1 | only `app_user` | `/orgs/new`, the wizard from step 1 |
| after step 1 | the organization, membership, settings, built-in views, `step = 'preset'` | the wizard from step 2, the organization already named |
| after step 2 | additionally `default_preset_code`, `step = 'project'` | the wizard from step 3 |
| after step 3 | additionally the project with the full preset, `step = 'invite'` | the wizard from step 4 |
| after step 4 or skipping it | `step = 'done'` | `/board`, the wizard does not come back |

Resuming is handled by `orgGuard`: if `GET /api/onboarding` returns a `step` other than `done` for
`flow = 'founder'`, it redirects to `/onboarding` regardless of the requested address.

An organization abandoned after step 1, without a project and without a second member, is reported
after 30 days in the installation's administrative report. **We do not delete it automatically**,
because a customer's data does not disappear through inactivity.

---

## 4. Flow B: joining from an invitation

### Screen 1, arriving from the link (`/invite/{token}`)

A route **without `authGuard`**, because the person may have neither an account nor a session.

```
┌─────────────────────────────────────────────────┐
│                                                 │
│                  ▦ NOWTASK                      │
│                                                 │
│   Marta Wiśniewska invites you to               │
│                                                 │
│                     NOWTASK                     │
│                                                 │
│   Role: Member                                  │
│   Address: piotr@nowtask.app                    │
│                                                 │
│         [ Join the team ]                       │
│                                                 │
│   The invitation expires on 3 September.        │
│                                                 │
└─────────────────────────────────────────────────┘
```

`GET /api/invites/{token}` returns what is needed to draw the screen and **gives away nothing
beyond that**: the organization name, the inviter's first name, the role, a masked address
(`p****@nowtask.app`), the expiry date. No member list, no project count. The token in the address is
like a password and has to be assumed to have leaked into browser history.

Error states, each with its own screen:

| State | Heading | What next |
| --- | --- | --- |
| `expired` | "The invitation has expired" | a "Request a new one" button, which sends a notification to the inviter |
| `revoked` | "The invitation was revoked" | a link to sign in, without details |
| `accepted` | "This invitation has already been used" | a link to sign in |
| unknown token | "We do not know this invitation" | the same screen as `revoked`, deliberately indistinguishable |
| signed in, but with a different address | "You are signed in as artur@…, and the invitation is for piotr@…" | two buttons: "Sign out and continue", "Cancel" |
| signed in, already a member of this organization | "You are already in nowtask" | a "Go to the board" button, the invitation is closed as `accepted` |

Deliberate: an unknown token and a revoked one look the same. Otherwise the link becomes an oracle
for checking whether a given invitation existed.

### Screen 2a, the account exists

We recognize it by an `app_user` with that address.

```
Join nowtask

  You already have a nowtask account on piotr@nowtask.app.

  Password  [ __________________ 👁 ]

  [ Sign in and join ]
  ─────────  or  ─────────
  [ 🛡 Corporate login ]
```

After successful authentication: `organization_member` is created, `organization_invite` moves to
`accepted`, the session switches to the new organization, a redirect to `/board`.

Someone who was already signed in with a matching address does not see this screen. The "Join the
team" button creates the membership straight away.

### Screen 2b, the account does not exist

```
Join nowtask

  Full name        [ _______________ ]
  Email address    [ piotr@nowtask.app ]  (locked)
  Password         [ __________ 👁 ]
  ░░░░░░░░░░  strength: good

  [ Create an account and join ]
  ─────────  or  ─────────
  [ 🛡 Corporate login ]

  By creating an account you accept the terms of service.
```

The address is locked and taken from the invitation. It cannot be changed, because the token was
sent to a specific address.

An account created this way is **verified straight away** (`email_verified_at = now()`), because
access to mail at that address has just been proven by clicking the link. That saves the user a
second visit to their inbox.

On success: the account, the membership, `onboarding_progress` with `flow = 'invitee'` and
`step = 'tour'`, a session, a redirect to `/board` with the tour.

### Screen 2c, corporate login

The button leads to `/oauth2/authorization/nowtask` with a `state` parameter carrying the invitation
token, so that on return we know which organization to add the person to.

Linking rules on return:

1. If the OIDC token has a `sub` known from `app_user.oidc_subject`, that is the account. The email
   address from the token updates `app_user.email`, provided it is not taken.
2. If the `sub` is unknown, `email_verified` in the token is `true` and an account with that address
   exists, we link them: `oidc_subject` gets set.
3. If `email_verified` is `false` or absent, **we do not link**. A provider that does not confirm the
   address would let someone take over another person's account by entering their address in their
   own profile. We show a "Your identity provider does not confirm the address, sign in with a
   password" screen.
4. If there is no account, we create one from the token data (`name`, `email`), without a password,
   `email_verified_at = now()`.

Assignment to an organization on corporate login **without an invitation**: if the address domain
matches an `organization.sso_domain`, the account gets a membership with the `member` role. If it
matches nothing, the account lands on `/orgs/new`. We do not guess the organization from anything
else.

The Keycloak realm is **one per installation** (`nowtask`), not one per organization. A realm per
organization would mean creating realms dynamically from the wizard and a separate
`ClientRegistration` per organization, which Spring Security does not do without a custom
`ClientRegistrationRepository`. Out of scope for this round of work.

**The `infra/keycloak/` directory is empty**, so before any of this works, someone has to add a realm
import file with a `nowtask` client, the return address
`http://localhost:8081/login/oauth2/code/nowtask` and mappings for `email`, `name`,
`email_verified`.

### Screen 3, the first tour

Four steps, an overlay highlighting an element with a bubble. Skippable at any moment (`Esc` or
"Skip").

| Step | Highlights | Content |
| --- | --- | --- |
| 1 | the board columns | "This is where the team works. Drag a card to change its status." |
| 2 | a task card | "Click a task to see details, subtasks and history." |
| 3 | the view bar in the topbar | "The same tasks in four views: board, list, timeline, overview." |
| 4 | the "Automations" item | "Rules do the repetitive steps for you." |

Stored in `onboarding_progress.tour_seen_at`. The tour is shown **once per user per organization**,
not once per account lifetime. Someone who joined a second company usually knows the interface, so
steps 1 and 2 are redundant for them, but the status configuration may differ, so a light reminder
costs four clicks and is not worth building a separate path for.

---

## 5. When we stop showing onboarding

Three independent conditions, any one is enough:

1. **Complete.** All five checklist items ticked off. `completed_at = now()`, the list disappears
   with an animation and does not come back.
2. **Dismissed.** Clicking `Hide`. `dismissed_at = now()`. The list disappears. It can be brought
   back in Settings, the "Help" section, with the "Show the starter list" button.
3. **Time.** 30 days from `created_at`. A background job sets `dismissed_at`. A team that has not
   turned on automation after a month probably does not need it, and a permanent list with three
   unfinished items becomes noise.

The wizard (`step`) and the checklist (`checklist`) are two different things and expire separately.
The wizard cannot be skipped except at step 4, the checklist can be skipped entirely.

For `flow = 'invitee'` the checklist has three items instead of five: `createTask`, `moveTask`,
`tryAutomation`. `inviteMember` and `customizeFlow` are administrative operations, and a person
joining usually has no permission for them (`Permissions.ALL`: `perm.invite` is `no` for the
`member` role, `perm.manageFields` is `no`). Showing an item that cannot be done is a flaw.

---

## 6. What happens to the existing mechanisms

### 6.1 Session and CSRF

No change to the mechanism. Three additions:

- `SecurityConfig` lets through without authentication: `/api/auth/signup`,
  `/api/auth/verify-email`, `/api/auth/resend-verification`, `GET /api/invites/{token}`,
  `POST /api/invites/{token}/accept`. On top of the existing `permitAll` list
  (`/api/auth/login`, `/api/meta`, `/actuator/health*`, `/actuator/info`).
- All those paths are `POST` and require `X-XSRF-TOKEN`, so the frontend has to fetch the cookie
  first. `AuthService.login` already does `GET /api/meta` for exactly that purpose; `signup` and
  invitation acceptance repeat the pattern. It is worth extracting into a single `ensureCsrf()`
  method instead of copying it in three places.
- Signup and invitation acceptance open a session, so they have to call `request.changeSessionId()`
  before saving the context. Without it the session identifier created before authentication
  survives it, which is a session fixation vulnerability. `AuthController.login` has the same
  problem today: it saves the context through `contextRepository.saveContext(...)` without changing
  the session identifier.

### 6.2 Rate limiting

The new paths are open to the world, so:

| Path | Limit |
| --- | --- |
| `POST /api/auth/signup` | 5 per hour per IP address |
| `POST /api/auth/login` | 10 per 15 minutes per email address, then a growing delay |
| `GET /api/invites/{token}` | 20 per hour per IP address |
| `POST /api/auth/resend-verification` | 3 per hour per account |
| `POST /api/admin/invites` and `/bulk` | 50 invitations per day per organization |

The last limit protects against using the product as a mail relay.

### 6.3 `DemoPasswordInitializer`

Today it iterates every account in the installation. Once organizations exist, with public signup,
this is a mechanism we do not want switched on by accident.

The changes, all three necessary:

1. **An organization count condition.** If there is more than one organization in the database, the
   component does nothing and writes a warning to the log. A demonstration installation has one
   organization and will stay that way.
2. **Narrowing to the demo organization.** Instead of `users.findAll()` it iterates over the members
   of organization `00000000-0000-0000-0000-000000000042` (the fixed identifier from
   `docs/multi-tenancy.md`, migration `V40`).
3. **An explicit switch.** A new `nowtask.demo.enabled` property (`false` by default), next to the
   existing `nowtask.demo.password`. An empty password already disables the mechanism today, but
   `application.yml` defaults to `demo1234`, so anyone who does not set `NOWTASK_DEMO_PASSWORD` gets
   a working mechanism without making a deliberate decision. We reverse that: off by default,
   `docker-compose.yml` turns it on explicitly.

Additionally: demo accounts get `email_verified_at` in a migration so that they do not try to send
verification mail to nonexistent `@nowtask.app` addresses.

Condition 1 is the one that really closes the hole. Without it all it takes is someone on a public
installation creating an account through OIDC (an account with no password, `password_hash IS
NULL`), and an application restart gives it `demo1234`.

### 6.4 Mail

`docker-compose.yml` already has `mailpit` on ports 8025 and 1025, and the backend gets
`NOWTASK_MAIL_HOST` and `NOWTASK_MAIL_PORT`. The `integrations` module has
`spring-boot-starter-mail` in its dependencies. Nothing has to be added to the infrastructure.

Three mail templates, all in three languages, chosen by the recipient's language (and for a new
account by the `Accept-Language` header):

| Key | When | Contains |
| --- | --- | --- |
| `mail.verifyEmail` | signup, resend | a link valid for 7 days |
| `mail.invite` | issuing an invitation | the organization name, the inviter's first name, the role, a link valid for 14 days |
| `mail.inviteReminder` | 7 days after issuing, when `state = 'open'` | the same, shorter, once |

Delivery goes through `integrations`, which does not depend on `identity` in the other direction.
`identity` publishes an event in `shared.events` (`InviteIssued`, `EmailVerificationRequested`),
`integrations` receives it. That boundary is already described in `docs/api-contract.md` and I do not
propose touching it.

### 6.5 The login screen

Three fixes in `frontend/src/app/features/login/login.html`:

- the `login.saml` button loses `disabled`, gets `href="/oauth2/authorization/nowtask"` and changes
  its key to `login.sso` with the text "Corporate login". The SAML label is simply untrue, the
  contract talks about OIDC.
- the `login.passkey` button **disappears from the template**, as `docs/api-contract.md` says ("it
  disappears from the screen until there is an implementation"). The key stays in the dictionaries.
- under the form there is already "No account? Create one" leading to `/signup`. Once organizations
  arrive the text changes to "Create an organization".

A fourth thing, outside this document: `login.ts` has `email = 'artur@nowtask.app'` and
`password = 'demo1234'` hardcoded. That has to go before public signup exists anywhere.

---

## 7. Endpoints

The format follows `docs/api-contract.md`.

### Account

```
POST   /api/auth/signup               {name, email, password} -> SignupResultDto
POST   /api/auth/verify-email         {token} -> UserDto
POST   /api/auth/resend-verification  -> 204
```

`SignupResultDto`: `{ user: UserDto, suggestOrg: { id, name, slug } | null }`.
A non-empty `suggestOrg` means the address domain matches an `organization.sso_domain` and the
interface has to show the intermediate screen from step 0.

`POST /api/auth/signup` returns `201` and opens a session. Error codes: `409 EMAIL_TAKEN`,
`422 PASSWORD_TOO_COMMON`, `422 EMAIL_DISPOSABLE`, `429` when the limit is exceeded.

`POST /api/auth/verify-email` also works without a session (the link from the mail may be opened in
another browser). `422 TOKEN_EXPIRED`, `422 TOKEN_USED`.

### Invitations, the invitee side

```
GET    /api/invites/{token}           -> InvitePreviewDto
POST   /api/invites/{token}/accept    {name?, password?} -> BootstrapDto
POST   /api/invites/{token}/request-new -> 204
```

`InvitePreviewDto`:

```
{
  organizationName: string,
  organizationSlug: string,
  invitedByName: string,
  role: RoleId,
  maskedEmail: string,
  expiresAt: string,
  state: "open" | "expired" | "revoked" | "accepted" | "unknown",
  accountExists: boolean,
  ssoAvailable: boolean
}
```

`state: "unknown"` is returned with code `200`, not `404`, and looks identical to `revoked` (an
empty `organizationName`, an empty `invitedByName`). Telling them apart by response code would turn
the endpoint into an oracle.

`POST /api/invites/{token}/accept`:

- when `accountExists` and there is a session for that account: `name` and `password` are ignored,
- when `accountExists` and there is no session: `password` is required, it authenticates and joins,
- when there is no account: `name` and `password` are required, it creates the account,
- returns the `BootstrapDto` of the new organization so that the frontend does not make a second
  round trip,
- `422 INVITE_EXPIRED`, `422 INVITE_REVOKED`, `409 ALREADY_MEMBER`, `401` on a wrong password.

`POST /api/invites/{token}/request-new` works only for `state = 'expired'` and sends a notification
to `invited_by`. Limit: once per day per invitation.

### Invitations, the organization side

`docs/api-contract.md` already has `POST /api/admin/invites {email, role} -> UserDto` and
`DELETE /api/admin/invites/{id}`. We add three operations:

```
GET    /api/admin/invites             ?state= -> InviteDto[]
POST   /api/admin/invites/bulk        {emails: string[], role} -> BulkInviteResultDto
POST   /api/admin/invites/{id}/resend -> InviteDto
```

`InviteDto`: `{ id, email, role, state, invitedById, createdAt, expiresAt }`.

`BulkInviteResultDto`: `{ sent: InviteDto[], failed: [{ email, code, messageKey }] }`.
Item error codes: `INVALID_EMAIL`, `ALREADY_MEMBER`, `ALREADY_INVITED`, `LIMIT_REACHED`.

All three require the issuer's address to be verified (`422 EMAIL_NOT_VERIFIED`) and a role of at
least `manager` (`Permissions.ALL` gives `perm.invite` as `conditional` for `manager`, `yes` for
`admin`).

### Onboarding

```
GET    /api/onboarding                -> OnboardingDto
PATCH  /api/onboarding                {step?, dismissed?, tourSeen?} -> OnboardingDto
```

`OnboardingDto`:

```
{
  flow: "founder" | "invitee",
  step: string,
  checklist: [{ code, done: boolean, at: string | null }],
  tourSeen: boolean,
  completed: boolean,
  dismissed: boolean
}
```

`checklist` arrives as a list, not as a map, so that item order lives on the server and can be
changed without a frontend release.

`PATCH` does not allow ticking off a checklist item. Items are ticked off only by the event
listener. Sending `checklist` in the body is a `400`.

`GET /api/onboarding` is attached to `BootstrapDto` as the `onboarding` field, so that the shell
does not make a second request on every entry.

There are no path collisions: `docs/api-contract.md` uses neither `/api/invites` nor
`/api/onboarding`, and `/api/auth/signup`, `/verify-email`, `/resend-verification` do not collide
with `/login`, `/logout`, `/me`.

---

## 8. Routes and guards in the frontend

```
signup                 Signup           no guard
invite/:token          InviteLanding    no guard
orgs/new               CreateOrg        authGuard
orgs/suspended         OrgSuspended     authGuard
onboarding             OnboardingWizard authGuard + orgGuard
''                     Shell            authGuard + orgGuard + onboardingGuard
```

`onboardingGuard` lets through when `onboarding.step === 'done'` or when `flow === 'invitee'`,
otherwise it moves to `/onboarding`. We do not apply it to `orgs/new`, because that is step 1 of the
wizard.

`authGuard` gets `returnUrl` remembering in a query parameter, which it does not do today
(`frontend/src/app/core/auth.guard.ts` returns `router.createUrlTree(['/login'])` without a
parameter). Without it, a person who landed on `/invite/{token}`, was sent to the login screen and
signed in, ends up on the board instead of coming back to the invitation.

New i18n key spaces: `signup.*`, `invite.*`, `onboarding.*`, `preset.*`, `tour.*`, `mail.*`. Every
key has to be added to `pl.ts`, `en.ts` and `de.ts` at the same time, because `pl.ts` is the source
of the `TranslationKey` type and a missing key in the others is a compilation error.

---

## 9. Changes required in `docs/api-contract.md`

I am not modifying that file. The list of what has to be added to it.

1. **New public paths.** `POST /api/auth/signup`, `POST /api/auth/verify-email`,
   `POST /api/auth/resend-verification`, `GET /api/invites/{token}`,
   `POST /api/invites/{token}/accept`, `POST /api/invites/{token}/request-new`.
   Add to the shared rules that these paths work without a session but still require
   `X-XSRF-TOKEN` on state changing methods.
2. **`GET /api/admin/invites`, `POST /api/admin/invites/bulk`, `POST /api/admin/invites/{id}/resend`.**
3. **`POST /api/admin/invites` returns `InviteDto`, not `UserDto`.** The contract says "pending
   account" today, and after the change no account is created when an invitation is issued.
   `GET /api/admin/members` still returns a `UserDto` with `pending: true` for open invitations so
   that the administration screen needs no rebuild, but issuing an invitation returns the
   invitation.
4. **`GET /api/onboarding`, `PATCH /api/onboarding`** and the `onboarding` field in `BootstrapDto`.
5. **`UserDto` gets `emailVerified: boolean`.** The wizard and administration have to know whether
   invitations may be sent.
6. **Code `429`.** The contract lists `400`, `404`, `422`. The rate limiting from point 6.2 requires
   `429` with a `Retry-After` header.
7. **New error codes** in the `code` field (the field itself is a change from
   `docs/multi-tenancy.md`): `EMAIL_TAKEN`, `PASSWORD_TOO_COMMON`, `EMAIL_DISPOSABLE`,
   `EMAIL_NOT_VERIFIED`, `TOKEN_EXPIRED`, `TOKEN_USED`, `INVITE_EXPIRED`, `INVITE_REVOKED`,
   `ALREADY_MEMBER`.
8. **The "corporate login" section** extended with the account linking rules from point 4, screen
   2c: linking by `sub`, linking by address only when `email_verified = true`, refusal when there is
   no confirmation, one realm per installation.
9. **Passkey.** The contract says the button "disappears from the screen". Confirm that this means
   removal from the template, not `disabled`, because today it is `disabled` and visible.
10. **New events in `shared.events`:** `InviteIssued`, `InviteAccepted`,
    `EmailVerificationRequested`, `MemberJoined`. They are received by `integrations` (mail) and by
    the checklist logic.
11. **Migration numbering.** Add a row: `V60` to `V69`, onboarding, invitations, address
    verification.

---

## 10. Open questions for a human to settle

1. **Is signup open to everyone, or invitation only?** I designed it open (`POST /api/auth/signup`
   with no restrictions beyond the rate limit), because the "first person creating an organization"
   flow requires it. But the same installation may be an internal deployment for one company, where
   open signup is unacceptable. A configuration switch is needed
   (`nowtask.signup.mode: open | invite-only | sso-only`) and a decision on the default value. I am
   not guessing, because a wrong default is either a dead product or an open door.
2. **Does address verification block the wizard, or only invitations?** I chose the latter (point 3,
   step 0), because the first version of the product should be viewable without a trip to the inbox.
   If the product is to have a free plan, blocking immediately is the only sensible choice, because
   otherwise any bot creates organizations without limit.
3. **Terms of service and privacy policy.** The signup screen has the sentence "By creating an
   account you accept the terms of service", but no such documents exist in the repository and I do
   not know whether they should be a link, a checkbox, or there at all. For a company account in the
   European Union this is not cosmetics.
4. **The default role when joining through `sso_domain` without an invitation.** I put down
   `member`. The alternative is `guest` (safer, but the person sees nothing after signing in and
   writes to the administrator) or a queue awaiting approval (safest, but that is a third flow and a
   third screen). The choice depends on who we trust in a company domain.
5. **What happens to an invitation issued to an address that later changes owner inside the
   company?** An invitation lives 14 days, so the window is narrow, but with `sso_domain` and an
   account linked by address this is a real access takeover scenario. I have no basis for deciding
   whether an additional confirmation is worth introducing.

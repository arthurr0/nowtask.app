# Integrations

Everything that leaves nowtask or comes into it from outside: in-app notifications, webhooks,
mail and the GitHub App. All of it is configured under **Organization → Integrations** by
someone with the `INTEGRATIONS_MANAGE` permission, except the GitHub App registration and the
SMTP relay, which are instance configuration.

## In-app notifications

Assigning a task creates a notification for the assignee, and the bell in the header shows the
unread count and the list on click. Each person chooses under **Settings → Notifications**
which events reach them in the app and which by mail.

## Webhooks

A webhook integration receives a `POST` for the events you pick, with a JSON body describing
the task and the change. Set a secret and every request carries an `X-Nowtask-Signature`
header with the HMAC-SHA256 of the body, so the receiver can verify the origin.

The endpoint address decides the format. A Slack or Discord webhook address is recognised and
the payload is rendered as a message with an embed in the nowtask palette; any other address
gets the raw JSON. A **Send test** button on the integration delivers a sample message so the
receiving side can be checked before the first real event.

An automation rule can target a webhook through the **Notify channel** action, so a channel
can be told about exactly the tasks and transitions that matter to it.

Every delivery attempt, successful or not, lands in the integration log with the response
code, and delivery happens off the request thread, so a silent receiver does not slow the
interface down.

## Mail

A mail integration sends a message over SMTP to the addresses you configure, for the same
events and through the same rule action as a webhook. It needs the relay configured on the
instance, see [configuration.md](configuration.md#mail); without `NOWTASK_MAIL_HOST` the
integration reports a delivery error instead of sending.

Account mail runs through the same relay: address confirmation, an invitation and its
reminder, a welcome message once the address is confirmed, a note to the inviter when someone
joins, a password reset link and a confirmation that the password changed. Every message goes
out as HTML with a plain text alternative, in light and dark, with the logo attached inline so
it shows even when the client blocks remote images. The language follows the
`Accept-Language` header, which the frontend fills from the language chosen in the interface.

The templates are Thymeleaf files in `backend/integrations/src/main/resources/templates/mail`,
the wording sits in `resources/mail/messages_{pl,en,de}.properties`, and the shared layout in
`templates/mail/html/layout.html` and `parts.html`. `./gradlew :integrations:test` renders
every template in every language and leaves previews in
`backend/integrations/build/mail-preview`, so a change to the design can be checked in a
browser without sending anything.

## GitHub

The GitHub integration works both ways: what happens in a repository shows up on the task,
and what changes on the task is pushed back to its issue.

### Registering the app

One GitHub App belongs to the whole instance and every organization installs it on its own
GitHub account, so the private key never lands in the database. Register the app at
<https://github.com/settings/apps> with:

- Webhook URL `{NOWTASK_APP_URL}/api/integrations/github/webhook` and a webhook secret.
- Setup URL and callback URL `{NOWTASK_APP_URL}/app/organization?section=integrations`, with
  "Request user authorization during installation" enabled.
- Repository permissions: Issues read and write, Pull requests read, Contents read, Metadata
  read.
- Events: push, pull request, pull request review, pull request review comment, issues, issue
  comment, create, delete, check run, workflow run, release, installation.

Then set the six `NOWTASK_GITHUB_*` variables from [configuration.md](configuration.md#github-app).
The client id and secret are what proves that whoever finishes the install in the browser
really owns the installation; without them a stranger could attach their installation to
someone else's organization.

### Installing it in an organization

With the app configured, **Organization → Integrations** shows an **Install** button. After
the install, a `github` integration names the repositories it covers, the projects it applies
to, the repository where issues opened from tasks land, and the status a task moves to on each
kind of event: a branch created, a push, an opened pull request, an approving review, a review
asking for changes, a merge, a failed CI run, a closed or reopened issue.

One integration per repository is the way to tie a project to its own repository. Name the
project key prefixes in the integration and it works in both directions: a commit in the
backend repository no longer moves a frontend task that its message happens to mention, and
an issue opened from a task lands in the repository belonging to that task's project. Leave
the projects empty and the integration covers everything.

### What flows in and out

A task is recognised by its key in a branch name, a commit message, a pull request title, an
issue title or a release note, so `feature/NOW-12-import` and `NOW-12 fix the importer` both
reach `NOW-12`. The integration listens to pushes, branches created and deleted, pull requests,
reviews and review comments, issues and their comments, finished CI runs and published
releases. What comes in becomes a comment on the task and, when configured, a status change.
Everything is also kept as a link, so the task page has a **GitHub** section listing its pull
requests, issues, commits, branches, CI runs and releases with their state.

What changes on the task is pushed back to its issue. A marker in the issue body together
with a delivery record keeps the two sides from bouncing updates off each other, and every
delivery from GitHub is remembered for a week so a redelivery changes nothing twice. Incoming
webhooks are verified against the webhook secret before anything is read.

### Personal accounts

Each person links their own GitHub account under **Settings → GitHub account**. The consent
asks for identity only, never for code. Once linked, a comment or a review that arrives from
that account is written into the task as that person rather than as whoever connected the
app, and the integration can assign a task to whoever opened the pull request. Someone who
has not linked an account still comes through, attributed to the connecting account.

### Rules

Automation rules can read and write GitHub too. A condition on the `github` field tests for
`openPull`, `mergedPull`, `failedChecks` or `linked`, and the actions comment on the linked
issue or pull request, close the issue or add a label to it.

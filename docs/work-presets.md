# Work form presets

A design document. Three presets: **sprint (scrum)**, **agile (kanban)**, **waterfall**. A preset is
applied when a project is created and can be changed later, moving existing tasks onto the new
statuses with a preview of the mapping before it is applied.

Migration ranges for this area: **V50 to V59**.

It assumes multi-tenancy from `docs/multi-tenancy.md` is in place. A preset always belongs to a
project, and a project always belongs to an organization.

---

## 1. What is missing in today's model

Checked against `V1__init.sql`, `V3` to `V6` and the code of the `workspace` module. Without these
ten things presets cannot be expressed.

| Gap | State today | What I add |
| --- | --- | --- |
| Sprints do not exist as an entity | `task.sprint_code TEXT` with no table, `TaskService.CURRENT_SPRINT = "S24"` as a constant in the code | a `sprint` table, `task.sprint_id` |
| A `select` custom field has no value list | `custom_field` has `type = 'select'`, but the allowed values live nowhere; the frontend has no `options` in `CustomField` either | a `custom_field_option` table |
| An estimate has no unit | `task.estimate INTEGER`, no telling whether it is points, hours or days | `project.estimate_unit` |
| A saved view does not know its project | `saved_view` only has `code`, `query`, `position`, `shared`, `owner_id` | `saved_view.project_id` |
| A project knows neither its preset nor its working rules | `project` has `id`, `name`, `code`, `position` | ten columns, point 2.1 |
| A transition requirement is only a label | `status_transition.requirement` is an i18n key (`transition.reviewerRequired`); `WorkspaceService.transitionAllowed` only checks that the pair exists and ignores `requirement` | a closed dictionary of requirement codes enforced by the backend, point 2.4 |
| A task cannot be tied to a milestone | `milestone` exists, but nothing points at it | `task.milestone_id` |
| The work in progress limit does nothing | `status_def.wip_limit` is only displayed | `project.wip_enforced` and a check in `TaskService` |
| Grouping does not know milestones, sprints or custom fields | the contract allows `status`, `assignee`, `priority`, `epic`, `label` | three new values, point 2.6 |
| A saved view is either built-in or a user's | contract: built-in views have a `code` and an empty `name`, deleting one is a `422` | `saved_view.origin` with a third value `preset`, point 2.5 |

Plus two things outside the schema:

- the automation catalog (`GET /api/rules/catalog` from `docs/api-contract.md`) knows neither the
  `sprintStarted`, `sprintCompleted` nor `scheduled` triggers, and the scrum and kanban presets need
  them,
- `status_def.swatch` is a color, presets have to set it, because the default `var(--c-ink-2)` for
  six statuses gives six identical columns.

---

## 2. Data model

### 2.1 The preset on a project

```sql
ALTER TABLE project
    ADD COLUMN preset_code        TEXT    NOT NULL DEFAULT 'kanban',
    ADD COLUMN preset_applied_at  TIMESTAMPTZ,
    ADD COLUMN preset_customized  BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN sprints_enabled    BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN milestones_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN estimate_unit      TEXT,
    ADD COLUMN wip_enforced       BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN dependency_guard   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN block_disallowed_drag BOOLEAN,
    ADD COLUMN default_view_code  TEXT    NOT NULL DEFAULT 'board',
    ADD CONSTRAINT project_preset_check
        CHECK (preset_code IN ('scrum', 'kanban', 'waterfall', 'custom')),
    ADD CONSTRAINT project_estimate_unit_check
        CHECK (estimate_unit IS NULL OR estimate_unit IN ('points', 'hours', 'days')),
    ADD CONSTRAINT project_default_view_check
        CHECK (default_view_code IN ('board', 'list', 'timeline', 'calendar'));
```

`block_disallowed_drag` is nullable and `NULL` means "inherit from
`workspace_settings.block_disallowed_drag`". That way kanban can loosen the flow in one project
without touching the setting for the whole organization.

`preset_customized` flips to `TRUE` on the first manual change of a status, transition or custom
field in the project. The interface then shows "kanban (modified)".

`preset_code = 'custom'` is a project that never got a preset or deliberately left one.

### 2.2 Sprints

```sql
CREATE TABLE sprint (
    id              UUID PRIMARY KEY,
    organization_id UUID        NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
    project_id      UUID        NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    code            TEXT        NOT NULL,
    name            TEXT        NOT NULL,
    goal            TEXT        NOT NULL DEFAULT '',
    start_date      DATE        NOT NULL,
    end_date        DATE        NOT NULL,
    state           TEXT        NOT NULL DEFAULT 'planned',
    position        INTEGER     NOT NULL DEFAULT 0,
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    UNIQUE (project_id, code),
    CONSTRAINT sprint_state_check CHECK (state IN ('planned', 'active', 'completed')),
    CONSTRAINT sprint_dates_check CHECK (end_date >= start_date)
);

CREATE UNIQUE INDEX idx_sprint_one_active
    ON sprint (project_id) WHERE state = 'active';

ALTER TABLE task ADD COLUMN sprint_id UUID REFERENCES sprint (id) ON DELETE SET NULL;
CREATE INDEX idx_task_org_sprint_id ON task (organization_id, sprint_id);
```

The partial index enforces one active sprint per project. That is a scrum rule and it is better
guarded by the database than by the service layer.

`task.sprint_code` stays for the duration of the migration and disappears only in `V56`. At that
point `TaskService.CURRENT_SPRINT` stops existing, and "the current sprint" becomes
`SELECT id FROM sprint WHERE project_id = ? AND state = 'active'`.

**Irreversible:** once `task.sprint_code` is dropped, sprint membership exists only as a foreign
key. Deleting a sprint sets `sprint_id` to `NULL` and the membership history is lost, unless we
record it in `task_history` (and we do, see point 6.4, step 12).

### 2.3 Select field values

```sql
CREATE TABLE custom_field_option (
    id              UUID    PRIMARY KEY,
    organization_id UUID    NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
    field_id        UUID    NOT NULL REFERENCES custom_field (id) ON DELETE CASCADE,
    value           TEXT    NOT NULL,
    label           TEXT    NOT NULL,
    swatch          TEXT    NOT NULL DEFAULT 'var(--c-ink-2)',
    position        INTEGER NOT NULL DEFAULT 0,
    UNIQUE (field_id, value)
);
```

`value` goes into `task.custom` as the key's value (`{"risk_level": "high"}`), `label` is what is
displayed. The demo data today stores Polish labels directly in `task.custom`
(`"risk_level":"Wysokie"`), which makes translation impossible. Migration `V52` normalizes that to
`high`, `medium`, `low` and creates the matching options.

### 2.4 Transition requirements

`status_transition.requirement` stays `TEXT`, but stops being an arbitrary label and becomes a code
from a closed list. The backend enforces it, the frontend translates it through the
`transition.<code>` key.

| Code | Meaning | What the backend checks |
| --- | --- | --- |
| `reviewerRequired` | requires a reviewer | `task.reviewer_id IS NOT NULL` |
| `roleReviewer` | only the task's reviewer | `context.userId == task.reviewer_id` or the `admin` role |
| `roleManager` | only `manager` or `admin` | `context.role.canManage()` |
| `commentRequired` | requires a comment | at least one `task_comment` newer than the last status change |
| `estimateRequired` | requires an estimate | `task.estimate IS NOT NULL AND task.estimate > 0` |
| `assigneeRequired` | requires an assignee | `task.assignee_id IS NOT NULL` |
| `subtasksDone` | all subtasks ticked off | no `subtask` with `done = FALSE` |
| `sprintRequired` | the task has to be in the active sprint | `task.sprint_id` points at a sprint with `state = 'active'` |
| `dependenciesDone` | predecessors closed | no task `t` such that `task_relation(t -> task, 'blocks')` and the status of `t` is not in the `done` category |
| `approvalRequired` | requires approval | the `approval_ref` custom field in `task.custom` is non-empty |

Three of those codes (`reviewerRequired`, `roleReviewer`, `commentRequired`) are already in the demo
data and in the `pl.ts`, `en.ts`, `de.ts` dictionaries. The other seven are new keys to be added to
the three files at the same time.

An unmet requirement is a `422` with `code: "TRANSITION_REQUIREMENT"` and a body naming the code.
That is a behavior change from today's `WorkspaceService.transitionAllowed`, which skips
`requirement`. Listed in point 9.

### 2.5 Milestones and views

```sql
ALTER TABLE task ADD COLUMN milestone_id UUID REFERENCES milestone (id) ON DELETE SET NULL;
CREATE INDEX idx_task_org_milestone ON task (organization_id, milestone_id);

ALTER TABLE saved_view
    ADD COLUMN project_id UUID REFERENCES project (id) ON DELETE CASCADE,
    ADD COLUMN origin     TEXT NOT NULL DEFAULT 'user',
    ADD CONSTRAINT saved_view_origin_check CHECK (origin IN ('builtin', 'preset', 'user'));

UPDATE saved_view SET origin = 'builtin' WHERE code IS NOT NULL;

ALTER TABLE saved_view DROP CONSTRAINT saved_view_org_code_key;
ALTER TABLE saved_view ADD CONSTRAINT saved_view_org_project_code_key
    UNIQUE NULLS NOT DISTINCT (organization_id, project_id, code);
```

`project_id IS NULL` is a view covering all projects in the organization. The three built-in views
(`view.atRisk`, `view.unassigned`, `view.automated`) stay with `project_id = NULL` and
`origin = 'builtin'`.
`NULLS NOT DISTINCT` is necessary so that two scrum projects in the same organization can each have
their own `view.sprintBoard`, while making it impossible to create two organization-wide views with
the same code.

`origin` is needed because the contract splits views into two disjoint classes today: a built-in one
has a `code` and an empty `name`, and deleting it ends in a `422`. A preset view has a `code`
(because the label comes from the `view.*` i18n key, just like in built-in views) and **has to be
deletable**, because a team that does not use the "Urgent" view has every right to delete it without
abandoning the preset. Without a third value those two requirements exclude each other. The rule
after the change:

| `origin` | `code` | `name` | deletable |
| --- | --- | --- | --- |
| `builtin` | present | empty | no, `422` |
| `preset` | present | empty | yes |
| `user` | absent | present | yes |

### 2.6 Grouping

`docs/api-contract.md` allows five values for `groupBy` today: `status`, `assignee`, `priority`,
`epic`, `label`. Presets need three more:

```
status | assignee | priority | epic | label | milestone | sprint | custom:<fieldKey>
```

A value outside the list is a `400`, just as it is for `sort` today.
`custom:<fieldKey>` groups by `task.custom ->> fieldKey` and requires the field to be of type
`select` or `person`, because grouping by a text field gives as many groups as there are tasks.

Without `milestone` the waterfall preset has no default view (point 5.4), without `sprint` scrum
will not show the board split by iteration, without `custom:` kanban will not group by class of
service.

### 2.7 The preset catalog

The definitions of the three presets **do not live in the database**. They are constants in the
`workspace` module (`app.nowtask.workspace.preset.PresetCatalog`), because they are part of the
product, not customer data. The database only stores the result of applying a preset (`status_def`,
`status_transition`, `custom_field`, `saved_view`, `automation_rule`, the columns on `project`).

The consequence: changing a preset definition in a new version of the application **does not change
projects that already applied it**. To update them, you have to go through the preset change path
from point 5. That is deliberate, because silently changing statuses in a running project after an
upgrade would be unacceptable.

---

## 3. The `scrum` preset

The name in the interface: "Sprint (Scrum)". For a team working in fixed iterations, with estimates
in points and a sprint burndown.

### 3.1 Statuses

| position | code | label (the `status.*` key) | category | wip_limit | swatch |
| --- | --- | --- | --- | --- | --- |
| 0 | `backlog` | Product backlog | `notStarted` | none | `var(--c-line-strong)` |
| 1 | `sprintBacklog` | Sprint backlog | `notStarted` | none | `var(--c-ink-3)` |
| 2 | `inProgress` | In progress | `inFlight` | 8 | `var(--c-ink-2)` |
| 3 | `review` | Review | `inFlight` | 4 | `var(--c-accent)` |
| 4 | `testing` | Testing | `inFlight` | 4 | `var(--c-signal)` |
| 5 | `done` | Done | `done` | none | `var(--c-done)` |

`wip_enforced = FALSE`. In scrum the scope is limited by the sprint, not by the column, so the
limits are a visual warning (the column gets an outline) rather than a block.

### 3.2 Transitions

| from | to | requirement |
| --- | --- | --- |
| `backlog` | `sprintBacklog` | `estimateRequired` |
| `sprintBacklog` | `backlog` | none |
| `sprintBacklog` | `inProgress` | `sprintRequired` |
| `inProgress` | `sprintBacklog` | `commentRequired` |
| `inProgress` | `review` | `reviewerRequired` |
| `review` | `inProgress` | `commentRequired` |
| `review` | `testing` | `roleReviewer` |
| `testing` | `inProgress` | `commentRequired` |
| `testing` | `done` | `subtasksDone` |
| `done` | `inProgress` | `roleManager` |

`block_disallowed_drag = NULL`, that is inherited from the organization (`TRUE` by default).

### 3.3 Custom fields

| name | field_key | type | options | restricted_to_role |
| --- | --- | --- | --- | --- |
| Acceptance criteria | `acceptance_criteria` | `text` | none | none |
| Needs QA | `needs_qa` | `toggle` | none | none |
| Blocked reason | `blocked_reason` | `text` | none | none |
| Team | `team` | `select` | values from the organization's `team.name` | none |

Story points are **not created as a custom field**, because `task.estimate` already exists and it is
what carries the estimate. The `story_points` field from the demo data (a `custom_field` with the
`story_points` key) duplicates `task.estimate` and the scrum preset does not create it.

### 3.4 Views and grouping

| code | label (i18n key) | project_id | query | default_view_code |
| --- | --- | --- | --- | --- |
| `view.sprintBoard` | Sprint board | the project | `{"sprint":"<active>","groupBy":"status"}` | yes, `board` |
| `view.sprintBacklog` | Sprint backlog | the project | `{"statusId":"<sprintBacklog>","sort":"priority"}` | no |
| `view.productBacklog` | Product backlog | the project | `{"statusId":"<backlog>","sort":"priority"}` | no |
| `view.myWork` | My tasks | the project | `{"assigneeId":"<me>","groupBy":"status"}` | no |

`default_view_code = 'board'`, default grouping `status`.

### 3.5 Sprints, milestones, estimates

| Property | Setting |
| --- | --- |
| `sprints_enabled` | `TRUE` |
| sprint length | 14 days, a sprint is created automatically when the preset is applied, code `S1` |
| `milestones_enabled` | `FALSE` |
| `estimate_unit` | `points` |
| allowed estimate values | 1, 2, 3, 5, 8, 13, 21 (Fibonacci), the interface suggests them, the backend does not block others |
| `dependency_guard` | `FALSE` |
| `default_view_code` | `board` |

The sprint burndown (`burndown_point`) is computed for the active sprint from the sum of
`task.estimate`, so the `points` unit is required here. Without `estimate_unit` the chart from
`MetricsService.burndown()` mixes hours with points.

### 3.6 Automation rules

Three rules, all `enabled = TRUE`, `draft = FALSE`, `project_id` set to the project.

| name | trigger_def | conditions | actions |
| --- | --- | --- | --- |
| Work started | `{"kind":"assigned","value":"any"}` | an empty group | `[{"kind":"setStatus","value":"status.inProgress"}]` |
| Handover to review | `{"kind":"statusChanged","value":"status.review"}` | an empty group | `[{"kind":"addWatcher","value":"reviewer"},{"kind":"setDueDate","value":"+2d"}]` |
| Sprint wrap-up | `{"kind":"sprintCompleted"}` | `{"kind":"group","join":"and","children":[{"field":"statusCategory","op":"neq","value":"done"}]}` | `[{"kind":"moveToNextSprint"}]` |

The `sprintCompleted` trigger and the `moveToNextSprint` action do not exist in the catalog from
`docs/api-contract.md`. Point 9.

---

## 4. The `kanban` preset

The name in the interface: "Agile (Kanban)". For a team working as a stream, without iterations,
with a hard work in progress limit.

### 4.1 Statuses

| position | code | label | category | wip_limit | swatch |
| --- | --- | --- | --- | --- | --- |
| 0 | `backlog` | Backlog | `notStarted` | none | `var(--c-line-strong)` |
| 1 | `ready` | Ready | `notStarted` | 10 | `var(--c-ink-3)` |
| 2 | `inProgress` | In progress | `inFlight` | 5 | `var(--c-ink-2)` |
| 3 | `review` | Review | `inFlight` | 3 | `var(--c-accent)` |
| 4 | `done` | Done | `done` | none | `var(--c-done)` |

`wip_enforced = TRUE`. Moving a task into a column where the task count has reached `wip_limit` ends
in a `422` with `code: "WIP_LIMIT"` and the number in the message. The exception: the `admin` role
gets a `409` asking for confirmation and can exceed the limit deliberately, which is recorded in
`task_history` with a `wipOverride` field.

This is the only preset where the limit does anything. Without it kanban is not kanban.

### 4.2 Transitions

| from | to | requirement |
| --- | --- | --- |
| `backlog` | `ready` | none |
| `ready` | `backlog` | none |
| `ready` | `inProgress` | `assigneeRequired` |
| `inProgress` | `ready` | `commentRequired` |
| `inProgress` | `review` | none |
| `review` | `inProgress` | `commentRequired` |
| `review` | `done` | `roleReviewer` |
| `done` | `inProgress` | `roleManager` |

`block_disallowed_drag = FALSE`. Kanban is meant to be flexible in order but strict on limits.
Jumping from `backlog` straight to `inProgress` is allowed (there is no transition, so with
`block_disallowed_drag = FALSE` it goes through with a warning), but the WIP limit will stop it if
the column is full. Those are two different safeguards and they deliberately work independently.

### 4.3 Custom fields

| name | field_key | type | options (`value` : `label`) | restricted_to_role |
| --- | --- | --- | --- | --- |
| Class of service | `class_of_service` | `select` | `standard` : Standard, `expedite` : Expedite, `fixedDate` : Fixed date, `intangible` : Intangible | none |
| Blocked reason | `blocked_reason` | `text` | none | none |
| Team | `team` | `select` | values from the organization's `team.name` | none |

`class_of_service` is the first place where `custom_field_option` is genuinely needed. Without it the
preset would have to write the four values as free text.

### 4.4 Views and grouping

| code | label (i18n key) | query | default |
| --- | --- | --- | --- |
| `view.flow` | Flow | `{"groupBy":"status"}` | yes, `board` |
| `view.blocked` | Blocked | `{"label":"blocked","groupBy":"assignee"}` | no |
| `view.expedite` | Urgent | `{"groupBy":"status"}` with the filter `custom:class_of_service = expedite` | no |
| `view.myWork` | My tasks | `{"assigneeId":"<me>","groupBy":"status"}` | no |

The `view.expedite` view requires filtering by a custom field, which `TaskQuery` cannot do today.
Point 9.

### 4.5 Sprints, milestones, estimates

| Property | Setting |
| --- | --- |
| `sprints_enabled` | `FALSE` |
| `milestones_enabled` | `FALSE` |
| `estimate_unit` | `NULL`, estimates disabled |
| `wip_enforced` | `TRUE` |
| `dependency_guard` | `FALSE` |
| `default_view_code` | `board` |

Estimates are disabled deliberately. Kanban measures cycle time, and `MetricsService` already
computes it (`tasks.averageCycleTimeDays()` from `task.started_at` and `completed_at`). The estimate
field in the task form is then hidden, and `task.estimate` stays in the database and accepts values
through the API, so that changing the preset does not erase data.

### 4.6 Automation rules

| name | trigger_def | conditions | actions |
| --- | --- | --- | --- |
| Work started | `{"kind":"assigned","value":"any"}` | status in the `notStarted` category | `[{"kind":"setStatus","value":"status.inProgress"}]` |
| Blocked signal | `{"kind":"labelAdded","value":"blocked"}` | an empty group | `[{"kind":"addWatcher","value":"manager"},{"kind":"notifyChannel","value":"#blocked"}]` |
| Backlog has gone stale | `{"kind":"scheduled","value":"daily"}` | status `backlog` and `updated_at` older than 60 days | `[{"kind":"addLabel","value":"stale"}]` |

The `scheduled` trigger does not exist in the catalog. Point 9. Task NOW-211 in the demo data
("Time based rules: reminders before the due date") describes exactly this need, so the kanban preset
cannot be delivered before time based rules.

---

## 5. The `waterfall` preset

The name in the interface: "Waterfall". For phase based work with sign-offs, milestones and
dependencies between tasks.

### 5.1 Statuses

| position | code | label | category | wip_limit | swatch |
| --- | --- | --- | --- | --- | --- |
| 0 | `new` | Reported | `notStarted` | none | `var(--c-line-strong)` |
| 1 | `analysis` | Analysis | `inFlight` | none | `var(--c-ink-3)` |
| 2 | `design` | Design | `inFlight` | none | `var(--c-ink-2)` |
| 3 | `implementation` | Implementation | `inFlight` | none | `var(--c-accent)` |
| 4 | `verification` | Sign-off | `inFlight` | none | `var(--c-signal)` |
| 5 | `done` | Closed | `done` | none | `var(--c-done)` |
| 6 | `onHold` | On hold | `notStarted` | none | `var(--c-warn)` |

`wip_enforced = FALSE`, there are no limits. In phase based work all the tasks of a phase move
together.

### 5.2 Transitions

Forward, one phase at a time:

| from | to | requirement |
| --- | --- | --- |
| `new` | `analysis` | `dependenciesDone` |
| `analysis` | `design` | `approvalRequired` |
| `design` | `implementation` | `approvalRequired` |
| `implementation` | `verification` | `subtasksDone` |
| `verification` | `done` | `roleManager` |

Backward, one phase at a time, always with a justification:

| from | to | requirement |
| --- | --- | --- |
| `analysis` | `new` | `commentRequired` |
| `design` | `analysis` | `commentRequired` |
| `implementation` | `design` | `commentRequired` |
| `verification` | `implementation` | `commentRequired` |
| `done` | `verification` | `roleManager` |

Putting on hold and coming back, two rows per phase:

| from | to | requirement |
| --- | --- | --- |
| `analysis` | `onHold` | `commentRequired` |
| `design` | `onHold` | `commentRequired` |
| `implementation` | `onHold` | `commentRequired` |
| `verification` | `onHold` | `commentRequired` |
| `onHold` | `analysis` | none |
| `onHold` | `design` | none |
| `onHold` | `implementation` | none |
| `onHold` | `verification` | none |

18 rows in `status_transition` in total. The model stores directed pairs, so "any phase to on hold"
has to be spelled out. That is a limitation of today's model and I do not propose changing it,
because a shortcut like `from_status IS NULL` meaning "from any" breaks the foreign key and the
indexes.

`block_disallowed_drag = TRUE`, fixed, not inherited. A waterfall that does not guard the order of
phases stops being a waterfall.

### 5.3 Custom fields

| name | field_key | type | options | restricted_to_role |
| --- | --- | --- | --- | --- |
| Phase owner | `phase_owner` | `person` | none | none |
| Approval number | `approval_ref` | `text` | none | `manager` |
| Risk | `risk_level` | `select` | `low` : Low, `medium` : Medium, `high` : High | none |
| Cost | `cost_pln` | `currency` | none | `manager` |
| Planned end | `planned_end` | `date` | none | none |

`approval_ref` is the field the `approvalRequired` requirement from point 2.4 relies on. Restricting
it to the `manager` role means only a manager and an admin can fill it in, that is only they can
push a task through a phase sign-off. That is intentional.

`risk_level` corresponds to the `risk_level` field from the demo data, but with normalized values
(`high` instead of `Wysokie`), see point 2.3.

### 5.4 Views and grouping

| code | label (i18n key) | query | default |
| --- | --- | --- | --- |
| `view.plan` | Plan | `{"groupBy":"milestone","sort":"dueDate"}` | yes, `timeline` |
| `view.phases` | Phases | `{"groupBy":"status"}` | no |
| `view.risks` | Risks | `{"groupBy":"custom:risk_level","sort":"dueDate"}` | no |
| `view.blockedBy` | Waiting on predecessors | `{"groupBy":"assignee"}` with a dependency filter | no |

`default_view_code = 'timeline'`, default grouping `milestone`. This is the only preset that does not
start on the board, because a board with six phases and no limits says little, while the timeline
(`features/timeline`) shows exactly what a waterfall needs.

### 5.5 Sprints, milestones, estimates

| Property | Setting |
| --- | --- |
| `sprints_enabled` | `FALSE` |
| `milestones_enabled` | `TRUE`, the preset creates two: "Analysis sign-off" and "Release" |
| `estimate_unit` | `days` |
| `wip_enforced` | `FALSE` |
| `dependency_guard` | `TRUE` |
| `default_view_code` | `timeline` |

`dependency_guard = TRUE` turns on the `dependenciesDone` requirement on the first transition and
additionally blocks setting a `start_date` earlier than the predecessor's `end_date`. The `blocks`
relation already exists in `task_relation` and is used in the demo data, so no new type is needed.

### 5.6 Automation rules

| name | trigger_def | conditions | actions |
| --- | --- | --- | --- |
| Unblock the successor | `{"kind":"statusChanged","value":"status.done"}` | an empty group | `[{"kind":"notifyBlockedTasks"}]` |
| On hold requires escalation | `{"kind":"statusChanged","value":"status.onHold"}` | an empty group | `[{"kind":"addWatcher","value":"manager"},{"kind":"notifyChannel","value":"#escalations"}]` |
| A milestone is approaching | `{"kind":"scheduled","value":"daily"}` | the task has a `milestone_id`, the milestone is due in 3 days, the status is outside the `done` category | `[{"kind":"addLabel","value":"at-risk"},{"kind":"notifyChannel","value":"#plan"}]` |

The `notifyBlockedTasks` action and the `scheduled` trigger are new. Point 9.

---

## 6. The preset change algorithm

The hardest part. Changing a preset means swapping the set of statuses under a running project that
holds tasks, automation rules, views and history.

### 6.1 Three phases

```
preview  →  apply  →  undo (optional, time limited)
```

The preview changes nothing in the database beyond a single `preset_change` row in the `preview`
state. Applying does everything in one transaction. Undo is possible for 24 hours and only while
nobody has changed the status of any task in the project since it was applied.

### 6.2 The mapping proposal, three passes

For every source status we look for a target status in this order. The first pass that produces a
result wins.

**Pass 1, code match.** `source.code == target.code`. Catches `backlog`, `inProgress`, `review`,
`done` when going from scrum to kanban, that is the most common case.

**Pass 2, category and relative position.** Within the same `category` we take the status with the
same ordinal counted inside the category, and if the target category has fewer, the last one.
Example: scrum has three statuses in the `inFlight` category (`inProgress`, `review`, `testing`),
kanban has two (`inProgress`, `review`). The third source one (`testing`) maps to the second target
one (`review`).

**Pass 3, category fallback.** The first target status with the same category. This always returns
something, because there are three categories and every preset has at least one status in each of
them. Checked: scrum has `notStarted` (2), `inFlight` (3), `done` (1); kanban has 2, 2, 1;
waterfall has 2, 4, 1.

The conclusion: **moving between these three presets cannot produce a status with no counterpart.**
It could arise when coming from `custom`, that is from a project where someone manually added a
category... no, the categories are closed (`StatusCategory` in `shared`). A status with no
counterpart is therefore impossible by definition, as long as the target preset has statuses in all
three categories. That is a condition `PresetCatalog` checks with a unit test on every build.

### 6.3 What the user sees in the preview

The "Change the way of working" screen, one column, three sections.

```
Change the way of working: Sprint (Scrum)  →  Agile (Kanban)
Project: Platform (NOW) · 47 tasks

┌─ Status mapping ─────────────────────────────────────────────────┐
│ Product backlog        12 tasks   →  [ Backlog          ▾ ]  auto │
│ Sprint backlog          8 tasks   →  [ Ready            ▾ ]  auto │
│ In progress             9 tasks   →  [ In progress      ▾ ]  code │
│ Review                  4 tasks   →  [ Review           ▾ ]  code │
│ Testing                 3 tasks   →  [ Review           ▾ ]  auto │
│ Done                   11 tasks   →  [ Done             ▾ ]  auto │
└──────────────────────────────────────────────────────────────────┘

┌─ What else will change ──────────────────────────────────────────┐
│ ⚠ 2 automation rules refer to the "Testing" status               │
│    · "Handover to QA" → the action will point at "Review"        │
│    · "Sprint wrap-up" → the trigger disappears, rule → draft     │
│ ⚠ 1 saved view filters by the "Sprint backlog" status            │
│    · "Sprint backlog" → the filter will point at "Ready"         │
│ ⚠ The "Review" column will get 7 tasks against a limit of 3      │
│ ⚠ Sprints will be turned off. 20 tasks will lose their sprint    │
│    S1 assignment. The history will record the change.            │
│ ⚠ Estimates in points will stop being visible. The values stay   │
│    in the database.                                              │
│ ✓ 4 custom fields unchanged, 1 new: "Class of service"           │
│ ✓ 18 transitions will be replaced by 8                           │
└──────────────────────────────────────────────────────────────────┘

┌─ Undo ───────────────────────────────────────────────────────────┐
│ The change can be undone for 24 hours, as long as nobody changes │
│ the status of any task in the project.                           │
└──────────────────────────────────────────────────────────────────┘

              [ Cancel ]        [ Apply the change ]
```

The marker on the right of every mapping row says where the proposal came from: `code` (pass 1),
`auto` (pass 2 or 3), `manual` (the user changed it in the dropdown). The dropdown contains every
target status, without being limited to the category, because sometimes a team deliberately wants to
move "Testing" into "Done".

A row without a chosen target blocks the button. The server checks it again anyway and returns a
`422`.

### 6.4 What happens on apply

One transaction, in this order:

1. Checking the `previewToken`. The preview returns a token containing a digest of the project state
   (status identifiers and positions, the task count in each of them). If someone moved a task
   between the preview and the apply, the digest does not match and the server returns a `409` asking
   for the preview to be repeated. Without it two people changing the preset at once leave the
   project in a state neither of them saw.
2. Writing a snapshot into `preset_change.snapshot` (JSONB): the full `status_def`,
   `status_transition`, `custom_field` with options, the project's `saved_view`, the project's
   `automation_rule` and the complete set of preset columns from `project`.
3. Inserting the new `status_def` rows. **Always with new identifiers**, never through an `UPDATE`
   of the existing ones. The reason: `task_history` stores status labels as text
   (`old_value = 'status.doing'`), and `automation_run.detail_params` may contain codes. Changing
   the meaning of an existing identifier throws the history out of alignment.
4. `UPDATE task SET status_id = <new> WHERE status_id = <old>` for every mapping row.
5. An entry in `task_history` for every moved task: `field = 'status'`,
   `old_value = 'status.<oldCode>'`, `new_value = 'status.<newCode>'`, `actor_id` is the person
   applying it, `rule_name = NULL`. Tasks where the old and new code are the same get no entry.
6. Deleting the old `status_transition` rows and inserting the new ones.
7. Rewriting `automation_rule`: in `trigger_def`, `conditions` and `actions` we look for values
   matching `status.<oldCode>` and replace them with `status.<newCode>` according to the mapping. A
   rule whose **trigger** stops making sense (for example `sprintCompleted` when sprints are turned
   off) gets `enabled = FALSE` and `draft = TRUE`, and keeps its name. We never delete a rule,
   because a user wrote it.
8. Rewriting `saved_view.query`: the `statusId` and `sprint` fields go through the same mapping. A
   view whose filter loses meaning (`sprint` with sprints turned off) loses that key from the query,
   and its `name` gets a suffix applied by the interface, not by the database.
9. Deleting the old `status_def` rows. This has to come after step 4, otherwise the `task.status_id`
   foreign key blocks it. `status_def` has no `ON DELETE CASCADE` on `task.status_id`, only a plain
   `REFERENCES status_def (id)`, so the order is enforced by the database and cannot be got wrong.
10. Creating the missing `custom_field` and `custom_field_option` rows. Existing fields with the same
    `field_key` are left untouched, together with the data in `task.custom`. Fields the new preset
    does not provide for are **not deleted.**
11. Creating the missing preset `saved_view` rows. Users' own views stay.
12. Updating the preset columns on `project` and any side actions: turning sprints on creates the
    first sprint, turning them off sets `task.sprint_id = NULL` for the whole project and records it
    in `task_history` as the `sprint` field.
13. `preset_change.state = 'applied'`, `applied_at = now()`.

### 6.5 Undo

```sql
CREATE TABLE preset_change (
    id              UUID PRIMARY KEY,
    organization_id UUID        NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
    project_id      UUID        NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    from_preset     TEXT        NOT NULL,
    to_preset       TEXT        NOT NULL,
    state           TEXT        NOT NULL DEFAULT 'preview',
    mapping         JSONB       NOT NULL,
    snapshot        JSONB,
    warnings        JSONB       NOT NULL DEFAULT '[]'::JSONB,
    moved_tasks     INTEGER     NOT NULL DEFAULT 0,
    actor_id        UUID        NOT NULL REFERENCES app_user (id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    applied_at      TIMESTAMPTZ,
    undone_at       TIMESTAMPTZ,
    CONSTRAINT preset_change_state_check
        CHECK (state IN ('preview', 'applied', 'undone', 'expired'))
);

CREATE TABLE preset_change_task (
    change_id       UUID NOT NULL REFERENCES preset_change (id) ON DELETE CASCADE,
    organization_id UUID NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
    task_id         UUID NOT NULL REFERENCES task (id) ON DELETE CASCADE,
    from_status_code TEXT NOT NULL,
    to_status_code   TEXT NOT NULL,
    from_sprint_code TEXT,
    PRIMARY KEY (change_id, task_id)
);
```

Undo restores `status_def`, `status_transition`, `automation_rule`, `saved_view` and the `project`
columns from `snapshot`, and then sets the status of every task in `preset_change_task` to
`from_status_code`. The restored statuses get **new identifiers**, because the old ones were
deleted, and `task_history` works on codes anyway.

Undo is refused (`422`) in three cases:

- 24 hours have passed since `applied_at`,
- there is a `task_history` entry with `field = 'status'` newer than `applied_at` for any task in
  the project,
- another preset change has been applied since.

After a refusal the only way is an ordinary preset change in the other direction, with the full
preview. Once a day a background job sets `state = 'expired'` on entries older than 24 hours and
**deletes their `snapshot` and their `preset_change_task` rows**, because a snapshot of rules and
views is a copy of customer data and there is no reason to keep it forever. What remains is the
audit entry alone with `mapping`, `warnings` and `moved_tasks`.

**Irreversible:** once the 24 hour window expires, the previous status layout cannot be restored
automatically. `task_history` remains, from which a human can read where a task was, but there is no
button.

### 6.6 What the algorithm does not do

- It does not merge tasks. If two source statuses map to one target, the tasks simply land together.
  The order within the column follows from `task.priority` and `updated_at`, because `task` has no
  column for its position in a board column.
- It does not change `task.estimate` when `estimate_unit` changes. Converting points into days is
  guesswork. The values stay, the interface only changes the unit next to the number and shows a
  warning in the preview.
- It does not touch `task.custom`. A custom field removed from the preset stays in the database
  together with its values.

---

## 7. Presets versus manual status changes

A preset is a starting point, not a padlock.

- After a preset is applied, all the endpoints from `docs/api-contract.md`
  (`POST /api/workspace/statuses`, `PATCH`, `DELETE`, `/reorder`, `/transitions`) work normally.
- The first such change sets `project.preset_customized = TRUE`. The interface then shows "Agile
  (Kanban), modified" and, on an attempt to apply the same preset again, warns that the changes will
  be overwritten.
- `DELETE /api/workspace/statuses/{id}` keeps today's behavior: a `422` when the status has tasks. A
  preset does not bypass that, which is why a preset change does not go through that endpoint but
  through `POST /api/projects/{id}/preset/apply`, which moves the tasks first.
- Applying the same preset again (`presetCode` equal to `project.preset_code`) is legal and serves to
  restore the pattern after manual changes. It goes through the same preview, because the effects can
  be surprising (removing a manually added status moves the tasks out of it).
- Changing a status in a project never sets `preset_code` to `custom`. Moving to `custom` happens
  only explicitly, through `PATCH /api/projects/{id}` with `{"presetCode": "custom"}`, which means
  "stop offering me pattern updates".

---

## 8. Endpoints

The format follows `docs/api-contract.md`.

### The preset catalog

```
GET    /api/presets                       -> PresetDto[]
GET    /api/presets/{code}                -> PresetDetailDto
```

`PresetDto`: `{ code, nameKey, descriptionKey, statusCount, sprintsEnabled, milestonesEnabled,
estimateUnit, defaultViewCode, wipEnforced }`.

`PresetDetailDto` adds `statuses[]`, `transitions[]`, `customFields[]`, `views[]`, `rules[]` in the
`StatusDto`, `TransitionDto`, `CustomFieldDto`, `SavedViewDto`, `RuleDto` shapes from the existing
contract, without identifiers (those are created only when the preset is applied).

Available without a selected organization, because the company creation wizard shows presets before
a project exists.

### Projects

`docs/api-contract.md` defines no `/api/projects` path at all, even though `BootstrapDto` returns a
project list. We close that here.

```
GET    /api/projects                      -> ProjectDto[]
POST   /api/projects                      {name, code?, presetCode} -> ProjectDto
PATCH  /api/projects/{id}                 {name?, code?, presetCode?, defaultViewCode?,
                                           estimateUnit?, wipEnforced?, dependencyGuard?,
                                           blockDisallowedDrag?, position?} -> ProjectDto
DELETE /api/projects/{id}                 -> 204
```

`ProjectDto` extends today's `{ id, name, code }` with `presetCode`, `presetCustomized`,
`sprintsEnabled`, `milestonesEnabled`, `estimateUnit`, `wipEnforced`, `dependencyGuard`,
`blockDisallowedDrag`, `defaultViewCode`, `taskCount`.

A `code` omitted at creation is derived from the name (uppercase, without diacritics, up to 4
characters, with a numeric suffix on a collision within the organization).

A `PATCH` with just `presetCode` **does not change the preset**. It returns a `422` with a hint to
use the preview path. The only exception is `presetCode: "custom"`, see point 7.

`DELETE /api/projects/{id}` returns a `422` when it is the organization's last project, because
`WorkspaceService.defaultProject()` then throws a `NotFoundException` and the whole application stops
loading.

### Changing the preset

```
POST   /api/projects/{id}/preset/preview          {presetCode, overrides?} -> PresetPreviewDto
POST   /api/projects/{id}/preset/apply            {previewToken, mapping} -> PresetChangeDto
GET    /api/projects/{id}/preset/changes          -> PresetChangeDto[]
POST   /api/projects/{id}/preset/changes/{cid}/undo -> PresetChangeDto
```

`overrides` is `{ [sourceStatusId]: targetStatusCode }`, partial. The server maps the rest with the
algorithm from point 6.2 and returns the full result, so the interface can call the preview after
every change in a dropdown.

`PresetPreviewDto`:

```
{
  previewToken: string,
  fromPreset: string,
  toPreset: string,
  taskCount: number,
  mapping: [{ fromStatusId, fromCode, fromLabel, taskCount,
              toCode, toLabel, source: "code" | "auto" | "manual" }],
  warnings: [{ kind, messageKey, params, severity: "info" | "warning" }],
  undoWindowHours: number
}
```

`warnings[].kind` takes: `ruleStatusRewritten`, `ruleDisabled`, `viewFilterRewritten`,
`viewFilterDropped`, `wipExceeded`, `sprintsDisabled`, `sprintsEnabled`, `estimateUnitChanged`,
`fieldAdded`, `fieldOrphaned`, `transitionsReplaced`, `milestonesDisabled`.

`POST .../apply` returns a `409` on a stale `previewToken`, a `422` on an incomplete mapping or a
role below `manager`.

Roles: the preview for `member` and above (so that the team can see the effects), applying and
undoing only for `manager` and `admin`.

### Sprints

```
GET    /api/sprints?projectId=&state=     -> SprintDto[]
POST   /api/sprints                       {projectId, name, code?, startDate, endDate, goal?} -> SprintDto
PATCH  /api/sprints/{id}                  {name?, goal?, startDate?, endDate?} -> SprintDto
POST   /api/sprints/{id}/start            -> SprintDto
POST   /api/sprints/{id}/complete         {moveUnfinishedTo: "backlog" | "next" | "<sprintId>"} -> SprintDto
DELETE /api/sprints/{id}                  -> 204
```

`POST /api/sprints/{id}/start` returns a `422` when the project already has a sprint in the `active`
state (the partial index from point 2.2 would block it anyway, but the message has to be readable).

`POST /api/sprints/{id}/complete` moves the unfinished tasks, writes `completed_at` and publishes a
`SprintCompleted` event in `shared.events`, which the `automation` module receives.

`DELETE` returns a `422` for a sprint in the `active` state.

`GET /api/tasks` (existing) gets an additional `sprintId=` parameter, next to today's `sprint=`
which takes a code. The code stays for the duration of the migration.

### Select field values

```
GET    /api/workspace/custom-fields/{id}/options            -> FieldOptionDto[]
POST   /api/workspace/custom-fields/{id}/options            {value, label, swatch?, position?} -> FieldOptionDto
PATCH  /api/workspace/custom-fields/{id}/options/{optionId} {label?, swatch?, position?} -> FieldOptionDto
DELETE /api/workspace/custom-fields/{id}/options/{optionId} -> 204
```

`value` is immutable after creation, just like `field_key` in a custom field, because it sits in
`task.custom` and in rules. `DELETE` returns a `422` when any task holds that value.

### Milestones

`docs/api-contract.md` has `GET /api/workspace/milestones`. We add the remaining operations and the
link to a task:

```
POST   /api/workspace/milestones          {name, dueDate, projectId} -> MilestoneDto
PATCH  /api/workspace/milestones/{id}     {name?, dueDate?} -> MilestoneDto
DELETE /api/workspace/milestones/{id}     -> 204
```

`PATCH /api/tasks/{key}` additionally accepts `milestoneId` and `sprintId`, with the same semantics
as the other fields (an explicit `null` clears, omission leaves unchanged).

There are no collisions with existing paths: `/api/presets`, `/api/projects` and `/api/sprints` do
not appear in `docs/api-contract.md`, and the `/options` and `/preset/*` subpaths do not collide with
`/{id}`, because they have an extra segment.

---

## 9. Changes required in `docs/api-contract.md`

I am not modifying that file. The list of what has to be added to it.

1. **`GET /api/workspace/statuses|transitions|custom-fields|milestones|epics` get a required
   `?projectId=`.** Without it a preset makes no sense, because statuses belong to a project, and
   today `WorkspaceService.statuses()` returns every status in the database. The same change is
   required by `docs/multi-tenancy.md`, point 11.6.
2. **`POST /api/workspace/statuses` and `POST /api/workspace/custom-fields` get a `projectId` in the
   body.** Today `WorkspaceConfigService.createStatus` takes `workspace.defaultProject().id()`, that
   is always the first project.
3. **New endpoints** from point 8: `/api/presets`, `/api/projects`, `/api/sprints`,
   `/api/workspace/custom-fields/{id}/options`, `/api/workspace/milestones` (POST, PATCH, DELETE),
   `/api/projects/{id}/preset/*`.
4. **`ProjectDto`** grows by ten fields, point 8.
5. **`CustomFieldDto`** gets `options: FieldOptionDto[]` for the `select` type.
6. **`StatusDto`** unchanged, but clarify that `code` is unique within a project, not within an
   organization.
7. **`TaskDto` and `TaskDetailDto`** get `sprintId`, `milestoneId` and `projectId`. `projectId` is
   not in `TaskDto` at all today, which with several projects makes it impossible for the frontend
   to work out which statuses to show in a task's dropdown.
8. **`PATCH /api/tasks/{key}`** accepts `sprintId` and `milestoneId`.
9. **`GET /api/tasks`** accepts `projectId=`, `sprintId=`, `milestoneId=` and `custom.<fieldKey>=`
   (a custom field filter, needed by the `view.expedite` view).
10. **`groupBy`** gets three values on top of today's five: `milestone`, `sprint` and
    `custom:<fieldKey>`, point 2.6.
11. **`SavedViewDto`** gets `origin: "builtin" | "preset" | "user"` and `projectId`. Today's split
    into two disjoint classes (`code` with an empty `name` versus the other way round) does not fit
    a view created by a preset, which has a label from an i18n key but has to be deletable. The
    deletion rule changes from "a view with a `code` is a `422`" to "a view with `origin = builtin`
    is a `422`", point 2.5.
12. **`GET /api/rules/catalog`** grows by the `scheduled`, `sprintStarted`, `sprintCompleted`
    triggers and the `moveToNextSprint`, `notifyBlockedTasks` actions. The contract describes that
    catalog as the source of truth for the rule builder, so this is a contract change.
13. **Enforcing `requirement`.** The contract says today only that a transition outside the list
    returns a `422` when `blockDisallowedDrag` is on. Add that an unmet `requirement` also returns a
    `422`, with the list of codes from point 2.4.
14. **`status_transition.requirement`** stops being an arbitrary label. A value outside the
    dictionary is a `400` on `POST /api/workspace/transitions`.
15. **Enforcing `wip_limit`.** New: a `422` with `code: "WIP_LIMIT"` on a project with
    `wipEnforced = true`.
16. **New events in `shared.events`**: `SprintStarted`, `SprintCompleted`, `PresetApplied`. The
    contract describes module boundaries through events, so they belong to it.
17. **Migration numbering.** Add a row: `V50` to `V59`, work form presets.

---

## 10. Open questions for a human to settle

1. **Should presets be editable by an organization, that is does a fourth entity, an "organization
   template", appear?** I designed the catalog as constants in the code (point 2.7), because that is
   the simplest thing and it is enough for three presets. If the product is to sell "your own process
   templates", the whole `PresetCatalog` moves into a table and the change preview then has to handle
   a preset whose definition changed since it was applied. That is a different class of complexity
   and I am not guessing whether we want it.
2. **The undo window: 24 hours.** The number comes from the fact that a preset change is usually
   noticed within one working day. A longer window means holding a snapshot of the customer's rules
   and views for longer. To be confirmed.
3. **What about tasks moved into a status whose category changed?** A task in `testing` (category
   `inFlight`, `completed_at IS NULL`) mapped onto a status in the `done` category should probably
   get a `completed_at`, because otherwise throughput charts will not count it. But setting
   `completed_at = now()` falsifies the cycle time for dozens of tasks at once. A third option is
   `completed_at = updated_at`. I am not choosing, because it breaks either the metrics or the data,
   and the decision belongs to whoever uses those metrics.
4. **The name `agile` or `kanban`?** The task says "agile (kanban)". I used the code `kanban`,
   because `agile` describes a family that scrum belongs to as well, and the code goes into the
   database and the API, so it cannot be changed later without a migration. The label in the
   interface stays "Agile (Kanban)".

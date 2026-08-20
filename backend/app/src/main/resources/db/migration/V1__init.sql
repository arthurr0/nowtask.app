-- The nowtask schema. It mirrors the model the frontend screens are built on.

CREATE TABLE app_user (
    id            UUID PRIMARY KEY,
    name          TEXT        NOT NULL,
    short_name    TEXT        NOT NULL,
    initials      TEXT        NOT NULL DEFAULT '',
    email         TEXT        NOT NULL UNIQUE,
    password_hash TEXT,
    role          TEXT        NOT NULL,
    capacity      INTEGER     NOT NULL DEFAULT 0,
    pending       BOOLEAN     NOT NULL DEFAULT FALSE,
    invited_on    DATE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE team (
    id       UUID PRIMARY KEY,
    name     TEXT    NOT NULL,
    headcount INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE team_member (
    team_id UUID NOT NULL REFERENCES team (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    PRIMARY KEY (team_id, user_id)
);

CREATE TABLE project (
    id       UUID PRIMARY KEY,
    name     TEXT    NOT NULL,
    code     TEXT    NOT NULL UNIQUE,
    position INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE status_def (
    id         UUID PRIMARY KEY,
    project_id UUID    NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    code       TEXT    NOT NULL,
    label      TEXT    NOT NULL,
    category   TEXT    NOT NULL,
    wip_limit  INTEGER,
    position   INTEGER NOT NULL,
    swatch     TEXT    NOT NULL DEFAULT 'var(--c-ink-2)',
    UNIQUE (project_id, code)
);

CREATE TABLE status_transition (
    id          UUID PRIMARY KEY,
    from_status UUID NOT NULL REFERENCES status_def (id) ON DELETE CASCADE,
    to_status   UUID NOT NULL REFERENCES status_def (id) ON DELETE CASCADE,
    requirement TEXT,
    UNIQUE (from_status, to_status)
);

CREATE TABLE epic (
    id         UUID PRIMARY KEY,
    project_id UUID    NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    name       TEXT    NOT NULL,
    position   INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE task (
    id               UUID PRIMARY KEY,
    project_id       UUID        NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    epic_id          UUID REFERENCES epic (id) ON DELETE SET NULL,
    task_key         TEXT        NOT NULL UNIQUE,
    title            TEXT        NOT NULL,
    description      TEXT        NOT NULL DEFAULT '',
    status_id        UUID        NOT NULL REFERENCES status_def (id),
    priority         TEXT        NOT NULL DEFAULT 'medium',
    assignee_id      UUID REFERENCES app_user (id) ON DELETE SET NULL,
    reviewer_id      UUID REFERENCES app_user (id) ON DELETE SET NULL,
    sprint_code      TEXT,
    due_date         DATE,
    start_date       DATE,
    end_date         DATE,
    estimate         INTEGER,
    progress         INTEGER     NOT NULL DEFAULT 0,
    attachment_count INTEGER     NOT NULL DEFAULT 0,
    watcher_count    INTEGER     NOT NULL DEFAULT 0,
    automated        BOOLEAN     NOT NULL DEFAULT FALSE,
    custom           JSONB       NOT NULL DEFAULT '{}'::JSONB,
    started_at       TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_task_status ON task (status_id);
CREATE INDEX idx_task_assignee ON task (assignee_id);
CREATE INDEX idx_task_due ON task (due_date);
CREATE INDEX idx_task_sprint ON task (sprint_code);

CREATE TABLE task_label (
    task_id UUID NOT NULL REFERENCES task (id) ON DELETE CASCADE,
    label   TEXT NOT NULL,
    PRIMARY KEY (task_id, label)
);

CREATE TABLE subtask (
    id          UUID PRIMARY KEY,
    task_id     UUID    NOT NULL REFERENCES task (id) ON DELETE CASCADE,
    title       TEXT    NOT NULL,
    done        BOOLEAN NOT NULL DEFAULT FALSE,
    assignee_id UUID REFERENCES app_user (id) ON DELETE SET NULL,
    position    INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_subtask_task ON subtask (task_id, position);

CREATE TABLE task_relation (
    from_task UUID NOT NULL REFERENCES task (id) ON DELETE CASCADE,
    to_task   UUID NOT NULL REFERENCES task (id) ON DELETE CASCADE,
    kind      TEXT NOT NULL,
    PRIMARY KEY (from_task, to_task, kind)
);

CREATE TABLE task_comment (
    id         UUID PRIMARY KEY,
    task_id    UUID        NOT NULL REFERENCES task (id) ON DELETE CASCADE,
    author_id  UUID        NOT NULL REFERENCES app_user (id),
    body       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_comment_task ON task_comment (task_id, created_at);

CREATE TABLE automation_rule (
    id          UUID PRIMARY KEY,
    project_id  UUID REFERENCES project (id) ON DELETE CASCADE,
    name        TEXT        NOT NULL,
    summary     TEXT        NOT NULL DEFAULT '',
    scope_label TEXT        NOT NULL DEFAULT '',
    enabled     BOOLEAN     NOT NULL DEFAULT TRUE,
    draft       BOOLEAN     NOT NULL DEFAULT FALSE,
    -- a counter refreshed by the application, so that hundreds of thousands of entries are not counted on every screen entry
    runs_30d    INTEGER     NOT NULL DEFAULT 0,
    trigger_def JSONB       NOT NULL,
    conditions  JSONB       NOT NULL,
    actions     JSONB       NOT NULL,
    edited_by   UUID REFERENCES app_user (id) ON DELETE SET NULL,
    edited_at   DATE        NOT NULL DEFAULT CURRENT_DATE,
    position    INTEGER     NOT NULL DEFAULT 0
);

CREATE TABLE task_history (
    id         UUID PRIMARY KEY,
    task_id    UUID        NOT NULL REFERENCES task (id) ON DELETE CASCADE,
    field      TEXT        NOT NULL,
    old_value  TEXT,
    new_value  TEXT,
    actor_id   UUID REFERENCES app_user (id) ON DELETE SET NULL,
    -- The rule name instead of a foreign key: the tasks module does not know the automation module tables
    rule_name  TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_history_task ON task_history (task_id, created_at DESC);

CREATE TABLE automation_run (
    id            UUID PRIMARY KEY,
    rule_id       UUID        NOT NULL REFERENCES automation_rule (id) ON DELETE CASCADE,
    -- Only the task key, without a foreign key: the boundary of the automation module towards tasks
    task_key      TEXT        NOT NULL,
    outcome       TEXT        NOT NULL,
    detail_key    TEXT        NOT NULL,
    detail_params JSONB       NOT NULL DEFAULT '{}'::JSONB,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_run_rule ON automation_run (rule_id, created_at DESC);

CREATE TABLE custom_field (
    id                 UUID PRIMARY KEY,
    project_id         UUID REFERENCES project (id) ON DELETE CASCADE,
    name               TEXT    NOT NULL,
    field_key          TEXT    NOT NULL,
    type               TEXT    NOT NULL,
    scope_label        TEXT    NOT NULL DEFAULT '',
    restricted_to_role TEXT,
    position           INTEGER NOT NULL DEFAULT 0,
    UNIQUE (project_id, field_key)
);

CREATE TABLE saved_view (
    id       UUID PRIMARY KEY,
    code     TEXT    NOT NULL UNIQUE,
    position INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE api_key (
    id           UUID PRIMARY KEY,
    prefix       TEXT        NOT NULL,
    label        TEXT        NOT NULL,
    last_used_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE milestone (
    id         UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    name       TEXT NOT NULL,
    due_date   DATE NOT NULL
);

-- Chart snapshots. In a real deployment a job running once a day appends them.
CREATE TABLE burndown_point (
    id          UUID PRIMARY KEY,
    project_id  UUID    NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    sprint_code TEXT    NOT NULL,
    day         DATE    NOT NULL,
    remaining   INTEGER,
    ideal       INTEGER NOT NULL,
    UNIQUE (project_id, sprint_code, day)
);

CREATE TABLE throughput_week (
    id         UUID PRIMARY KEY,
    project_id UUID    NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    label      TEXT    NOT NULL,
    week_start DATE    NOT NULL,
    completed  INTEGER NOT NULL,
    UNIQUE (project_id, week_start)
);

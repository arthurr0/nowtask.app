ALTER TABLE project
    ADD COLUMN preset_code           TEXT    NOT NULL DEFAULT 'kanban',
    ADD COLUMN preset_applied_at     TIMESTAMPTZ,
    ADD COLUMN preset_customized     BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN sprints_enabled       BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN milestones_enabled    BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN estimate_unit         TEXT,
    ADD COLUMN wip_enforced          BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN dependency_guard      BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN block_disallowed_drag BOOLEAN,
    ADD COLUMN default_view_code     TEXT    NOT NULL DEFAULT 'board',
    ADD CONSTRAINT project_preset_check
        CHECK (preset_code IN ('scrum', 'kanban', 'waterfall', 'custom')),
    ADD CONSTRAINT project_estimate_unit_check
        CHECK (estimate_unit IS NULL OR estimate_unit IN ('points', 'hours', 'days')),
    ADD CONSTRAINT project_default_view_check
        CHECK (default_view_code IN ('board', 'list', 'timeline', 'calendar'));

UPDATE project SET preset_code = 'custom';

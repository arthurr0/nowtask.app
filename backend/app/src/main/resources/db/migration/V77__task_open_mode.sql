ALTER TABLE user_view_preference
    ADD COLUMN task_open_mode TEXT NOT NULL DEFAULT 'dialog';

ALTER TABLE user_view_preference
    ADD CONSTRAINT user_view_preference_open_mode_check
        CHECK (task_open_mode IN ('dialog', 'page'));

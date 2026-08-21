ALTER TABLE workspace_settings
    ALTER COLUMN current_sprint DROP DEFAULT,
    ALTER COLUMN current_sprint DROP NOT NULL;

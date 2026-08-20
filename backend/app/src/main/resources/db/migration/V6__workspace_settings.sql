CREATE TABLE workspace_settings (
    id                    UUID PRIMARY KEY,
    date_format           TEXT    NOT NULL DEFAULT 'dd.MM.yyyy',
    time_format           TEXT    NOT NULL DEFAULT 'HH:mm',
    first_day_of_week     INTEGER NOT NULL DEFAULT 1,
    time_zone             TEXT    NOT NULL DEFAULT 'Europe/Warsaw',
    currency              TEXT    NOT NULL DEFAULT 'PLN',
    allow_user_override   BOOLEAN NOT NULL DEFAULT TRUE,
    block_disallowed_drag BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO workspace_settings (id) VALUES ('00000000-0000-0000-0000-000000000001');

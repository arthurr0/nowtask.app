CREATE TABLE user_nav_item (
    user_id  UUID    NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    code     TEXT    NOT NULL,
    position INTEGER NOT NULL,
    hidden   BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (user_id, code)
);

CREATE INDEX idx_user_nav_item_user ON user_nav_item (user_id, position);

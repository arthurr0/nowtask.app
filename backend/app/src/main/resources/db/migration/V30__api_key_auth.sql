-- API key authentication. Only the SHA-256 hash of the full key stays in the database,
-- the prefix is kept separately so that a key can be recognized on the list.
-- The organization_id column will be added here with one ALTER TABLE once multi-tenancy arrives.

ALTER TABLE api_key
    ADD COLUMN token_hash TEXT,
    ADD COLUMN scopes     TEXT        NOT NULL DEFAULT '',
    ADD COLUMN owner_id   UUID REFERENCES app_user (id) ON DELETE CASCADE,
    ADD COLUMN created_by UUID REFERENCES app_user (id) ON DELETE SET NULL,
    ADD COLUMN expires_at TIMESTAMPTZ,
    ADD COLUMN revoked_at TIMESTAMPTZ;

CREATE UNIQUE INDEX uq_api_key_token_hash ON api_key (token_hash);
CREATE INDEX idx_api_key_prefix ON api_key (prefix);
CREATE INDEX idx_api_key_owner ON api_key (owner_id);

-- The demo entries have no hash, so they could never authenticate.
-- We mark them as revoked so that the state on the list is truthful.
UPDATE api_key SET revoked_at = now() WHERE token_hash IS NULL;

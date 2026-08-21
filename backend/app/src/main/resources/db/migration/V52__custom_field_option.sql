CREATE TABLE custom_field_option (
    id              UUID    PRIMARY KEY,
    organization_id UUID    NOT NULL REFERENCES organization (id) ON DELETE CASCADE
                            DEFAULT nullif(current_setting('app.organization_id', true), '')::UUID,
    field_id        UUID    NOT NULL REFERENCES custom_field (id) ON DELETE CASCADE,
    value           TEXT    NOT NULL,
    label           TEXT    NOT NULL,
    swatch          TEXT    NOT NULL DEFAULT 'var(--c-ink-2)',
    position        INTEGER NOT NULL DEFAULT 0,
    UNIQUE (field_id, value)
);

CREATE INDEX idx_field_option_org ON custom_field_option (organization_id, field_id, position);

ALTER TABLE custom_field_option ENABLE ROW LEVEL SECURITY;
CREATE POLICY custom_field_option_tenant ON custom_field_option
    USING      (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID)
    WITH CHECK (organization_id = nullif(current_setting('app.organization_id', true), '')::UUID);

CREATE TRIGGER custom_field_option_same_org BEFORE INSERT OR UPDATE ON custom_field_option
    FOR EACH ROW EXECUTE FUNCTION assert_same_org('custom_field', 'field_id');

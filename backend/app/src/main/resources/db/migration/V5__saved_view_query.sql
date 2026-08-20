ALTER TABLE saved_view
    ADD COLUMN name     TEXT,
    ADD COLUMN query    JSONB   NOT NULL DEFAULT '{}'::JSONB,
    ADD COLUMN shared   BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN owner_id UUID;

ALTER TABLE saved_view ALTER COLUMN code DROP NOT NULL;

UPDATE saved_view SET name = 'At risk', query = '{"sort":"dueDate"}'::JSONB
WHERE code = 'view.atRisk';

UPDATE saved_view SET name = 'Unassigned', query = '{"unassigned":true}'::JSONB
WHERE code = 'view.unassigned';

UPDATE saved_view SET name = 'Rule driven', query = '{"automated":true}'::JSONB
WHERE code = 'view.automated';

ALTER TABLE saved_view ALTER COLUMN name SET NOT NULL;

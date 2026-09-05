UPDATE saved_view
SET query = '{"layout":"list","sort":"due","filter":{"join":"and","conditions":[{"field":"dueDate","op":"onOrBefore","values":["+2d"]},{"field":"statusCategory","op":"notIn","values":["done"]}]}}'::JSONB
WHERE code = 'view.atRisk';

UPDATE saved_view
SET query = '{"layout":"list","filter":{"join":"and","conditions":[{"field":"assignee","op":"isEmpty","values":[]},{"field":"statusCategory","op":"notIn","values":["done"]}]}}'::JSONB
WHERE code = 'view.unassigned';

UPDATE saved_view
SET query = '{"layout":"list","filter":{"join":"and","conditions":[{"field":"automated","op":"is","values":["true"]}]}}'::JSONB
WHERE code = 'view.automated';

-- Demo data. Made up, but realistic: it mirrors the set from the approved mockups.
--
-- Dates are computed relative to the Monday of the previous week, so the board and the charts look
-- equally fresh no matter when the database was created.
-- Identifiers are deterministic (md5 of a readable key) so that the migration is repeatable.

CREATE FUNCTION pg_temp.d(days INT) RETURNS DATE AS $$
    SELECT (date_trunc('week', CURRENT_DATE)::DATE - 7) + days
$$ LANGUAGE sql;

CREATE FUNCTION pg_temp.ts(days INT, hours INT, minutes INT) RETURNS TIMESTAMPTZ AS $$
    SELECT ((date_trunc('week', CURRENT_DATE)::DATE - 7) + days)::TIMESTAMPTZ
         + make_interval(hours => hours, mins => minutes)
$$ LANGUAGE sql;

CREATE FUNCTION pg_temp.uid(key TEXT) RETURNS UUID AS $$
    SELECT md5(key)::UUID
$$ LANGUAGE sql;

INSERT INTO app_user (id, name, short_name, initials, email, role, capacity, pending, invited_on) VALUES
    (pg_temp.uid('user:u1'), 'Artur Kołecki',      'Artur K.', 'AK', 'artur@nowtask.app',  'admin',   26, FALSE, NULL),
    (pg_temp.uid('user:u2'), 'Marta Wiśniewska',  'Marta W.', 'MW', 'marta@nowtask.app',  'manager', 26, FALSE, NULL),
    (pg_temp.uid('user:u3'), 'Piotr Zając',       'Piotr Z.', 'PZ', 'piotr@nowtask.app',  'member',  26, FALSE, NULL),
    (pg_temp.uid('user:u4'), 'Ola Baran',         'Ola B.',   'OB', 'ola@nowtask.app',    'member',  26, FALSE, NULL),
    (pg_temp.uid('user:u5'), 'Jakub Lis',         'Jakub L.', 'JL', 'jakub@nowtask.app',  'member',  26, FALSE, NULL),
    (pg_temp.uid('user:u6'), 'hanna@kontrahent.pl', 'Hanna',  '',   'hanna@kontrahent.pl',   'guest',    0, TRUE,  pg_temp.d(1));

INSERT INTO team (id, name, headcount) VALUES
    (pg_temp.uid('team:t1'), 'Platform', 6),
    (pg_temp.uid('team:t2'), 'Mobile app', 5),
    (pg_temp.uid('team:t3'), 'Design', 3);

INSERT INTO team_member (team_id, user_id) VALUES
    (pg_temp.uid('team:t1'), pg_temp.uid('user:u1')),
    (pg_temp.uid('team:t1'), pg_temp.uid('user:u2')),
    (pg_temp.uid('team:t1'), pg_temp.uid('user:u3')),
    (pg_temp.uid('team:t1'), pg_temp.uid('user:u5')),
    (pg_temp.uid('team:t2'), pg_temp.uid('user:u3')),
    (pg_temp.uid('team:t2'), pg_temp.uid('user:u4')),
    (pg_temp.uid('team:t3'), pg_temp.uid('user:u4'));

INSERT INTO project (id, name, code, position) VALUES
    (pg_temp.uid('project:p1'), 'Platform', 'NOW', 0),
    (pg_temp.uid('project:p2'), 'Mobile app', 'MOB', 1),
    (pg_temp.uid('project:p3'), 'Design system', 'DS', 2);

INSERT INTO status_def (id, project_id, code, label, category, wip_limit, position, swatch) VALUES
    (pg_temp.uid('status:backlog'), pg_temp.uid('project:p1'), 'backlog', 'Backlog',     'notStarted', NULL, 0, 'var(--c-line-strong)'),
    (pg_temp.uid('status:todo'),    pg_temp.uid('project:p1'), 'todo',    'To do',       'notStarted',   10, 1, 'var(--c-ink-3)'),
    (pg_temp.uid('status:doing'),   pg_temp.uid('project:p1'), 'doing',   'In progress', 'inFlight',      5, 2, 'var(--c-ink-2)'),
    (pg_temp.uid('status:review'),  pg_temp.uid('project:p1'), 'review',  'Review',      'inFlight',      4, 3, 'var(--c-accent)'),
    (pg_temp.uid('status:done'),    pg_temp.uid('project:p1'), 'done',    'Done',        'done',       NULL, 4, 'var(--c-ink)');

INSERT INTO status_transition (id, from_status, to_status, requirement) VALUES
    (pg_temp.uid('tr:1'), pg_temp.uid('status:backlog'), pg_temp.uid('status:todo'),   NULL),
    (pg_temp.uid('tr:2'), pg_temp.uid('status:todo'),    pg_temp.uid('status:doing'),  NULL),
    (pg_temp.uid('tr:3'), pg_temp.uid('status:doing'),   pg_temp.uid('status:review'), 'transition.reviewerRequired'),
    (pg_temp.uid('tr:4'), pg_temp.uid('status:review'),  pg_temp.uid('status:done'),   'transition.roleReviewer'),
    (pg_temp.uid('tr:5'), pg_temp.uid('status:review'),  pg_temp.uid('status:doing'),  'transition.commentRequired');

INSERT INTO epic (id, project_id, name, position) VALUES
    (pg_temp.uid('epic:e1'), pg_temp.uid('project:p1'), 'Automation engine', 0),
    (pg_temp.uid('epic:e2'), pg_temp.uid('project:p1'), 'Personalization', 1),
    (pg_temp.uid('epic:e3'), pg_temp.uid('project:p1'), 'Performance', 2);

INSERT INTO task (id, project_id, epic_id, task_key, title, description, status_id, priority, assignee_id, reviewer_id,
                  sprint_code, due_date, start_date, end_date, estimate, progress, attachment_count, watcher_count,
                  automated, custom, started_at, completed_at) VALUES
    (pg_temp.uid('task:NOW-208'), pg_temp.uid('project:p1'), NULL, 'NOW-208',
     'Board export to CSV and XLSX',
     'The export has to cover the currently visible columns and the applied filters, not the whole task database. XLSX needs headers and date formatting consistent with the region settings.',
     pg_temp.uid('status:backlog'), 'medium', pg_temp.uid('user:u3'), NULL,
     'S24', NULL, NULL, NULL, 5, 0, 0, 1, FALSE, '{"team":"Platform"}'::JSONB, NULL, NULL),

    (pg_temp.uid('task:NOW-211'), pg_temp.uid('project:p1'), pg_temp.uid('epic:e1'), 'NOW-211',
     'Time based rules: reminders before the due date',
     'A rule has to be able to run on a schedule and check whether the due date falls inside a given window. A safeguard is needed so that a single task does not get a reminder more than once a day.',
     pg_temp.uid('status:backlog'), 'medium', NULL, NULL,
     'S24', NULL, pg_temp.d(7), pg_temp.d(11), 8, 0, 0, 2, TRUE, '{"team":"Platform"}'::JSONB, NULL, NULL),

    (pg_temp.uid('task:NOW-214'), pg_temp.uid('project:p1'), pg_temp.uid('epic:e2'), 'NOW-214',
     'Compact mode for the list view',
     'Row density driven by a CSS variable, without duplicating templates. Row height must not drop below the readability threshold for screen readers.',
     pg_temp.uid('status:backlog'), 'low', pg_temp.uid('user:u4'), NULL,
     'S24', NULL, NULL, NULL, 3, 0, 0, 1, FALSE, '{"team":"Platform"}'::JSONB, NULL, NULL),

    (pg_temp.uid('task:NOW-217'), pg_temp.uid('project:p1'), pg_temp.uid('epic:e1'), 'NOW-217',
     'Outgoing webhooks for automation rules',
     'A rule action has to be able to send a request to a given address, with a signature and a retry on failure. Failed attempts land in the rule log.',
     pg_temp.uid('status:backlog'), 'medium', NULL, NULL,
     'S24', NULL, NULL, NULL, 8, 0, 0, 0, FALSE, '{"team":"Platform"}'::JSONB, NULL, NULL),

    (pg_temp.uid('task:NOW-219'), pg_temp.uid('project:p1'), pg_temp.uid('epic:e2'), 'NOW-219',
     'Calendar view with draggable due dates',
     'A fourth view over the same task model. Dragging a card changes the due date, provided the transition is allowed by the flow.',
     pg_temp.uid('status:backlog'), 'low', NULL, NULL,
     'S24', NULL, NULL, NULL, 13, 0, 0, 0, FALSE, '{"team":"Platform"}'::JSONB, NULL, NULL),

    (pg_temp.uid('task:NOW-222'), pg_temp.uid('project:p1'), pg_temp.uid('epic:e1'), 'NOW-222',
     'Rule log export to CSV',
     'The audit needs an extract of rule runs for a chosen period, together with the outcome and the task identifier.',
     pg_temp.uid('status:backlog'), 'low', NULL, NULL,
     'S24', NULL, NULL, NULL, 3, 0, 0, 0, FALSE, '{"team":"Platform"}'::JSONB, NULL, NULL),

    (pg_temp.uid('task:NOW-190'), pg_temp.uid('project:p1'), pg_temp.uid('epic:e3'), 'NOW-190',
     'Moving the notifications module onto an event queue',
     'Notifications sent synchronously block the task write when the mail provider is slow. We are moving them onto an event queue with retries and a dead letter queue.',
     pg_temp.uid('status:todo'), 'high', pg_temp.uid('user:u3'), pg_temp.uid('user:u1'),
     'S24', pg_temp.d(9), pg_temp.d(9), pg_temp.d(16), 13, 15, 1, 3, FALSE,
     '{"team":"Platform","risk_level":"High","needs_qa":true,"cost_pln":38000}'::JSONB, NULL, NULL),

    (pg_temp.uid('task:NOW-193'), pg_temp.uid('project:p1'), pg_temp.uid('epic:e2'), 'NOW-193',
     'Custom fields panel: number, date and select types',
     'An administrator adds a field, gives it a key and a scope, and the task form shows it right away. The key is immutable after creation, because rules and the API use it.',
     pg_temp.uid('status:todo'), 'medium', pg_temp.uid('user:u2'), pg_temp.uid('user:u1'),
     'S24', pg_temp.d(11), pg_temp.d(7), pg_temp.d(11), 8, 0, 0, 2, FALSE,
     '{"team":"Platform","risk_level":"Low","needs_qa":true,"cost_pln":9800}'::JSONB, NULL, NULL),

    (pg_temp.uid('task:NOW-196'), pg_temp.uid('project:p1'), pg_temp.uid('epic:e2'), 'NOW-196',
     'Dark theme for the timeline view',
     'Bars and grid lines have their own values in the dark theme so that weekends do not blend into the background. We check the contrast on the smallest bar.',
     pg_temp.uid('status:todo'), 'low', pg_temp.uid('user:u4'), NULL,
     'S24', pg_temp.d(17), pg_temp.d(14), pg_temp.d(17), 3, 0, 2, 1, TRUE,
     '{"team":"Design","risk_level":"Low","needs_qa":false,"cost_pln":4200}'::JSONB, NULL, NULL),

    (pg_temp.uid('task:NOW-201'), pg_temp.uid('project:p1'), NULL, 'NOW-201',
     'Task import from a CSV file with column mapping',
     'The user picks a file, maps columns onto fields and sees a preview of the first rows before saving. The import has to be reversible within a day.',
     pg_temp.uid('status:todo'), 'low', NULL, NULL,
     'S24', NULL, NULL, NULL, NULL, 0, 0, 0, FALSE, '{"team":"Platform"}'::JSONB, NULL, NULL),

    (pg_temp.uid('task:NOW-172'), pg_temp.uid('project:p1'), pg_temp.uid('epic:e1'), 'NOW-172',
     'Rule builder: nested conditions and AND/OR groups',
     'The current builder allows only a flat list of conditions joined by AND. We need nested groups so that a rule like: status = In progress AND (priority = High OR the due date falls within 48 hours) can be built. Groups have to be draggable, and the maximum nesting depth is three levels.',
     pg_temp.uid('status:doing'), 'high', pg_temp.uid('user:u2'), pg_temp.uid('user:u3'),
     'S24', pg_temp.d(6), pg_temp.d(1), pg_temp.d(6), 8, 62, 3, 5, TRUE,
     '{"team":"Platform","risk_level":"Medium","needs_qa":true,"cost_pln":14200}'::JSONB, pg_temp.ts(1, 9, 0), NULL),

    (pg_temp.uid('task:NOW-181'), pg_temp.uid('project:p1'), pg_temp.uid('epic:e2'), 'NOW-181',
     'PL, EN and DE translations for the settings screen',
     'The settings screen has the most text in the whole application, so it is a good test for translation bundles. We group the keys by section, not by component.',
     pg_temp.uid('status:doing'), 'medium', pg_temp.uid('user:u5'), pg_temp.uid('user:u2'),
     'S24', pg_temp.d(10), pg_temp.d(8), pg_temp.d(10), 5, 40, 0, 2, FALSE,
     '{"team":"Platform","risk_level":"Low","needs_qa":false,"cost_pln":6400}'::JSONB, pg_temp.ts(8, 10, 30), NULL),

    (pg_temp.uid('task:NOW-186'), pg_temp.uid('project:p1'), pg_temp.uid('epic:e3'), 'NOW-186',
     'List virtualization at 5000 tasks',
     'On a large project the list view renders every row at once and scrolling starts dropping frames. We are virtualizing the rows while keeping range selection and keyboard shortcuts.',
     pg_temp.uid('status:doing'), 'medium', pg_temp.uid('user:u1'), pg_temp.uid('user:u3'),
     'S24', pg_temp.d(15), pg_temp.d(17), pg_temp.d(25), 13, 10, 0, 2, FALSE,
     '{"team":"Platform","risk_level":"Medium","needs_qa":true,"cost_pln":22000}'::JSONB, pg_temp.ts(9, 11, 0), NULL),

    (pg_temp.uid('task:NOW-164'), pg_temp.uid('project:p1'), pg_temp.uid('epic:e1'), 'NOW-164',
     'Permissions at the level of a single field',
     'A custom field can be restricted to a role. The restriction has to work in the API, the export and the rules, not only in the interface.',
     pg_temp.uid('status:review'), 'high', pg_temp.uid('user:u3'), pg_temp.uid('user:u1'),
     'S24', pg_temp.d(8), pg_temp.d(3), pg_temp.d(8), 8, 85, 1, 4, TRUE,
     '{"team":"Platform","risk_level":"High","needs_qa":true,"cost_pln":18600}'::JSONB, pg_temp.ts(3, 9, 15), NULL),

    (pg_temp.uid('task:NOW-169'), pg_temp.uid('project:p1'), NULL, 'NOW-169',
     'Task change history with a per-field filter',
     'The history has to show who changed a given field and when, and to tell a change made by a human apart from a change made by a rule.',
     pg_temp.uid('status:review'), 'medium', pg_temp.uid('user:u5'), pg_temp.uid('user:u2'),
     'S24', pg_temp.d(7), pg_temp.d(2), pg_temp.d(7), 5, 90, 0, 1, FALSE,
     '{"team":"Platform","risk_level":"Low","needs_qa":false,"cost_pln":7200}'::JSONB, pg_temp.ts(2, 8, 45), NULL),

    (pg_temp.uid('task:NOW-151'), pg_temp.uid('project:p1'), pg_temp.uid('epic:e2'), 'NOW-151',
     'Configurable board columns',
     'Board columns follow from the status definitions, not from a separate view configuration.',
     pg_temp.uid('status:done'), 'medium', pg_temp.uid('user:u2'), pg_temp.uid('user:u1'),
     'S24', pg_temp.d(3), pg_temp.d(0), pg_temp.d(3), 8, 100, 0, 2, FALSE,
     '{"team":"Platform"}'::JSONB, pg_temp.ts(0, 9, 0), pg_temp.ts(3, 16, 20)),

    (pg_temp.uid('task:NOW-158'), pg_temp.uid('project:p1'), pg_temp.uid('epic:e2'), 'NOW-158',
     'Command palette under ⌘K',
     'Jumping to a task, a view or a setting without taking your hands off the keyboard.',
     pg_temp.uid('status:done'), 'medium', pg_temp.uid('user:u4'), pg_temp.uid('user:u2'),
     'S24', pg_temp.d(5), pg_temp.d(1), pg_temp.d(5), 5, 100, 0, 1, FALSE,
     '{"team":"Platform"}'::JSONB, pg_temp.ts(1, 10, 0), pg_temp.ts(5, 14, 5)),

    (pg_temp.uid('task:NOW-143'), pg_temp.uid('project:p1'), pg_temp.uid('epic:e2'), 'NOW-143',
     'Keyboard shortcuts in the board view',
     'Navigating cards with the arrow keys and changing status without using the mouse.',
     pg_temp.uid('status:done'), 'low', pg_temp.uid('user:u4'), pg_temp.uid('user:u2'),
     'S24', pg_temp.d(7), pg_temp.d(3), pg_temp.d(7), 3, 100, 0, 1, FALSE,
     '{"team":"Platform"}'::JSONB, pg_temp.ts(3, 9, 30), pg_temp.ts(7, 11, 40)),

    (pg_temp.uid('task:NOW-147'), pg_temp.uid('project:p1'), pg_temp.uid('epic:e2'), 'NOW-147',
     'Filters saved as team views',
     'A set of filters, grouping and columns is saved under a name and can be shared with the team.',
     pg_temp.uid('status:done'), 'medium', pg_temp.uid('user:u5'), pg_temp.uid('user:u1'),
     'S24', pg_temp.d(8), pg_temp.d(5), pg_temp.d(8), 8, 100, 0, 2, TRUE,
     '{"team":"Platform"}'::JSONB, pg_temp.ts(5, 8, 20), pg_temp.ts(8, 15, 10));

INSERT INTO task_label (task_id, label) VALUES
    (pg_temp.uid('task:NOW-208'), 'backend'),
    (pg_temp.uid('task:NOW-211'), 'automation'),
    (pg_temp.uid('task:NOW-214'), 'frontend'),
    (pg_temp.uid('task:NOW-214'), 'a11y'),
    (pg_temp.uid('task:NOW-217'), 'backend'),
    (pg_temp.uid('task:NOW-217'), 'automation'),
    (pg_temp.uid('task:NOW-219'), 'frontend'),
    (pg_temp.uid('task:NOW-222'), 'automation'),
    (pg_temp.uid('task:NOW-190'), 'backend'),
    (pg_temp.uid('task:NOW-190'), 'infra'),
    (pg_temp.uid('task:NOW-193'), 'frontend'),
    (pg_temp.uid('task:NOW-196'), 'frontend'),
    (pg_temp.uid('task:NOW-196'), 'design'),
    (pg_temp.uid('task:NOW-201'), 'backend'),
    (pg_temp.uid('task:NOW-172'), 'automation'),
    (pg_temp.uid('task:NOW-181'), 'i18n'),
    (pg_temp.uid('task:NOW-186'), 'frontend'),
    (pg_temp.uid('task:NOW-186'), 'performance'),
    (pg_temp.uid('task:NOW-164'), 'backend'),
    (pg_temp.uid('task:NOW-164'), 'security'),
    (pg_temp.uid('task:NOW-169'), 'backend'),
    (pg_temp.uid('task:NOW-151'), 'frontend'),
    (pg_temp.uid('task:NOW-158'), 'frontend'),
    (pg_temp.uid('task:NOW-158'), 'a11y'),
    (pg_temp.uid('task:NOW-143'), 'a11y'),
    (pg_temp.uid('task:NOW-147'), 'frontend');

INSERT INTO subtask (id, task_id, title, done, assignee_id, position) VALUES
    (pg_temp.uid('sub:172:1'), pg_temp.uid('task:NOW-172'), 'Data model for condition groups', TRUE, pg_temp.uid('user:u3'), 0),
    (pg_temp.uid('sub:172:2'), pg_temp.uid('task:NOW-172'), 'Nesting depth validation on the API side', TRUE, pg_temp.uid('user:u3'), 1),
    (pg_temp.uid('sub:172:3'), pg_temp.uid('task:NOW-172'), 'Rendering the condition tree', TRUE, pg_temp.uid('user:u2'), 2),
    (pg_temp.uid('sub:172:4'), pg_temp.uid('task:NOW-172'), 'Changing a group operator', TRUE, pg_temp.uid('user:u2'), 3),
    (pg_temp.uid('sub:172:5'), pg_temp.uid('task:NOW-172'), 'Removing empty groups', TRUE, pg_temp.uid('user:u2'), 4),
    (pg_temp.uid('sub:172:6'), pg_temp.uid('task:NOW-172'), 'Dragging conditions between groups', FALSE, pg_temp.uid('user:u2'), 5),
    (pg_temp.uid('sub:172:7'), pg_temp.uid('task:NOW-172'), 'Rule preview as a sentence', FALSE, pg_temp.uid('user:u2'), 6),
    (pg_temp.uid('sub:172:8'), pg_temp.uid('task:NOW-172'), 'Tests at three nesting levels', FALSE, pg_temp.uid('user:u3'), 7),

    (pg_temp.uid('sub:190:1'), pg_temp.uid('task:NOW-190'), 'Broker choice and local configuration', TRUE, pg_temp.uid('user:u3'), 0),
    (pg_temp.uid('sub:190:2'), pg_temp.uid('task:NOW-190'), 'Event producer on task write', FALSE, pg_temp.uid('user:u3'), 1),
    (pg_temp.uid('sub:190:3'), pg_temp.uid('task:NOW-190'), 'Consumer sending the notifications', FALSE, pg_temp.uid('user:u3'), 2),
    (pg_temp.uid('sub:190:4'), pg_temp.uid('task:NOW-190'), 'Retries and a dead letter queue', FALSE, NULL, 3),
    (pg_temp.uid('sub:190:5'), pg_temp.uid('task:NOW-190'), 'Latency metrics', FALSE, NULL, 4),
    (pg_temp.uid('sub:190:6'), pg_temp.uid('task:NOW-190'), 'Retiring the old path', FALSE, NULL, 5),

    (pg_temp.uid('sub:193:1'), pg_temp.uid('task:NOW-193'), 'Field definition form', FALSE, pg_temp.uid('user:u2'), 0),
    (pg_temp.uid('sub:193:2'), pg_temp.uid('task:NOW-193'), 'Key uniqueness validation', FALSE, pg_temp.uid('user:u2'), 1),
    (pg_temp.uid('sub:193:3'), pg_temp.uid('task:NOW-193'), 'Rendering the field on a task', FALSE, NULL, 2),

    (pg_temp.uid('sub:196:1'), pg_temp.uid('task:NOW-196'), 'Bar and grid tokens', FALSE, pg_temp.uid('user:u4'), 0),
    (pg_temp.uid('sub:196:2'), pg_temp.uid('task:NOW-196'), 'Weekends and the today marker', FALSE, pg_temp.uid('user:u4'), 1),
    (pg_temp.uid('sub:196:3'), pg_temp.uid('task:NOW-196'), 'Contrast check', FALSE, NULL, 2),

    (pg_temp.uid('sub:181:1'), pg_temp.uid('task:NOW-181'), 'Extracting the keys from the screen', TRUE, pg_temp.uid('user:u5'), 0),
    (pg_temp.uid('sub:181:2'), pg_temp.uid('task:NOW-181'), 'EN translation', TRUE, pg_temp.uid('user:u5'), 1),
    (pg_temp.uid('sub:181:3'), pg_temp.uid('task:NOW-181'), 'DE translation', FALSE, pg_temp.uid('user:u5'), 2),
    (pg_temp.uid('sub:181:4'), pg_temp.uid('task:NOW-181'), 'Checking label lengths in DE', FALSE, NULL, 3),

    (pg_temp.uid('sub:186:1'), pg_temp.uid('task:NOW-186'), 'Measuring current performance', TRUE, pg_temp.uid('user:u1'), 0),
    (pg_temp.uid('sub:186:2'), pg_temp.uid('task:NOW-186'), 'Row virtualization', FALSE, pg_temp.uid('user:u1'), 1),
    (pg_temp.uid('sub:186:3'), pg_temp.uid('task:NOW-186'), 'Range selection with virtualization', FALSE, NULL, 2),

    (pg_temp.uid('sub:164:1'), pg_temp.uid('task:NOW-164'), 'Field permission model', TRUE, pg_temp.uid('user:u3'), 0),
    (pg_temp.uid('sub:164:2'), pg_temp.uid('task:NOW-164'), 'Filtering in the API', TRUE, pg_temp.uid('user:u3'), 1),
    (pg_temp.uid('sub:164:3'), pg_temp.uid('task:NOW-164'), 'Hiding it in the export', TRUE, pg_temp.uid('user:u3'), 2),
    (pg_temp.uid('sub:164:4'), pg_temp.uid('task:NOW-164'), 'Tests for the Guest role', FALSE, pg_temp.uid('user:u3'), 3),

    (pg_temp.uid('sub:169:1'), pg_temp.uid('task:NOW-169'), 'Writing history entries', TRUE, pg_temp.uid('user:u5'), 0),
    (pg_temp.uid('sub:169:2'), pg_temp.uid('task:NOW-169'), 'Per-field filter', TRUE, pg_temp.uid('user:u5'), 1),

    (pg_temp.uid('sub:208:1'), pg_temp.uid('task:NOW-208'), 'CSV serialization honoring the region separator', FALSE, pg_temp.uid('user:u3'), 0),
    (pg_temp.uid('sub:208:2'), pg_temp.uid('task:NOW-208'), 'XLSX generation', FALSE, pg_temp.uid('user:u3'), 1),
    (pg_temp.uid('sub:208:3'), pg_temp.uid('task:NOW-208'), 'Background export queue', FALSE, NULL, 2),
    (pg_temp.uid('sub:208:4'), pg_temp.uid('task:NOW-208'), 'Size limit and a truncation message', FALSE, NULL, 3);

INSERT INTO task_relation (from_task, to_task, kind) VALUES
    (pg_temp.uid('task:NOW-172'), pg_temp.uid('task:NOW-211'), 'blocks'),
    (pg_temp.uid('task:NOW-172'), pg_temp.uid('task:NOW-164'), 'relates'),
    (pg_temp.uid('task:NOW-190'), pg_temp.uid('task:NOW-186'), 'blocks'),
    (pg_temp.uid('task:NOW-164'), pg_temp.uid('task:NOW-172'), 'relates');

INSERT INTO task_comment (id, task_id, author_id, body, created_at) VALUES
    (pg_temp.uid('comment:c1'), pg_temp.uid('task:NOW-172'), pg_temp.uid('user:u2'),
     'Three nesting levels are enough, but we have to block that hard in the API, not only in the UI. Otherwise someone builds a rule through an import and the builder falls over while rendering.',
     pg_temp.ts(9, 11, 40)),
    (pg_temp.uid('comment:c2'), pg_temp.uid('task:NOW-172'), pg_temp.uid('user:u3'),
     'Done, the validator rejects a depth above three and points at the specific group. What is left is the rule preview as a sentence.',
     pg_temp.ts(9, 12, 5)),
    (pg_temp.uid('comment:c3'), pg_temp.uid('task:NOW-172'), pg_temp.uid('user:u1'),
     'Let us build the sentence preview on translation keys, not by gluing strings together. Otherwise the German version falls apart on the first condition.',
     pg_temp.ts(9, 13, 12)),
    (pg_temp.uid('comment:c4'), pg_temp.uid('task:NOW-190'), pg_temp.uid('user:u1'),
     'The dead letter queue needs a view in the administration panel, otherwise nobody will notice stuck notifications.',
     pg_temp.ts(8, 9, 20));

INSERT INTO automation_rule (id, project_id, name, summary, scope_label, enabled, draft, runs_30d,
                             trigger_def, conditions, actions, edited_by, edited_at, position) VALUES
    (pg_temp.uid('rule:r1'), pg_temp.uid('project:p1'), 'Work started',
     'Task assigned → status In progress', 'Platform', TRUE, FALSE, 128,
     '{"kind":"assigned","value":"anyone"}'::JSONB,
     '{"id":"g1","kind":"group","join":"and","children":[]}'::JSONB,
     '[{"id":"a1","kind":"setStatus","value":"status.doing","icon":"board"}]'::JSONB,
     pg_temp.uid('user:u1'), pg_temp.d(-14), 0),

    (pg_temp.uid('rule:r2'), pg_temp.uid('project:p1'), 'Handover to review',
     'Status Review → reviewer, due date, notification', 'Platform', TRUE, FALSE, 67,
     '{"kind":"statusChanged","value":"status.review"}'::JSONB,
     '{"id":"g1","kind":"group","join":"and","children":[{"id":"c1","kind":"condition","fieldKey":"cond.priority","op":"isOneOf","values":["priority.high","priority.critical"]},{"id":"g2","kind":"group","join":"or","children":[{"id":"c2","kind":"condition","fieldKey":"cond.label","op":"contains","values":["backend"]},{"id":"c3","kind":"condition","fieldKey":"cond.estimate","op":"greaterThan","values":["5"]}]}]}'::JSONB,
     '[{"id":"a1","kind":"assignReviewer","value":"Piotr Z.","icon":"user"},{"id":"a2","kind":"setDueDate","value":"+2 business days","icon":"calendar"},{"id":"a3","kind":"notifyChannel","value":"#reviews","icon":"message"}]'::JSONB,
     pg_temp.uid('user:u1'), pg_temp.d(2), 1),

    (pg_temp.uid('rule:r3'), pg_temp.uid('project:p1'), 'Due date alert',
     'Daily at 07:00 → label and notification', 'Platform', TRUE, FALSE, 44,
     '{"kind":"schedule","value":"07:00"}'::JSONB,
     '{"id":"g1","kind":"group","join":"and","children":[{"id":"c1","kind":"condition","fieldKey":"cond.dueIn","op":"isBefore","values":["48 h"]},{"id":"c2","kind":"condition","fieldKey":"cond.status","op":"isOneOf","values":["status.doing","status.review"]}]}'::JSONB,
     '[{"id":"a1","kind":"addLabel","value":"at-risk","icon":"flag"},{"id":"a2","kind":"notifyChannel","value":"#due-dates","icon":"message"}]'::JSONB,
     pg_temp.uid('user:u2'), pg_temp.d(-8), 2),

    (pg_temp.uid('rule:r4'), NULL, 'Auto-assignment by label',
     'Label backend → module owner', 'All projects', TRUE, FALSE, 312,
     '{"kind":"labelAdded","value":"backend"}'::JSONB,
     '{"id":"g1","kind":"group","join":"and","children":[{"id":"c1","kind":"condition","fieldKey":"cond.status","op":"isOneOf","values":["status.backlog","status.todo"]}]}'::JSONB,
     '[{"id":"a1","kind":"assignOwner","value":"Piotr Z.","icon":"user"}]'::JSONB,
     pg_temp.uid('user:u1'), pg_temp.d(-40), 3),

    (pg_temp.uid('rule:r5'), pg_temp.uid('project:p1'), 'Archiving after 14 days',
     'Done for 14 days → project archive', 'Platform', FALSE, TRUE, 0,
     '{"kind":"idleFor","value":"14 days"}'::JSONB,
     '{"id":"g1","kind":"group","join":"and","children":[{"id":"c1","kind":"condition","fieldKey":"cond.status","op":"isOneOf","values":["status.done"]}]}'::JSONB,
     '[{"id":"a1","kind":"archive","value":"Project archive","icon":"layers"}]'::JSONB,
     pg_temp.uid('user:u2'), pg_temp.d(-1), 4),

    (pg_temp.uid('rule:r6'), NULL, 'Blocker escalation',
     'Label blocker for more than 24 h → manager', 'All projects', TRUE, FALSE, 9,
     '{"kind":"idleFor","value":"24 h"}'::JSONB,
     '{"id":"g1","kind":"group","join":"and","children":[{"id":"c1","kind":"condition","fieldKey":"cond.label","op":"contains","values":["blocker"]}]}'::JSONB,
     '[{"id":"a1","kind":"escalate","value":"Marta W.","icon":"user"},{"id":"a2","kind":"notifyChannel","value":"#escalations","icon":"message"}]'::JSONB,
     pg_temp.uid('user:u1'), pg_temp.d(-25), 5);

INSERT INTO task_history (id, task_id, field, old_value, new_value, actor_id, rule_name, created_at) VALUES
    (pg_temp.uid('hist:h3'), pg_temp.uid('task:NOW-172'), 'status',   'status.todo', 'status.doing', NULL, 'Work started', pg_temp.ts(1, 9, 14)),
    (pg_temp.uid('hist:h4'), pg_temp.uid('task:NOW-172'), 'assignee', NULL,          'Marta W.',     NULL, 'Work started', pg_temp.ts(1, 9, 14)),
    (pg_temp.uid('hist:h5'), pg_temp.uid('task:NOW-172'), 'subtask',  NULL,          'Nesting depth validation on the API side', pg_temp.uid('user:u3'), NULL, pg_temp.ts(8, 16, 2)),
    (pg_temp.uid('hist:h2'), pg_temp.uid('task:NOW-172'), 'labels',   NULL,          'at-risk',      NULL, 'Due date alert', pg_temp.ts(9, 7, 0)),
    (pg_temp.uid('hist:h1'), pg_temp.uid('task:NOW-172'), 'estimate', '5',           '8',            pg_temp.uid('user:u2'), NULL, pg_temp.ts(9, 11, 37)),
    (pg_temp.uid('hist:h6'), pg_temp.uid('task:NOW-164'), 'status',   'status.doing', 'status.review', pg_temp.uid('user:u3'), NULL, pg_temp.ts(8, 8, 5)),
    (pg_temp.uid('hist:h7'), pg_temp.uid('task:NOW-164'), 'reviewer', NULL,          'Artur K.',     NULL, 'Handover to review', pg_temp.ts(8, 8, 5));

INSERT INTO automation_run (id, rule_id, task_key, outcome, detail_key, detail_params, created_at) VALUES
    (pg_temp.uid('run:rr1'), pg_temp.uid('rule:r2'), 'NOW-164', 'ok',      'run.actionsExecuted', '{"count":"3"}'::JSONB,          pg_temp.ts(9, 14, 22)),
    (pg_temp.uid('run:rr2'), pg_temp.uid('rule:r2'), 'NOW-169', 'skipped', 'run.conditionNotMet', '{"value":"3"}'::JSONB,          pg_temp.ts(9, 9, 41)),
    (pg_temp.uid('run:rr3'), pg_temp.uid('rule:r2'), 'NOW-158', 'error',   'run.channelSilent',   '{"channel":"#reviews"}'::JSONB, pg_temp.ts(8, 17, 3)),
    (pg_temp.uid('run:rr4'), pg_temp.uid('rule:r1'), 'NOW-186', 'ok',      'run.actionsExecuted', '{"count":"1"}'::JSONB,          pg_temp.ts(9, 8, 12)),
    (pg_temp.uid('run:rr5'), pg_temp.uid('rule:r3'), 'NOW-172', 'ok',      'run.actionsExecuted', '{"count":"2"}'::JSONB,          pg_temp.ts(9, 7, 0)),
    (pg_temp.uid('run:rr6'), pg_temp.uid('rule:r4'), 'NOW-217', 'ok',      'run.actionsExecuted', '{"count":"1"}'::JSONB,          pg_temp.ts(8, 15, 31)),
    (pg_temp.uid('run:rr7'), pg_temp.uid('rule:r6'), 'NOW-190', 'ok',      'run.actionsExecuted', '{"count":"2"}'::JSONB,          pg_temp.ts(7, 18, 44)),
    (pg_temp.uid('run:rr8'), pg_temp.uid('rule:r6'), 'NOW-190', 'error',   'run.channelSilent',   '{"channel":"#escalations"}'::JSONB, pg_temp.ts(6, 12, 10));

INSERT INTO custom_field (id, project_id, name, field_key, type, scope_label, restricted_to_role, position) VALUES
    (pg_temp.uid('cf:f1'), NULL,                        'Team',            'team',         'select',   'settings.allProjects', NULL,      0),
    (pg_temp.uid('cf:f2'), pg_temp.uid('project:p1'),   'Risk',            'risk_level',   'select',   'Platform',             NULL,      1),
    (pg_temp.uid('cf:f3'), NULL,                        'Cost',            'cost_pln',     'currency', 'settings.allProjects', 'manager', 2),
    (pg_temp.uid('cf:f4'), pg_temp.uid('project:p1'),   'Needs QA',        'needs_qa',     'toggle',   'Platform',             NULL,      3),
    (pg_temp.uid('cf:f5'), NULL,                        'Release date',    'release_date', 'date',     'settings.allProjects', NULL,      4),
    (pg_temp.uid('cf:f6'), NULL,                        'Spec link',       'spec_url',     'url',      'settings.allProjects', NULL,      5),
    (pg_temp.uid('cf:f7'), NULL,                        'Story points',    'story_points', 'number',   'settings.allProjects', NULL,      6),
    (pg_temp.uid('cf:f8'), NULL,                        'Client',          'client_ref',   'relation', 'settings.allProjects', 'manager', 7);

INSERT INTO saved_view (id, code, position) VALUES
    (pg_temp.uid('view:v1'), 'view.atRisk', 0),
    (pg_temp.uid('view:v2'), 'view.unassigned', 1),
    (pg_temp.uid('view:v3'), 'view.automated', 2);

INSERT INTO api_key (id, prefix, label, last_used_at) VALUES
    (pg_temp.uid('key:k1'), 'now_live_9f2c…4a1', 'CI integration',      now() - INTERVAL '2 minutes'),
    (pg_temp.uid('key:k2'), 'now_live_1b7e…c93', 'Warehouse reports',   pg_temp.ts(8, 10, 0));

INSERT INTO milestone (id, project_id, name, due_date) VALUES
    (pg_temp.uid('ms:m1'), pg_temp.uid('project:p1'), 'Scope freeze', pg_temp.d(11)),
    (pg_temp.uid('ms:m2'), pg_temp.uid('project:p1'), 'Release 2.4',  pg_temp.d(25));

-- Sprint burndown: business days, the plan linear from 120 to 0, the actual only up to today.
INSERT INTO burndown_point (id, project_id, sprint_code, day, remaining, ideal)
SELECT
    md5('burndown:' || day_offset::TEXT)::UUID,
    pg_temp.uid('project:p1'),
    'S24',
    pg_temp.d(day_offset),
    CASE
        WHEN pg_temp.d(day_offset) > CURRENT_DATE THEN NULL
        ELSE GREATEST(0, 120 - ROUND(day_offset * 6.4)::INT)
    END,
    GREATEST(0, 120 - ROUND(day_offset * 120.0 / 13)::INT)
FROM generate_series(0, 13) AS day_offset
WHERE EXTRACT(ISODOW FROM pg_temp.d(day_offset)) < 6;

-- Throughput: eight closed weeks before the current one.
INSERT INTO throughput_week (id, project_id, label, week_start, completed)
SELECT
    md5('throughput:' || weeks_back::TEXT)::UUID,
    pg_temp.uid('project:p1'),
    'W' || EXTRACT(WEEK FROM (date_trunc('week', CURRENT_DATE)::DATE - weeks_back * 7))::TEXT,
    date_trunc('week', CURRENT_DATE)::DATE - weeks_back * 7,
    value
FROM (VALUES (7, 17), (6, 22), (5, 14), (4, 25), (3, 19), (2, 27), (1, 23), (0, 31)) AS t(weeks_back, value);

import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';
import { ApiError, NowtaskClient } from './client.js';
import { SCOPE_PURPOSE, type Scope } from './scopes.js';

interface ToolSpec<S extends z.ZodRawShape> {
  name: string;
  title: string;
  scope: Scope | null;
  summary: string;
  inputSchema: S;
  readOnly: boolean;
  destructive?: boolean;
  idempotent?: boolean;
  run: (args: z.objectOutputType<S, z.ZodTypeAny>) => Promise<unknown>;
}

function scopeLine(scope: Scope | null): string {
  if (scope === null) {
    return 'Requires no scope; works with any nowtask API key.';
  }
  return `Requires the "${scope}" scope on the API key (${SCOPE_PURPOSE[scope]}).`;
}

function textResult(payload: unknown, isError = false) {
  const text = typeof payload === 'string' ? payload : JSON.stringify(payload, null, 2);
  return { content: [{ type: 'text' as const, text }], isError };
}

function define<S extends z.ZodRawShape>(server: McpServer, client: NowtaskClient, spec: ToolSpec<S>): void {
  server.registerTool(
    spec.name,
    {
      title: spec.title,
      description: `${spec.summary}\n\n${scopeLine(spec.scope)}`,
      inputSchema: spec.inputSchema,
      annotations: {
        title: spec.title,
        readOnlyHint: spec.readOnly,
        destructiveHint: spec.destructive === true,
        idempotentHint: spec.idempotent === true,
        openWorldHint: true
      }
    },
    (async (args: z.objectOutputType<S, z.ZodTypeAny>) => {
      if (spec.scope !== null) {
        const missing = client.missingScopeMessage(spec.scope);
        if (missing !== null) {
          return textResult(missing, true);
        }
      }
      try {
        return textResult(await spec.run(args));
      } catch (error) {
        if (error instanceof ApiError) {
          return textResult(error.agentMessage, true);
        }
        const reason = error instanceof Error ? error.message : String(error);
        return textResult(`Unexpected failure while calling nowtask: ${reason}`, true);
      }
    }) as never
  );
}

function patchBody(source: Record<string, unknown>, keys: string[]): Record<string, unknown> {
  const body: Record<string, unknown> = {};
  for (const key of keys) {
    if (source[key] !== undefined) {
      body[key] = source[key];
    }
  }
  return body;
}

const taskKey = z
  .string()
  .regex(/^[A-Z]+-\d+$/, 'Task key looks like "NOW-172": project prefix, dash, number.')
  .describe('Task key exactly as shown in nowtask, for example "NOW-172". Not a UUID.');

const uuid = (what: string) => z.string().uuid().describe(what);
const isoDate = (what: string) =>
  z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Use the calendar format YYYY-MM-DD.').describe(what);

export function registerAllTools(server: McpServer, client: NowtaskClient): void {
  define(server, client, {
    name: 'nowtask_whoami',
    title: 'nowtask connection and permissions',
    scope: null,
    readOnly: true,
    idempotent: true,
    summary:
      'Report which nowtask workspace this server talks to, which API key it uses and which scopes that key has. ' +
      'Call this once at the start of a session, or whenever a tool fails with a permission error, so you can tell the user exactly which scope is missing instead of guessing.',
    inputSchema: {},
    run: async () => {
      const identity = await client.refreshIdentity();
      if (identity === null) {
        return 'The nowtask API did not confirm the API key. Either the backend is unreachable or the key is invalid, revoked or expired.';
      }
      return {
        key: { prefix: identity.prefix, label: identity.label },
        actingAs: identity.owner,
        grantedScopes: identity.scopes,
        scopeMeaning: Object.fromEntries(identity.scopes.map(scope => [scope, SCOPE_PURPOSE[scope as Scope] ?? 'unknown scope'])),
        note: 'Every change you make is written into the task history and the audit log under this key, so it is visible to humans that an agent did it.'
      };
    }
  });

  define(server, client, {
    name: 'nowtask_workspace',
    title: 'Workspace configuration',
    scope: 'workspace:read',
    readOnly: true,
    idempotent: true,
    summary:
      'Return the workspace dictionary: projects, statuses (id, code, label), people, epics and saved views. ' +
      'Use it to turn a human name into the UUID that write tools need, for example to find the status id for "in review" or the user id for "Anna". ' +
      'Call it once and reuse the result; it changes rarely.',
    inputSchema: {},
    run: async () => client.request('/api/bootstrap')
  });

  define(server, client, {
    name: 'nowtask_tasks_search',
    title: 'Search and filter tasks',
    scope: 'tasks:read',
    readOnly: true,
    idempotent: true,
    summary:
      'List tasks with optional filters and paging. This is the main way to find work. ' +
      'With no arguments it returns the current sprint, which is usually what "what are we working on" means. ' +
      'Combine filters freely: they are joined with AND. Ids come from nowtask_workspace. ' +
      'Prefer this over nowtask_search when you want structured filtering rather than a free-text jump.',
    inputSchema: {
      query: z.string().optional().describe('Free text matched against title and description.'),
      statusId: uuid('Only tasks in this status. Get the id from nowtask_workspace.').optional(),
      assigneeId: uuid('Only tasks assigned to this person.').optional(),
      label: z.string().optional().describe('Only tasks carrying this label, matched exactly.'),
      priority: z.enum(['critical', 'high', 'medium', 'low']).optional().describe('Only tasks with this priority.'),
      epicId: uuid('Only tasks inside this epic.').optional(),
      dueBefore: isoDate('Only tasks whose due date is before this day, format YYYY-MM-DD.').optional(),
      unassigned: z.boolean().optional().describe('true returns only tasks with nobody assigned.'),
      automated: z.boolean().optional().describe('true returns only tasks touched by an automation rule.'),
      sprint: z.string().optional().describe('Sprint code, for example "S-24". Omit for the current sprint.'),
      groupBy: z.enum(['status', 'assignee', 'priority', 'epic']).optional().describe('Adds a groups summary to the answer.'),
      sort: z.string().optional().describe('Sort expression, for example "dueDate" or "-priority" for descending.'),
      page: z.number().int().min(0).optional().describe('Zero-based page number. Requires size.'),
      size: z.number().int().min(1).max(200).optional().describe('Page size, 1-200. Use 20-50 for a readable answer.')
    },
    run: async args => client.request('/api/tasks', { query: args as Record<string, string | number | boolean | undefined> })
  });

  define(server, client, {
    name: 'nowtask_task_get',
    title: 'Task detail',
    scope: 'tasks:read',
    readOnly: true,
    idempotent: true,
    summary:
      'Return one task in full: description, assignee, reviewer, dates, estimate, labels, subtasks, relations and custom fields. ' +
      'Read a task with this tool before changing it, so you patch only what you mean to change.',
    inputSchema: { key: taskKey },
    run: async ({ key }) => client.request(`/api/tasks/${encodeURIComponent(key)}`)
  });

  define(server, client, {
    name: 'nowtask_task_comments',
    title: 'Task comments',
    scope: 'tasks:read',
    readOnly: true,
    idempotent: true,
    summary: 'Return the comment thread of one task, oldest first. Use it to understand a decision before acting on the task.',
    inputSchema: { key: taskKey },
    run: async ({ key }) => client.request(`/api/tasks/${encodeURIComponent(key)}/comments`)
  });

  define(server, client, {
    name: 'nowtask_task_history',
    title: 'Task history',
    scope: 'tasks:read',
    readOnly: true,
    idempotent: true,
    summary:
      'Return the change log of one task: which field changed, from what to what, who did it and when. ' +
      'Entries whose ruleName starts with "agent:" were made through an API key, that is by an AI agent, and the text after the colon names the key.',
    inputSchema: { key: taskKey },
    run: async ({ key }) => client.request(`/api/tasks/${encodeURIComponent(key)}/history`)
  });

  define(server, client, {
    name: 'nowtask_task_create',
    title: 'Create task',
    scope: 'tasks:write',
    readOnly: false,
    summary:
      'Create a new task and return it, including the task key that nowtask assigns. ' +
      'Only the title is mandatory; everything else is optional and can be filled in later with nowtask_task_update. ' +
      'Write the title as a short imperative sentence in the language the workspace uses. ' +
      'Do not invent UUIDs: take statusId, projectId, assigneeId, reviewerId and epicId from nowtask_workspace, or leave them out.',
    inputSchema: {
      title: z.string().min(1).max(300).describe('Short title, one line, no trailing period.'),
      description: z.string().optional().describe('Longer description. Plain text, newlines allowed.'),
      statusId: uuid('Starting status. Omitted means the first status of the workflow.').optional(),
      projectId: uuid('Project the task belongs to. Omitted means the first project of the workspace.').optional(),
      sprintCode: z.string().optional().describe('Sprint code the task starts in, for example "2026-S3".'),
      priority: z.enum(['critical', 'high', 'medium', 'low']).optional().describe('Priority. Defaults to medium.'),
      assigneeId: uuid('Person responsible for the work.').optional(),
      reviewerId: uuid('Person who reviews the result.').optional(),
      dueDate: isoDate('Due date, format YYYY-MM-DD.').optional(),
      estimate: z.number().int().min(0).optional().describe('Estimate in story points.'),
      epicId: uuid('Epic this task belongs to.').optional(),
      labels: z.array(z.string()).optional().describe('Labels to attach, for example ["frontend","bug"].')
    },
    run: async args => client.request('/api/tasks', { method: 'POST', body: args })
  });

  define(server, client, {
    name: 'nowtask_task_update',
    title: 'Update task fields',
    scope: 'tasks:write',
    readOnly: false,
    idempotent: true,
    summary:
      'Change fields of an existing task. Send only the fields you want to change: any field you leave out stays as it is. ' +
      'Sending null clears a field, for example assigneeId: null unassigns the task. ' +
      'To move a task between columns prefer nowtask_task_set_status, which checks the allowed transitions and explains a refusal.',
    inputSchema: {
      key: taskKey,
      title: z.string().min(1).max(300).optional().describe('New title.'),
      description: z.string().nullable().optional().describe('New description, or null to clear it.'),
      priority: z.enum(['critical', 'high', 'medium', 'low']).optional().describe('New priority.'),
      assigneeId: z.string().uuid().nullable().optional().describe('New assignee id, or null to unassign.'),
      reviewerId: z.string().uuid().nullable().optional().describe('New reviewer id, or null to clear.'),
      epicId: z.string().uuid().nullable().optional().describe('New epic id, or null to detach from the epic.'),
      dueDate: z.string().nullable().optional().describe('Due date YYYY-MM-DD, or null to clear.'),
      startDate: z.string().nullable().optional().describe('Start date YYYY-MM-DD, or null to clear.'),
      endDate: z.string().nullable().optional().describe('End date YYYY-MM-DD, or null to clear.'),
      estimate: z.number().int().min(0).nullable().optional().describe('Estimate in story points, or null to clear.'),
      statusId: z.string().uuid().optional().describe('New status id. Rejected with a 422 when the transition is not allowed.')
    },
    run: async args => {
      const { key, ...rest } = args as Record<string, unknown> & { key: string };
      const body = patchBody(rest, [
        'title',
        'description',
        'priority',
        'assigneeId',
        'reviewerId',
        'epicId',
        'dueDate',
        'startDate',
        'endDate',
        'estimate',
        'statusId'
      ]);
      if (Object.keys(body).length === 0) {
        return 'Nothing to change: no field was supplied besides the task key.';
      }
      return client.request(`/api/tasks/${encodeURIComponent(key)}`, { method: 'PATCH', body });
    }
  });

  define(server, client, {
    name: 'nowtask_task_set_status',
    title: 'Move task to another status',
    scope: 'tasks:write',
    readOnly: false,
    idempotent: true,
    summary:
      'Move one task to another status, which is what "start", "finish", "move to review" mean on the board. ' +
      'Pass either statusId or statusCode; statusCode is the short code from nowtask_workspace, for example "in_review". ' +
      'The workspace may forbid a jump between two statuses; then nowtask answers 422 and you should pick an allowed intermediate status instead of retrying the same move.',
    inputSchema: {
      key: taskKey,
      statusId: z.string().uuid().optional().describe('Target status id. Preferred when you already know it.'),
      statusCode: z.string().optional().describe('Target status code, for example "done". Resolved through the workspace configuration.')
    },
    run: async ({ key, statusId, statusCode }) => {
      let target = statusId;
      if (!target) {
        if (!statusCode) {
          return 'Pass either statusId or statusCode.';
        }
        const workspace = await client.request<{ statuses: { id: string; code: string; label: string }[] }>('/api/bootstrap');
        const match = workspace.statuses.find(status => status.code.toLowerCase() === statusCode.toLowerCase());
        if (!match) {
          return `No status with code "${statusCode}". Available codes: ${workspace.statuses.map(status => status.code).join(', ')}.`;
        }
        target = match.id;
      }
      return client.request(`/api/tasks/${encodeURIComponent(key)}`, { method: 'PATCH', body: { statusId: target } });
    }
  });

  define(server, client, {
    name: 'nowtask_task_add_comment',
    title: 'Comment on a task',
    scope: 'tasks:write',
    readOnly: false,
    summary:
      'Add a comment to a task. Use it to leave findings, questions or a summary of what you changed, instead of silently editing fields. ' +
      'Comments cannot be edited or removed through this server, so write the final text in one go.',
    inputSchema: {
      key: taskKey,
      body: z.string().min(1).max(5000).describe('Comment text. Plain text, newlines allowed.')
    },
    run: async ({ key, body }) => client.request(`/api/tasks/${encodeURIComponent(key)}/comments`, { method: 'POST', body: { body } })
  });

  define(server, client, {
    name: 'nowtask_task_add_label',
    title: 'Add a label',
    scope: 'tasks:write',
    readOnly: false,
    idempotent: true,
    summary: 'Attach one label to a task and return the full label list. Reuse labels that already exist in the workspace instead of inventing near-duplicates.',
    inputSchema: { key: taskKey, label: z.string().min(1).max(60).describe('Label text, for example "bug".') },
    run: async ({ key, label }) => client.request(`/api/tasks/${encodeURIComponent(key)}/labels`, { method: 'POST', body: { label } })
  });

  define(server, client, {
    name: 'nowtask_task_remove_label',
    title: 'Remove a label',
    scope: 'tasks:write',
    readOnly: false,
    idempotent: true,
    summary: 'Detach one label from a task and return the remaining labels. The label itself survives on other tasks.',
    inputSchema: { key: taskKey, label: z.string().min(1).describe('Label to remove, matched exactly.') },
    run: async ({ key, label }) =>
      client.request(`/api/tasks/${encodeURIComponent(key)}/labels/${encodeURIComponent(label)}`, { method: 'DELETE' })
  });

  define(server, client, {
    name: 'nowtask_task_add_relation',
    title: 'Link two tasks',
    scope: 'tasks:write',
    readOnly: false,
    idempotent: true,
    summary:
      'Create a link between two tasks, for example that this task blocks another one. ' +
      'The link is directed: key is the source task, taskKey is the other end.',
    inputSchema: {
      key: taskKey,
      kind: z.enum(['blocks', 'blockedBy', 'relatesTo', 'duplicates']).describe('Kind of link seen from the source task.'),
      taskKey: z.string().describe('Key of the task at the other end, for example "NOW-181".')
    },
    run: async ({ key, kind, taskKey: other }) =>
      client.request(`/api/tasks/${encodeURIComponent(key)}/relations`, { method: 'POST', body: { kind, taskKey: other } })
  });

  define(server, client, {
    name: 'nowtask_task_remove_relation',
    title: 'Unlink two tasks',
    scope: 'tasks:write',
    readOnly: false,
    idempotent: true,
    summary: 'Remove a link between two tasks. Neither task is deleted, only the link between them.',
    inputSchema: {
      key: taskKey,
      kind: z.enum(['blocks', 'blockedBy', 'relatesTo', 'duplicates']).describe('Kind of link to remove.'),
      taskKey: z.string().describe('Key of the task at the other end.')
    },
    run: async ({ key, kind, taskKey: other }) =>
      client.request(
        `/api/tasks/${encodeURIComponent(key)}/relations/${encodeURIComponent(kind)}/${encodeURIComponent(other)}`,
        { method: 'DELETE' }
      )
  });

  define(server, client, {
    name: 'nowtask_task_set_custom_field',
    title: 'Set a custom field',
    scope: 'tasks:write',
    readOnly: false,
    idempotent: true,
    summary:
      'Set the value of one workspace custom field on a task. Field keys and their types come from nowtask_workspace. ' +
      'Match the declared type: send a number for a number field, a YYYY-MM-DD string for a date field.',
    inputSchema: {
      key: taskKey,
      fieldKey: z.string().describe('Custom field key, for example "clientBudget".'),
      value: z.union([z.string(), z.number(), z.boolean(), z.null()]).describe('New value, or null to clear the field.')
    },
    run: async ({ key, fieldKey, value }) =>
      client.request(`/api/tasks/${encodeURIComponent(key)}/custom/${encodeURIComponent(fieldKey)}`, {
        method: 'PUT',
        body: { value }
      })
  });

  define(server, client, {
    name: 'nowtask_task_toggle_watch',
    title: 'Toggle watching a task',
    scope: 'tasks:write',
    readOnly: false,
    summary:
      'Toggle whether the account behind the API key watches this task. This is a switch, not a setter: calling it twice returns to the starting state, so read the returned watching flag.',
    inputSchema: { key: taskKey },
    run: async ({ key }) => client.request(`/api/tasks/${encodeURIComponent(key)}/watch`, { method: 'POST' })
  });

  define(server, client, {
    name: 'nowtask_subtask_add',
    title: 'Add a subtask',
    scope: 'tasks:write',
    readOnly: false,
    summary: 'Add one checklist item to a task. Use short, checkable steps; break a large task into several calls rather than one long title.',
    inputSchema: {
      key: taskKey,
      title: z.string().min(1).max(200).describe('Subtask title, one short step.'),
      assigneeId: uuid('Person responsible for this step.').optional()
    },
    run: async ({ key, title, assigneeId }) =>
      client.request(`/api/tasks/${encodeURIComponent(key)}/subtasks`, { method: 'POST', body: { title, assigneeId } })
  });

  define(server, client, {
    name: 'nowtask_subtask_update',
    title: 'Update a subtask',
    scope: 'tasks:write',
    readOnly: false,
    idempotent: true,
    summary: 'Change a subtask: rename it, mark it done or not done, or reassign it. Fields you leave out stay unchanged. Prefer done:true here over the toggle tool when you know the target state.',
    inputSchema: {
      key: taskKey,
      subtaskId: uuid('Subtask id from nowtask_task_get.'),
      title: z.string().min(1).max(200).optional().describe('New title.'),
      done: z.boolean().optional().describe('true marks the step finished, false reopens it.'),
      assigneeId: z.string().uuid().nullable().optional().describe('New assignee id, or null to unassign.')
    },
    run: async args => {
      const { key, subtaskId, ...rest } = args as Record<string, unknown> & { key: string; subtaskId: string };
      const body = patchBody(rest, ['title', 'done', 'assigneeId']);
      if (Object.keys(body).length === 0) {
        return 'Nothing to change: supply title, done or assigneeId.';
      }
      return client.request(`/api/tasks/${encodeURIComponent(key)}/subtasks/${encodeURIComponent(subtaskId)}`, {
        method: 'PATCH',
        body
      });
    }
  });

  define(server, client, {
    name: 'nowtask_subtask_toggle',
    title: 'Toggle a subtask',
    scope: 'tasks:write',
    readOnly: false,
    summary: 'Flip one subtask between done and not done and return the refreshed task. Use nowtask_subtask_update when you know which state you want.',
    inputSchema: { key: taskKey, subtaskId: uuid('Subtask id from nowtask_task_get.') },
    run: async ({ key, subtaskId }) =>
      client.request(`/api/tasks/${encodeURIComponent(key)}/subtasks/${encodeURIComponent(subtaskId)}/toggle`, { method: 'POST' })
  });

  define(server, client, {
    name: 'nowtask_subtask_delete',
    title: 'Delete a subtask (irreversible)',
    scope: 'tasks:delete',
    readOnly: false,
    destructive: true,
    idempotent: true,
    summary:
      'IRREVERSIBLE. Permanently remove one subtask; there is no undo and no trash. ' +
      'Only call it when the user asked for this exact subtask to be removed. If the step is merely finished, mark it done instead.',
    inputSchema: { key: taskKey, subtaskId: uuid('Subtask id from nowtask_task_get.') },
    run: async ({ key, subtaskId }) => {
      await client.request(`/api/tasks/${encodeURIComponent(key)}/subtasks/${encodeURIComponent(subtaskId)}`, {
        method: 'DELETE'
      });
      return `Subtask ${subtaskId} of ${key} was permanently deleted.`;
    }
  });

  define(server, client, {
    name: 'nowtask_task_delete',
    title: 'Delete a task (irreversible)',
    scope: 'tasks:delete',
    readOnly: false,
    destructive: true,
    idempotent: true,
    summary:
      'IRREVERSIBLE. Permanently delete a whole task with its comments, subtasks and history; there is no undo and no trash. ' +
      'Never call it to "clean up" or on your own initiative. Only call it after the user named this exact task and asked for deletion. ' +
      'A finished task belongs in the done status, not in the bin.',
    inputSchema: { key: taskKey },
    run: async ({ key }) => {
      await client.request(`/api/tasks/${encodeURIComponent(key)}`, { method: 'DELETE' });
      return `Task ${key} was permanently deleted.`;
    }
  });

  define(server, client, {
    name: 'nowtask_tasks_bulk_assign',
    title: 'Assign many tasks',
    scope: 'tasks:write',
    readOnly: false,
    idempotent: true,
    summary: 'Assign a list of tasks to one person in a single call. Pass assigneeId null to unassign all of them. Keep the list to what the user actually asked for.',
    inputSchema: {
      keys: z.array(z.string()).min(1).max(100).describe('Task keys, for example ["NOW-172","NOW-173"].'),
      assigneeId: z.string().uuid().nullable().describe('Person to assign, or null to unassign.')
    },
    run: async ({ keys, assigneeId }) => {
      await client.request('/api/tasks/bulk/assign', { method: 'POST', body: { keys, assigneeId } });
      return `Assignment updated for ${keys.length} task(s).`;
    }
  });

  define(server, client, {
    name: 'nowtask_tasks_bulk_status',
    title: 'Move many tasks',
    scope: 'tasks:write',
    readOnly: false,
    idempotent: true,
    summary: 'Move a list of tasks to the same status in a single call. Tasks whose transition is not allowed are refused; read the answer instead of assuming success.',
    inputSchema: {
      keys: z.array(z.string()).min(1).max(100).describe('Task keys to move.'),
      statusId: uuid('Target status id from nowtask_workspace.')
    },
    run: async ({ keys, statusId }) => {
      await client.request('/api/tasks/bulk/status', { method: 'POST', body: { keys, statusId } });
      return `Status updated for ${keys.length} task(s).`;
    }
  });

  define(server, client, {
    name: 'nowtask_rules_list',
    title: 'List automation rules',
    scope: 'rules:read',
    readOnly: true,
    idempotent: true,
    summary: 'List the automation rules of the workspace with their trigger, conditions, actions and whether they are enabled. Read this before you blame a rule for a change on a task.',
    inputSchema: {},
    run: async () => client.request('/api/rules')
  });

  define(server, client, {
    name: 'nowtask_rule_get',
    title: 'Automation rule detail',
    scope: 'rules:read',
    readOnly: true,
    idempotent: true,
    summary: 'Return one automation rule in full, by its UUID from nowtask_rules_list.',
    inputSchema: { ruleId: uuid('Rule id from nowtask_rules_list.') },
    run: async ({ ruleId }) => client.request(`/api/rules/${encodeURIComponent(ruleId)}`)
  });

  define(server, client, {
    name: 'nowtask_rule_runs',
    title: 'Automation run log',
    scope: 'rules:read',
    readOnly: true,
    idempotent: true,
    summary: 'Return the run log of one rule: when it fired, on which task and whether the outcome was ok, skipped or error. Use it to diagnose a rule that "does nothing".',
    inputSchema: { ruleId: uuid('Rule id from nowtask_rules_list.') },
    run: async ({ ruleId }) => client.request(`/api/rules/${encodeURIComponent(ruleId)}/runs`)
  });

  define(server, client, {
    name: 'nowtask_rule_run',
    title: 'Run a rule on one task',
    scope: 'rules:run',
    readOnly: false,
    summary:
      'Run one automation rule manually against one task, as if its trigger had fired. The rule really performs its actions, so the task can change status, gain a label or send a notification. ' +
      'Read the rule with nowtask_rule_get first so you know what it will do.',
    inputSchema: { ruleId: uuid('Rule id from nowtask_rules_list.'), key: taskKey },
    run: async ({ ruleId, key }) => client.request(`/api/rules/${encodeURIComponent(ruleId)}/run`, { method: 'POST', body: { taskKey: key } })
  });

  define(server, client, {
    name: 'nowtask_rule_toggle',
    title: 'Enable or disable a rule',
    scope: 'rules:write',
    readOnly: false,
    summary:
      'Flip an automation rule between enabled and disabled. This is a switch, not a setter, and it changes how the workspace behaves for everyone, so only use it when the user asked. Read the returned enabled flag.',
    inputSchema: { ruleId: uuid('Rule id from nowtask_rules_list.') },
    run: async ({ ruleId }) => client.request(`/api/rules/${encodeURIComponent(ruleId)}/toggle`, { method: 'POST' })
  });

  define(server, client, {
    name: 'nowtask_metrics_overview',
    title: 'Workspace metrics',
    scope: 'metrics:read',
    readOnly: true,
    idempotent: true,
    summary: 'Return the dashboard numbers: task counts per state, burndown, weekly throughput and workload per person. Use it to answer "how are we doing" without listing every task.',
    inputSchema: {},
    run: async () => client.request('/api/metrics/overview')
  });

  define(server, client, {
    name: 'nowtask_timeline',
    title: 'Scheduled work',
    scope: 'tasks:read',
    readOnly: true,
    idempotent: true,
    summary: 'Return tasks that have start and end dates, with their progress and what they block. Use it for questions about the schedule, overlaps and what is late.',
    inputSchema: {},
    run: async () => client.request('/api/timeline')
  });

  define(server, client, {
    name: 'nowtask_search',
    title: 'Quick search',
    scope: 'tasks:read',
    readOnly: true,
    idempotent: true,
    summary:
      'One free-text search across tasks, rules, people and saved views, at most five hits per category. ' +
      'Use it when the user names something vaguely and you do not yet know what kind of object it is; use nowtask_tasks_search when you already know you want tasks.',
    inputSchema: { q: z.string().min(1).describe('Search text.') },
    run: async ({ q }) => client.request('/api/search', { query: { q } })
  });
}

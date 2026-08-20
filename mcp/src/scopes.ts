export const SCOPES = [
  'tasks:read',
  'tasks:write',
  'tasks:delete',
  'rules:read',
  'rules:run',
  'rules:write',
  'metrics:read',
  'workspace:read'
] as const;

export type Scope = (typeof SCOPES)[number];

export const SCOPE_PURPOSE: Record<Scope, string> = {
  'tasks:read': 'read tasks, comments, history, timeline and search',
  'tasks:write': 'create and change tasks, subtasks, labels, relations and comments',
  'tasks:delete': 'permanently delete tasks and subtasks',
  'rules:read': 'read automation rules and their run log',
  'rules:run': 'run an automation rule manually on one task',
  'rules:write': 'enable or disable automation rules',
  'metrics:read': 'read workspace metrics',
  'workspace:read': 'read statuses, epics, people and other workspace configuration'
};

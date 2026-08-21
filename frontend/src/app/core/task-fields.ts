export type TaskFieldKey =
  | 'description'
  | 'priority'
  | 'assignee'
  | 'reviewer'
  | 'dueDate'
  | 'estimate'
  | 'epic'
  | 'labels'
  | 'sprint';

export const CUSTOM_FIELD_PREFIX = 'custom:';

export const TASK_FIELDS: readonly { key: TaskFieldKey; label: string }[] = [
  { key: 'description', label: 'task.description' },
  { key: 'priority', label: 'list.priority' },
  { key: 'assignee', label: 'common.assignee' },
  { key: 'reviewer', label: 'task.reviewer' },
  { key: 'dueDate', label: 'task.dueDate' },
  { key: 'estimate', label: 'task.estimate' },
  { key: 'epic', label: 'task.epic' },
  { key: 'labels', label: 'list.labels' },
  { key: 'sprint', label: 'filters.sprint' },
];

export function customFieldKey(fieldKey: string): string {
  return CUSTOM_FIELD_PREFIX + fieldKey;
}

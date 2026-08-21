import type { TaskViewCode } from './api-types';

export interface TaskViewMeta {
  code: TaskViewCode;
  path: string;
  icon: string;
  label: string;
}

export const TASK_VIEWS: readonly TaskViewMeta[] = [
  { code: 'board', path: '/app/board', icon: 'board', label: 'nav.board' },
  { code: 'list', path: '/app/list', icon: 'list', label: 'nav.list' },
  { code: 'timeline', path: '/app/timeline', icon: 'timeline', label: 'nav.timeline' },
  { code: 'calendar', path: '/app/calendar', icon: 'calendar', label: 'nav.calendar' },
];

export function taskViewMeta(code: TaskViewCode): TaskViewMeta {
  return TASK_VIEWS.find((view) => view.code === code) ?? TASK_VIEWS[0];
}

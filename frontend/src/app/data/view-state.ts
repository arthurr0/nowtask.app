import { Injectable, computed, effect, inject, signal } from '@angular/core';
import type { ListColumn, TaskDto, TaskQueryDto } from '../core/api-types';
import { WorkspaceStore } from './workspace.store';

export type GroupBy = 'status' | 'assignee' | 'priority' | 'epic';
export type SortBy = 'manual' | 'due' | 'priority' | 'title' | 'key';

export interface ColumnPref {
  code: ListColumn;
  hidden: boolean;
}

export const GROUP_FIELDS: Record<GroupBy, string> = {
  status: 'status',
  assignee: 'assignee',
  priority: 'priority',
  epic: 'epic',
};

export const SORT_FIELDS: Partial<Record<SortBy, string>> = {
  due: 'dueDate',
  priority: 'priority',
};

export const COLUMN_FIELDS: Record<ListColumn, string> = {
  labels: 'labels',
  assignee: 'assignee',
  priority: 'priority',
  due: 'dueDate',
  estimate: 'estimate',
};

export const LIST_COLUMNS: readonly { code: ListColumn; label: string }[] = [
  { code: 'labels', label: 'list.labels' },
  { code: 'assignee', label: 'common.assignee' },
  { code: 'priority', label: 'list.priority' },
  { code: 'due', label: 'list.due' },
  { code: 'estimate', label: 'list.pts' },
];

function defaultColumns(): ColumnPref[] {
  return LIST_COLUMNS.map((column) => ({ code: column.code, hidden: false }));
}

function columnsFromQuery(visible: readonly ListColumn[] | undefined): ColumnPref[] {
  if (!visible) return defaultColumns();
  const known = visible.filter((code) => LIST_COLUMNS.some((column) => column.code === code));
  const shown = known.map((code) => ({ code, hidden: false }));
  const rest = LIST_COLUMNS.filter((column) => !known.includes(column.code)).map((column) => ({
    code: column.code,
    hidden: true,
  }));
  return [...shown, ...rest];
}

const PRIORITY_ORDER: Record<string, number> = { critical: 0, high: 1, medium: 2, low: 3 };

@Injectable({ providedIn: 'root' })
export class ViewState {
  private readonly store = inject(WorkspaceStore);

  readonly search = signal('');
  readonly assigneeId = signal<string | null>(null);
  readonly label = signal<string | null>(null);
  readonly priority = signal<string | null>(null);
  readonly epicId = signal<string | null>(null);
  readonly statusId = signal<string | null>(null);
  readonly projectId = signal<string | null>(null);
  readonly sprint = signal<string | null>(null);
  readonly unassigned = signal(false);
  readonly automated = signal(false);
  readonly overdueOnly = signal(false);
  readonly groupBy = signal<GroupBy>('status');
  readonly sort = signal<SortBy>('manual');
  readonly activeViewId = signal<string | null>(null);
  readonly columns = signal<ColumnPref[]>(defaultColumns());

  constructor() {
    effect(() => {
      const group = this.groupBy();
      if (
        group !== 'status' &&
        !this.store.taskFieldEnabled(GROUP_FIELDS[group], this.projectId())
      ) {
        this.groupBy.set('status');
      }

      const sortField = SORT_FIELDS[this.sort()];
      if (sortField && !this.store.taskFieldEnabled(sortField, this.projectId())) {
        this.sort.set('manual');
      }
    });
  }

  readonly availableColumns = computed(() =>
    LIST_COLUMNS.filter((column) =>
      this.store.taskFieldEnabled(COLUMN_FIELDS[column.code], this.projectId()),
    ),
  );

  columnAvailable(code: ListColumn): boolean {
    return this.store.taskFieldEnabled(COLUMN_FIELDS[code], this.projectId());
  }

  readonly visibleColumns = computed<ListColumn[]>(() =>
    this.columns()
      .filter((column) => !column.hidden && this.columnAvailable(column.code))
      .map((column) => column.code),
  );

  readonly hiddenColumnCount = computed(
    () =>
      this.columns().filter((column) => column.hidden && this.columnAvailable(column.code)).length,
  );

  toggleColumn(code: ListColumn): void {
    this.columns.update((columns) =>
      columns.map((column) =>
        column.code === code ? { ...column, hidden: !column.hidden } : column,
      ),
    );
  }

  moveColumn(code: ListColumn, step: -1 | 1): void {
    this.columns.update((columns) => {
      const next = [...columns];
      const index = next.findIndex((column) => column.code === code);
      const target = index + step;
      if (index < 0 || target < 0 || target >= next.length) return columns;
      const [moved] = next.splice(index, 1);
      next.splice(target, 0, moved);
      return next;
    });
  }

  resetColumns(): void {
    this.columns.set(defaultColumns());
  }

  readonly activeCount = computed(() => {
    let count = 0;
    if (this.search().trim()) count += 1;
    if (this.assigneeId()) count += 1;
    if (this.label()) count += 1;
    if (this.priority()) count += 1;
    if (this.epicId()) count += 1;
    if (this.statusId()) count += 1;
    if (this.projectId()) count += 1;
    if (this.sprint()) count += 1;
    if (this.unassigned()) count += 1;
    if (this.automated()) count += 1;
    if (this.overdueOnly()) count += 1;
    return count;
  });

  readonly hasFilters = computed(() => this.activeCount() > 0);

  reset(): void {
    this.search.set('');
    this.assigneeId.set(null);
    this.label.set(null);
    this.priority.set(null);
    this.epicId.set(null);
    this.statusId.set(null);
    this.projectId.set(null);
    this.sprint.set(null);
    this.unassigned.set(false);
    this.automated.set(false);
    this.overdueOnly.set(false);
    this.activeViewId.set(null);
  }

  matches(task: TaskDto): boolean {
    const needle = this.search().trim().toLowerCase();
    if (needle) {
      const haystack = `${task.key} ${task.title} ${task.labels.join(' ')}`.toLowerCase();
      if (!haystack.includes(needle)) return false;
    }
    if (this.assigneeId() && task.assigneeId !== this.assigneeId()) return false;
    if (this.unassigned() && task.assigneeId !== null) return false;
    if (this.label() && !task.labels.includes(this.label()!)) return false;
    if (this.priority() && task.priority !== this.priority()) return false;
    if (this.epicId() && task.epicId !== this.epicId()) return false;
    if (this.statusId() && task.statusId !== this.statusId()) return false;
    if (this.projectId() && task.projectId !== this.projectId()) return false;
    if (this.sprint() && task.sprintCode !== this.sprint()) return false;
    if (this.automated() && !task.automated) return false;
    if (this.overdueOnly()) {
      if (!task.dueDate) return false;
      if (task.dueDate >= this.store.today) return false;
      if (task.statusCode === 'done') return false;
    }
    return true;
  }

  apply(tasks: readonly TaskDto[]): TaskDto[] {
    return this.sortTasks(tasks.filter((task) => this.matches(task)));
  }

  sortTasks(tasks: readonly TaskDto[]): TaskDto[] {
    const mode = this.sort();
    if (mode === 'manual') return [...tasks];
    const sorted = [...tasks];
    sorted.sort((a, b) => {
      switch (mode) {
        case 'due':
          return compareNullable(a.dueDate, b.dueDate);
        case 'priority':
          return (PRIORITY_ORDER[a.priority] ?? 9) - (PRIORITY_ORDER[b.priority] ?? 9);
        case 'title':
          return a.title.localeCompare(b.title);
        case 'key':
          return a.key.localeCompare(b.key, undefined, { numeric: true });
        default:
          return 0;
      }
    });
    return sorted;
  }

  toQuery(): TaskQueryDto {
    const query: TaskQueryDto = {};
    if (this.search().trim()) query.query = this.search().trim();
    if (this.assigneeId()) query.assigneeId = this.assigneeId()!;
    if (this.label()) query.label = this.label()!;
    if (this.priority()) query.priority = this.priority()!;
    if (this.epicId()) query.epicId = this.epicId()!;
    if (this.statusId()) query.statusId = this.statusId()!;
    if (this.projectId()) query.projectId = this.projectId()!;
    if (this.sprint()) query.sprint = this.sprint()!;
    if (this.unassigned()) query.unassigned = true;
    if (this.automated()) query.automated = true;
    query.groupBy = this.groupBy();
    query.sort = this.sort();
    query.columns = this.visibleColumns();
    return query;
  }

  applyQuery(query: TaskQueryDto | null | undefined, viewId: string | null = null): void {
    this.reset();
    this.activeViewId.set(viewId);
    this.columns.set(columnsFromQuery(query?.columns));
    if (!query) return;
    this.search.set(query.query ?? '');
    this.assigneeId.set(query.assigneeId ?? null);
    this.label.set(query.label ?? null);
    this.priority.set(query.priority ?? null);
    this.epicId.set(query.epicId ?? null);
    this.statusId.set(query.statusId ?? null);
    this.projectId.set(query.projectId ?? null);
    this.sprint.set(query.sprint ?? null);
    this.unassigned.set(query.unassigned === true);
    this.automated.set(query.automated === true);
    if (query.groupBy) this.groupBy.set(query.groupBy as GroupBy);
    if (query.sort) this.sort.set(query.sort as SortBy);
  }
}

function compareNullable(a: string | null, b: string | null): number {
  if (a === b) return 0;
  if (a === null) return 1;
  if (b === null) return -1;
  return a < b ? -1 : 1;
}

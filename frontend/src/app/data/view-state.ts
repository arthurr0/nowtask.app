import { Injectable, computed, effect, inject, signal } from '@angular/core';
import type {
  FilterConditionDto,
  FilterGroupDto,
  ListColumn,
  SavedViewDto,
  TaskDto,
  TaskQueryDto,
  TaskViewCode,
} from '../core/api-types';
import {
  condition,
  countConditions,
  emptyGroup,
  firstValues,
  isGroup,
  matchesGroup,
  normalizeQuery,
  pruneGroup,
  serializeGroup,
  type FilterContext,
} from '../core/task-filter';
import { SettingsStore } from './feature.stores';
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
  status: 'status',
  labels: 'labels',
  assignee: 'assignee',
  priority: 'priority',
  due: 'dueDate',
  estimate: 'estimate',
};

export const LIST_COLUMNS: readonly { code: ListColumn; label: string }[] = [
  { code: 'status', label: 'common.status' },
  { code: 'labels', label: 'list.labels' },
  { code: 'assignee', label: 'common.assignee' },
  { code: 'priority', label: 'list.priority' },
  { code: 'due', label: 'list.due' },
  { code: 'estimate', label: 'list.pts' },
];

const GROUPS: readonly GroupBy[] = ['status', 'assignee', 'priority', 'epic'];
const SORTS: readonly SortBy[] = ['manual', 'due', 'priority', 'title', 'key'];

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
  private readonly settings = inject(SettingsStore);

  readonly search = signal('');
  readonly projectId = signal<string | null>(null);
  readonly filter = signal<FilterGroupDto>(emptyGroup());
  readonly groupBy = signal<GroupBy>('status');
  readonly sort = signal<SortBy>('manual');
  readonly layout = signal<TaskViewCode>('board');
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

  readonly activeView = computed<SavedViewDto | null>(() => {
    const id = this.activeViewId();
    if (!id) return null;
    return this.store.savedViews().find((view) => view.id === id) ?? null;
  });

  readonly activeViewName = computed(() => {
    const view = this.activeView();
    if (!view) return '';
    return view.name || (view.code ? view.code : '');
  });

  readonly dirty = computed(() => {
    const view = this.activeView();
    if (!view) return false;
    return signature(this.toQuery()) !== signature(view.query);
  });

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

  readonly conditionCount = computed(() => countConditions(this.filter()));

  readonly activeCount = computed(() => {
    let count = this.conditionCount();
    if (this.search().trim()) count += 1;
    if (this.projectId()) count += 1;
    return count;
  });

  readonly hasFilters = computed(() => this.activeCount() > 0);

  readonly assigneeId = computed<string | null>(() => {
    const values = firstValues(this.filter(), 'assignee', 'in');
    return values && values.length === 1 ? values[0] : null;
  });

  readonly unassigned = computed(() => firstValues(this.filter(), 'assignee', 'isEmpty') !== null);

  readonly sprint = computed<string | null>(() => {
    const values = firstValues(this.filter(), 'sprint', 'in');
    if (!values || values.length !== 1) return null;
    return values[0] === 'current' ? this.store.currentSprint() || null : values[0];
  });

  conditionFor(field: string): FilterConditionDto | null {
    for (const node of this.filter().conditions) {
      if (!isGroup(node) && node.field === field) return node;
    }
    return null;
  }

  valuesFor(field: string, op: string): string[] {
    return firstValues(this.filter(), field, op) ?? [];
  }

  setCondition(field: string, op: string, values: string[]): void {
    this.filter.update((group) => {
      const conditions = [...group.conditions];
      const index = conditions.findIndex((node) => !isGroup(node) && node.field === field);
      const next = condition(field, op, values);
      if (index >= 0) conditions[index] = next;
      else conditions.push(next);
      return { ...group, conditions };
    });
  }

  removeCondition(field: string): void {
    this.filter.update((group) => ({
      ...group,
      conditions: group.conditions.filter((node) => isGroup(node) || node.field !== field),
    }));
  }

  removeAt(index: number): void {
    this.filter.update((group) => ({
      ...group,
      conditions: group.conditions.filter((_, position) => position !== index),
    }));
  }

  replaceAt(index: number, node: FilterConditionDto | FilterGroupDto): void {
    this.filter.update((group) => ({
      ...group,
      conditions: group.conditions.map((current, position) =>
        position === index ? node : current,
      ),
    }));
  }

  append(node: FilterConditionDto | FilterGroupDto): void {
    this.filter.update((group) => ({ ...group, conditions: [...group.conditions, node] }));
  }

  toggleValue(field: string, value: string, op = 'in'): void {
    const current = this.conditionFor(field);
    if (!current || current.op !== op) {
      this.setCondition(field, op, [value]);
      return;
    }
    const values = current.values.includes(value)
      ? current.values.filter((item) => item !== value)
      : [...current.values, value];
    if (values.length) this.setCondition(field, op, values);
    else this.removeCondition(field);
  }

  setAssignee(userId: string | null): void {
    if (!userId) {
      this.removeCondition('assignee');
      return;
    }
    const current = this.conditionFor('assignee');
    if (current?.op === 'in' && current.values.length === 1 && current.values[0] === userId) {
      this.removeCondition('assignee');
      return;
    }
    this.setCondition('assignee', 'in', [userId]);
  }

  toggleUnassigned(): void {
    if (this.unassigned()) this.removeCondition('assignee');
    else this.setCondition('assignee', 'isEmpty', []);
  }

  setOverdue(): void {
    this.setCondition('dueDate', 'before', ['today']);
    this.setCondition('statusCategory', 'notIn', ['done']);
  }

  readonly overdueOnly = computed(
    () =>
      this.valuesFor('dueDate', 'before')[0] === 'today' &&
      this.valuesFor('statusCategory', 'notIn').includes('done'),
  );

  clearFilters(): void {
    this.search.set('');
    this.filter.set(emptyGroup());
    this.projectId.set(null);
  }

  reset(): void {
    this.clearFilters();
    this.activeViewId.set(null);
  }

  leaveView(): void {
    if (!this.activeViewId()) return;
    const project = this.projectId();
    this.reset();
    this.groupBy.set('status');
    this.sort.set('manual');
    this.resetColumns();
    this.projectId.set(project);
  }

  private context(): FilterContext {
    const today = this.store.today;
    const me = this.store.currentUser()?.id ?? null;
    const currentSprint = this.store.currentSprint();
    return {
      me,
      currentSprint,
      today,
      statusCategory: (statusId) => this.store.status(statusId)?.category ?? null,
      customType: (fieldKey) =>
        this.settings.customFields().find((field) => field.fieldKey === fieldKey)?.type ?? null,
    };
  }

  matches(task: TaskDto): boolean {
    const needle = this.search().trim().toLowerCase();
    if (needle) {
      const haystack = `${task.key} ${task.title} ${task.labels.join(' ')}`.toLowerCase();
      if (!haystack.includes(needle)) return false;
    }
    if (this.projectId() && task.projectId !== this.projectId()) return false;
    return matchesGroup(task, this.filter(), this.context());
  }

  apply(tasks: readonly TaskDto[]): TaskDto[] {
    const ctx = this.context();
    const needle = this.search().trim().toLowerCase();
    const project = this.projectId();
    const group = this.filter();
    return this.sortTasks(
      tasks.filter((task) => {
        if (needle) {
          const haystack = `${task.key} ${task.title} ${task.labels.join(' ')}`.toLowerCase();
          if (!haystack.includes(needle)) return false;
        }
        if (project && task.projectId !== project) return false;
        return matchesGroup(task, group, ctx);
      }),
    );
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
    if (this.projectId()) query.projectId = this.projectId()!;
    query.filter = pruneGroup(this.filter());
    query.layout = this.layout();
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
    this.projectId.set(query.projectId ?? null);
    this.filter.set(normalizeQuery(query));
    if (query.layout) this.layout.set(query.layout);
    this.groupBy.set(
      GROUPS.includes(query.groupBy as GroupBy) ? (query.groupBy as GroupBy) : 'status',
    );
    this.sort.set(SORTS.includes(query.sort as SortBy) ? (query.sort as SortBy) : 'manual');
  }
}

function signature(query: TaskQueryDto): string {
  return JSON.stringify({
    query: query.query ?? '',
    projectId: query.projectId ?? null,
    filter: serializeGroup(normalizeQuery(query)),
    layout: query.layout ?? null,
    groupBy: query.groupBy ?? 'status',
    sort: query.sort ?? 'manual',
    columns: query.columns ?? null,
  });
}

function compareNullable(a: string | null, b: string | null): number {
  if (a === b) return 0;
  if (a === null) return 1;
  if (b === null) return -1;
  return a < b ? -1 : 1;
}

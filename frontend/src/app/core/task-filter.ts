import type {
  CustomFieldDto,
  FilterConditionDto,
  FilterGroupDto,
  FilterNodeDto,
  TaskDto,
  TaskQueryDto,
} from './api-types';
import type { StatusCategory } from './models';
import { CUSTOM_FIELD_PREFIX, customFieldKey } from './task-fields';

export type FilterKind = 'text' | 'ref' | 'labels' | 'date' | 'number' | 'boolean';

export interface FilterFieldMeta {
  key: string;
  kind: FilterKind;
  label: string;
  nullable: boolean;
  gate: string | null;
  icon: string;
  quick: boolean;
}

export const SYSTEM_FIELDS: readonly FilterFieldMeta[] = [
  {
    key: 'assignee',
    kind: 'ref',
    label: 'common.assignee',
    nullable: true,
    gate: 'assignee',
    icon: 'user',
    quick: true,
  },
  {
    key: 'labels',
    kind: 'labels',
    label: 'list.labels',
    nullable: true,
    gate: 'labels',
    icon: 'clip',
    quick: true,
  },
  {
    key: 'priority',
    kind: 'ref',
    label: 'list.priority',
    nullable: false,
    gate: 'priority',
    icon: 'flag',
    quick: true,
  },
  {
    key: 'status',
    kind: 'ref',
    label: 'common.status',
    nullable: false,
    gate: null,
    icon: 'board',
    quick: true,
  },
  {
    key: 'sprint',
    kind: 'ref',
    label: 'filters.sprint',
    nullable: true,
    gate: 'sprint',
    icon: 'timeline',
    quick: true,
  },
  {
    key: 'epic',
    kind: 'ref',
    label: 'task.epic',
    nullable: true,
    gate: 'epic',
    icon: 'layers',
    quick: true,
  },
  {
    key: 'project',
    kind: 'ref',
    label: 'filters.project',
    nullable: false,
    gate: null,
    icon: 'board',
    quick: false,
  },
  {
    key: 'statusCategory',
    kind: 'ref',
    label: 'filters.field.statusCategory',
    nullable: false,
    gate: null,
    icon: 'check',
    quick: false,
  },
  {
    key: 'reviewer',
    kind: 'ref',
    label: 'task.reviewer',
    nullable: true,
    gate: 'reviewer',
    icon: 'eye',
    quick: false,
  },
  {
    key: 'dueDate',
    kind: 'date',
    label: 'task.dueDate',
    nullable: true,
    gate: 'dueDate',
    icon: 'clock',
    quick: false,
  },
  {
    key: 'startDate',
    kind: 'date',
    label: 'filters.field.startDate',
    nullable: true,
    gate: null,
    icon: 'calendar',
    quick: false,
  },
  {
    key: 'endDate',
    kind: 'date',
    label: 'filters.field.endDate',
    nullable: true,
    gate: null,
    icon: 'calendar',
    quick: false,
  },
  {
    key: 'createdAt',
    kind: 'date',
    label: 'filters.field.createdAt',
    nullable: false,
    gate: null,
    icon: 'clock',
    quick: false,
  },
  {
    key: 'updatedAt',
    kind: 'date',
    label: 'filters.field.updatedAt',
    nullable: false,
    gate: null,
    icon: 'clock',
    quick: false,
  },
  {
    key: 'completedAt',
    kind: 'date',
    label: 'filters.field.completedAt',
    nullable: true,
    gate: null,
    icon: 'check',
    quick: false,
  },
  {
    key: 'estimate',
    kind: 'number',
    label: 'task.estimate',
    nullable: true,
    gate: 'estimate',
    icon: 'chart',
    quick: false,
  },
  {
    key: 'progress',
    kind: 'number',
    label: 'filters.field.progress',
    nullable: false,
    gate: null,
    icon: 'chart',
    quick: false,
  },
  {
    key: 'title',
    kind: 'text',
    label: 'task.title',
    nullable: false,
    gate: null,
    icon: 'pencil',
    quick: false,
  },
  {
    key: 'key',
    kind: 'text',
    label: 'filters.field.key',
    nullable: false,
    gate: null,
    icon: 'key',
    quick: false,
  },
  {
    key: 'description',
    kind: 'text',
    label: 'task.description',
    nullable: true,
    gate: 'description',
    icon: 'log',
    quick: false,
  },
  {
    key: 'automated',
    kind: 'boolean',
    label: 'filters.automated',
    nullable: false,
    gate: null,
    icon: 'bolt',
    quick: false,
  },
];

export const OPS: Record<FilterKind, readonly string[]> = {
  text: ['contains', 'notContains', 'is', 'isNot', 'isEmpty', 'isNotEmpty'],
  ref: ['in', 'notIn', 'isEmpty', 'isNotEmpty'],
  labels: ['in', 'notIn', 'all', 'isEmpty', 'isNotEmpty'],
  date: ['on', 'before', 'after', 'onOrBefore', 'onOrAfter', 'between', 'isEmpty', 'isNotEmpty'],
  number: ['eq', 'ne', 'gt', 'gte', 'lt', 'lte', 'between', 'isEmpty', 'isNotEmpty'],
  boolean: ['is'],
};

export const EMPTY_OPS = ['isEmpty', 'isNotEmpty'];

export const RELATIVE_DATES: readonly { value: string; label: string }[] = [
  { value: 'today', label: 'filters.date.today' },
  { value: '-7d', label: 'filters.date.weekAgo' },
  { value: '-30d', label: 'filters.date.monthAgo' },
  { value: '+7d', label: 'filters.date.inWeek' },
  { value: '+14d', label: 'filters.date.inTwoWeeks' },
  { value: '+30d', label: 'filters.date.inMonth' },
];

export const PRIORITIES = ['critical', 'high', 'medium', 'low'] as const;
export const STATUS_CATEGORIES: readonly StatusCategory[] = ['notStarted', 'inFlight', 'done'];

export function isGroup(node: FilterNodeDto): node is FilterGroupDto {
  return 'conditions' in node && Array.isArray(node.conditions);
}

export function emptyGroup(join: 'and' | 'or' = 'and'): FilterGroupDto {
  return { join, conditions: [] };
}

export function condition(field: string, op: string, values: string[] = []): FilterConditionDto {
  return { field, op, values: [...values] };
}

export function opsFor(meta: FilterFieldMeta): readonly string[] {
  const ops = OPS[meta.kind];
  return meta.nullable ? ops : ops.filter((op) => !EMPTY_OPS.includes(op));
}

export function defaultOp(meta: FilterFieldMeta): string {
  return opsFor(meta)[0];
}

export function needsValues(op: string): boolean {
  return !EMPTY_OPS.includes(op);
}

export function isMultiValue(meta: FilterFieldMeta, op: string): boolean {
  if (meta.kind === 'ref' || meta.kind === 'labels')
    return op === 'in' || op === 'notIn' || op === 'all';
  return op === 'between';
}

export function customFieldMeta(field: CustomFieldDto): FilterFieldMeta {
  const kind: FilterKind = (() => {
    switch (field.type) {
      case 'number':
      case 'currency':
      case 'formula':
        return 'number';
      case 'date':
        return 'date';
      case 'toggle':
        return 'boolean';
      case 'select':
      case 'person':
        return 'ref';
      default:
        return 'text';
    }
  })();
  return {
    key: customFieldKey(field.fieldKey),
    kind,
    label: field.name,
    nullable: kind !== 'boolean',
    gate: customFieldKey(field.fieldKey),
    icon: 'sliders',
    quick: false,
  };
}

export function isCustomKey(key: string): boolean {
  return key.startsWith(CUSTOM_FIELD_PREFIX);
}

export function customKeyOf(key: string): string {
  return isCustomKey(key) ? key.slice(CUSTOM_FIELD_PREFIX.length) : key;
}

export function isBlankNode(node: FilterNodeDto): boolean {
  if (isGroup(node)) return node.conditions.every((child) => isBlankNode(child));
  return !node.field;
}

export function isActiveCondition(node: FilterConditionDto): boolean {
  if (!node.field) return false;
  if (!needsValues(node.op)) return true;
  const values = node.values.filter((value) => value.trim() !== '');
  if (node.op === 'between') return values.length >= 2;
  return values.length > 0;
}

export function countConditions(node: FilterNodeDto): number {
  if (isGroup(node)) return node.conditions.reduce((sum, child) => sum + countConditions(child), 0);
  return isActiveCondition(node) ? 1 : 0;
}

export function normalizeQuery(query: TaskQueryDto | null | undefined): FilterGroupDto {
  if (!query) return emptyGroup();
  const legacy: FilterConditionDto[] = [];
  if (query.statusId) legacy.push(condition('status', 'in', [query.statusId]));
  if (query.assigneeId) legacy.push(condition('assignee', 'in', [query.assigneeId]));
  if (query.label) legacy.push(condition('labels', 'in', [query.label]));
  if (query.priority) legacy.push(condition('priority', 'in', [query.priority]));
  if (query.epicId) legacy.push(condition('epic', 'in', [query.epicId]));
  if (query.dueBefore) legacy.push(condition('dueDate', 'before', [query.dueBefore]));
  if (query.unassigned === true) legacy.push(condition('assignee', 'isEmpty'));
  if (query.unassigned === false) legacy.push(condition('assignee', 'isNotEmpty'));
  if (query.automated !== undefined && query.automated !== null) {
    legacy.push(condition('automated', 'is', [query.automated ? 'true' : 'false']));
  }
  if (query.sprint) legacy.push(condition('sprint', 'in', [query.sprint]));

  const base = query.filter ? cloneGroup(query.filter) : emptyGroup();
  if (!legacy.length) return base;
  if (base.join === 'and') return { join: 'and', conditions: [...legacy, ...base.conditions] };
  if (isBlankNode(base)) return { join: 'and', conditions: legacy };
  return { join: 'and', conditions: [...legacy, base] };
}

export function cloneGroup(group: FilterGroupDto): FilterGroupDto {
  return {
    join: group.join === 'or' ? 'or' : 'and',
    conditions: (group.conditions ?? []).map((node) =>
      isGroup(node) ? cloneGroup(node) : condition(node.field, node.op, node.values ?? []),
    ),
  };
}

export function pruneGroup(group: FilterGroupDto): FilterGroupDto {
  return {
    join: group.join,
    conditions: group.conditions
      .map((node) => (isGroup(node) ? pruneGroup(node) : node))
      .filter((node) => (isGroup(node) ? node.conditions.length > 0 : isActiveCondition(node))),
  };
}

export function serializeGroup(group: FilterGroupDto): string {
  return JSON.stringify(pruneGroup(group));
}

const RELATIVE = /^([+-]?)(\d+)([dwm])$/;

export function resolveDate(value: string, today: string): string | null {
  const trimmed = value.trim().toLowerCase();
  if (!trimmed) return null;
  if (trimmed === 'today') return today;
  const match = RELATIVE.exec(trimmed);
  if (match) {
    let amount = Number(match[2]);
    if (match[1] === '-') amount = -amount;
    const date = new Date(today + 'T00:00:00Z');
    if (match[3] === 'w') date.setUTCDate(date.getUTCDate() + amount * 7);
    else if (match[3] === 'm') date.setUTCMonth(date.getUTCMonth() + amount);
    else date.setUTCDate(date.getUTCDate() + amount);
    return date.toISOString().slice(0, 10);
  }
  if (/^\d{4}-\d{2}-\d{2}$/.test(trimmed)) return trimmed;
  return null;
}

export function isRelativeDate(value: string): boolean {
  const trimmed = value.trim().toLowerCase();
  return trimmed === 'today' || RELATIVE.test(trimmed);
}

export interface FilterContext {
  me: string | null;
  currentSprint: string;
  today: string;
  statusCategory: (statusId: string) => StatusCategory | null;
  customType: (fieldKey: string) => string | null;
}

export function matchesGroup(task: TaskDto, group: FilterGroupDto, ctx: FilterContext): boolean {
  const results: boolean[] = [];
  for (const node of group.conditions) {
    const result = isGroup(node)
      ? matchesGroupOrNull(task, node, ctx)
      : matchesCondition(task, node, ctx);
    if (result !== null) results.push(result);
  }
  if (!results.length) return true;
  return group.join === 'or' ? results.some(Boolean) : results.every(Boolean);
}

function matchesGroupOrNull(
  task: TaskDto,
  group: FilterGroupDto,
  ctx: FilterContext,
): boolean | null {
  if (countConditions(group) === 0) return null;
  return matchesGroup(task, group, ctx);
}

function matchesCondition(
  task: TaskDto,
  node: FilterConditionDto,
  ctx: FilterContext,
): boolean | null {
  if (!node.field) return null;
  const values = node.values.map((value) => value.trim()).filter(Boolean);
  const op = node.op;
  if (needsValues(op) && !values.length) return null;

  switch (node.field) {
    case 'title':
      return text(task.title, op, values);
    case 'key':
      return text(task.key, op, values);
    case 'description':
      return null;
    case 'status':
      return ref(task.statusId, op, values);
    case 'statusCategory':
      return ref(ctx.statusCategory(task.statusId), op, values);
    case 'project':
      return ref(task.projectId, op, values);
    case 'epic':
      return ref(task.epicId, op, values);
    case 'sprint':
      return ref(
        task.sprintCode,
        op,
        values.map((value) => (value === 'current' ? ctx.currentSprint : value)),
      );
    case 'assignee':
      return ref(task.assigneeId, op, resolveMe(values, ctx));
    case 'reviewer':
      return ref(task.reviewerId, op, resolveMe(values, ctx));
    case 'priority':
      return ref(task.priority, op, values);
    case 'labels':
      return labels(task.labels, op, values);
    case 'dueDate':
      return date(task.dueDate, op, values, ctx.today);
    case 'startDate':
      return date(task.startDate, op, values, ctx.today);
    case 'endDate':
      return date(task.endDate, op, values, ctx.today);
    case 'createdAt':
      return date(day(task.createdAt), op, values, ctx.today);
    case 'updatedAt':
      return date(day(task.updatedAt), op, values, ctx.today);
    case 'completedAt':
      return date(day(task.completedAt), op, values, ctx.today);
    case 'estimate':
      return number(task.estimate, op, values);
    case 'progress':
      return number(task.progress, op, values);
    case 'automated':
      return bool(task.automated, op, values);
    default:
      break;
  }

  if (isCustomKey(node.field)) {
    const fieldKey = customKeyOf(node.field);
    const raw = task.custom?.[fieldKey];
    const present = raw !== undefined && raw !== null && raw !== '';
    switch (ctx.customType(fieldKey)) {
      case 'number':
      case 'currency':
      case 'formula':
        return number(present ? Number(raw) : null, op, values);
      case 'date':
        return date(present ? String(raw).slice(0, 10) : null, op, values, ctx.today);
      case 'toggle':
        return bool(raw === true || raw === 'true', op, values);
      case 'select':
      case 'person':
        return ref(present ? String(raw) : null, op, resolveMe(values, ctx));
      default:
        return text(present ? String(raw) : null, op, values);
    }
  }
  return null;
}

function resolveMe(values: string[], ctx: FilterContext): string[] {
  return values.map((value) => (value === 'me' ? (ctx.me ?? '') : value));
}

function day(value: string | null): string | null {
  return value ? value.slice(0, 10) : null;
}

function text(current: string | null, op: string, values: string[]): boolean {
  const haystack = (current ?? '').toLowerCase();
  const needle = (values[0] ?? '').toLowerCase();
  switch (op) {
    case 'contains':
      return haystack.includes(needle);
    case 'notContains':
      return !haystack.includes(needle);
    case 'is':
      return haystack === needle;
    case 'isNot':
      return haystack !== needle;
    case 'isEmpty':
      return haystack === '';
    case 'isNotEmpty':
      return haystack !== '';
    default:
      return true;
  }
}

function ref(current: string | null, op: string, values: string[]): boolean {
  switch (op) {
    case 'in':
    case 'is':
      return current !== null && values.includes(current);
    case 'notIn':
    case 'isNot':
      return current === null || !values.includes(current);
    case 'isEmpty':
      return current === null;
    case 'isNotEmpty':
      return current !== null;
    default:
      return true;
  }
}

function labels(current: readonly string[], op: string, values: string[]): boolean {
  switch (op) {
    case 'in':
    case 'is':
      return values.some((value) => current.includes(value));
    case 'notIn':
    case 'isNot':
      return !values.some((value) => current.includes(value));
    case 'all':
      return values.every((value) => current.includes(value));
    case 'isEmpty':
      return current.length === 0;
    case 'isNotEmpty':
      return current.length > 0;
    default:
      return true;
  }
}

function date(current: string | null, op: string, values: string[], today: string): boolean {
  if (op === 'isEmpty') return current === null;
  if (op === 'isNotEmpty') return current !== null;
  if (current === null) return false;
  const first = resolveDate(values[0] ?? '', today);
  if (!first) return true;
  switch (op) {
    case 'on':
    case 'is':
      return current === first;
    case 'before':
      return current < first;
    case 'after':
      return current > first;
    case 'onOrBefore':
      return current <= first;
    case 'onOrAfter':
      return current >= first;
    case 'between': {
      const second = resolveDate(values[1] ?? '', today);
      if (!second) return true;
      return current >= first && current <= second;
    }
    default:
      return true;
  }
}

function number(current: number | null, op: string, values: string[]): boolean {
  if (op === 'isEmpty') return current === null || Number.isNaN(current);
  if (op === 'isNotEmpty') return current !== null && !Number.isNaN(current);
  const first = Number(values[0]);
  if (Number.isNaN(first)) return true;
  if (current === null || Number.isNaN(current)) return op === 'ne';
  switch (op) {
    case 'eq':
    case 'is':
      return current === first;
    case 'ne':
    case 'isNot':
      return current !== first;
    case 'gt':
      return current > first;
    case 'gte':
      return current >= first;
    case 'lt':
      return current < first;
    case 'lte':
      return current <= first;
    case 'between': {
      const second = Number(values[1]);
      if (Number.isNaN(second)) return true;
      return current >= first && current <= second;
    }
    default:
      return true;
  }
}

function bool(current: boolean, op: string, values: string[]): boolean {
  const wanted = values[0] !== 'false';
  return op === 'isNot' ? current !== wanted : current === wanted;
}

export function firstValues(group: FilterGroupDto, field: string, op?: string): string[] | null {
  for (const node of group.conditions) {
    if (isGroup(node)) continue;
    if (node.field === field && (!op || node.op === op)) return node.values;
  }
  return null;
}

export type Lang = 'pl' | 'en' | 'de';
export type ThemeChoice = 'light' | 'dark' | 'system';
export type Accent = 'graphite' | 'blue' | 'clay' | 'moss' | 'plum';
export type Density = 'compact' | 'cozy' | 'roomy';
export type RadiusStep = '0' | '4' | '8' | '14';

export type RoleId = 'admin' | 'manager' | 'member' | 'guest';
export type Priority = 'low' | 'medium' | 'high' | 'critical';
export type StatusCategory = 'notStarted' | 'inFlight' | 'done';

export interface User {
  id: string;
  name: string;
  short: string;
  initials: string;
  email: string;
  role: RoleId;
  capacity: number;
  pending?: boolean;
}

export interface Team {
  id: string;
  name: string;
  memberIds: string[];
}

export interface Project {
  id: string;
  name: string;
  key: string;
}

export interface StatusDef {
  id: string;
  labelKey: string;
  category: StatusCategory;
  wipLimit: number | null;
  order: number;
  swatch: string;
}

export interface Transition {
  from: string;
  to: string;
  requirementKey: string | null;
}

export interface Subtask {
  id: string;
  title: string;
  done: boolean;
  assigneeId: string | null;
}

export interface Relation {
  type: 'blocks' | 'relates';
  key: string;
}

export interface Task {
  id: string;
  key: string;
  title: string;
  description: string;
  projectId: string;
  epicId: string | null;
  statusId: string;
  priority: Priority;
  assigneeId: string | null;
  reviewerId: string | null;
  labels: string[];
  dueDate: string | null;
  startDate: string | null;
  endDate: string | null;
  estimate: number | null;
  progress: number;
  subtasks: Subtask[];
  commentCount: number;
  attachmentCount: number;
  watcherCount: number;
  automated: boolean;
  relations: Relation[];
  custom: Record<string, string | number | boolean>;
}

export interface Epic {
  id: string;
  name: string;
  projectId: string;
}

export interface Comment {
  id: string;
  taskId: string;
  authorId: string;
  at: string;
  body: string;
}

export interface ActivityEntry {
  id: string;
  taskId: string;
  at: string;
  actorId: string | null;
  ruleName: string | null;
  messageKey: string;
  params: Record<string, string>;
}

export interface HistoryEntry {
  id: string;
  taskId: string;
  at: string;
  fieldKey: string;
  from: string | null;
  to: string;
  actorId: string | null;
  ruleName: string | null;
}

export type TriggerKind = 'statusChanged' | 'assigned' | 'labelAdded' | 'schedule' | 'idleFor';

export interface Trigger {
  kind: TriggerKind;
  value: string;
}

export type ConditionOp = 'isOneOf' | 'contains' | 'greaterThan' | 'equals' | 'isBefore';

export interface Condition {
  id: string;
  kind: 'condition';
  fieldKey: string;
  op: ConditionOp;
  values: string[];
}

export interface ConditionGroup {
  id: string;
  kind: 'group';
  join: 'and' | 'or';
  children: Array<Condition | ConditionGroup>;
}

export type ActionKind =
  | 'assignReviewer'
  | 'assignOwner'
  | 'addWatchers'
  | 'setDueDate'
  | 'notifyChannel'
  | 'setStatus'
  | 'addLabel'
  | 'escalate'
  | 'archive';

export interface RuleAction {
  id: string;
  kind: ActionKind;
  value: string;
  icon: string;
}

export interface Rule {
  id: string;
  name: string;
  summary: string;
  scope: string;
  enabled: boolean;
  draft: boolean;
  runs30d: number;
  trigger: Trigger;
  conditions: ConditionGroup;
  actions: RuleAction[];
  editedAt: string;
  editedById: string;
}

export interface RuleRun {
  id: string;
  ruleId: string;
  at: string;
  taskKey: string;
  outcome: 'ok' | 'skipped' | 'error';
  detailKey: string;
  detailParams: Record<string, string>;
}

export type FieldType =
  | 'text'
  | 'number'
  | 'currency'
  | 'date'
  | 'select'
  | 'toggle'
  | 'person'
  | 'url'
  | 'relation'
  | 'formula';

export interface CustomField {
  id: string;
  name: string;
  key: string;
  type: FieldType;
  scopeKey: string;
  scopeValue?: string;
  restrictedToRole: RoleId | null;
}

export type PermissionValue = 'yes' | 'no' | 'conditional';

export interface Permission {
  key: string;
  admin: PermissionValue;
  manager: PermissionValue;
  member: PermissionValue;
  guest: PermissionValue;
}

export interface LanguageInfo {
  code: string;
  name: string;
  coverage: number;
  isDefault: boolean;
  inProgress: boolean;
}

export interface ApiKey {
  id: string;
  prefix: string;
  label: string;
  lastUsedKey: string;
  lastUsedValue: string;
}

export interface SavedView {
  id: string;
  labelKey: string;
  count: number;
}

export interface BurndownPoint {
  day: string;
  remaining: number | null;
  ideal: number;
}

export interface ThroughputWeek {
  label: string;
  value: number;
}

export interface WorkloadRow {
  userId: string;
  points: number;
  capacity: number;
}

export interface RuleUsage {
  ruleId: string;
  runs: number;
  failing: boolean;
}

export interface Milestone {
  id: string;
  labelKey: string;
  date: string;
}

import type { Priority, RoleId, StatusCategory } from './models';

export interface UserDto {
  id: string;
  name: string;
  shortName: string;
  initials: string;
  email: string;
  role: RoleId;
  capacity: number;
  pending: boolean;
  invitedOn: string | null;
}

export interface TeamDto {
  id: string;
  name: string;
  headcount: number;
}

export interface ProjectDto {
  id: string;
  name: string;
  code: string;
  position: number;
  archived: boolean;
}

export interface StatusDto {
  id: string;
  code: string;
  label: string;
  category: StatusCategory;
  wipLimit: number | null;
  position: number;
  swatch: string;
}

export interface TransitionDto {
  id: string;
  fromStatus: string;
  toStatus: string;
  fromCode: string;
  toCode: string;
  requirement: string | null;
}

export interface EpicDto {
  id: string;
  name: string;
  projectId: string | null;
}

export interface SavedViewDto {
  id: string;
  code: string | null;
  name: string | null;
  query: TaskQueryDto;
  shared: boolean;
  ownerId: string | null;
  count: number;
}

export type ListColumn = 'labels' | 'assignee' | 'priority' | 'due' | 'estimate';

export interface TaskQueryDto {
  query?: string;
  statusId?: string;
  assigneeId?: string;
  label?: string;
  priority?: string;
  epicId?: string;
  projectId?: string;
  dueBefore?: string;
  unassigned?: boolean;
  automated?: boolean;
  sprint?: string;
  groupBy?: string;
  sort?: string;
  columns?: ListColumn[];
}

export interface TaskGroupDto {
  key: string;
  label: string;
  count: number;
}

export interface TaskPageDto {
  items: TaskDto[];
  total: number;
  page: number;
  size: number;
  groups?: TaskGroupDto[];
}

export interface WorkspaceSettingsDto {
  dateFormat: string;
  timeFormat: string;
  firstDayOfWeek: number;
  timeZone: string;
  currency: string;
  allowUserOverride: boolean;
  blockDisallowedDrag: boolean;
  currentSprint: string;
}

export type NavItemCode =
  'overview' | 'my-tasks' | 'board' | 'list' | 'timeline' | 'automations' | 'agents' | 'reports';

export interface NavItemDto {
  code: NavItemCode;
  hidden: boolean;
}

export interface BootstrapDto {
  currentUser: UserDto;
  users: UserDto[];
  teams: TeamDto[];
  projects: ProjectDto[];
  statuses: StatusDto[];
  transitions: TransitionDto[];
  epics: EpicDto[];
  savedViews: SavedViewDto[];
  settings: WorkspaceSettingsDto;
  navigation: NavItemDto[];
  sprints: string[];
  activeRuleCount: number;
}

export interface TaskDto {
  id: string;
  key: string;
  title: string;
  statusId: string;
  statusCode: string;
  priority: Priority;
  assigneeId: string | null;
  labels: string[];
  dueDate: string | null;
  startDate: string | null;
  endDate: string | null;
  estimate: number | null;
  progress: number;
  subtasksDone: number;
  subtasksTotal: number;
  commentCount: number;
  automated: boolean;
  epicId: string | null;
  projectId: string | null;
  sprintCode: string | null;
}

export interface SubtaskDto {
  id: string;
  title: string;
  done: boolean;
  assigneeId: string | null;
}

export interface RelationDto {
  kind: 'blocks' | 'relates';
  taskKey: string;
}

export interface TaskDetailDto {
  summary: TaskDto;
  description: string;
  reviewerId: string | null;
  attachmentCount: number;
  watcherCount: number;
  watching: boolean;
  custom: Record<string, string | number | boolean>;
  subtasks: SubtaskDto[];
  relations: RelationDto[];
}

export interface CommentDto {
  id: string;
  authorId: string;
  body: string;
  createdAt: string;
}

export interface HistoryDto {
  id: string;
  field: string;
  oldValue: string | null;
  newValue: string | null;
  actorId: string | null;
  ruleName: string | null;
  createdAt: string;
}

export interface RuleDto {
  id: string;
  name: string;
  summary: string;
  scopeLabel: string;
  enabled: boolean;
  draft: boolean;
  runs30d: number;
  trigger: { kind: string; value: string };
  conditions: RuleConditionGroupDto;
  actions: RuleActionDto[];
  editedById: string | null;
  editedAt: string;
}

export interface RuleConditionDto {
  id: string;
  kind: 'condition';
  fieldKey: string;
  op: string;
  values: string[];
}

export interface RuleConditionGroupDto {
  id: string;
  kind: 'group';
  join: 'and' | 'or';
  children: Array<RuleConditionDto | RuleConditionGroupDto>;
}

export interface RuleActionDto {
  id: string;
  kind: string;
  value: string;
  icon: string;
}

export interface RunDto {
  id: string;
  ruleId: string;
  ruleName: string;
  taskKey: string;
  outcome: 'ok' | 'skipped' | 'error';
  detailKey: string;
  detailParams: Record<string, string>;
  createdAt: string;
}

export interface RuleUsageDto {
  ruleId: string;
  ruleName: string;
  runs: number;
  failing: boolean;
}

export interface CustomFieldDto {
  id: string;
  name: string;
  fieldKey: string;
  type: string;
  scopeLabel: string;
  restrictedToRole: RoleId | null;
}

export interface MilestoneDto {
  id: string;
  name: string;
  dueDate: string;
}

export interface PermissionDto {
  key: string;
  admin: 'yes' | 'no' | 'conditional';
  manager: 'yes' | 'no' | 'conditional';
  member: 'yes' | 'no' | 'conditional';
  guest: 'yes' | 'no' | 'conditional';
}

export interface ApiKeyDto {
  id: string;
  prefix: string;
  label: string;
  lastUsedAt: string | null;
  scopes: string[];
  ownerId: string | null;
  createdAt: string;
  expiresAt: string | null;
  revokedAt: string | null;
  state: string;
}

export interface IssuedApiKeyDto {
  key: string;
  view: ApiKeyDto;
}

export interface AuditDto {
  id: string;
  at: string;
  actorId: string | null;
  apiKeyId: string | null;
  actorLabel: string;
  action: string;
  subject: string;
  detail: Record<string, unknown>;
}

export interface AuditPageDto {
  items: AuditDto[];
  total: number;
}

export interface AgentDto {
  id: string;
  prefix: string;
  label: string;
  scopes: string[];
  lastUsedAt: string | null;
  expiresAt: string | null;
  state: string;
}

export interface AgentActivityDto {
  id: string;
  at: string;
  agent: string;
  action: string;
  method: string | null;
  path: string | null;
  status: number | null;
}

export interface AgentsOverviewDto {
  agents: AgentDto[];
  activity: AgentActivityDto[];
}

export interface ScheduledTaskDto {
  key: string;
  title: string;
  epicId: string | null;
  assigneeId: string | null;
  startDate: string;
  endDate: string;
  progress: number;
  blocks: string[];
}

export interface BurndownPointDto {
  day: string;
  remaining: number | null;
  ideal: number;
}

export interface ThroughputWeekDto {
  label: string;
  completed: number;
}

export interface WorkloadRowDto {
  userId: string;
  points: number;
  capacity: number;
}

export interface OverviewDto {
  tasksInProgress: number;
  completedThisWeek: number;
  averageCycleTimeDays: number | null;
  ruleExecutions: number;
  ruleErrors: number;
  burndown: BurndownPointDto[];
  throughput: ThroughputWeekDto[];
  workload: WorkloadRowDto[];
  ruleUsage: RuleUsageDto[];
  periodDays: number;
}

export interface MetaDto {
  name: string;
  version: string;
  defaultLanguage: string;
  languages: string[];
}

export interface NotificationDto {
  id: string;
  at: string;
  kind: string;
  titleKey: string;
  params: Record<string, string>;
  taskKey?: string;
  read: boolean;
}

export interface NotificationPageDto {
  items: NotificationDto[];
  unread: number;
}

export type IntegrationKind = 'webhook' | 'email';

export interface IntegrationDeliveryDto {
  id: string;
  at: string;
  event: string;
  taskKey?: string;
  ok: boolean;
  detail: string;
}

export interface IntegrationDto {
  id: string;
  kind: IntegrationKind;
  name: string;
  enabled: boolean;
  config: Record<string, unknown>;
  createdAt: string;
  lastStatus?: string;
  lastAt?: string;
  lastDetail: string;
  deliveries: IntegrationDeliveryDto[];
}

export interface IntegrationTestDto {
  ok: boolean;
  detail: string;
}

import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { I18nService } from '../core/i18n/i18n.service';
import { TASK_VIEWS } from '../core/task-views';
import { OnboardingService } from '../core/onboarding.service';
import { firstValueFrom } from 'rxjs';
import type {
  BootstrapDto,
  CustomFieldDto,
  EpicDto,
  NavItemDto,
  ProjectDto,
  RuleDto,
  SavedViewDto,
  StatusDto,
  TaskDto,
  TaskPageDto,
  TaskFieldSettingDto,
  TaskOpenMode,
  TaskQueryDto,
  TaskViewCode,
  TaskViewSettingDto,
  TeamDto,
  TransitionDto,
  UserDto,
  WorkspaceSettingsDto,
} from '../core/api-types';

export interface NewTaskInput {
  title: string;
  description?: string;
  statusId: string;
  projectId?: string | null;
  sprintCode?: string | null;
  priority?: string;
  assigneeId?: string | null;
  reviewerId?: string | null;
  dueDate?: string | null;
  estimate?: number | null;
  epicId?: string | null;
  labels?: string[];
}

export interface SearchResultDto {
  tasks: TaskDto[];
  rules: RuleDto[];
  people: UserDto[];
  views: SavedViewDto[];
}

@Injectable({ providedIn: 'root' })
export class WorkspaceStore {
  private readonly http = inject(HttpClient);
  private readonly i18n = inject(I18nService);
  private readonly onboarding = inject(OnboardingService);

  private readonly bootstrapSignal = signal<BootstrapDto | null>(null);
  private readonly tasksSignal = signal<TaskDto[]>([]);
  private readonly loadingSignal = signal(false);
  private readonly errorSignal = signal<string | null>(null);

  readonly loading = this.loadingSignal.asReadonly();
  readonly error = this.errorSignal.asReadonly();
  readonly ready = computed(() => this.bootstrapSignal() !== null);

  readonly tasks = this.tasksSignal.asReadonly();

  readonly users = computed<UserDto[]>(() => this.bootstrapSignal()?.users ?? []);
  readonly teams = computed<TeamDto[]>(() => this.bootstrapSignal()?.teams ?? []);
  readonly projects = computed<ProjectDto[]>(() => this.bootstrapSignal()?.projects ?? []);
  readonly activeProjects = computed(() => this.projects().filter((project) => !project.archived));
  readonly epics = computed<EpicDto[]>(() => this.bootstrapSignal()?.epics ?? []);

  epicsOfProject(projectId: string | null): EpicDto[] {
    if (!projectId) return this.epics();
    return this.epics().filter((epic) => epic.projectId === projectId);
  }
  readonly statuses = computed<StatusDto[]>(() => this.bootstrapSignal()?.statuses ?? []);
  readonly transitions = computed<TransitionDto[]>(() => this.bootstrapSignal()?.transitions ?? []);
  readonly savedViews = computed<SavedViewDto[]>(() =>
    [...(this.bootstrapSignal()?.savedViews ?? [])].sort((a, b) => a.position - b.position),
  );

  savedView(id: string | null): SavedViewDto | null {
    if (!id) return null;
    return this.savedViews().find((view) => view.id === id) ?? null;
  }
  readonly sprints = computed<string[]>(() => this.bootstrapSignal()?.sprints ?? []);
  readonly settings = computed<WorkspaceSettingsDto | null>(
    () => this.bootstrapSignal()?.settings ?? null,
  );
  readonly navigation = computed<NavItemDto[]>(() => this.bootstrapSignal()?.navigation ?? []);
  readonly currentSprint = computed(() => this.settings()?.currentSprint ?? '');
  readonly activeRuleCount = computed(() => this.bootstrapSignal()?.activeRuleCount ?? 0);

  readonly taskFieldSettings = computed<TaskFieldSettingDto[]>(
    () => this.bootstrapSignal()?.taskFieldSettings ?? [],
  );

  taskFieldEnabled(fieldKey: string, projectId: string | null | undefined): boolean {
    if (projectId) return this.resolveTaskField(fieldKey, projectId);

    const projects = this.activeProjects();
    if (!projects.length) return this.resolveTaskField(fieldKey, null);
    return projects.some((project) => this.resolveTaskField(fieldKey, project.id));
  }

  private resolveTaskField(fieldKey: string, projectId: string | null): boolean {
    const settings = this.taskFieldSettings();
    if (projectId) {
      const own = settings.find(
        (setting) => setting.projectId === projectId && setting.fieldKey === fieldKey,
      );
      if (own) return own.enabled;
    }
    const shared = settings.find(
      (setting) => setting.projectId === null && setting.fieldKey === fieldKey,
    );
    return shared ? shared.enabled : true;
  }

  async updateTaskFieldSettings(
    projectId: string | null,
    fields: Record<string, boolean | null>,
  ): Promise<void> {
    await firstValueFrom(
      this.http.patch<TaskFieldSettingDto[]>('/api/workspace/task-fields', { projectId, fields }),
    );
    await this.load(true);
  }

  readonly taskViewSettings = computed<TaskViewSettingDto[]>(
    () => this.bootstrapSignal()?.taskViewSettings ?? [],
  );

  taskViewEnabled(viewCode: TaskViewCode, projectId: string | null | undefined): boolean {
    if (projectId) return this.resolveTaskView(viewCode, projectId);

    const projects = this.activeProjects();
    if (!projects.length) return this.resolveTaskView(viewCode, null);
    return projects.some((project) => this.resolveTaskView(viewCode, project.id));
  }

  private resolveTaskView(viewCode: TaskViewCode, projectId: string | null): boolean {
    const settings = this.taskViewSettings();
    if (projectId) {
      const own = settings.find(
        (setting) => setting.projectId === projectId && setting.viewCode === viewCode,
      );
      if (own) return own.enabled;
    }
    const shared = settings.find(
      (setting) => setting.projectId === null && setting.viewCode === viewCode,
    );
    return shared ? shared.enabled : true;
  }

  enabledViews(projectId: string | null | undefined): TaskViewCode[] {
    const enabled = TASK_VIEWS.filter((view) => this.taskViewEnabled(view.code, projectId)).map(
      (view) => view.code,
    );
    return enabled.length ? enabled : [TASK_VIEWS[0].code];
  }

  async updateTaskViewSettings(
    projectId: string | null,
    views: Record<string, boolean | null>,
  ): Promise<void> {
    await firstValueFrom(
      this.http.patch<TaskViewSettingDto[]>('/api/workspace/task-views', { projectId, views }),
    );
    await this.load(true);
  }

  readonly defaultView = computed<TaskViewCode>(
    () => this.bootstrapSignal()?.defaultView ?? 'board',
  );

  readonly resolvedDefaultView = computed<TaskViewCode>(() => {
    const wanted = this.defaultView();
    const available = this.enabledViews(null);
    return available.includes(wanted) ? wanted : available[0];
  });

  async saveDefaultView(view: TaskViewCode): Promise<void> {
    const previous = this.bootstrapSignal()?.defaultView;
    this.patchDefaultView(view);

    try {
      const saved = await firstValueFrom(
        this.http.put<{ view: TaskViewCode }>('/api/me/default-view', { view }),
      );
      this.patchDefaultView(saved.view);
    } catch (error) {
      if (previous) this.patchDefaultView(previous);
      throw error;
    }
  }

  private patchDefaultView(defaultView: TaskViewCode): void {
    this.bootstrapSignal.update((bootstrap) =>
      bootstrap ? { ...bootstrap, defaultView } : bootstrap,
    );
  }

  readonly taskOpenMode = computed<TaskOpenMode>(
    () => this.bootstrapSignal()?.taskOpenMode ?? 'dialog',
  );

  async saveTaskOpenMode(mode: TaskOpenMode): Promise<void> {
    const previous = this.bootstrapSignal()?.taskOpenMode;
    this.patchTaskOpenMode(mode);

    try {
      const saved = await firstValueFrom(
        this.http.put<{ mode: TaskOpenMode }>('/api/me/task-open-mode', { mode }),
      );
      this.patchTaskOpenMode(saved.mode);
    } catch (error) {
      if (previous) this.patchTaskOpenMode(previous);
      throw error;
    }
  }

  private patchTaskOpenMode(taskOpenMode: TaskOpenMode): void {
    this.bootstrapSignal.update((bootstrap) =>
      bootstrap ? { ...bootstrap, taskOpenMode } : bootstrap,
    );
  }

  readonly permissions = computed<readonly string[]>(
    () => this.bootstrapSignal()?.permissions ?? [],
  );

  can(permission: string): boolean {
    return this.permissions().includes(permission);
  }

  readonly currentUser = computed<UserDto | null>(
    () => this.bootstrapSignal()?.currentUser ?? null,
  );
  readonly activeMembers = computed(() => this.users().filter((user) => !user.pending));

  readonly boardStatuses = computed(() =>
    [...this.statuses()].sort((a, b) => a.position - b.position),
  );

  readonly allLabels = computed(() => {
    const labels = new Set<string>();
    for (const task of this.tasks()) {
      for (const label of task.labels) labels.add(label);
    }
    return [...labels].sort((a, b) => a.localeCompare(b));
  });

  readonly myTaskCount = computed(() => {
    const me = this.currentUser()?.id;
    if (!me) return 0;
    return this.tasks().filter((task) => task.assigneeId === me && task.statusCode !== 'done')
      .length;
  });

  get today(): string {
    return new Date().toISOString().slice(0, 10);
  }

  async load(force = false): Promise<void> {
    if (this.ready() && !force) {
      return;
    }

    this.loadingSignal.set(true);
    this.errorSignal.set(null);

    try {
      const [bootstrap, page] = await Promise.all([
        firstValueFrom(this.http.get<BootstrapDto>('/api/bootstrap')),
        firstValueFrom(this.http.get<TaskPageDto>('/api/tasks')),
      ]);
      this.bootstrapSignal.set(bootstrap);
      this.tasksSignal.set(page.items);
      this.onboarding.adopt(bootstrap.onboarding);
    } catch (error) {
      this.errorSignal.set(describe(error));
    } finally {
      this.loadingSignal.set(false);
    }
  }

  clear(): void {
    this.bootstrapSignal.set(null);
    this.tasksSignal.set([]);
    this.loadingSignal.set(false);
    this.errorSignal.set(null);
  }

  async reloadTasks(): Promise<void> {
    const page = await firstValueFrom(this.http.get<TaskPageDto>('/api/tasks'));
    this.tasksSignal.set(page.items);
    void this.refreshViewCounts().catch(() => undefined);
  }

  async reloadBootstrap(): Promise<void> {
    const bootstrap = await firstValueFrom(this.http.get<BootstrapDto>('/api/bootstrap'));
    this.bootstrapSignal.set(bootstrap);
    this.onboarding.adopt(bootstrap.onboarding);
  }

  user(id: string | null): UserDto | null {
    if (!id) return null;
    return this.users().find((user) => user.id === id) ?? null;
  }

  statusName(status: StatusDto | null | undefined): string {
    if (!status) return '';
    return this.i18n.label('status.' + status.code, status.label);
  }

  statusNameByCode(code: string): string {
    const status = this.statusByCode(code);
    return status ? this.statusName(status) : code;
  }

  status(id: string): StatusDto | null {
    return this.statuses().find((status) => status.id === id) ?? null;
  }

  statusByCode(code: string): StatusDto | null {
    return this.statuses().find((status) => status.code === code) ?? null;
  }

  task(key: string): TaskDto | null {
    return this.tasks().find((task) => task.key === key) ?? null;
  }

  project(id: string | null): ProjectDto | null {
    if (!id) return null;
    return this.projects().find((project) => project.id === id) ?? null;
  }

  async saveNavigation(items: readonly NavItemDto[]): Promise<void> {
    const previous = this.bootstrapSignal()?.navigation;
    this.patchNavigation([...items]);

    try {
      const saved = await firstValueFrom(
        this.http.put<NavItemDto[]>('/api/me/navigation', { items }),
      );
      this.patchNavigation(saved);
    } catch (error) {
      if (previous) this.patchNavigation(previous);
      throw error;
    }
  }

  private patchNavigation(navigation: NavItemDto[]): void {
    this.bootstrapSignal.update((bootstrap) =>
      bootstrap ? { ...bootstrap, navigation } : bootstrap,
    );
  }

  async createProject(name: string, code: string): Promise<ProjectDto> {
    const created = await firstValueFrom(
      this.http.post<ProjectDto>('/api/workspace/projects', { name, code }),
    );
    await this.reloadBootstrap();
    return created;
  }

  async updateProject(id: string, body: Record<string, unknown>): Promise<void> {
    await firstValueFrom(this.http.patch<ProjectDto>(`/api/workspace/projects/${id}`, body));
    await this.reloadBootstrap();
  }

  async deleteProject(id: string): Promise<void> {
    await firstValueFrom(this.http.delete(`/api/workspace/projects/${id}`));
    await this.reloadBootstrap();
  }

  epic(id: string | null): EpicDto | null {
    if (!id) return null;
    return this.epics().find((epic) => epic.id === id) ?? null;
  }

  tasksByStatus(statusId: string): TaskDto[] {
    return this.tasks().filter((task) => task.statusId === statusId);
  }

  allowedTargets(fromStatusId: string): StatusDto[] {
    const transitions = this.transitions();
    if (!transitions.length) return this.boardStatuses();
    const from = this.status(fromStatusId);
    if (!from) return this.boardStatuses();
    const allowed = transitions
      .filter((transition) => transition.fromStatus === fromStatusId)
      .map((transition) => transition.toStatus);
    if (!allowed.length) return this.boardStatuses();
    return this.boardStatuses().filter(
      (status) => status.id === fromStatusId || allowed.includes(status.id),
    );
  }

  async createTask(input: NewTaskInput): Promise<TaskDto> {
    const created = await firstValueFrom(this.http.post<TaskDto>('/api/tasks', input));
    this.tasksSignal.update((tasks) => [created, ...tasks]);
    void this.onboarding.syncAfterActivity();
    return created;
  }

  async patchTask(taskKey: string, patch: Record<string, unknown>): Promise<TaskDto> {
    const updated = await firstValueFrom(this.http.patch<TaskDto>(`/api/tasks/${taskKey}`, patch));
    this.replaceTask(updated);
    void this.onboarding.syncAfterActivity();
    return updated;
  }

  async moveTask(taskKey: string, statusId: string): Promise<void> {
    await this.patchTask(taskKey, { statusId });
  }

  async deleteTask(taskKey: string): Promise<void> {
    await firstValueFrom(this.http.delete(`/api/tasks/${taskKey}`));
    this.tasksSignal.update((tasks) => tasks.filter((task) => task.key !== taskKey));
  }

  async assignTasks(taskKeys: readonly string[], userId: string | null): Promise<void> {
    await firstValueFrom(
      this.http.post('/api/tasks/bulk/assign', { keys: [...taskKeys], assigneeId: userId }),
    );
    await this.reloadTasks();
  }

  async setStatusForTasks(taskKeys: readonly string[], statusId: string): Promise<void> {
    await firstValueFrom(
      this.http.post('/api/tasks/bulk/status', { keys: [...taskKeys], statusId }),
    );
    await this.reloadTasks();
    void this.onboarding.syncAfterActivity();
  }

  async deleteTasks(taskKeys: readonly string[]): Promise<void> {
    await firstValueFrom(this.http.post('/api/tasks/bulk/delete', { keys: [...taskKeys] }));
    await this.reloadTasks();
  }

  async setPriorityForTasks(taskKeys: readonly string[], priority: string): Promise<void> {
    for (const key of taskKeys) {
      await firstValueFrom(this.http.patch<TaskDto>(`/api/tasks/${key}`, { priority }));
    }
    await this.reloadTasks();
  }

  async addLabelToTasks(taskKeys: readonly string[], label: string): Promise<void> {
    for (const key of taskKeys) {
      await firstValueFrom(this.http.post<string[]>(`/api/tasks/${key}/labels`, { label }));
    }
    await this.reloadTasks();
  }

  async toggleSubtask(taskKey: string, subtaskId: string): Promise<TaskDto> {
    const updated = await firstValueFrom(
      this.http.post<TaskDto>(`/api/tasks/${taskKey}/subtasks/${subtaskId}/toggle`, {}),
    );
    this.replaceTask(updated);
    return updated;
  }

  search(query: string): Promise<SearchResultDto> {
    return firstValueFrom(this.http.get<SearchResultDto>('/api/search', { params: { q: query } }));
  }

  async createView(name: string, query: TaskQueryDto, shared = false): Promise<SavedViewDto> {
    const created = await firstValueFrom(
      this.http.post<SavedViewDto>('/api/views', { name, query, shared }),
    );
    await this.reloadBootstrap();
    return created;
  }

  async updateView(
    id: string,
    body: { name?: string; query?: TaskQueryDto; shared?: boolean },
  ): Promise<SavedViewDto> {
    const updated = await firstValueFrom(this.http.patch<SavedViewDto>(`/api/views/${id}`, body));
    await this.reloadBootstrap();
    return updated;
  }

  async reorderViews(ids: readonly string[]): Promise<void> {
    await firstValueFrom(this.http.post<SavedViewDto[]>('/api/views/reorder', { ids: [...ids] }));
    await this.reloadBootstrap();
  }

  async deleteView(id: string): Promise<void> {
    await firstValueFrom(this.http.delete(`/api/views/${id}`));
    await this.reloadBootstrap();
  }

  async refreshViewCounts(): Promise<void> {
    const savedViews = await firstValueFrom(this.http.get<SavedViewDto[]>('/api/views'));
    this.bootstrapSignal.update((bootstrap) =>
      bootstrap ? { ...bootstrap, savedViews } : bootstrap,
    );
  }

  async createStatus(body: {
    code: string;
    label: string;
    category: string;
    wipLimit: number | null;
  }): Promise<void> {
    await firstValueFrom(this.http.post<StatusDto>('/api/workspace/statuses', body));
    await this.reloadBootstrap();
  }

  async updateStatus(id: string, body: Record<string, unknown>): Promise<void> {
    await firstValueFrom(this.http.patch<StatusDto>(`/api/workspace/statuses/${id}`, body));
    await this.reloadBootstrap();
  }

  async deleteStatus(id: string): Promise<void> {
    await firstValueFrom(this.http.delete(`/api/workspace/statuses/${id}`));
    await this.reloadBootstrap();
  }

  async reorderStatuses(ids: readonly string[]): Promise<void> {
    await firstValueFrom(this.http.post('/api/workspace/statuses/reorder', { ids: [...ids] }));
    await this.reloadBootstrap();
  }

  async createTransition(
    fromStatus: string,
    toStatus: string,
    requirement: string | null,
  ): Promise<void> {
    await firstValueFrom(
      this.http.post<TransitionDto>('/api/workspace/transitions', {
        fromStatus,
        toStatus,
        requirement,
      }),
    );
    await this.reloadBootstrap();
  }

  async deleteTransition(id: string): Promise<void> {
    await firstValueFrom(this.http.delete(`/api/workspace/transitions/${id}`));
    await this.reloadBootstrap();
  }

  async createEpic(name: string, projectId: string | null): Promise<void> {
    await firstValueFrom(this.http.post<EpicDto>('/api/workspace/epics', { name, projectId }));
    await this.reloadBootstrap();
  }

  async renameEpic(id: string, name: string): Promise<void> {
    await firstValueFrom(this.http.patch<EpicDto>(`/api/workspace/epics/${id}`, { name }));
    await this.reloadBootstrap();
  }

  async deleteEpic(id: string): Promise<void> {
    await firstValueFrom(this.http.delete(`/api/workspace/epics/${id}`));
    await this.reloadBootstrap();
    await this.reloadTasks();
  }

  loadWorkspaceSettings(): Promise<WorkspaceSettingsDto> {
    return firstValueFrom(this.http.get<WorkspaceSettingsDto>('/api/workspace/settings'));
  }

  patchWorkspaceSettings(body: Record<string, unknown>): Promise<WorkspaceSettingsDto> {
    return firstValueFrom(this.http.patch<WorkspaceSettingsDto>('/api/workspace/settings', body));
  }

  createCustomField(body: {
    name: string;
    fieldKey: string;
    type: string;
    scopeLabel: string;
    requiredPermission: string | null;
  }): Promise<CustomFieldDto> {
    return firstValueFrom(this.http.post<CustomFieldDto>('/api/workspace/custom-fields', body));
  }

  updateCustomField(id: string, body: Record<string, unknown>): Promise<CustomFieldDto> {
    return firstValueFrom(
      this.http.patch<CustomFieldDto>(`/api/workspace/custom-fields/${id}`, body),
    );
  }

  deleteCustomField(id: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`/api/workspace/custom-fields/${id}`));
  }

  private replaceTask(updated: TaskDto): void {
    this.tasksSignal.update((tasks) =>
      tasks.map((task) => (task.key === updated.key ? updated : task)),
    );
  }
}

export function describe(error: unknown): string {
  if (typeof error === 'object' && error !== null && 'status' in error) {
    const status = (error as { status: number }).status;
    if (status === 0) {
      return 'No connection to the server.';
    }
    const message = (error as { error?: { message?: string } }).error?.message;
    return message ?? `The server answered with error ${status}.`;
  }
  return 'Unknown connection error.';
}

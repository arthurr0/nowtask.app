import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { OnboardingService } from '../core/onboarding.service';
import { firstValueFrom } from 'rxjs';
import type {
  AgentsOverviewDto,
  ApiKeyDto,
  IntegrationDto,
  IntegrationTestDto,
  NotificationDto,
  NotificationPageDto,
  AuditPageDto,
  CommentDto,
  RelationDto,
  CustomFieldDto,
  HistoryDto,
  IssuedApiKeyDto,
  MilestoneDto,
  OverviewDto,
  PermissionCatalogDto,
  PermissionDto,
  RoleDto,
  RuleDto,
  RunDto,
  SubtaskDto,
  ScheduledTaskDto,
  TaskDetailDto,
  TaskDto,
  TeamDto,
  UserDto,
} from '../core/api-types';
import { describe } from './workspace.store';

export interface RunReportDto {
  matched: number;
  applied: number;
  skipped: number;
  taskKeys: string[];
}

abstract class LoadableStore {
  protected readonly http = inject(HttpClient);

  protected readonly loadingSignal = signal(false);
  protected readonly errorSignal = signal<string | null>(null);

  readonly loading = this.loadingSignal.asReadonly();
  readonly error = this.errorSignal.asReadonly();

  abstract clear(): void;

  protected resetState(): void {
    this.loadingSignal.set(false);
    this.errorSignal.set(null);
  }

  protected async guard(work: () => Promise<void>): Promise<void> {
    this.loadingSignal.set(true);
    this.errorSignal.set(null);
    try {
      await work();
    } catch (error) {
      this.errorSignal.set(describe(error));
    } finally {
      this.loadingSignal.set(false);
    }
  }
}

@Injectable({ providedIn: 'root' })
export class RulesStore extends LoadableStore {
  private readonly onboarding = inject(OnboardingService);
  private readonly rulesSignal = signal<RuleDto[]>([]);
  private readonly runsSignal = signal<Record<string, RunDto[]>>({});
  private readonly loadedSignal = signal(false);

  readonly rules = this.rulesSignal.asReadonly();
  readonly loaded = this.loadedSignal.asReadonly();

  readonly activeCount = computed(
    () => this.rules().filter((rule) => rule.enabled && !rule.draft).length,
  );

  async load(): Promise<void> {
    await this.guard(async () => {
      this.rulesSignal.set(await firstValueFrom(this.http.get<RuleDto[]>('/api/rules')));
      this.loadedSignal.set(true);
    });
  }

  runsFor(ruleId: string): RunDto[] {
    return this.runsSignal()[ruleId] ?? [];
  }

  async loadRuns(ruleId: string): Promise<void> {
    if (this.runsSignal()[ruleId]) {
      return;
    }
    const runs = await firstValueFrom(this.http.get<RunDto[]>(`/api/rules/${ruleId}/runs`));
    this.runsSignal.update((current) => ({ ...current, [ruleId]: runs }));
  }

  async toggle(ruleId: string): Promise<void> {
    const updated = await firstValueFrom(
      this.http.post<RuleDto>(`/api/rules/${ruleId}/toggle`, {}),
    );
    this.rulesSignal.update((rules) =>
      rules.map((rule) => (rule.id === updated.id ? updated : rule)),
    );
    void this.onboarding.syncAfterActivity();
  }

  rule(id: string): RuleDto | null {
    return this.rules().find((rule) => rule.id === id) ?? null;
  }

  async create(body: Record<string, unknown>): Promise<RuleDto> {
    const created = await firstValueFrom(this.http.post<RuleDto>('/api/rules', body));
    await this.load();
    return created;
  }

  async update(id: string, body: Record<string, unknown>): Promise<RuleDto> {
    const updated = await firstValueFrom(this.http.patch<RuleDto>(`/api/rules/${id}`, body));
    this.rulesSignal.update((rules) =>
      rules.map((rule) => (rule.id === updated.id ? updated : rule)),
    );
    return updated;
  }

  async remove(id: string): Promise<void> {
    await firstValueFrom(this.http.delete(`/api/rules/${id}`));
    this.rulesSignal.update((rules) => rules.filter((rule) => rule.id !== id));
    this.runsSignal.update((current) => {
      const next = { ...current };
      delete next[id];
      return next;
    });
  }

  async run(id: string, taskKey: string | null): Promise<RunReportDto> {
    const report = await firstValueFrom(
      this.http.post<RunReportDto>(`/api/rules/${id}/run`, { taskKey }),
    );
    await this.refreshRuns(id);
    return report;
  }

  async refreshRuns(ruleId: string): Promise<void> {
    const runs = await firstValueFrom(this.http.get<RunDto[]>(`/api/rules/${ruleId}/runs`));
    this.runsSignal.update((current) => ({ ...current, [ruleId]: runs }));
  }

  clear(): void {
    this.rulesSignal.set([]);
    this.runsSignal.set({});
    this.loadedSignal.set(false);
    this.resetState();
  }
}

@Injectable({ providedIn: 'root' })
export class MetricsStore extends LoadableStore {
  private readonly overviewSignal = signal<OverviewDto | null>(null);

  readonly overview = this.overviewSignal.asReadonly();

  private readonly daysSignal = signal(7);

  readonly days = this.daysSignal.asReadonly();

  async load(days = this.daysSignal()): Promise<void> {
    await this.guard(async () => {
      this.overviewSignal.set(
        await firstValueFrom(
          this.http.get<OverviewDto>('/api/metrics/overview', {
            params: { days: String(days) },
          }),
        ),
      );
      this.daysSignal.set(days);
    });
  }

  clear(): void {
    this.overviewSignal.set(null);
    this.resetState();
  }
}

@Injectable({ providedIn: 'root' })
export class TimelineStore extends LoadableStore {
  private readonly scheduledSignal = signal<ScheduledTaskDto[]>([]);
  private readonly milestonesSignal = signal<MilestoneDto[]>([]);

  readonly scheduled = this.scheduledSignal.asReadonly();
  readonly milestones = this.milestonesSignal.asReadonly();

  async load(): Promise<void> {
    await this.guard(async () => {
      const [scheduled, milestones] = await Promise.all([
        firstValueFrom(this.http.get<ScheduledTaskDto[]>('/api/timeline')),
        firstValueFrom(this.http.get<MilestoneDto[]>('/api/workspace/milestones')),
      ]);
      this.scheduledSignal.set(scheduled);
      this.milestonesSignal.set(milestones);
    });
  }

  clear(): void {
    this.scheduledSignal.set([]);
    this.milestonesSignal.set([]);
    this.resetState();
  }
}

@Injectable({ providedIn: 'root' })
export class AgentsStore extends LoadableStore {
  private readonly overviewSignal = signal<AgentsOverviewDto | null>(null);

  readonly overview = this.overviewSignal.asReadonly();

  readonly agents = computed(() => this.overviewSignal()?.agents ?? []);
  readonly activity = computed(() => this.overviewSignal()?.activity ?? []);
  readonly activeAgents = computed(() => this.agents().filter((agent) => agent.state === 'active'));

  async load(): Promise<void> {
    await this.guard(async () => {
      this.overviewSignal.set(
        await firstValueFrom(this.http.get<AgentsOverviewDto>('/api/agents')),
      );
    });
  }

  async createKey(
    label: string,
    scopes: string[],
    expiresInDays: number | null,
  ): Promise<IssuedApiKeyDto> {
    const issued = await firstValueFrom(
      this.http.post<IssuedApiKeyDto>('/api/organization/api-keys', { label, scopes, expiresInDays }),
    );
    await this.load();
    return issued;
  }

  async revokeKey(id: string): Promise<void> {
    await firstValueFrom(this.http.delete(`/api/organization/api-keys/${id}`));
    await this.load();
  }

  clear(): void {
    this.overviewSignal.set(null);
    this.resetState();
  }
}

@Injectable({ providedIn: 'root' })
export class OrganizationStore extends LoadableStore {
  private readonly onboarding = inject(OnboardingService);
  private readonly membersSignal = signal<UserDto[]>([]);
  private readonly teamsSignal = signal<TeamDto[]>([]);
  private readonly permissionsSignal = signal<PermissionDto[]>([]);
  private readonly rolesSignal = signal<RoleDto[]>([]);
  private readonly apiKeysSignal = signal<ApiKeyDto[]>([]);
  private readonly auditSignal = signal<AuditPageDto | null>(null);
  private readonly auditPageSignal = signal(0);

  readonly audit = this.auditSignal.asReadonly();
  readonly auditPage = this.auditPageSignal.asReadonly();
  readonly members = this.membersSignal.asReadonly();
  readonly teams = this.teamsSignal.asReadonly();
  readonly permissions = this.permissionsSignal.asReadonly();
  readonly roles = this.rolesSignal.asReadonly();
  readonly apiKeys = this.apiKeysSignal.asReadonly();

  async load(): Promise<void> {
    await this.guard(async () => {
      const [members, teams, permissions] = await Promise.all([
        firstValueFrom(this.http.get<UserDto[]>('/api/organization/members')),
        firstValueFrom(this.http.get<TeamDto[]>('/api/organization/teams')),
        firstValueFrom(this.http.get<PermissionCatalogDto>('/api/organization/permissions')),
      ]);
      this.membersSignal.set(members);
      this.teamsSignal.set(teams);
      this.permissionsSignal.set(permissions.catalog);
      this.rolesSignal.set(permissions.roles);

      try {
        this.apiKeysSignal.set(
          await firstValueFrom(this.http.get<ApiKeyDto[]>('/api/organization/api-keys')),
        );
      } catch {
        this.apiKeysSignal.set([]);
      }
    });
  }

  async invite(name: string, email: string, role: string): Promise<UserDto> {
    const created = await firstValueFrom(
      this.http.post<UserDto>('/api/organization/members', { name, email, role }),
    );
    await this.load();
    void this.onboarding.syncAfterActivity();
    return created;
  }

  async updateMember(id: string, body: Record<string, unknown>): Promise<void> {
    await firstValueFrom(this.http.patch<UserDto>(`/api/organization/members/${id}`, body));
    await this.load();
  }

  async removeMember(id: string): Promise<void> {
    await firstValueFrom(this.http.delete(`/api/organization/members/${id}`));
    await this.load();
  }

  async createTeam(name: string): Promise<void> {
    await firstValueFrom(this.http.post<TeamDto>('/api/organization/teams', { name }));
    await this.load();
  }

  async renameTeam(id: string, name: string): Promise<void> {
    await firstValueFrom(this.http.patch<TeamDto>(`/api/organization/teams/${id}`, { name }));
    await this.load();
  }

  async deleteTeam(id: string): Promise<void> {
    await firstValueFrom(this.http.delete(`/api/organization/teams/${id}`));
    await this.load();
  }

  teamMembers(id: string): Promise<string[]> {
    return firstValueFrom(this.http.get<string[]>(`/api/organization/teams/${id}/members`));
  }

  async addTeamMember(id: string, userId: string): Promise<void> {
    await firstValueFrom(this.http.post<TeamDto>(`/api/organization/teams/${id}/members`, { userId }));
    await this.load();
  }

  async removeTeamMember(id: string, userId: string): Promise<void> {
    await firstValueFrom(this.http.delete<TeamDto>(`/api/organization/teams/${id}/members/${userId}`));
    await this.load();
  }

  async createApiKey(
    label: string,
    scopes: string[],
    expiresInDays: number | null,
  ): Promise<IssuedApiKeyDto> {
    const issued = await firstValueFrom(
      this.http.post<IssuedApiKeyDto>('/api/organization/api-keys', { label, scopes, expiresInDays }),
    );
    await this.load();
    return issued;
  }

  async revokeApiKey(id: string): Promise<void> {
    await firstValueFrom(this.http.delete(`/api/organization/api-keys/${id}`));
    await this.load();
  }

  async loadAudit(page = 0): Promise<void> {
    const result = await firstValueFrom(
      this.http.get<AuditPageDto>('/api/organization/audit', {
        params: { page: String(page), size: '50' },
      }),
    );
    this.auditSignal.set(result);
    this.auditPageSignal.set(page);
  }

  clear(): void {
    this.membersSignal.set([]);
    this.teamsSignal.set([]);
    this.permissionsSignal.set([]);
    this.apiKeysSignal.set([]);
    this.auditSignal.set(null);
    this.auditPageSignal.set(0);
    this.resetState();
  }
}

@Injectable({ providedIn: 'root' })
export class SettingsStore extends LoadableStore {
  private readonly customFieldsSignal = signal<CustomFieldDto[]>([]);

  readonly customFields = this.customFieldsSignal.asReadonly();

  async load(): Promise<void> {
    await this.guard(async () => {
      this.customFieldsSignal.set(
        await firstValueFrom(this.http.get<CustomFieldDto[]>('/api/workspace/custom-fields')),
      );
    });
  }

  clear(): void {
    this.customFieldsSignal.set([]);
    this.resetState();
  }
}

@Injectable({ providedIn: 'root' })
export class TaskDetailStore extends LoadableStore {
  private readonly detailSignal = signal<TaskDetailDto | null>(null);
  private readonly commentsSignal = signal<CommentDto[]>([]);
  private readonly historySignal = signal<HistoryDto[]>([]);
  private readonly rulesSignal = signal<RuleDto[]>([]);

  readonly detail = this.detailSignal.asReadonly();
  readonly comments = this.commentsSignal.asReadonly();
  readonly history = this.historySignal.asReadonly();
  readonly rules = this.rulesSignal.asReadonly();

  readonly subtasksDone = computed(
    () => this.detail()?.subtasks.filter((subtask) => subtask.done).length ?? 0,
  );

  async load(key: string): Promise<void> {
    await this.guard(async () => {
      const [detail, comments, history, rules] = await Promise.all([
        firstValueFrom(this.http.get<TaskDetailDto>(`/api/tasks/${key}`)),
        firstValueFrom(this.http.get<CommentDto[]>(`/api/tasks/${key}/comments`)),
        firstValueFrom(this.http.get<HistoryDto[]>(`/api/tasks/${key}/history`)),
        firstValueFrom(this.http.get<RuleDto[]>(`/api/rules/touching/${key}`)),
      ]);
      this.detailSignal.set(detail);
      this.commentsSignal.set(comments);
      this.historySignal.set(history);
      this.rulesSignal.set(rules);
    });
  }

  async toggleSubtask(key: string, subtaskId: string): Promise<void> {
    await firstValueFrom(this.http.post(`/api/tasks/${key}/subtasks/${subtaskId}/toggle`, {}));
    await this.reloadDetail(key);
  }

  async addComment(key: string, body: string): Promise<void> {
    await firstValueFrom(this.http.post<CommentDto>(`/api/tasks/${key}/comments`, { body }));
    this.commentsSignal.set(
      await firstValueFrom(this.http.get<CommentDto[]>(`/api/tasks/${key}/comments`)),
    );
  }

  async patch(key: string, body: Record<string, unknown>): Promise<TaskDto> {
    const summary = await firstValueFrom(this.http.patch<TaskDto>(`/api/tasks/${key}`, body));
    this.detailSignal.update((detail) => (detail ? { ...detail, summary } : detail));
    if ('description' in body) {
      this.detailSignal.update((detail) =>
        detail ? { ...detail, description: String(body['description'] ?? '') } : detail,
      );
    }
    if ('reviewerId' in body) {
      const reviewerId = (body['reviewerId'] as string | null) ?? null;
      this.detailSignal.update((detail) => (detail ? { ...detail, reviewerId } : detail));
    }
    await this.reloadHistory(key);
    return summary;
  }

  async addSubtask(key: string, title: string, assigneeId: string | null): Promise<void> {
    await firstValueFrom(
      this.http.post<SubtaskDto>(`/api/tasks/${key}/subtasks`, { title, assigneeId }),
    );
    await this.reloadDetail(key);
  }

  async updateSubtask(
    key: string,
    subtaskId: string,
    body: Record<string, unknown>,
  ): Promise<void> {
    await firstValueFrom(
      this.http.patch<SubtaskDto>(`/api/tasks/${key}/subtasks/${subtaskId}`, body),
    );
    await this.reloadDetail(key);
  }

  async deleteSubtask(key: string, subtaskId: string): Promise<void> {
    await firstValueFrom(this.http.delete(`/api/tasks/${key}/subtasks/${subtaskId}`));
    await this.reloadDetail(key);
  }

  async addLabel(key: string, label: string): Promise<void> {
    const labels = await firstValueFrom(
      this.http.post<string[]>(`/api/tasks/${key}/labels`, { label }),
    );
    this.applyLabels(labels);
  }

  async removeLabel(key: string, label: string): Promise<void> {
    const labels = await firstValueFrom(
      this.http.delete<string[]>(`/api/tasks/${key}/labels/${encodeURIComponent(label)}`),
    );
    this.applyLabels(labels);
  }

  async addRelation(key: string, kind: string, taskKey: string): Promise<void> {
    const relations = await firstValueFrom(
      this.http.post<RelationDto[]>(`/api/tasks/${key}/relations`, { kind, taskKey }),
    );
    this.detailSignal.update((detail) => (detail ? { ...detail, relations } : detail));
  }

  async removeRelation(key: string, kind: string, taskKey: string): Promise<void> {
    await firstValueFrom(this.http.delete(`/api/tasks/${key}/relations/${kind}/${taskKey}`));
    await this.reloadDetail(key);
  }

  async setCustomValue(key: string, fieldKey: string, value: unknown): Promise<void> {
    const detail = await firstValueFrom(
      this.http.put<TaskDetailDto>(`/api/tasks/${key}/custom/${fieldKey}`, { value }),
    );
    this.detailSignal.set(detail);
    await this.reloadHistory(key);
  }

  async toggleWatch(key: string): Promise<boolean> {
    const result = await firstValueFrom(
      this.http.post<{ watching: boolean }>(`/api/tasks/${key}/watch`, {}),
    );
    this.detailSignal.update((detail) =>
      detail
        ? {
            ...detail,
            watching: result.watching,
            watcherCount: detail.watcherCount + (result.watching ? 1 : -1),
          }
        : detail,
    );
    return result.watching;
  }

  async remove(key: string): Promise<void> {
    await firstValueFrom(this.http.delete(`/api/tasks/${key}`));
    this.clear();
  }

  private applyLabels(labels: string[]): void {
    this.detailSignal.update((detail) =>
      detail ? { ...detail, summary: { ...detail.summary, labels } } : detail,
    );
  }

  private async reloadDetail(key: string): Promise<void> {
    this.detailSignal.set(await firstValueFrom(this.http.get<TaskDetailDto>(`/api/tasks/${key}`)));
  }

  private async reloadHistory(key: string): Promise<void> {
    this.historySignal.set(
      await firstValueFrom(this.http.get<HistoryDto[]>(`/api/tasks/${key}/history`)),
    );
  }

  clear(): void {
    this.detailSignal.set(null);
    this.commentsSignal.set([]);
    this.historySignal.set([]);
    this.rulesSignal.set([]);
    this.resetState();
  }
}

@Injectable({ providedIn: 'root' })
export class NotificationsStore extends LoadableStore {
  private readonly itemsSignal = signal<NotificationDto[]>([]);
  private readonly unreadSignal = signal(0);

  readonly items = this.itemsSignal.asReadonly();
  readonly unread = this.unreadSignal.asReadonly();

  async load(): Promise<void> {
    await this.guard(async () => {
      this.apply(await firstValueFrom(this.http.get<NotificationPageDto>('/api/notifications')));
    });
  }

  async markRead(id: string): Promise<void> {
    const updated = await firstValueFrom(
      this.http.post<NotificationDto>(`/api/notifications/${id}/read`, {}),
    );
    this.itemsSignal.update((items) =>
      items.map((item) => (item.id === updated.id ? updated : item)),
    );
    this.unreadSignal.set(this.itemsSignal().filter((item) => !item.read).length);
  }

  async markAllRead(): Promise<void> {
    await firstValueFrom(this.http.post('/api/notifications/read-all', {}));
    this.itemsSignal.update((items) => items.map((item) => ({ ...item, read: true })));
    this.unreadSignal.set(0);
  }

  private apply(page: NotificationPageDto): void {
    this.itemsSignal.set(page.items);
    this.unreadSignal.set(page.unread);
  }

  clear(): void {
    this.itemsSignal.set([]);
    this.unreadSignal.set(0);
    this.resetState();
  }
}

@Injectable({ providedIn: 'root' })
export class IntegrationsStore extends LoadableStore {
  private readonly itemsSignal = signal<IntegrationDto[]>([]);

  readonly items = this.itemsSignal.asReadonly();

  async load(): Promise<void> {
    await this.guard(async () => {
      this.itemsSignal.set(
        await firstValueFrom(this.http.get<IntegrationDto[]>('/api/integrations')),
      );
    });
  }

  async create(body: Record<string, unknown>): Promise<IntegrationDto> {
    const created = await firstValueFrom(this.http.post<IntegrationDto>('/api/integrations', body));
    await this.load();
    return created;
  }

  async update(id: string, body: Record<string, unknown>): Promise<IntegrationDto> {
    const updated = await firstValueFrom(
      this.http.patch<IntegrationDto>(`/api/integrations/${id}`, body),
    );
    this.itemsSignal.update((items) =>
      items.map((item) => (item.id === updated.id ? updated : item)),
    );
    return updated;
  }

  async remove(id: string): Promise<void> {
    await firstValueFrom(this.http.delete(`/api/integrations/${id}`));
    this.itemsSignal.update((items) => items.filter((item) => item.id !== id));
  }

  async test(id: string): Promise<IntegrationTestDto> {
    const result = await firstValueFrom(
      this.http.post<IntegrationTestDto>(`/api/integrations/${id}/test`, {}),
    );
    await this.load();
    return result;
  }

  clear(): void {
    this.itemsSignal.set([]);
    this.resetState();
  }
}

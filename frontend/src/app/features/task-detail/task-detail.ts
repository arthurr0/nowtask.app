import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { I18nService } from '../../core/i18n/i18n.service';
import { dateTime, fullDate, isOverdue, money } from '../../core/format';
import type { CustomFieldDto, GitHubTaskLinkDto } from '../../core/api-types';
import { customFieldKey, type TaskFieldKey } from '../../core/task-fields';
import { IntegrationsStore, SettingsStore, TaskDetailStore } from '../../data/feature.stores';
import { WorkspaceStore, type NewTaskInput } from '../../data/workspace.store';
import { Avatar } from '../../ui/avatar';
import { ConfirmService } from '../../ui/confirm.service';
import { Icon } from '../../ui/icon';
import { InlineEdit } from '../../ui/inline-edit';
import { Menu, type MenuItem } from '../../ui/menu';
import { PageState } from '../../ui/page-state';
import { PromptService } from '../../ui/prompt.service';
import { Switch } from '../../ui/switch';
import { ToastService } from '../../ui/toast.service';
import { Topbar } from '../../ui/topbar';
import { ViewControls } from '../../ui/view-controls';

type Tab = 'feed' | 'comments' | 'history';

interface CustomRow {
  field: CustomFieldDto;
  raw: string | number | boolean | null;
  display: string;
  editable: boolean;
}

const PRIORITIES = ['critical', 'high', 'medium', 'low'] as const;
const CLEAR = '__clear__';

const GITHUB_ICONS: Record<string, string> = {
  issue: 'log',
  pull: 'git-branch',
  commit: 'save',
  branch: 'git-branch',
  release: 'flag',
  workflow: 'bolt',
};

@Component({
  selector: 'app-task-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon, Avatar, Menu, InlineEdit, Switch, Topbar, ViewControls, PageState],
  templateUrl: './task-detail.html',
})
export class TaskDetail {
  protected readonly store = inject(WorkspaceStore);
  protected readonly details = inject(TaskDetailStore);
  private readonly settings = inject(SettingsStore);
  private readonly integrations = inject(IntegrationsStore);
  private readonly confirm = inject(ConfirmService);
  private readonly prompt = inject(PromptService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);
  protected readonly t = this.i18n.t;
  protected readonly label = this.i18n.label;

  readonly key = input.required<string>();

  protected readonly tab = signal<Tab>('feed');
  protected readonly commentDraft = signal('');
  protected readonly subtaskDraft = signal('');
  protected readonly labelDraft = signal('');
  protected readonly addingLabel = signal(false);
  protected readonly busy = signal(false);
  protected readonly gitHubLinks = signal<GitHubTaskLinkDto[]>([]);

  constructor() {
    effect(() => {
      const key = this.key();
      void this.details.load(key);
      void this.settings.load();
      void this.loadGitHubLinks(key);
    });
  }

  private async loadGitHubLinks(key: string): Promise<void> {
    try {
      this.gitHubLinks.set(await this.integrations.taskLinks(key));
    } catch {
      this.gitHubLinks.set([]);
    }
  }

  protected gitHubIcon(link: GitHubTaskLinkDto): string {
    return GITHUB_ICONS[link.kind] ?? 'git-branch';
  }

  protected gitHubLabel(link: GitHubTaskLinkDto): string {
    if (link.kind === 'pull' || link.kind === 'issue') return '#' + link.number;
    if (link.kind === 'commit') return link.ref.slice(0, 7);
    return link.ref;
  }

  protected gitHubTone(link: GitHubTaskLinkDto): string {
    const state = (link.checkState || link.state || '').toLowerCase();
    if (['merged', 'success', 'approved', 'published'].includes(state)) return 'ok';
    if (['failure', 'timed_out', 'changes_requested', 'deleted'].includes(state)) return 'bad';
    return 'plain';
  }

  protected readonly detail = this.details.detail;
  protected readonly summary = computed(() => this.detail()?.summary ?? null);

  protected readonly status = computed(() => {
    const statusId = this.summary()?.statusId;
    return statusId ? this.store.status(statusId) : null;
  });

  protected readonly assignee = computed(() => this.store.user(this.summary()?.assigneeId ?? null));
  protected readonly reviewer = computed(() => this.store.user(this.detail()?.reviewerId ?? null));
  protected readonly epic = computed(() => this.store.epic(this.summary()?.epicId ?? null));
  protected readonly projectName = computed(
    () => this.store.project(this.summary()?.projectId ?? null)?.name ?? '',
  );

  protected readonly doneSubtasks = this.details.subtasksDone;
  protected readonly subtaskProgress = computed(() => {
    const subtasks = this.detail()?.subtasks ?? [];
    if (!subtasks.length) return 0;
    return Math.round((this.doneSubtasks() / subtasks.length) * 100);
  });

  protected readonly comments = this.details.comments;
  protected readonly history = this.details.history;
  protected readonly rules = this.details.rules;

  protected readonly canSeeProtected = (permission: string): boolean => this.store.can(permission);

  protected fieldEnabled(field: TaskFieldKey): boolean {
    return this.store.taskFieldEnabled(field, this.summary()?.projectId ?? null);
  }

  protected readonly customRows = computed<CustomRow[]>(() => {
    const custom = this.detail()?.custom ?? {};
    const projectId = this.summary()?.projectId ?? null;
    return this.settings
      .customFields()
      .filter((field) => this.store.taskFieldEnabled(customFieldKey(field.fieldKey), projectId))
      .map((field) => {
        const raw = field.fieldKey in custom ? custom[field.fieldKey] : null;
        return {
          field,
          raw,
          display: raw === null ? '' : this.renderValue(field, raw),
          editable:
            field.requiredPermission === null || this.canSeeProtected(field.requiredPermission),
        };
      });
  });

  protected readonly hasRestrictedField = computed(() =>
    this.settings
      .customFields()
      .some(
        (field) =>
          field.requiredPermission !== null && !this.canSeeProtected(field.requiredPermission),
      ),
  );

  protected readonly overdue = computed(() =>
    isOverdue(this.summary()?.dueDate ?? null, this.store.today),
  );

  protected readonly statusItems = computed<MenuItem[]>(() => {
    const current = this.summary()?.statusId;
    if (!current) return [];
    return this.store.allowedTargets(current).map((status) => ({
      id: status.id,
      label: this.store.statusName(status),
      checked: status.id === current,
    }));
  });

  protected readonly priorityItems = computed<MenuItem[]>(() =>
    PRIORITIES.map((priority) => ({
      id: priority,
      label: this.t('priority.' + priority),
      icon: priority === 'critical' || priority === 'high' ? 'flag' : undefined,
      checked: this.summary()?.priority === priority,
    })),
  );

  protected readonly assigneeItems = computed<MenuItem[]>(() => {
    const items: MenuItem[] = this.store.activeMembers().map((user) => ({
      id: user.id,
      label: user.name,
      icon: 'user',
      checked: this.summary()?.assigneeId === user.id,
    }));
    items.push({ id: CLEAR, label: 'common.unassigned', separatorBefore: true });
    return items;
  });

  protected readonly reviewerItems = computed<MenuItem[]>(() => {
    const items: MenuItem[] = this.store.activeMembers().map((user) => ({
      id: user.id,
      label: user.name,
      icon: 'user',
      checked: this.detail()?.reviewerId === user.id,
    }));
    items.push({ id: CLEAR, label: 'common.none', separatorBefore: true });
    return items;
  });

  protected readonly epicItems = computed<MenuItem[]>(() => {
    const items: MenuItem[] = this.store.epics().map((epic) => ({
      id: epic.id,
      label: epic.name,
      icon: 'layers',
      checked: this.summary()?.epicId === epic.id,
    }));
    items.push({ id: CLEAR, label: 'common.none', separatorBefore: true });
    return items;
  });

  protected readonly taskMenu = computed<MenuItem[]>(() => [
    {
      id: 'watch',
      label: this.detail()?.watching ? 'task.unwatch' : 'task.watch',
      icon: 'eye',
    },
    { id: 'copy', label: 'task.copyLink', icon: 'link' },
    { id: 'duplicate', label: 'board.duplicate', icon: 'copy' },
    { id: 'relates', label: 'task.addRelates', icon: 'link', separatorBefore: true },
    { id: 'blocks', label: 'task.addBlocks', icon: 'alert' },
    { id: 'delete', label: 'task.deleteTask', icon: 'trash', danger: true, separatorBefore: true },
  ]);

  protected dateTime = dateTime;
  protected fullDate = fullDate;

  estimateText(): string {
    const estimate = this.summary()?.estimate;
    return estimate === null || estimate === undefined ? '' : String(estimate);
  }

  rawText(row: CustomRow): string {
    return row.raw === null ? '' : String(row.raw);
  }

  setTab(tab: Tab): void {
    this.tab.set(tab);
  }

  reload(): void {
    void this.details.load(this.key());
  }

  isAgent(ruleName: string | null): boolean {
    return ruleName !== null && ruleName.startsWith('agent:');
  }

  actorLabel(ruleName: string | null, actorId: string | null): string {
    if (this.isAgent(ruleName)) {
      return this.t('activity.agent', { name: ruleName!.slice('agent:'.length).trim() });
    }
    if (ruleName) {
      return this.t('activity.rule', { name: ruleName });
    }
    return this.store.user(actorId)?.shortName ?? '';
  }

  translateValue(value: string | null): string {
    if (!value) return '—';
    return value.includes('.') && !value.includes(' ') ? this.t(value) : value;
  }

  saveTitle(title: string): void {
    const trimmed = title.trim();
    if (!trimmed || trimmed === this.summary()?.title) return;
    void this.patch({ title: trimmed });
  }

  saveDescription(description: string): void {
    if (description === this.detail()?.description) return;
    void this.patch({ description });
  }

  saveEstimate(value: string): void {
    const trimmed = value.trim();
    if (!trimmed) {
      void this.patch({ estimate: null });
      return;
    }
    const parsed = Number.parseInt(trimmed, 10);
    if (!Number.isFinite(parsed)) {
      this.toast.error(this.t('task.estimateInvalid'));
      return;
    }
    void this.patch({ estimate: parsed });
  }

  saveDueDate(value: string): void {
    void this.patch({ dueDate: value || null });
  }

  pickStatus(item: MenuItem): void {
    if (item.id === this.summary()?.statusId) return;
    void this.patch({ statusId: item.id });
  }

  pickPriority(item: MenuItem): void {
    void this.patch({ priority: item.id });
  }

  pickAssignee(item: MenuItem): void {
    void this.patch({ assigneeId: item.id === CLEAR ? null : item.id });
  }

  pickReviewer(item: MenuItem): void {
    void this.patch({ reviewerId: item.id === CLEAR ? null : item.id });
  }

  pickEpic(item: MenuItem): void {
    void this.patch({ epicId: item.id === CLEAR ? null : item.id });
  }

  toggleSubtask(subtaskId: string): void {
    void this.run(async () => {
      await this.details.toggleSubtask(this.key(), subtaskId);
      await this.store.reloadTasks();
    });
  }

  addSubtask(): void {
    const title = this.subtaskDraft().trim();
    if (!title) return;
    this.subtaskDraft.set('');
    void this.run(async () => {
      await this.details.addSubtask(this.key(), title, null);
      await this.store.reloadTasks();
    });
  }

  renameSubtask(subtaskId: string, title: string): void {
    const trimmed = title.trim();
    if (!trimmed) return;
    void this.run(() => this.details.updateSubtask(this.key(), subtaskId, { title: trimmed }));
  }

  async removeSubtask(subtaskId: string, title: string): Promise<void> {
    const confirmed = await this.confirm.askDelete(title);
    if (!confirmed) return;
    await this.run(async () => {
      await this.details.deleteSubtask(this.key(), subtaskId);
      await this.store.reloadTasks();
    });
  }

  async assignSubtask(subtaskId: string, item: MenuItem): Promise<void> {
    await this.run(() =>
      this.details.updateSubtask(this.key(), subtaskId, {
        assigneeId: item.id === CLEAR ? null : item.id,
      }),
    );
  }

  subtaskAssigneeItems(current: string | null): MenuItem[] {
    const items: MenuItem[] = this.store.activeMembers().map((user) => ({
      id: user.id,
      label: user.name,
      icon: 'user',
      checked: current === user.id,
    }));
    items.push({ id: CLEAR, label: 'common.unassigned', separatorBefore: true });
    return items;
  }

  startLabel(): void {
    this.addingLabel.set(true);
  }

  addLabel(): void {
    const label = this.labelDraft().trim();
    this.labelDraft.set('');
    this.addingLabel.set(false);
    if (!label) return;
    void this.run(async () => {
      await this.details.addLabel(this.key(), label);
      await this.store.reloadTasks();
    });
  }

  removeLabel(label: string): void {
    void this.run(async () => {
      await this.details.removeLabel(this.key(), label);
      await this.store.reloadTasks();
    });
  }

  async removeRelation(kind: string, taskKey: string): Promise<void> {
    const confirmed = await this.confirm.askDelete(taskKey);
    if (!confirmed) return;
    await this.run(() => this.details.removeRelation(this.key(), kind, taskKey));
  }

  toggleCustom(row: CustomRow): void {
    void this.saveCustom(row, !(row.raw === true));
  }

  saveCustomDate(row: CustomRow, value: string): void {
    void this.saveCustom(row, value || null);
  }

  saveCustomText(row: CustomRow, value: string): void {
    const trimmed = value.trim();
    if (!trimmed) {
      void this.saveCustom(row, null);
      return;
    }
    if (row.field.type === 'number' || row.field.type === 'currency') {
      const parsed = Number(trimmed.replace(',', '.'));
      if (!Number.isFinite(parsed)) {
        this.toast.error(this.t('task.numberInvalid'));
        return;
      }
      void this.saveCustom(row, parsed);
      return;
    }
    void this.saveCustom(row, trimmed);
  }

  private async saveCustom(row: CustomRow, value: unknown): Promise<void> {
    if (!row.editable) return;
    await this.run(() => this.details.setCustomValue(this.key(), row.field.fieldKey, value));
  }

  async onTaskMenu(item: MenuItem): Promise<void> {
    switch (item.id) {
      case 'watch':
        await this.run(async () => {
          const watching = await this.details.toggleWatch(this.key());
          this.toast.success(this.t(watching ? 'task.watching' : 'task.notWatching'));
        });
        break;
      case 'copy':
        await this.copyLink();
        break;
      case 'duplicate':
        await this.duplicate();
        break;
      case 'relates':
        await this.addRelation('relates');
        break;
      case 'blocks':
        await this.addRelation('blocks');
        break;
      case 'delete':
        await this.removeTask();
        break;
    }
  }

  async copyLink(): Promise<void> {
    try {
      await navigator.clipboard.writeText(`${window.location.origin}/app/tasks/${this.key()}`);
      this.toast.success(this.t('task.linkCopied'));
    } catch {
      this.toast.error(this.t('task.linkCopyFailed'));
    }
  }

  submitComment(): void {
    const body = this.commentDraft().trim();
    if (!body) return;
    this.commentDraft.set('');
    void this.run(() => this.details.addComment(this.key(), body));
  }

  private async addRelation(kind: string): Promise<void> {
    const taskKey = await this.prompt.ask({
      title: kind === 'blocks' ? 'task.addBlocks' : 'task.addRelates',
      message: 'task.addRelationHint',
      label: 'task.relatedKey',
      placeholder: 'task.relatedKeyPlaceholder',
    });
    if (!taskKey) return;
    const normalized = taskKey.trim().toUpperCase();
    if (normalized === this.key()) {
      this.toast.error(this.t('task.relationSelf'));
      return;
    }
    if (!this.store.task(normalized)) {
      this.toast.error(this.t('task.relationUnknown', { key: normalized }));
      return;
    }
    await this.run(() => this.details.addRelation(this.key(), kind, normalized));
  }

  private async duplicate(): Promise<void> {
    const task = this.summary();
    const detail = this.detail();
    if (!task || !detail) return;
    await this.run(async () => {
      const input: NewTaskInput = {
        title: this.t('board.copyOf', { title: task.title }),
        statusId: task.statusId,
        projectId: task.projectId,
      };

      if (this.fieldEnabled('description')) input.description = detail.description;
      if (this.fieldEnabled('priority')) input.priority = task.priority;
      if (this.fieldEnabled('assignee')) input.assigneeId = task.assigneeId;
      if (this.fieldEnabled('reviewer')) input.reviewerId = detail.reviewerId;
      if (this.fieldEnabled('dueDate')) input.dueDate = task.dueDate;
      if (this.fieldEnabled('estimate')) input.estimate = task.estimate;
      if (this.fieldEnabled('epic')) input.epicId = task.epicId;
      if (this.fieldEnabled('labels')) input.labels = [...task.labels];

      const created = await this.store.createTask(input);
      this.toast.success(this.t('composer.created', { key: created.key }), {
        action: {
          label: this.t('composer.openTask'),
          run: () => void this.router.navigate(['/app/tasks', created.key]),
        },
      });
    });
  }

  private async removeTask(): Promise<void> {
    const confirmed = await this.confirm.askDelete(this.key());
    if (!confirmed) return;
    try {
      await this.store.deleteTask(this.key());
      this.toast.success(this.t('board.deleted', { key: this.key() }));
      void this.router.navigate(['/app']);
    } catch (error) {
      this.toast.error(this.errorText(error));
    }
  }

  private async patch(body: Record<string, unknown>): Promise<void> {
    await this.run(async () => {
      await this.details.patch(this.key(), body);
      await this.store.reloadTasks();
    });
  }

  private async run(work: () => Promise<unknown>): Promise<void> {
    if (this.busy()) return;
    this.busy.set(true);
    try {
      await work();
    } catch (error) {
      this.toast.error(this.errorText(error));
      await this.details.load(this.key());
    } finally {
      this.busy.set(false);
    }
  }

  private errorText(error: unknown): string {
    if (typeof error === 'object' && error !== null && 'error' in error) {
      const body = (error as { error?: { message?: string } }).error;
      if (body?.message) return body.message;
    }
    return this.t('common.actionFailed');
  }

  private renderValue(field: CustomFieldDto, value: string | number | boolean): string {
    if (field.type === 'currency' && typeof value === 'number') return money(value);
    if (field.type === 'toggle') return value ? 'yes' : 'no';
    return String(value);
  }
}

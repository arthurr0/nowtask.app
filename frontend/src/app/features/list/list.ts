import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { I18nService } from '../../core/i18n/i18n.service';
import { isOverdue, shortDate } from '../../core/format';
import type { ListColumn, TaskDto } from '../../core/api-types';
import { LIST_COLUMNS, ViewState } from '../../data/view-state';
import { WorkspaceStore } from '../../data/workspace.store';
import { Avatar } from '../../ui/avatar';
import { ConfirmService } from '../../ui/confirm.service';
import { FilterBar } from '../../ui/filter-bar';
import { Icon } from '../../ui/icon';
import { Menu, type MenuItem } from '../../ui/menu';
import { Notifications } from '../../ui/notifications';
import { PromptService } from '../../ui/prompt.service';
import { ToastService } from '../../ui/toast.service';
import { Topbar } from '../../ui/topbar';
import { ViewControls } from '../../ui/view-controls';
import { ViewTabs } from '../../ui/view-tabs';
import { TaskComposer } from '../task-composer/task-composer';

interface ListGroup {
  id: string;
  label: string;
  tasks: TaskDto[];
}

const PRIORITIES = ['critical', 'high', 'medium', 'low'] as const;
const NONE = '__none__';

const HEADER_CLASS: Record<ListColumn, string> = {
  labels: 'w-[172px]',
  assignee: 'w-[116px]',
  priority: 'w-24',
  due: 'w-[78px]',
  estimate: 'w-11 text-right',
};

@Component({
  selector: 'app-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, Avatar, Menu, Topbar, ViewControls, ViewTabs, FilterBar, Notifications],
  templateUrl: './list.html',
})
export class TaskList {
  protected readonly store = inject(WorkspaceStore);
  protected readonly view = inject(ViewState);
  protected readonly composer = inject(TaskComposer);
  private readonly confirm = inject(ConfirmService);
  private readonly prompt = inject(PromptService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(I18nService);
  protected readonly t = this.i18n.t;
  private readonly router = inject(Router);

  readonly onlyMine = input(false);

  protected readonly exportItems = computed<MenuItem[]>(() => [
    { id: 'csv', label: 'list.exportCsv', icon: 'save' },
    { id: 'xlsx', label: 'list.exportXlsx', icon: 'columns' },
  ]);

  protected readonly selected = signal<ReadonlySet<string>>(new Set());
  protected readonly collapsed = signal<ReadonlySet<string>>(new Set());

  protected exportTasks(item: MenuItem): void {
    const params = new URLSearchParams();
    for (const [key, value] of Object.entries(this.view.toQuery())) {
      if (value === null || value === undefined) continue;
      if (Array.isArray(value)) {
        for (const entry of value) params.append(key, String(entry));
      } else {
        params.set(key, String(value));
      }
    }
    if (this.onlyMine()) {
      const me = this.store.currentUser()?.id;
      if (me) params.set('assigneeId', me);
    }
    params.set('format', item.id);
    window.open(`/api/export/tasks?${params.toString()}`, '_blank');
  }

  protected readonly visibleTasks = computed(() => {
    const tasks = this.view.apply(this.store.tasks());
    if (!this.onlyMine()) return tasks;
    const me = this.store.currentUser()?.id;
    if (!me) return [];
    return tasks.filter((task) => task.assigneeId === me);
  });

  protected readonly groups = computed<ListGroup[]>(() => {
    const tasks = this.visibleTasks();
    switch (this.view.groupBy()) {
      case 'assignee': {
        const groups: ListGroup[] = this.store.activeMembers().map((user) => ({
          id: user.id,
          label: user.name,
          tasks: tasks.filter((task) => task.assigneeId === user.id),
        }));
        groups.push({
          id: NONE,
          label: this.t('common.unassigned'),
          tasks: tasks.filter((task) => task.assigneeId === null),
        });
        return groups.filter((group) => group.tasks.length > 0);
      }
      case 'priority':
        return PRIORITIES.map((priority) => ({
          id: priority,
          label: this.t('priority.' + priority),
          tasks: tasks.filter((task) => task.priority === priority),
        })).filter((group) => group.tasks.length > 0);
      case 'epic': {
        const groups: ListGroup[] = this.store
          .epicsOfProject(this.view.projectId())
          .map((epic) => ({
            id: epic.id,
            label: epic.name,
            tasks: tasks.filter((task) => task.epicId === epic.id),
          }));
        groups.push({
          id: NONE,
          label: this.t('common.none'),
          tasks: tasks.filter((task) => task.epicId === null),
        });
        return groups.filter((group) => group.tasks.length > 0);
      }
      default:
        return this.store
          .boardStatuses()
          .map((status) => ({
            id: status.id,
            label: this.store.statusName(status),
            tasks: tasks.filter((task) => task.statusId === status.id),
          }))
          .filter((group) => group.tasks.length > 0);
    }
  });

  protected readonly projectLabel = computed(() => {
    const project = this.store.project(this.view.projectId());
    return project ? project.name : this.t('projects.showAll');
  });

  protected readonly selectedCount = computed(() => this.selected().size);
  protected readonly allSelected = computed(() => {
    const tasks = this.visibleTasks();
    return tasks.length > 0 && tasks.every((task) => this.selected().has(task.key));
  });

  protected readonly assignItems = computed<MenuItem[]>(() => {
    const items: MenuItem[] = this.store
      .activeMembers()
      .map((user) => ({ id: user.id, label: user.name, icon: 'user' }));
    items.push({ id: NONE, label: this.t('common.unassigned'), separatorBefore: true });
    return items;
  });

  protected readonly statusItems = computed<MenuItem[]>(() =>
    this.store.boardStatuses().map((status) => ({
      id: status.id,
      label: this.store.statusName(status),
      icon: 'board',
    })),
  );

  protected readonly priorityItems = computed<MenuItem[]>(() =>
    PRIORITIES.map((priority) => ({
      id: priority,
      label: this.t('priority.' + priority),
      icon: 'flag',
    })),
  );

  protected shortDate = shortDate;

  columnLabel(code: ListColumn): string {
    return LIST_COLUMNS.find((column) => column.code === code)?.label ?? code;
  }

  headerClass(code: ListColumn): string {
    return HEADER_CLASS[code];
  }

  rowMenu(task: TaskDto): MenuItem[] {
    const me = this.store.currentUser();
    return [
      { id: 'open', label: 'board.openTask', icon: 'arrow-right' },
      {
        id: 'assign-me',
        label: 'board.assignToMe',
        icon: 'user',
        disabled: !me || task.assigneeId === me.id,
      },
      { id: 'copy', label: 'task.copyLink', icon: 'link', separatorBefore: true },
      { id: 'duplicate', label: 'board.duplicate', icon: 'copy' },
      { id: 'delete', label: 'common.delete', icon: 'trash', danger: true, separatorBefore: true },
    ];
  }

  isSelected(key: string): boolean {
    return this.selected().has(key);
  }

  isCollapsed(groupId: string): boolean {
    return this.collapsed().has(groupId);
  }

  overdue(task: TaskDto): boolean {
    return isOverdue(task.dueDate, this.store.today);
  }

  toggleGroup(groupId: string): void {
    this.collapsed.update((set) => {
      const next = new Set(set);
      if (next.has(groupId)) next.delete(groupId);
      else next.add(groupId);
      return next;
    });
  }

  toggleRow(key: string, event: Event): void {
    event.stopPropagation();
    this.selected.update((set) => {
      const next = new Set(set);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  }

  toggleAll(): void {
    if (this.allSelected()) {
      this.selected.set(new Set());
      return;
    }
    this.selected.set(new Set(this.visibleTasks().map((task) => task.key)));
  }

  open(task: TaskDto): void {
    void this.router.navigate(['/app/tasks', task.key]);
  }

  clearSelection(): void {
    this.selected.set(new Set());
  }

  async assignTo(item: MenuItem): Promise<void> {
    const keys = [...this.selected()];
    await this.run(() => this.store.assignTasks(keys, item.id === NONE ? null : item.id));
  }

  async setStatus(item: MenuItem): Promise<void> {
    const keys = [...this.selected()];
    await this.run(() => this.store.setStatusForTasks(keys, item.id));
  }

  async setPriority(item: MenuItem): Promise<void> {
    const keys = [...this.selected()];
    await this.run(() => this.store.setPriorityForTasks(keys, item.id));
  }

  async addLabel(): Promise<void> {
    const keys = [...this.selected()];
    const label = await this.prompt.ask({
      title: 'list.addLabelTitle',
      label: 'composer.newLabel',
      placeholder: 'composer.newLabelPlaceholder',
      confirmLabel: 'composer.addLabel',
    });
    if (!label) return;
    await this.run(() => this.store.addLabelToTasks(keys, label));
  }

  async removeSelected(): Promise<void> {
    const keys = [...this.selected()];
    const confirmed = await this.confirm.ask({
      title: 'ui.confirm.deleteTitle',
      message: this.t('list.deleteMany', { count: keys.length }),
      confirmLabel: 'common.delete',
      destructive: true,
    });
    if (!confirmed) return;
    await this.run(() => this.store.deleteTasks(keys));
  }

  async onRowMenu(item: MenuItem, task: TaskDto): Promise<void> {
    switch (item.id) {
      case 'open':
        this.open(task);
        break;
      case 'assign-me': {
        const me = this.store.currentUser();
        if (me) await this.run(() => this.store.patchTask(task.key, { assigneeId: me.id }));
        break;
      }
      case 'copy':
        await this.copyLink(task);
        break;
      case 'duplicate':
        await this.duplicate(task);
        break;
      case 'delete': {
        const confirmed = await this.confirm.askDelete(task.key);
        if (confirmed) await this.run(() => this.store.deleteTask(task.key));
        break;
      }
    }
  }

  private async copyLink(task: TaskDto): Promise<void> {
    try {
      await navigator.clipboard.writeText(`${window.location.origin}/app/tasks/${task.key}`);
      this.toast.success(this.t('task.linkCopied'));
    } catch {
      this.toast.error(this.t('task.linkCopyFailed'));
    }
  }

  private async duplicate(task: TaskDto): Promise<void> {
    await this.run(async () => {
      const created = await this.store.createTask({
        title: this.t('board.copyOf', { title: task.title }),
        statusId: task.statusId,
        priority: task.priority,
        assigneeId: task.assigneeId,
        dueDate: task.dueDate,
        estimate: task.estimate,
        epicId: task.epicId,
        labels: [...task.labels],
      });
      this.toast.success(this.t('composer.created', { key: created.key }));
    });
  }

  private async run(work: () => Promise<unknown>): Promise<void> {
    try {
      await work();
      this.clearSelection();
    } catch (error) {
      this.toast.error(this.errorText(error));
    }
  }

  private errorText(error: unknown): string {
    if (typeof error === 'object' && error !== null && 'error' in error) {
      const body = (error as { error?: { message?: string } }).error;
      if (body?.message) return body.message;
    }
    return this.t('common.actionFailed');
  }
}

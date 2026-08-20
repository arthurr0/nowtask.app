import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { CdkDrag, CdkDropList, CdkDropListGroup, type CdkDragDrop } from '@angular/cdk/drag-drop';
import { I18nService } from '../../core/i18n/i18n.service';
import type { TaskDto } from '../../core/api-types';
import { ViewState } from '../../data/view-state';
import { WorkspaceStore } from '../../data/workspace.store';
import { Avatar } from '../../ui/avatar';
import { ConfirmService } from '../../ui/confirm.service';
import { FilterBar } from '../../ui/filter-bar';
import { Icon } from '../../ui/icon';
import { Menu, type MenuItem } from '../../ui/menu';
import { Notifications } from '../../ui/notifications';
import { ToastService } from '../../ui/toast.service';
import { Topbar } from '../../ui/topbar';
import { ViewControls } from '../../ui/view-controls';
import { ViewTabs } from '../../ui/view-tabs';
import { TaskComposer } from '../task-composer/task-composer';
import { TaskCard } from './task-card';

interface BoardColumn {
  id: string;
  label: string;
  swatch: string;
  wipLimit: number | null;
  tasks: TaskDto[];
}

const PRIORITIES = ['critical', 'high', 'medium', 'low'] as const;
const NONE = '__none__';

@Component({
  selector: 'app-board',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CdkDrag,
    CdkDropList,
    CdkDropListGroup,
    Icon,
    Avatar,
    Menu,
    Topbar,
    ViewControls,
    ViewTabs,
    FilterBar,
    Notifications,
    TaskCard,
  ],
  templateUrl: './board.html',
})
export class Board {
  protected readonly store = inject(WorkspaceStore);
  protected readonly view = inject(ViewState);
  protected readonly composer = inject(TaskComposer);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(I18nService);
  protected readonly t = this.i18n.t;
  private readonly router = inject(Router);

  protected readonly selectedKey = signal<string | null>(null);
  protected readonly mobileColumnId = signal<string | null>(null);
  protected readonly collapsed = signal<ReadonlySet<string>>(new Set());

  protected readonly visibleTasks = computed(() => this.view.apply(this.store.tasks()));

  protected readonly columns = computed<BoardColumn[]>(() => {
    const tasks = this.visibleTasks();
    switch (this.view.groupBy()) {
      case 'assignee': {
        const columns: BoardColumn[] = this.store.activeMembers().map((user) => ({
          id: user.id,
          label: user.name,
          swatch: 'var(--c-line-strong)',
          wipLimit: null,
          tasks: tasks.filter((task) => task.assigneeId === user.id),
        }));
        columns.push({
          id: NONE,
          label: this.t('common.unassigned'),
          swatch: 'var(--c-line-strong)',
          wipLimit: null,
          tasks: tasks.filter((task) => task.assigneeId === null),
        });
        return columns;
      }
      case 'priority':
        return PRIORITIES.map((priority) => ({
          id: priority,
          label: this.t('priority.' + priority),
          swatch: 'var(--c-line-strong)',
          wipLimit: null,
          tasks: tasks.filter((task) => task.priority === priority),
        }));
      case 'epic': {
        const columns: BoardColumn[] = this.store
          .epicsOfProject(this.view.projectId())
          .map((epic) => ({
            id: epic.id,
            label: epic.name,
            swatch: 'var(--c-line-strong)',
            wipLimit: null,
            tasks: tasks.filter((task) => task.epicId === epic.id),
          }));
        columns.push({
          id: NONE,
          label: this.t('common.none'),
          swatch: 'var(--c-line-strong)',
          wipLimit: null,
          tasks: tasks.filter((task) => task.epicId === null),
        });
        return columns;
      }
      default:
        return this.store.boardStatuses().map((status) => ({
          id: status.id,
          label: this.store.statusName(status),
          swatch: status.swatch,
          wipLimit: status.wipLimit,
          tasks: tasks.filter((task) => task.statusId === status.id),
        }));
    }
  });

  protected readonly mobileColumn = computed(() => {
    const columns = this.columns();
    const wanted = this.mobileColumnId();
    return columns.find((column) => column.id === wanted) ?? columns[0] ?? null;
  });

  protected readonly projectLabel = computed(() => {
    const project = this.store.project(this.view.projectId());
    return project ? project.name : this.t('projects.showAll');
  });

  protected readonly boardTitle = computed(() => {
    if (this.view.sprint()) return this.view.sprint()!;
    const sprint = this.store.currentSprint();
    return sprint ? this.t('board.sprintTitle', { sprint }) : this.t('board.allTasks');
  });

  protected readonly avatars = computed(() => this.store.activeMembers().slice(0, 3));
  protected readonly avatarOverflow = computed(() =>
    Math.max(0, this.store.activeMembers().length - 3),
  );

  protected readonly memberItems = computed<MenuItem[]>(() => {
    const items: MenuItem[] = this.store.activeMembers().map((user) => ({
      id: user.id,
      label: user.name,
      icon: 'user',
      checked: this.view.assigneeId() === user.id,
    }));
    items.push({ id: 'clear', label: 'filters.any', separatorBefore: true });
    return items;
  });

  protected readonly columnMenu: readonly MenuItem[] = [
    { id: 'add', label: 'board.addTask', icon: 'plus' },
    { id: 'collapse', label: 'board.collapseColumn', icon: 'chevron-right' },
    { id: 'settings', label: 'board.statusSettings', icon: 'sliders', separatorBefore: true },
  ];

  protected cardMenu(task: TaskDto): MenuItem[] {
    const me = this.store.currentUser();
    return [
      { id: 'open', label: 'board.openTask', icon: 'arrow-right' },
      {
        id: 'assign-me',
        label: 'board.assignToMe',
        icon: 'user',
        disabled: !me || task.assigneeId === me.id,
      },
      { id: 'unassign', label: 'board.unassign', disabled: task.assigneeId === null },
      { id: 'copy', label: 'task.copyLink', icon: 'link', separatorBefore: true },
      { id: 'duplicate', label: 'board.duplicate', icon: 'copy' },
      { id: 'delete', label: 'common.delete', icon: 'trash', danger: true, separatorBefore: true },
    ];
  }

  filterByUser(userId: string): void {
    if (userId === 'clear') {
      this.view.assigneeId.set(null);
      return;
    }
    this.view.unassigned.set(false);
    this.view.assigneeId.update((value) => (value === userId ? null : userId));
  }

  isCollapsed(columnId: string): boolean {
    return this.collapsed().has(columnId);
  }

  select(task: TaskDto): void {
    this.selectedKey.set(task.key);
  }

  open(task: TaskDto): void {
    void this.router.navigate(['/app/tasks', task.key]);
  }

  newTask(column: BoardColumn): void {
    this.composer.open(this.prefillFor(column));
  }

  newTaskMobile(): void {
    const column = this.mobileColumn();
    if (column) this.newTask(column);
    else this.composer.open();
  }

  wipLabel(limit: number, used: number): string {
    return this.t('common.wip', { used, limit });
  }

  onColumnMenu(item: MenuItem, column: BoardColumn): void {
    switch (item.id) {
      case 'add':
        this.newTask(column);
        break;
      case 'collapse':
        this.collapsed.update((set) => {
          const next = new Set(set);
          next.add(column.id);
          return next;
        });
        break;
      case 'settings':
        void this.router.navigate(['/app/settings'], { queryParams: { tab: 'statuses' } });
        break;
    }
  }

  expand(columnId: string): void {
    this.collapsed.update((set) => {
      const next = new Set(set);
      next.delete(columnId);
      return next;
    });
  }

  async onCardMenu(item: MenuItem, task: TaskDto): Promise<void> {
    switch (item.id) {
      case 'open':
        this.open(task);
        break;
      case 'assign-me': {
        const me = this.store.currentUser();
        if (me) await this.patch(task, { assigneeId: me.id }, 'board.assigned');
        break;
      }
      case 'unassign':
        await this.patch(task, { assigneeId: null }, 'board.unassigned');
        break;
      case 'copy':
        await this.copyLink(task);
        break;
      case 'duplicate':
        await this.duplicate(task);
        break;
      case 'delete':
        await this.remove(task);
        break;
    }
  }

  drop(event: CdkDragDrop<string>): void {
    const key = event.item.data as string;
    const target = event.container.data;
    if (event.previousContainer.data === target) return;

    const task = this.store.task(key);
    if (!task) return;

    const patch = this.patchForColumn(target);
    if (!patch) return;

    void this.store.patchTask(key, patch).catch((error) => this.toast.error(this.errorText(error)));
  }

  private patchForColumn(columnId: string): Record<string, unknown> | null {
    const value = columnId === NONE ? null : columnId;
    switch (this.view.groupBy()) {
      case 'assignee':
        return { assigneeId: value };
      case 'priority':
        return value ? { priority: value } : null;
      case 'epic':
        return { epicId: value };
      default:
        return value ? { statusId: value } : null;
    }
  }

  private prefillFor(column: BoardColumn): Record<string, string | null> {
    const value = column.id === NONE ? null : column.id;
    switch (this.view.groupBy()) {
      case 'assignee':
        return { assigneeId: value };
      case 'priority':
        return { priority: value };
      case 'epic':
        return { epicId: value };
      default:
        return { statusId: value };
    }
  }

  private async patch(
    task: TaskDto,
    patch: Record<string, unknown>,
    messageKey: string,
  ): Promise<void> {
    try {
      await this.store.patchTask(task.key, patch);
      this.toast.success(this.t(messageKey, { key: task.key }));
    } catch (error) {
      this.toast.error(this.errorText(error));
    }
  }

  private async copyLink(task: TaskDto): Promise<void> {
    const url = `${window.location.origin}/app/tasks/${task.key}`;
    try {
      await navigator.clipboard.writeText(url);
      this.toast.success(this.t('task.linkCopied'));
    } catch {
      this.toast.error(this.t('task.linkCopyFailed'));
    }
  }

  private async duplicate(task: TaskDto): Promise<void> {
    try {
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
      this.toast.success(this.t('composer.created', { key: created.key }), {
        action: { label: this.t('composer.openTask'), run: () => this.open(created) },
      });
    } catch (error) {
      this.toast.error(this.errorText(error));
    }
  }

  private async remove(task: TaskDto): Promise<void> {
    const confirmed = await this.confirm.askDelete(task.key);
    if (!confirmed) return;
    try {
      await this.store.deleteTask(task.key);
      this.toast.success(this.t('board.deleted', { key: task.key }));
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

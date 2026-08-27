import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { I18nService } from '../core/i18n/i18n.service';
import { TaskOpenService } from '../core/task-open.service';
import { shortDate } from '../core/format';
import { NotificationsStore } from '../data/feature.stores';
import { WorkspaceStore } from '../data/workspace.store';
import { Icon } from './icon';
import { Menu, type MenuItem } from './menu';

@Component({
  selector: 'ui-notifications',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, Menu],
  template: `
    <ui-menu
      [items]="items()"
      triggerClass="relative flex h-8 w-8 items-center justify-center rounded-[7px] border border-line bg-surface-2 text-ink-2"
      triggerHeight="32px"
      ariaLabel="common.notifications"
      align="end"
      [minWidth]="280"
      (openChange)="onOpen($event)"
      (selected)="openItem($event)"
    >
      <ui-icon name="bell" [size]="16" />
      @if (count() > 0) {
        <span
          class="absolute -top-1 -right-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-warn px-1 font-mono text-[10px] text-inv-ink"
          >{{ count() }}</span
        >
      }
    </ui-menu>
  `,
})
export class Notifications {
  private readonly store = inject(WorkspaceStore);
  private readonly notifications = inject(NotificationsStore);
  private readonly taskOpen = inject(TaskOpenService);
  private readonly i18n = inject(I18nService);
  protected readonly t = this.i18n.t;

  private readonly mine = computed(() => {
    const me = this.store.currentUser()?.id;
    if (!me) return [];
    return this.store
      .tasks()
      .filter((task) => task.assigneeId === me && task.statusCode !== 'done');
  });

  private readonly overdue = computed(() => {
    const today = this.store.today;
    return this.mine().filter((task) => task.dueDate !== null && task.dueDate < today);
  });

  private readonly dueToday = computed(() =>
    this.mine().filter((task) => task.dueDate === this.store.today),
  );

  protected readonly count = computed(
    () => this.notifications.unread() + this.overdue().length + this.dueToday().length,
  );

  protected readonly items = computed<MenuItem[]>(() => {
    const items: MenuItem[] = [];

    for (const entry of this.notifications.items()) {
      items.push({
        id: `notification:${entry.id}`,
        label: this.t(entry.titleKey, entry.params),
        icon: entry.read ? 'check' : 'bell',
        shortcut: shortDate(entry.at.slice(0, 10)),
      });
    }

    const events = items.length;

    for (const task of this.overdue()) {
      items.push({
        id: `task:${task.key}`,
        label: `${task.key} · ${task.title}`,
        icon: 'alert',
        shortcut: shortDate(task.dueDate),
        danger: true,
        separatorBefore: items.length === events && events > 0,
      });
    }

    for (const task of this.dueToday()) {
      items.push({
        id: `task:${task.key}`,
        label: `${task.key} · ${task.title}`,
        icon: 'clock',
        shortcut: this.t('notifications.today'),
        separatorBefore: items.length === events && events > 0,
      });
    }

    if (items.length === 0) {
      items.push({ id: 'none', label: 'notifications.allClear', disabled: true });
      return items;
    }

    if (this.notifications.unread() > 0) {
      items.push({
        id: 'read-all',
        label: 'notifications.markAllRead',
        icon: 'check',
        separatorBefore: true,
      });
    }

    return items;
  });

  protected onOpen(open: boolean): void {
    if (open) {
      void this.notifications.load();
    }
  }

  protected openItem(item: MenuItem): void {
    if (item.id === 'none') return;

    if (item.id === 'read-all') {
      void this.notifications.markAllRead();
      return;
    }

    if (item.id.startsWith('task:')) {
      this.taskOpen.open(item.id.slice('task:'.length));
      return;
    }

    const id = item.id.slice('notification:'.length);
    const entry = this.notifications.items().find((row) => row.id === id);
    if (!entry) return;

    if (!entry.read) {
      void this.notifications.markRead(id);
    }
    if (entry.taskKey) {
      this.taskOpen.open(entry.taskKey);
    }
  }
}

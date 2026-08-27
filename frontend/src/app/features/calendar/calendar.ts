import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { CdkDrag, CdkDropList, CdkDropListGroup, type CdkDragDrop } from '@angular/cdk/drag-drop';
import { I18nService } from '../../core/i18n/i18n.service';
import { TaskOpenService } from '../../core/task-open.service';
import type { TaskDto } from '../../core/api-types';
import { RealtimeService } from '../../core/realtime.service';
import { ViewState } from '../../data/view-state';
import { WorkspaceStore } from '../../data/workspace.store';
import { Avatar } from '../../ui/avatar';
import { EmptyState } from '../../ui/empty-state';
import { FilterBar } from '../../ui/filter-bar';
import { Icon } from '../../ui/icon';
import { Notifications } from '../../ui/notifications';
import { ToastService } from '../../ui/toast.service';
import { Topbar } from '../../ui/topbar';
import { ViewControls } from '../../ui/view-controls';
import { ViewTabs } from '../../ui/view-tabs';
import { TaskComposer } from '../task-composer/task-composer';

interface CalendarDay {
  iso: string;
  dayOfMonth: number;
  outside: boolean;
  weekend: boolean;
  today: boolean;
  tasks: TaskDto[];
}

const WEEKS = 6;
const DAYS_IN_WEEK = 7;
const VISIBLE_PER_DAY = 3;

@Component({
  selector: 'app-calendar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CdkDrag,
    CdkDropList,
    CdkDropListGroup,
    Icon,
    Avatar,
    Topbar,
    ViewControls,
    ViewTabs,
    FilterBar,
    Notifications,
    EmptyState,
  ],
  templateUrl: './calendar.html',
})
export class Calendar {
  protected readonly store = inject(WorkspaceStore);
  protected readonly view = inject(ViewState);
  protected readonly composer = inject(TaskComposer);
  protected readonly realtime = inject(RealtimeService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(I18nService);
  protected readonly t = this.i18n.t;
  private readonly taskOpen = inject(TaskOpenService);

  protected readonly visiblePerDay = VISIBLE_PER_DAY;

  private readonly monthCursor = signal(this.store.today.slice(0, 7));
  protected readonly expandedDay = signal<string | null>(null);

  protected readonly dueDateEnabled = computed(() =>
    this.store.taskFieldEnabled('dueDate', this.view.projectId()),
  );

  private readonly firstDayOfWeek = computed(() => this.store.settings()?.firstDayOfWeek ?? 1);

  protected readonly weekdays = computed(() => {
    const formatter = new Intl.DateTimeFormat(this.i18n.lang(), { weekday: 'short' });
    const first = this.firstDayOfWeek() % 7;
    return Array.from({ length: DAYS_IN_WEEK }, (_, index) =>
      formatter.format(new Date(Date.UTC(2024, 0, 7 + ((first + index) % 7)))),
    );
  });

  protected readonly monthLabel = computed(() => {
    const [year, month] = this.monthCursor().split('-').map(Number);
    return new Intl.DateTimeFormat(this.i18n.lang(), { month: 'long', year: 'numeric' }).format(
      new Date(Date.UTC(year, month - 1, 1)),
    );
  });

  protected readonly offMonth = computed(() => this.monthCursor() !== this.store.today.slice(0, 7));

  private readonly visibleTasks = computed(() => this.view.apply(this.store.tasks()));

  private readonly tasksByDay = computed(() => {
    const byDay = new Map<string, TaskDto[]>();
    for (const task of this.visibleTasks()) {
      if (!task.dueDate) continue;
      const day = task.dueDate.slice(0, 10);
      const bucket = byDay.get(day);
      if (bucket) bucket.push(task);
      else byDay.set(day, [task]);
    }
    return byDay;
  });

  protected readonly undatedCount = computed(
    () => this.visibleTasks().filter((task) => task.dueDate === null).length,
  );

  protected readonly monthTaskCount = computed(() => {
    const prefix = this.monthCursor();
    return this.visibleTasks().filter((task) => task.dueDate?.startsWith(prefix)).length;
  });

  protected readonly days = computed<CalendarDay[]>(() => {
    const [year, month] = this.monthCursor().split('-').map(Number);
    const firstOfMonth = new Date(Date.UTC(year, month - 1, 1));
    const offset = (firstOfMonth.getUTCDay() - (this.firstDayOfWeek() % 7) + 7) % 7;
    const start = Date.UTC(year, month - 1, 1 - offset);
    const byDay = this.tasksByDay();
    const today = this.store.today;

    return Array.from({ length: WEEKS * DAYS_IN_WEEK }, (_, index) => {
      const date = new Date(start + index * 86_400_000);
      const iso = date.toISOString().slice(0, 10);
      const weekday = date.getUTCDay();
      return {
        iso,
        dayOfMonth: date.getUTCDate(),
        outside: date.getUTCMonth() !== month - 1,
        weekend: weekday === 0 || weekday === 6,
        today: iso === today,
        tasks: byDay.get(iso) ?? [],
      };
    });
  });

  shiftMonth(step: -1 | 1): void {
    const [year, month] = this.monthCursor().split('-').map(Number);
    const moved = new Date(Date.UTC(year, month - 1 + step, 1));
    this.monthCursor.set(moved.toISOString().slice(0, 7));
    this.expandedDay.set(null);
  }

  jumpToToday(): void {
    this.monthCursor.set(this.store.today.slice(0, 7));
    this.expandedDay.set(null);
  }

  tasksOf(day: CalendarDay): TaskDto[] {
    if (this.expandedDay() === day.iso) return day.tasks;
    return day.tasks.slice(0, VISIBLE_PER_DAY);
  }

  hiddenCount(day: CalendarDay): number {
    if (this.expandedDay() === day.iso) return 0;
    return Math.max(0, day.tasks.length - VISIBLE_PER_DAY);
  }

  toggleDay(iso: string): void {
    this.expandedDay.update((current) => (current === iso ? null : iso));
  }

  statusSwatch(task: TaskDto): string {
    return this.store.status(task.statusId)?.swatch ?? 'var(--c-line-strong)';
  }

  overdue(day: CalendarDay, task: TaskDto): boolean {
    return day.iso < this.store.today && task.statusCode !== 'done';
  }

  open(task: TaskDto): void {
    this.taskOpen.open(task.key);
  }

  newTaskOn(day: CalendarDay): void {
    this.composer.open(this.dueDateEnabled() ? { dueDate: day.iso } : {});
  }

  drop(event: CdkDragDrop<string>): void {
    const key = event.item.data as string;
    const target = event.container.data;
    if (event.previousContainer.data === target) return;

    const task = this.store.task(key);
    if (!task || task.dueDate?.slice(0, 10) === target) return;

    void this.store
      .patchTask(key, { dueDate: target })
      .then(() => this.toast.success(this.t('calendar.moved', { key })))
      .catch((error: unknown) => this.toast.error(this.errorText(error)));
  }

  private errorText(error: unknown): string {
    if (typeof error === 'object' && error !== null && 'error' in error) {
      const body = (error as { error?: { message?: string } }).error;
      if (body?.message) return body.message;
    }
    return this.t('common.actionFailed');
  }
}

import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { I18nService } from '../../core/i18n/i18n.service';
import { TaskOpenService } from '../../core/task-open.service';
import { daysBetween } from '../../core/format';
import { TimelineStore } from '../../data/feature.stores';
import { ViewState } from '../../data/view-state';
import { WorkspaceStore } from '../../data/workspace.store';
import { TaskComposer } from '../task-composer/task-composer';
import { Icon } from '../../ui/icon';
import { PageState } from '../../ui/page-state';
import { Topbar } from '../../ui/topbar';
import { ViewControls } from '../../ui/view-controls';
import { ViewTabs } from '../../ui/view-tabs';

const MILESTONE_ROW = 40;
const ROW_HEIGHT = 44;

export type Zoom = 'day' | 'week' | 'month' | 'quarter';

const ZOOMS: Record<Zoom, { days: number; width: number; back: number }> = {
  day: { days: 7, width: 96, back: 1 },
  week: { days: 28, width: 32, back: 7 },
  month: { days: 60, width: 16, back: 14 },
  quarter: { days: 120, width: 8, back: 30 },
};

interface Bar {
  left: number;
  width: number;
  progress: number;
}

interface Row {
  id: string;
  kind: 'epic' | 'task';
  label: string;
  taskKey: string | null;
  initials: string;
  bar: Bar | null;
  y: number;
}

function gridStart(back: number): string {
  const today = new Date();
  const weekday = (today.getUTCDay() + 6) % 7;
  const monday = new Date(
    Date.UTC(today.getFullYear(), today.getMonth(), today.getDate() - weekday - back),
  );
  return monday.toISOString().slice(0, 10);
}

@Component({
  selector: 'app-timeline',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, Topbar, ViewControls, ViewTabs, PageState],
  templateUrl: './timeline.html',
})
export class Timeline implements OnInit {
  protected readonly store = inject(WorkspaceStore);
  protected readonly view = inject(ViewState);
  protected readonly composer = inject(TaskComposer);
  protected readonly timeline = inject(TimelineStore);
  protected readonly t = inject(I18nService).t;
  private readonly taskOpen = inject(TaskOpenService);

  protected readonly milestoneRow = MILESTONE_ROW;
  protected readonly rowHeight = ROW_HEIGHT;

  protected readonly zoom = signal<Zoom>('week');
  protected readonly zooms: readonly Zoom[] = ['day', 'week', 'month', 'quarter'];
  protected readonly collapsedEpics = signal<ReadonlySet<string>>(new Set());

  protected readonly dayCount = computed(() => ZOOMS[this.zoom()].days);
  protected readonly dayWidth = computed(() => ZOOMS[this.zoom()].width);
  protected readonly gridWidth = computed(() => this.dayCount() * this.dayWidth());
  protected readonly start = computed(() => gridStart(ZOOMS[this.zoom()].back));

  ngOnInit(): void {
    void this.timeline.load();
  }

  reload(): void {
    void this.timeline.load();
  }

  protected readonly projectLabel = computed(() => {
    const project = this.store.project(this.view.projectId());
    return project ? project.name : this.t('projects.showAll');
  });

  protected readonly ready = computed(
    () => this.timeline.scheduled().length > 0 || !this.timeline.loading(),
  );

  protected readonly days = computed(() =>
    Array.from({ length: this.dayCount() }, (_, index) => {
      const date = new Date(Date.parse(`${this.start()}T00:00:00Z`) + index * 86_400_000);
      const weekday = date.getUTCDay();
      return {
        index,
        label: String(date.getUTCDate()),
        iso: date.toISOString().slice(0, 10),
        weekend: weekday === 0 || weekday === 6,
      };
    }),
  );

  protected readonly weekendBands = computed(() =>
    this.days()
      .filter((day) => day.weekend && day.index % 7 === 5)
      .map((day) => day.index * this.dayWidth()),
  );

  protected readonly weekSeparators = computed(() =>
    Array.from(
      { length: Math.floor(this.dayCount() / 7) - 1 },
      (_, index) => (index + 1) * 7 * this.dayWidth(),
    ),
  );

  protected readonly monthSplit = computed(() => {
    const firstMonth = this.days()[0].iso.slice(0, 7);
    const inFirst = this.days().filter((day) => day.iso.startsWith(firstMonth)).length;
    return {
      first: inFirst * this.dayWidth(),
      second: (this.dayCount() - inFirst) * this.dayWidth(),
    };
  });

  protected readonly monthLabels = computed(() => {
    const formatter = new Intl.DateTimeFormat(
      this.t('common.language') === 'Language' ? 'en' : 'pl',
      {
        month: 'long',
        year: 'numeric',
      },
    );
    const days = this.days();
    return {
      first: formatter.format(new Date(days[0].iso)),
      second: formatter.format(new Date(days[this.dayCount() - 1].iso)),
    };
  });

  protected readonly todayX = computed(() => {
    const offset = daysBetween(this.start(), this.store.today);
    return offset >= 0 && offset < this.dayCount()
      ? offset * this.dayWidth() + this.dayWidth() / 2
      : null;
  });

  protected readonly rows = computed<Row[]>(() => {
    const rows: Row[] = [];
    let y = MILESTONE_ROW;

    const projectId = this.view.projectId();

    for (const epic of this.store.epics()) {
      const tasks = this.timeline
        .scheduled()
        .filter((task) => task.epicId === epic.id)
        .filter((task) => !projectId || this.store.task(task.key)?.projectId === projectId);
      if (!tasks.length) continue;

      const collapsed = this.collapsedEpics().has(epic.id);

      const starts = tasks.map((task) => task.startDate);
      const ends = tasks.map((task) => task.endDate);

      rows.push({
        id: epic.id,
        kind: 'epic',
        label: epic.name,
        taskKey: null,
        initials: String(tasks.length),
        bar: this.makeBar(
          starts.reduce((a, b) => (a < b ? a : b)),
          ends.reduce((a, b) => (a > b ? a : b)),
          0,
        ),
        y,
      });
      y += ROW_HEIGHT;

      if (collapsed) continue;

      for (const task of tasks) {
        rows.push({
          id: task.key,
          kind: 'task',
          label: task.title,
          taskKey: task.key,
          initials: this.store.user(task.assigneeId)?.initials ?? '',
          bar: this.makeBar(task.startDate, task.endDate, task.progress),
          y,
        });
        y += ROW_HEIGHT;
      }
    }

    return rows;
  });

  protected readonly totalHeight = computed(() => MILESTONE_ROW + this.rows().length * ROW_HEIGHT);

  protected readonly milestonePins = computed(() =>
    this.timeline.milestones().map((milestone) => ({
      ...milestone,
      x: daysBetween(this.start(), milestone.dueDate) * this.dayWidth() + this.dayWidth() / 2,
    })),
  );

  protected readonly dependencies = computed(() => {
    const byKey = new Map(this.rows().map((row) => [row.id, row]));
    const paths: { id: string; line: string; head: string }[] = [];

    for (const task of this.timeline.scheduled()) {
      const from = byKey.get(task.key);
      if (!from?.bar) continue;

      for (const blocked of task.blocks) {
        const to = byKey.get(blocked);
        if (!to?.bar) continue;

        const x1 = from.bar.left + from.bar.width;
        const y1 = from.y + ROW_HEIGHT / 2;
        const x2 = to.bar.left;
        const y2 = to.y + ROW_HEIGHT / 2;
        if (x2 < x1) continue;

        const mid = Math.max(x1 + 6, x2 - 6);
        paths.push({
          id: `${task.key}-${blocked}`,
          line: `M${x1} ${y1} H${mid} V${y2} H${x2 - 4}`,
          head: `M${x2 - 8} ${y2 - 3} l4 3 -4 3`,
        });
      }
    }

    return paths;
  });

  isEpicCollapsed(epicId: string): boolean {
    return this.collapsedEpics().has(epicId);
  }

  toggleEpic(epicId: string): void {
    this.collapsedEpics.update((set) => {
      const next = new Set(set);
      if (next.has(epicId)) next.delete(epicId);
      else next.add(epicId);
      return next;
    });
  }

  open(taskKey: string | null): void {
    if (taskKey) this.taskOpen.open(taskKey);
  }

  private makeBar(start: string, end: string, progress: number): Bar | null {
    const startOffset = daysBetween(this.start(), start);
    const span = daysBetween(start, end) + 1;
    if (startOffset + span <= 0 || startOffset >= this.dayCount()) return null;
    const clampedStart = Math.max(0, startOffset);
    const clampedSpan = Math.min(
      span - (clampedStart - startOffset),
      this.dayCount() - clampedStart,
    );
    return {
      left: clampedStart * this.dayWidth() + 4,
      width: Math.max(12, clampedSpan * this.dayWidth() - 8),
      progress,
    };
  }
}

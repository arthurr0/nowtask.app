import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  signal,
  viewChild,
  type ElementRef,
} from '@angular/core';
import { CdkDrag, CdkDropList, CdkDropListGroup, type CdkDragDrop } from '@angular/cdk/drag-drop';
import { Icon } from '../../ui/icon';
import { Logo } from '../../ui/logo';

export type DemoStatus = 'todo' | 'doing' | 'review' | 'done';
export type DemoPriority = 'low' | 'medium' | 'high';
type DemoView = 'board' | 'list' | 'timeline';

interface DemoTask {
  key: string;
  title: string;
  status: DemoStatus;
  priority: DemoPriority;
  points: number;
  who: string;
  start: number;
  span: number;
}

interface LogEntry {
  time: string;
  text: string;
  rule: boolean;
}

const DAYS = 10;
const REVIEWER = 'MR';

const STATUSES: ReadonlyArray<{ id: DemoStatus; label: string; dot: string; bar: string }> = [
  { id: 'todo', label: 'To do', dot: 'bg-line-strong', bar: 'bg-surface-3' },
  { id: 'doing', label: 'In progress', dot: 'bg-signal', bar: 'bg-signal' },
  { id: 'review', label: 'Review', dot: 'bg-accent', bar: 'bg-accent' },
  { id: 'done', label: 'Done', dot: 'bg-done', bar: 'bg-done' },
];

const PRIORITIES: ReadonlyArray<{ id: DemoPriority; label: string }> = [
  { id: 'low', label: 'Low' },
  { id: 'medium', label: 'Medium' },
  { id: 'high', label: 'High' },
];

const PEOPLE: ReadonlyArray<{ id: string; name: string }> = [
  { id: 'MK', name: 'Marcin K.' },
  { id: 'AK', name: 'Ada K.' },
  { id: 'PZ', name: 'Piotr Z.' },
  { id: REVIEWER, name: 'Marta R.' },
];

const SEED: ReadonlyArray<DemoTask> = [
  {
    key: 'NT-131',
    title: 'New intake form',
    status: 'review',
    priority: 'medium',
    points: 3,
    who: 'AK',
    start: 0,
    span: 3,
  },
  {
    key: 'NT-138',
    title: 'Fixes in the CSV export',
    status: 'doing',
    priority: 'medium',
    points: 3,
    who: 'PZ',
    start: 2,
    span: 4,
  },
  {
    key: 'NT-142',
    title: 'Customer database migration',
    status: 'todo',
    priority: 'high',
    points: 5,
    who: 'MK',
    start: 4,
    span: 5,
  },
  {
    key: 'NT-147',
    title: 'Calendar integration',
    status: 'doing',
    priority: 'low',
    points: 8,
    who: 'MK',
    start: 1,
    span: 6,
  },
  {
    key: 'NT-151',
    title: 'Permissions audit',
    status: 'todo',
    priority: 'medium',
    points: 2,
    who: 'AK',
    start: 6,
    span: 3,
  },
  {
    key: 'NT-126',
    title: 'Onboarding checklist',
    status: 'done',
    priority: 'low',
    points: 2,
    who: 'PZ',
    start: 0,
    span: 2,
  },
];

const SEED_LOG: ReadonlyArray<LogEntry> = [
  { time: '14:38', text: 'NT-131 marked as urgent', rule: false },
  { time: '11:04', text: 'NT-138 moved to In progress', rule: false },
  { time: '09:12', text: 'Rule assigned NT-142 to Marta R.', rule: true },
];

@Component({
  selector: 'app-landing-demo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CdkDrag, CdkDropList, CdkDropListGroup, Icon, Logo],
  templateUrl: './landing-demo.html',
})
export class LandingDemo {
  protected readonly statuses = STATUSES;
  protected readonly priorities = PRIORITIES;
  protected readonly people = PEOPLE;
  protected readonly days = Array.from({ length: DAYS }, (_, index) => index + 1);
  protected readonly dragDelay = { touch: 180, mouse: 0 };

  protected readonly views: ReadonlyArray<{ id: DemoView; label: string; icon: string }> = [
    { id: 'board', label: 'Board', icon: 'board' },
    { id: 'list', label: 'List', icon: 'list' },
    { id: 'timeline', label: 'Timeline', icon: 'timeline' },
  ];

  protected readonly tasks = signal<DemoTask[]>(SEED.map((task) => ({ ...task })));
  protected readonly log = signal<LogEntry[]>([...SEED_LOG]);
  protected readonly view = signal<DemoView>('board');
  protected readonly selectedKey = signal<string | null>(null);
  protected readonly composerStatus = signal<DemoStatus | null>(null);
  protected readonly draft = signal('');
  protected readonly ruleOn = signal(true);

  private readonly composerField = viewChild<ElementRef<HTMLInputElement>>('composerField');

  private nextNumber = 152;

  constructor() {
    effect(() => this.composerField()?.nativeElement.focus());
  }

  protected readonly columns = computed(() =>
    STATUSES.map((status) => ({
      ...status,
      tasks: this.tasks().filter((task) => task.status === status.id),
    })),
  );

  protected readonly ordered = computed(() =>
    [...this.tasks()].sort((a, b) => a.start - b.start || a.key.localeCompare(b.key)),
  );

  protected readonly selected = computed(
    () => this.tasks().find((task) => task.key === this.selectedKey()) ?? null,
  );

  statusLabel(status: DemoStatus): string {
    return STATUSES.find((item) => item.id === status)?.label ?? status;
  }

  statusBar(status: DemoStatus): string {
    return STATUSES.find((item) => item.id === status)?.bar ?? 'bg-surface-3';
  }

  statusDot(status: DemoStatus): string {
    return STATUSES.find((item) => item.id === status)?.dot ?? 'bg-line-strong';
  }

  personName(id: string): string {
    return PEOPLE.find((person) => person.id === id)?.name ?? id;
  }

  select(key: string): void {
    this.selectedKey.set(this.selectedKey() === key ? null : key);
  }

  drop(event: CdkDragDrop<DemoStatus>): void {
    if (event.previousContainer.data === event.container.data) return;
    this.moveTask(event.item.data as string, event.container.data);
  }

  moveTask(key: string, status: DemoStatus): void {
    const task = this.tasks().find((item) => item.key === key);
    if (!task || task.status === status) return;

    this.patch(key, { status });
    this.note(`${key} moved to ${this.statusLabel(status)}`, false);
    this.runRule(key);
  }

  setPriority(key: string, priority: DemoPriority): void {
    const task = this.tasks().find((item) => item.key === key);
    if (!task || task.priority === priority) return;

    this.patch(key, { priority });
    this.note(`${key} set to ${priority} priority`, false);
    this.runRule(key);
  }

  setAssignee(key: string, who: string): void {
    const task = this.tasks().find((item) => item.key === key);
    if (!task || task.who === who) return;

    this.patch(key, { who });
    this.note(`${key} assigned to ${this.personName(who)}`, false);
  }

  rename(key: string, title: string): void {
    const trimmed = title.trim();
    if (!trimmed) return;
    this.patch(key, { title: trimmed });
  }

  remove(key: string): void {
    this.tasks.update((tasks) => tasks.filter((task) => task.key !== key));
    if (this.selectedKey() === key) this.selectedKey.set(null);
    this.note(`${key} deleted`, false);
  }

  openComposer(status: DemoStatus): void {
    this.composerStatus.set(status);
    this.draft.set('');
  }

  closeComposer(): void {
    this.composerStatus.set(null);
    this.draft.set('');
  }

  commitComposer(): void {
    const status = this.composerStatus();
    const title = this.draft().trim();
    if (!status || !title) {
      this.closeComposer();
      return;
    }

    const key = `NT-${this.nextNumber++}`;
    const start = this.tasks().length % (DAYS - 3);

    this.tasks.update((tasks) => [
      ...tasks,
      { key, title, status, priority: 'medium', points: 3, who: 'MK', start, span: 3 },
    ]);
    this.note(`${key} created in ${this.statusLabel(status)}`, false);
    this.draft.set('');
  }

  commitAndClose(): void {
    this.commitComposer();
    this.closeComposer();
  }

  toggleRule(): void {
    this.ruleOn.update((on) => !on);
    this.note(this.ruleOn() ? 'Rule enabled' : 'Rule paused', true);
  }

  reset(): void {
    this.tasks.set(SEED.map((task) => ({ ...task })));
    this.log.set([...SEED_LOG]);
    this.selectedKey.set(null);
    this.closeComposer();
    this.ruleOn.set(true);
    this.nextNumber = 152;
  }

  private patch(key: string, patch: Partial<DemoTask>): void {
    this.tasks.update((tasks) =>
      tasks.map((task) => (task.key === key ? { ...task, ...patch } : task)),
    );
  }

  private runRule(key: string): void {
    if (!this.ruleOn()) return;

    const task = this.tasks().find((item) => item.key === key);
    if (!task || task.status !== 'review' || task.priority !== 'high') return;
    if (task.who === REVIEWER) return;

    this.patch(key, { who: REVIEWER });
    this.note(`Rule assigned ${key} to ${this.personName(REVIEWER)}`, true);
  }

  private note(text: string, rule: boolean): void {
    const time = new Date().toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
    this.log.update((entries) => [{ time, text, rule }, ...entries].slice(0, 8));
  }
}

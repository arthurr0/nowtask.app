import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { I18nService } from '../core/i18n/i18n.service';
import type { GroupBy, SortBy } from '../data/view-state';
import { LIST_COLUMNS, ViewState } from '../data/view-state';
import { WorkspaceStore } from '../data/workspace.store';
import { ColumnPicker } from './column-picker';
import { Icon } from './icon';
import { Menu, type MenuItem } from './menu';
import { PromptService } from './prompt.service';
import { ToastService } from './toast.service';

const GROUPS: readonly GroupBy[] = ['status', 'assignee', 'priority', 'epic'];
const SORTS: readonly SortBy[] = ['manual', 'due', 'priority', 'title', 'key'];
const PRIORITIES = ['low', 'medium', 'high', 'critical'] as const;

const CHIP =
  'flex h-7 items-center gap-1.5 rounded-full border border-line px-2.5 text-xs text-ink-2 whitespace-nowrap';
const CHIP_ACTIVE =
  'flex h-7 items-center gap-1.5 rounded-full border border-ink bg-inv px-2.5 text-xs font-medium text-inv-ink whitespace-nowrap';
const CHIP_DASHED =
  'flex h-7 items-center gap-1.5 rounded-full border border-dashed border-line-strong px-2.5 text-xs text-ink-3 whitespace-nowrap';
const TOOL =
  'flex h-7 items-center gap-1.5 rounded-field border border-line bg-surface-2 px-2.5 text-xs text-ink-2 whitespace-nowrap';

@Component({
  selector: 'ui-filter-bar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, Menu, ColumnPicker],
  template: `
    <div
      class="flex h-[50px] flex-none items-center gap-2.5 overflow-x-auto border-b border-line bg-surface px-5 scroll-thin"
    >
      <ng-content />

      <span class="h-5 w-px flex-none bg-line"></span>

      <span
        class="focus-ring flex h-7 flex-none items-center gap-1.5 rounded-field border border-line bg-surface-2 px-2.5"
      >
        <ui-icon name="search" [size]="13" class="text-ink-3" />
        <input
          class="w-32 min-w-0 bg-transparent text-xs outline-none placeholder:text-ink-3"
          [placeholder]="t('filters.searchInView')"
          [value]="view.search()"
          (input)="view.search.set($any($event.target).value)"
        />
        @if (view.search()) {
          <button
            type="button"
            class="text-ink-3"
            [attr.aria-label]="t('ui.field.clear')"
            (click)="view.search.set('')"
          >
            <ui-icon name="x" [size]="12" />
          </button>
        }
      </span>

      <ui-menu
        [items]="assigneeItems()"
        [triggerClass]="view.assigneeId() || view.unassigned() ? chipActive : chip"
        triggerHeight="28px"
        ariaLabel="common.assignee"
        (selected)="pickAssignee($event)"
      >
        <ui-icon name="user" [size]="13" />
        <span>{{ assigneeLabel() }}</span>
      </ui-menu>

      <ui-menu
        [items]="labelItems()"
        [triggerClass]="view.label() ? chipActive : chip"
        triggerHeight="28px"
        ariaLabel="common.label"
        (selected)="pickLabel($event)"
      >
        <span>{{ view.label() ?? t('common.label') }}</span>
      </ui-menu>

      <ui-menu
        [items]="priorityItems()"
        [triggerClass]="view.priority() ? chipActive : chip"
        triggerHeight="28px"
        ariaLabel="list.priority"
        (selected)="pickPriority($event)"
      >
        <span>{{ view.priority() ? t('priority.' + view.priority()) : t('list.priority') }}</span>
      </ui-menu>

      @if (store.activeProjects().length > 1) {
        <ui-menu
          [items]="projectItems()"
          [triggerClass]="view.projectId() ? chipActive : chip"
          triggerHeight="28px"
          ariaLabel="filters.project"
          (selected)="pickProject($event)"
        >
          <span>{{ store.project(view.projectId())?.name ?? t('filters.project') }}</span>
        </ui-menu>
      }

      @if (store.sprints().length > 1) {
        <ui-menu
          [items]="sprintItems()"
          [triggerClass]="view.sprint() ? chipActive : chip"
          triggerHeight="28px"
          ariaLabel="filters.sprint"
          (selected)="pickSprint($event)"
        >
          <span>{{ view.sprint() ?? t('filters.sprint') }}</span>
        </ui-menu>
      }

      @if (store.epics().length) {
        <ui-menu
          [items]="epicItems()"
          [triggerClass]="view.epicId() ? chipActive : chip"
          triggerHeight="28px"
          ariaLabel="task.epic"
          (selected)="pickEpic($event)"
        >
          <span>{{ store.epic(view.epicId())?.name ?? t('task.epic') }}</span>
        </ui-menu>
      }

      <ui-menu
        [items]="extraItems()"
        [triggerClass]="chipDashed"
        triggerHeight="28px"
        ariaLabel="common.filter"
        (selected)="toggleExtra($event)"
      >
        <ui-icon name="plus" [size]="13" />
        <span>{{ t('common.filter') }}</span>
      </ui-menu>

      @if (view.hasFilters()) {
        <button
          type="button"
          class="flex h-7 flex-none items-center gap-1.5 rounded-full px-2 text-xs text-ink-3 whitespace-nowrap"
          (click)="view.reset()"
        >
          <ui-icon name="x" [size]="13" />
          {{ t('filters.clear') }}
        </button>
      }

      <span class="flex-1"></span>

      @if (showGrouping()) {
        <ui-menu
          [items]="groupItems()"
          [triggerClass]="tool"
          triggerHeight="28px"
          ariaLabel="common.groupBy"
          (selected)="view.groupBy.set($any($event.id))"
        >
          <ui-icon name="layers" [size]="14" />
          <span class="hidden 2xl:inline">{{ t('common.groupBy') }}:</span>
          <span>{{ t('group.' + view.groupBy()) }}</span>
        </ui-menu>
      }

      <ui-menu
        [items]="sortItems()"
        [triggerClass]="tool"
        triggerHeight="28px"
        ariaLabel="filters.sort"
        (selected)="view.sort.set($any($event.id))"
      >
        <ui-icon name="sort" [size]="14" />
        <span class="hidden 2xl:inline">{{ t('filters.sort') }}:</span>
        <span>{{ t('sort.' + view.sort()) }}</span>
      </ui-menu>

      @if (showRules()) {
        <span
          class="hidden flex-none items-center gap-1.5 text-xs text-ink-2 whitespace-nowrap 2xl:flex"
          [attr.title]="t('common.activeRules', { count: store.activeRuleCount() })"
        >
          <ui-icon name="bolt" [size]="14" class="text-accent" />
          {{ t('common.activeRules', { count: store.activeRuleCount() }) }}
        </span>
      }

      @if (showColumns()) {
        <button
          type="button"
          [class]="tool"
          [attr.aria-label]="t('common.columns')"
          (click)="columnsOpen.set(true)"
        >
          <ui-icon name="columns" [size]="14" />
          <span class="hidden 2xl:inline">{{ t('common.columns') }}</span>
          @if (view.hiddenColumnCount()) {
            <span class="font-mono text-[11px] text-ink-3"
              >{{ view.visibleColumns().length }}/{{ columnCount }}</span
            >
          }
        </button>
      }

      @if (activeView(); as active) {
        <ui-menu
          [items]="saveItems"
          [triggerClass]="tool"
          triggerHeight="28px"
          ariaLabel="common.saveView"
          align="end"
          (selected)="onSaveMenu($event, active.id, active.name ?? '')"
        >
          <ui-icon name="save" [size]="14" />
          <span class="hidden 2xl:inline">{{ t('common.saveView') }}</span>
        </ui-menu>
      } @else {
        <button
          type="button"
          [class]="tool"
          [attr.aria-label]="t('common.saveView')"
          (click)="saveView()"
        >
          <ui-icon name="save" [size]="14" />
          <span class="hidden 2xl:inline">{{ t('common.saveView') }}</span>
        </button>
      }
    </div>

    @if (showColumns()) {
      <ui-column-picker [(open)]="columnsOpen" />
    }
  `,
})
export class FilterBar {
  protected readonly store = inject(WorkspaceStore);
  protected readonly view = inject(ViewState);
  private readonly toast = inject(ToastService);
  private readonly prompt = inject(PromptService);
  private readonly i18n = inject(I18nService);
  protected readonly t = this.i18n.t;

  readonly showGrouping = input(true);
  readonly showRules = input(false);
  readonly showColumns = input(false);

  protected readonly columnsOpen = signal(false);
  protected readonly columnCount = LIST_COLUMNS.length;

  protected readonly activeView = computed(
    () =>
      this.store.savedViews().find((view) => view.id === this.view.activeViewId() && view.name) ??
      null,
  );

  protected readonly saveItems: readonly MenuItem[] = [
    { id: 'update', label: 'filters.updateView', icon: 'save' },
    { id: 'new', label: 'filters.saveAsNew', icon: 'plus', separatorBefore: true },
  ];

  protected readonly chip = CHIP;
  protected readonly chipActive = CHIP_ACTIVE;
  protected readonly chipDashed = CHIP_DASHED;
  protected readonly tool = TOOL;

  protected readonly assigneeLabel = computed(() => {
    if (this.view.unassigned()) return this.t('common.unassigned');
    const user = this.store.user(this.view.assigneeId());
    return user?.shortName ?? this.t('common.assignee');
  });

  protected readonly assigneeItems = computed<MenuItem[]>(() => {
    const me = this.store.currentUser();
    const items: MenuItem[] = [];
    if (me) {
      items.push({
        id: me.id,
        label: this.t('filters.assignedToMe'),
        icon: 'user',
        checked: this.view.assigneeId() === me.id,
      });
    }
    items.push({
      id: 'unassigned',
      label: this.t('common.unassigned'),
      checked: this.view.unassigned(),
    });
    for (const user of this.store.activeMembers()) {
      if (me && user.id === me.id) continue;
      items.push({
        id: user.id,
        label: user.name,
        checked: this.view.assigneeId() === user.id,
        separatorBefore: items.length === 2,
      });
    }
    items.push({ id: 'clear', label: this.t('filters.any'), separatorBefore: true });
    return items;
  });

  protected readonly labelItems = computed<MenuItem[]>(() => {
    const items: MenuItem[] = this.store.allLabels().map((label) => ({
      id: label,
      label,
      checked: this.view.label() === label,
    }));
    items.push({ id: 'clear', label: this.t('filters.any'), separatorBefore: items.length > 0 });
    return items;
  });

  protected readonly priorityItems = computed<MenuItem[]>(() => {
    const items: MenuItem[] = PRIORITIES.map((priority) => ({
      id: priority,
      label: this.t('priority.' + priority),
      checked: this.view.priority() === priority,
    }));
    items.push({ id: 'clear', label: this.t('filters.any'), separatorBefore: true });
    return items;
  });

  protected readonly epicItems = computed<MenuItem[]>(() => {
    const items: MenuItem[] = this.store.epicsOfProject(this.view.projectId()).map((epic) => ({
      id: epic.id,
      label: epic.name,
      checked: this.view.epicId() === epic.id,
    }));
    items.push({ id: 'clear', label: this.t('filters.any'), separatorBefore: items.length > 0 });
    return items;
  });

  protected readonly projectItems = computed<MenuItem[]>(() => {
    const items: MenuItem[] = this.store.activeProjects().map((project) => ({
      id: project.id,
      label: project.name,
      hint: project.code,
      checked: this.view.projectId() === project.id,
    }));
    items.push({
      id: 'clear',
      label: this.t('projects.showAll'),
      separatorBefore: items.length > 0,
    });
    return items;
  });

  protected readonly sprintItems = computed<MenuItem[]>(() => {
    const current = this.store.currentSprint();
    const items: MenuItem[] = this.store.sprints().map((sprint) => ({
      id: sprint,
      label: sprint === current ? this.t('filters.sprintCurrent', { sprint }) : sprint,
      checked: this.view.sprint() === sprint,
    }));
    items.push({ id: 'clear', label: this.t('filters.any'), separatorBefore: items.length > 0 });
    return items;
  });

  protected readonly extraItems = computed<MenuItem[]>(() => [
    {
      id: 'overdue',
      label: this.t('filters.overdue'),
      icon: 'clock',
      checked: this.view.overdueOnly(),
      keepOpen: true,
    },
    {
      id: 'automated',
      label: this.t('filters.automated'),
      icon: 'bolt',
      checked: this.view.automated(),
      keepOpen: true,
    },
    {
      id: 'unassigned',
      label: this.t('common.unassigned'),
      icon: 'user',
      checked: this.view.unassigned(),
      keepOpen: true,
    },
  ]);

  protected readonly groupItems = computed<MenuItem[]>(() =>
    GROUPS.map((group) => ({
      id: group,
      label: this.t('group.' + group),
      checked: this.view.groupBy() === group,
    })),
  );

  protected readonly sortItems = computed<MenuItem[]>(() =>
    SORTS.map((sort) => ({
      id: sort,
      label: this.t('sort.' + sort),
      checked: this.view.sort() === sort,
    })),
  );

  protected pickAssignee(item: MenuItem): void {
    if (item.id === 'clear') {
      this.view.assigneeId.set(null);
      this.view.unassigned.set(false);
      return;
    }
    if (item.id === 'unassigned') {
      this.view.assigneeId.set(null);
      this.view.unassigned.update((value) => !value);
      return;
    }
    this.view.unassigned.set(false);
    this.view.assigneeId.update((value) => (value === item.id ? null : item.id));
  }

  protected pickLabel(item: MenuItem): void {
    if (item.id === 'clear') {
      this.view.label.set(null);
      return;
    }
    this.view.label.update((value) => (value === item.id ? null : item.id));
  }

  protected pickPriority(item: MenuItem): void {
    if (item.id === 'clear') {
      this.view.priority.set(null);
      return;
    }
    this.view.priority.update((value) => (value === item.id ? null : item.id));
  }

  protected pickEpic(item: MenuItem): void {
    if (item.id === 'clear') {
      this.view.epicId.set(null);
      return;
    }
    this.view.epicId.update((value) => (value === item.id ? null : item.id));
  }

  protected pickProject(item: MenuItem): void {
    if (item.id === 'clear') {
      this.view.projectId.set(null);
      return;
    }
    this.view.projectId.update((value) => (value === item.id ? null : item.id));
  }

  protected pickSprint(item: MenuItem): void {
    if (item.id === 'clear') {
      this.view.sprint.set(null);
      return;
    }
    this.view.sprint.update((value) => (value === item.id ? null : item.id));
  }

  protected toggleExtra(item: MenuItem): void {
    if (item.id === 'overdue') this.view.overdueOnly.update((value) => !value);
    if (item.id === 'automated') this.view.automated.update((value) => !value);
    if (item.id === 'unassigned') {
      this.view.unassigned.update((value) => !value);
      if (this.view.unassigned()) this.view.assigneeId.set(null);
    }
  }

  protected async onSaveMenu(item: MenuItem, id: string, name: string): Promise<void> {
    if (item.id === 'new') {
      await this.saveView();
      return;
    }
    try {
      await this.store.updateViewQuery(id, this.view.toQuery());
      this.toast.success(this.t('filters.viewSaved', { name }));
    } catch {
      this.toast.error(this.t('filters.viewFailed'));
    }
  }

  protected async saveView(): Promise<void> {
    const name = await this.prompt.ask({
      title: 'filters.saveViewTitle',
      message: 'filters.saveViewPrompt',
      label: 'filters.viewName',
      placeholder: 'filters.viewNamePlaceholder',
      value: this.suggestName(),
      confirmLabel: 'common.saveView',
    });
    if (!name) return;
    try {
      const created = await this.store.createView(name, this.view.toQuery());
      this.view.activeViewId.set(created.id);
      this.toast.success(this.t('filters.viewSaved', { name }));
    } catch {
      this.toast.error(this.t('filters.viewFailed'));
    }
  }

  private suggestName(): string {
    const parts: string[] = [];
    if (this.view.unassigned()) parts.push(this.t('common.unassigned'));
    const user = this.store.user(this.view.assigneeId());
    if (user) parts.push(user.shortName);
    const project = this.store.project(this.view.projectId());
    if (project) parts.push(project.name);
    if (this.view.sprint()) parts.push(this.view.sprint()!);
    if (this.view.label()) parts.push(this.view.label()!);
    if (this.view.priority()) parts.push(this.t('priority.' + this.view.priority()));
    if (this.view.overdueOnly()) parts.push(this.t('filters.overdue'));
    return parts.join(' · ') || this.t('filters.newView');
  }
}

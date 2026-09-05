import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { I18nService } from '../core/i18n/i18n.service';
import { condition, defaultOp } from '../core/task-filter';
import type { GroupBy, SortBy } from '../data/view-state';
import { GROUP_FIELDS, SORT_FIELDS, ViewState } from '../data/view-state';
import { FilterFields } from '../data/filter-fields';
import { WorkspaceStore } from '../data/workspace.store';
import { ColumnPicker } from './column-picker';
import { FilterChip } from './filter-chip';
import { FilterDialog } from './filter-dialog';
import { Icon } from './icon';
import { Menu, type MenuItem } from './menu';
import { PromptService } from './prompt.service';
import { ToastService } from './toast.service';

const GROUPS: readonly GroupBy[] = ['status', 'assignee', 'priority', 'epic'];
const SORTS: readonly SortBy[] = ['manual', 'due', 'priority', 'title', 'key'];

const CHIP_DASHED =
  'flex h-7 items-center gap-1.5 rounded-full border border-dashed border-line-strong px-2.5 text-xs text-ink-3 whitespace-nowrap';
const TOOL =
  'flex h-7 items-center gap-1.5 rounded-field border border-line bg-surface-2 px-2.5 text-xs text-ink-2 whitespace-nowrap';
const TOOL_ACTIVE =
  'flex h-7 items-center gap-1.5 rounded-field border border-ink bg-inv px-2.5 text-xs font-medium text-inv-ink whitespace-nowrap';

@Component({
  selector: 'ui-filter-bar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, Menu, ColumnPicker, FilterChip, FilterDialog],
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

      @for (node of view.filter().conditions; track $index; let index = $index) {
        <ui-filter-chip
          [node]="node"
          [autoOpen]="pending() === index"
          (opened)="pending.set(null)"
          (changed)="view.replaceAt(index, $event)"
          (removed)="view.removeAt(index)"
          (openAdvanced)="advancedOpen.set(true)"
        />
      }

      <ui-menu
        [items]="fieldItems()"
        [triggerClass]="chipDashed"
        triggerHeight="28px"
        ariaLabel="filters.addFilter"
        [minWidth]="230"
        (selected)="addField($event.id)"
      >
        <ui-icon name="plus" [size]="13" />
        <span>{{ t('filters.addFilter') }}</span>
      </ui-menu>

      <button
        type="button"
        [class]="view.filter().join === 'or' || hasGroups() ? toolActive : chipDashed"
        [attr.aria-label]="t('filters.advanced')"
        (click)="advancedOpen.set(true)"
      >
        <ui-icon name="sliders" [size]="13" />
        <span>{{ t('filters.advanced') }}</span>
        @if (view.filter().join === 'or') {
          <span class="font-mono text-[10px]">OR</span>
        }
      </button>

      @if (view.hasFilters()) {
        <button
          type="button"
          class="flex h-7 flex-none items-center gap-1.5 rounded-full px-2 text-xs text-ink-3 whitespace-nowrap"
          (click)="clear()"
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
              >{{ view.visibleColumns().length }}/{{ columnCount() }}</span
            >
          }
        </button>
      }

      @if (view.activeView(); as active) {
        <ui-menu
          [items]="saveItems()"
          [triggerClass]="view.dirty() ? toolActive : tool"
          triggerHeight="28px"
          ariaLabel="common.saveView"
          align="end"
          (selected)="onSaveMenu($event, active.id)"
        >
          <ui-icon name="save" [size]="14" />
          <span class="hidden 2xl:inline">{{
            view.dirty() ? t('filters.unsavedChanges') : t('common.saveView')
          }}</span>
          @if (view.dirty()) {
            <span class="h-1.5 w-1.5 rounded-full bg-accent 2xl:hidden"></span>
          }
        </ui-menu>
      } @else {
        <button
          type="button"
          [class]="tool"
          [attr.aria-label]="t('filters.saveAsView')"
          (click)="saveAsNew()"
        >
          <ui-icon name="save" [size]="14" />
          <span class="hidden 2xl:inline">{{ t('filters.saveAsView') }}</span>
        </button>
      }
    </div>

    @if (showColumns()) {
      <ui-column-picker [(open)]="columnsOpen" />
    }
    <ui-filter-dialog [(open)]="advancedOpen" />
  `,
})
export class FilterBar {
  protected readonly store = inject(WorkspaceStore);
  protected readonly view = inject(ViewState);
  private readonly catalog = inject(FilterFields);
  private readonly toast = inject(ToastService);
  private readonly prompt = inject(PromptService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);
  protected readonly t = this.i18n.t;

  readonly showGrouping = input(true);
  readonly showRules = input(false);
  readonly showColumns = input(false);

  protected readonly columnsOpen = signal(false);
  protected readonly advancedOpen = signal(false);
  protected readonly pending = signal<number | null>(null);
  protected readonly columnCount = computed(() => this.view.availableColumns().length);

  protected readonly chipDashed = CHIP_DASHED;
  protected readonly tool = TOOL;
  protected readonly toolActive = TOOL_ACTIVE;

  protected readonly hasGroups = computed(() =>
    this.view.filter().conditions.some((node) => 'conditions' in node),
  );

  protected fieldEnabled(fieldKey: string): boolean {
    return this.store.taskFieldEnabled(fieldKey, this.view.projectId());
  }

  protected readonly fieldItems = computed<MenuItem[]>(() => {
    const items: MenuItem[] = [];
    for (const meta of this.catalog.quickFields()) {
      items.push({ id: meta.key, label: meta.label, icon: meta.icon });
    }
    const more = this.catalog
      .systemFields()
      .filter((meta) => !meta.quick || !this.catalog.quickFields().includes(meta));
    more.forEach((meta, index) => {
      items.push({
        id: meta.key,
        label: meta.label,
        icon: meta.icon,
        separatorBefore: index === 0,
      });
    });
    this.catalog.customFields().forEach((meta, index) => {
      items.push({
        id: meta.key,
        label: meta.label,
        icon: meta.icon,
        separatorBefore: index === 0,
      });
    });
    return items;
  });

  protected readonly saveItems = computed<MenuItem[]>(() => {
    const active = this.view.activeView();
    const items: MenuItem[] = [];
    if (active && !active.builtin) {
      items.push({
        id: 'update',
        label: 'filters.saveChanges',
        icon: 'save',
        disabled: !this.view.dirty(),
      });
    }
    items.push({ id: 'new', label: 'filters.saveAsNew', icon: 'plus' });
    items.push({
      id: 'discard',
      label: 'filters.discard',
      icon: 'x',
      disabled: !this.view.dirty(),
      separatorBefore: true,
    });
    return items;
  });

  protected readonly groupItems = computed<MenuItem[]>(() =>
    GROUPS.filter((group) => group === 'status' || this.fieldEnabled(GROUP_FIELDS[group])).map(
      (group) => ({
        id: group,
        label: this.t('group.' + group),
        checked: this.view.groupBy() === group,
      }),
    ),
  );

  protected readonly sortItems = computed<MenuItem[]>(() =>
    SORTS.filter((sort) => {
      const field = SORT_FIELDS[sort];
      return !field || this.fieldEnabled(field);
    }).map((sort) => ({
      id: sort,
      label: this.t('sort.' + sort),
      checked: this.view.sort() === sort,
    })),
  );

  protected addField(key: string): void {
    const meta = this.catalog.meta(key);
    if (!meta) return;
    this.view.append(condition(key, defaultOp(meta), []));
    this.pending.set(this.view.filter().conditions.length - 1);
  }

  protected clear(): void {
    this.view.clearFilters();
  }

  protected async onSaveMenu(item: MenuItem, id: string): Promise<void> {
    if (item.id === 'new') {
      await this.saveAsNew();
      return;
    }
    const active = this.view.activeView();
    if (!active) return;
    if (item.id === 'discard') {
      this.view.applyQuery(active.query, active.id);
      return;
    }
    try {
      await this.store.updateView(id, { query: this.view.toQuery() });
      this.toast.success(this.t('filters.viewSaved', { name: active.name ?? '' }));
    } catch {
      this.toast.error(this.t('filters.viewFailed'));
    }
  }

  protected async saveAsNew(): Promise<void> {
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
      void this.router.navigate(['/app/views', created.id]);
    } catch {
      this.toast.error(this.t('filters.viewFailed'));
    }
  }

  private suggestName(): string {
    const active = this.view.activeView();
    if (active?.name) return this.t('filters.copyOf', { name: active.name });
    const parts: string[] = [];
    const project = this.store.project(this.view.projectId());
    if (project) parts.push(project.name);
    for (const node of this.view.filter().conditions) {
      if ('conditions' in node) continue;
      if (!node.values.length) continue;
      parts.push(this.catalog.summary(node));
      if (parts.length >= 3) break;
    }
    return parts.join(' · ') || this.t('filters.newView');
  }
}

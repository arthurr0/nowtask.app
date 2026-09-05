import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  untracked,
} from '@angular/core';
import { Router } from '@angular/router';
import type { SavedViewDto } from '../../core/api-types';
import { I18nService } from '../../core/i18n/i18n.service';
import { TASK_VIEWS } from '../../core/task-views';
import { ViewState } from '../../data/view-state';
import { WorkspaceStore } from '../../data/workspace.store';
import { ConfirmService } from '../../ui/confirm.service';
import { EmptyState } from '../../ui/empty-state';
import { Icon } from '../../ui/icon';
import { Menu, type MenuItem } from '../../ui/menu';
import { PromptService } from '../../ui/prompt.service';
import { ToastService } from '../../ui/toast.service';
import { Topbar } from '../../ui/topbar';
import { Board } from '../board/board';
import { Calendar } from '../calendar/calendar';
import { TaskList } from '../list/list';
import { Timeline } from '../timeline/timeline';

@Component({
  selector: 'app-saved-view',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Board, TaskList, Timeline, Calendar, Icon, Menu, Topbar, EmptyState],
  template: `
    @if (missing()) {
      <ui-topbar>
        <span class="text-[15px] font-semibold tracking-[-0.01em]">{{ t('nav.savedViews') }}</span>
      </ui-topbar>
      <ui-empty-state
        icon="save"
        title="filters.viewNotFound"
        description="nav.noSavedViews"
        actionLabel="nav.board"
        (action)="router.navigate(['/app/board'])"
      />
    } @else if (view(); as saved) {
      <div
        class="flex h-10 flex-none items-center gap-2.5 overflow-x-auto border-b border-line bg-surface-2 px-4 lg:px-5 scroll-thin"
      >
        <ui-icon name="save" [size]="14" class="flex-none text-ink-3" />
        <span class="text-xs text-ink-3">{{ t('filters.viewSubtitle') }}</span>
        <span class="truncate text-[13px] font-medium">{{ name() }}</span>
        <span class="kap rounded-full border border-line px-2 py-0.5">{{
          t(saved.builtin ? 'filters.builtin' : saved.shared ? 'filters.shared' : 'filters.private')
        }}</span>

        <span class="h-4 w-px flex-none bg-line"></span>

        <div class="flex items-center gap-0.5 rounded-[6px] border border-line bg-surface p-0.5">
          @for (layout of layouts(); track layout.code) {
            <button
              type="button"
              class="flex h-6 items-center gap-1 rounded-[4px] px-2 text-[11px] whitespace-nowrap"
              [class.bg-inv]="state.layout() === layout.code"
              [class.text-inv-ink]="state.layout() === layout.code"
              [class.text-ink-2]="state.layout() !== layout.code"
              [attr.aria-pressed]="state.layout() === layout.code"
              (click)="state.layout.set(layout.code)"
            >
              <ui-icon [name]="layout.icon" [size]="13" />
              <span class="hidden md:inline">{{ t(layout.label) }}</span>
            </button>
          }
        </div>

        <span class="flex-1"></span>

        @if (state.dirty()) {
          <span class="hidden text-[11px] text-ink-3 sm:inline">{{
            t('filters.unsavedChanges')
          }}</span>
          @if (!saved.builtin) {
            <button
              type="button"
              class="flex h-7 items-center gap-1.5 rounded-[6px] bg-inv px-2.5 text-xs font-medium text-inv-ink"
              (click)="save(saved)"
            >
              <ui-icon name="save" [size]="13" />
              {{ t('filters.saveChanges') }}
            </button>
          }
          <button
            type="button"
            class="flex h-7 items-center gap-1.5 rounded-[6px] border border-line bg-surface px-2.5 text-xs text-ink-2"
            (click)="discard(saved)"
          >
            <ui-icon name="x" [size]="13" />
            {{ t('filters.discard') }}
          </button>
        }

        <ui-menu
          [items]="menu()"
          triggerClass="hoverable flex h-7 w-7 items-center justify-center rounded-[6px] text-ink-3"
          triggerHeight="28px"
          ariaLabel="common.more"
          align="end"
          (selected)="onMenu($event, saved)"
        >
          <ui-icon name="dots" [size]="15" />
        </ui-menu>
      </div>

      @switch (state.layout()) {
        @case ('board') {
          <app-board class="contents" />
        }
        @case ('timeline') {
          <app-timeline class="contents" />
        }
        @case ('calendar') {
          <app-calendar class="contents" />
        }
        @default {
          <app-list class="contents" />
        }
      }
    }
  `,
})
export class SavedViewPage {
  protected readonly store = inject(WorkspaceStore);
  protected readonly state = inject(ViewState);
  protected readonly router = inject(Router);
  private readonly confirm = inject(ConfirmService);
  private readonly prompt = inject(PromptService);
  private readonly toast = inject(ToastService);
  protected readonly t = inject(I18nService).t;

  readonly id = input.required<string>();

  protected readonly view = computed<SavedViewDto | null>(() => this.store.savedView(this.id()));
  protected readonly missing = computed(() => this.store.ready() && this.view() === null);
  protected readonly name = computed(() => {
    const view = this.view();
    return view ? view.name || this.t(view.code ?? '') : '';
  });

  protected readonly layouts = computed(() =>
    TASK_VIEWS.filter((layout) => this.store.taskViewEnabled(layout.code, this.state.projectId())),
  );

  protected readonly menu = computed<MenuItem[]>(() => {
    const view = this.view();
    if (!view) return [];
    if (view.builtin) return [{ id: 'duplicate', label: 'filters.duplicateView', icon: 'copy' }];
    return [
      { id: 'rename', label: 'nav.renameView', icon: 'pencil' },
      { id: 'duplicate', label: 'filters.duplicateView', icon: 'copy' },
      {
        id: 'share',
        label: view.shared ? 'filters.unshareView' : 'filters.shareView',
        icon: view.shared ? 'lock' : 'users',
      },
      { id: 'delete', label: 'common.delete', icon: 'trash', danger: true, separatorBefore: true },
    ];
  });

  constructor() {
    effect(() => {
      const view = this.view();
      if (!view) return;
      untracked(() => {
        if (this.state.activeViewId() !== view.id) {
          this.state.applyQuery(view.query, view.id);
        }
        if (!this.layouts().some((layout) => layout.code === this.state.layout())) {
          this.state.layout.set(this.layouts()[0]?.code ?? 'list');
        }
      });
    });
  }

  protected async save(view: SavedViewDto): Promise<void> {
    try {
      await this.store.updateView(view.id, { query: this.state.toQuery() });
      this.toast.success(this.t('filters.viewSaved', { name: this.name() }));
    } catch {
      this.toast.error(this.t('filters.viewFailed'));
    }
  }

  protected discard(view: SavedViewDto): void {
    this.state.applyQuery(view.query, view.id);
  }

  protected async onMenu(item: MenuItem, view: SavedViewDto): Promise<void> {
    switch (item.id) {
      case 'rename': {
        const name = await this.prompt.ask({
          title: 'nav.renameView',
          label: 'filters.viewName',
          value: view.name ?? '',
        });
        if (!name) return;
        await this.run(() => this.store.updateView(view.id, { name }));
        break;
      }
      case 'duplicate': {
        const name = await this.prompt.ask({
          title: 'filters.duplicateView',
          label: 'filters.viewName',
          value: this.t('filters.copyOf', { name: this.name() }),
        });
        if (!name) return;
        try {
          const created = await this.store.createView(name, this.state.toQuery());
          this.toast.success(this.t('filters.viewSaved', { name }));
          void this.router.navigate(['/app/views', created.id]);
        } catch {
          this.toast.error(this.t('filters.viewFailed'));
        }
        break;
      }
      case 'share':
        await this.run(() => this.store.updateView(view.id, { shared: !view.shared }));
        break;
      case 'delete': {
        const confirmed = await this.confirm.askDelete(this.name());
        if (!confirmed) return;
        try {
          await this.store.deleteView(view.id);
          this.state.reset();
          this.toast.success(this.t('filters.viewDeleted'));
          void this.router.navigate(['/app/board']);
        } catch {
          this.toast.error(this.t('common.actionFailed'));
        }
        break;
      }
      default:
        break;
    }
  }

  private async run(work: () => Promise<unknown>): Promise<void> {
    try {
      await work();
      this.toast.success(this.t('common.saved'));
    } catch {
      this.toast.error(this.t('common.actionFailed'));
    }
  }
}

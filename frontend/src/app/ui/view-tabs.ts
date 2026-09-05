import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { I18nService } from '../core/i18n/i18n.service';
import { TASK_VIEWS, taskViewMeta } from '../core/task-views';
import type { SavedViewDto } from '../core/api-types';
import { ViewState } from '../data/view-state';
import { WorkspaceStore } from '../data/workspace.store';
import { Icon } from './icon';

@Component({
  selector: 'ui-view-tabs',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, Icon],
  template: `
    <div
      class="flex items-center gap-0.5 rounded-[7px] border border-line bg-surface-2 p-0.5"
      data-tour="views"
    >
      @for (tab of tabs(); track tab.path) {
        <a
          [routerLink]="tab.path"
          routerLinkActive="bg-surface !text-ink font-medium shadow-card"
          class="flex h-7 items-center gap-1.5 rounded-[5px] px-3 text-ink-2 whitespace-nowrap"
        >
          <ui-icon [name]="tab.icon" [size]="15" />
          <span>{{ t(tab.label) }}</span>
        </a>
      }
    </div>

    @if (views().length) {
      <span class="h-5 w-px flex-none bg-line"></span>
      <div class="flex items-center gap-0.5 rounded-[7px] border border-line bg-surface-2 p-0.5">
        @for (item of views(); track item.id) {
          <a
            [routerLink]="['/app/views', item.id]"
            routerLinkActive="bg-surface !text-ink font-medium shadow-card"
            class="flex h-7 items-center gap-1.5 rounded-[5px] px-3 text-ink-2 whitespace-nowrap"
            [attr.title]="viewName(item)"
          >
            <ui-icon [name]="layoutIcon(item)" [size]="14" class="text-ink-3" />
            <span class="max-w-36 truncate">{{ viewName(item) }}</span>
            <span class="font-mono text-[10px] text-ink-3">{{ item.count }}</span>
            @if (item.id === view.activeViewId() && view.dirty()) {
              <span class="h-1.5 w-1.5 rounded-full bg-accent"></span>
            }
          </a>
        }
      </div>
    }
  `,
  styles: `
    :host {
      display: flex;
      align-items: center;
      gap: 0.625rem;
      flex: none;
    }
  `,
})
export class ViewTabs {
  private readonly store = inject(WorkspaceStore);
  protected readonly view = inject(ViewState);
  protected readonly t = inject(I18nService).t;

  protected readonly tabs = computed(() =>
    TASK_VIEWS.filter((tab) => this.store.taskViewEnabled(tab.code, this.view.projectId())),
  );

  protected readonly views = computed(() => this.store.savedViews());

  protected viewName(item: SavedViewDto): string {
    return item.name || this.t(item.code ?? '');
  }

  protected layoutIcon(item: SavedViewDto): string {
    return taskViewMeta(item.query.layout ?? 'list').icon;
  }
}

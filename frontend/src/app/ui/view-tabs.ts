import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { I18nService } from '../core/i18n/i18n.service';
import { TASK_VIEWS } from '../core/task-views';
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
  `,
})
export class ViewTabs {
  private readonly store = inject(WorkspaceStore);
  private readonly view = inject(ViewState);
  protected readonly t = inject(I18nService).t;

  protected readonly tabs = computed(() =>
    TASK_VIEWS.filter((tab) => this.store.taskViewEnabled(tab.code, this.view.projectId())),
  );
}

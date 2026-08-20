import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { I18nService } from '../core/i18n/i18n.service';
import { Icon } from './icon';

@Component({
  selector: 'ui-view-tabs',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, Icon],
  template: `
    <div class="flex items-center gap-0.5 rounded-[7px] border border-line bg-surface-2 p-0.5">
      @for (tab of tabs; track tab.path) {
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
  protected readonly t = inject(I18nService).t;

  protected readonly tabs = [
    { path: '/app/board', icon: 'board', label: 'nav.board' },
    { path: '/app/list', icon: 'list', label: 'nav.list' },
    { path: '/app/timeline', icon: 'timeline', label: 'nav.timeline' },
  ] as const;
}

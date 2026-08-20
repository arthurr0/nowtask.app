import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { I18nService, LANGUAGES } from '../core/i18n/i18n.service';
import { PrefsService } from '../core/prefs.service';
import { Icon } from './icon';

@Component({
  selector: 'ui-view-controls',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  template: `
    <div class="flex items-center gap-3">
      <div
        class="flex items-center gap-0.5 rounded-[7px] border border-line bg-surface-2 p-0.5"
        role="group"
        [attr.aria-label]="t('common.language')"
      >
        @for (item of languages; track item.code) {
          <button
            type="button"
            class="flex h-[26px] items-center rounded-[5px] px-2.5 font-mono text-[11px] transition-colors"
            [class.bg-inv]="i18n.lang() === item.code"
            [class.text-inv-ink]="i18n.lang() === item.code"
            [class.text-ink-2]="i18n.lang() !== item.code"
            [attr.aria-pressed]="i18n.lang() === item.code"
            (click)="i18n.setLang(item.code)"
          >
            {{ item.short }}
          </button>
        }
      </div>

      <button
        type="button"
        class="flex h-8 w-8 items-center justify-center rounded-[7px] border border-line bg-surface-2 text-ink-2"
        [attr.aria-label]="t('common.toggleTheme')"
        (click)="prefs.toggleTheme()"
      >
        @if (prefs.resolvedTheme() === 'dark') {
          <ui-icon name="sun" />
        } @else {
          <ui-icon name="moon" />
        }
      </button>
    </div>
  `,
})
export class ViewControls {
  protected readonly i18n = inject(I18nService);
  protected readonly prefs = inject(PrefsService);
  protected readonly t = this.i18n.t;
  protected readonly languages = LANGUAGES;
}

import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { I18nService } from '../core/i18n/i18n.service';
import { Icon } from './icon';

@Component({
  selector: 'ui-page-state',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  template: `
    @if (error()) {
      <div class="flex flex-1 flex-col items-center justify-center gap-4 p-8 text-center">
        <span
          class="flex h-11 w-11 items-center justify-center rounded-full bg-warn-soft text-warn"
        >
          <ui-icon name="alert" [size]="22" />
        </span>
        <div class="flex max-w-[420px] flex-col gap-1.5">
          <p class="text-[15px] font-medium">{{ t('state.errorTitle') }}</p>
          <p class="text-[13px] text-ink-2">{{ error() }}</p>
        </div>
        <button
          type="button"
          class="flex h-9 items-center gap-2 rounded-[7px] bg-inv px-4 text-[13px] font-medium text-inv-ink"
          (click)="retry.emit()"
        >
          {{ t('state.retry') }}
        </button>
      </div>
    } @else if (loading()) {
      <div class="flex flex-1 flex-col items-center justify-center gap-3 p-8">
        <span class="h-6 w-6 animate-spin rounded-full border-2 border-line border-t-ink"></span>
        <p class="text-[13px] text-ink-3">{{ t('state.loading') }}</p>
      </div>
    }
  `,
})
export class PageState {
  protected readonly t = inject(I18nService).t;

  readonly loading = input(false);
  readonly error = input<string | null>(null);
  readonly retry = output<void>();
}

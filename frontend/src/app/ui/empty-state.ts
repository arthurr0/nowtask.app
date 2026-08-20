import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { I18nService } from '../core/i18n/i18n.service';
import { Icon } from './icon';

@Component({
  selector: 'ui-empty-state',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  template: `
    <div class="flex flex-1 flex-col items-center justify-center gap-3.5 p-8 text-center">
      <span
        class="flex h-11 w-11 flex-none items-center justify-center rounded-full bg-surface-2 text-ink-3"
      >
        <ui-icon [name]="icon()" [size]="20" />
      </span>

      <div class="flex max-w-[420px] flex-col gap-1.5">
        <p class="text-[15px] font-medium">{{ t(title()) }}</p>
        @if (description()) {
          <p class="text-[13px] text-ink-2">{{ t(description()) }}</p>
        }
      </div>

      @if (actionLabel()) {
        <button
          type="button"
          class="flex items-center gap-2 rounded-[7px] bg-inv px-4 text-[13px] font-medium text-inv-ink"
          [style.height]="'calc(var(--row-h) - 8px)'"
          (click)="action.emit()"
        >
          @if (actionIcon()) {
            <ui-icon [name]="actionIcon()" [size]="15" />
          }
          {{ t(actionLabel()) }}
        </button>
      }

      <ng-content />
    </div>
  `,
  styles: `
    :host {
      display: flex;
      flex: 1;
      min-height: 0;
    }
  `,
})
export class EmptyState {
  protected readonly t = inject(I18nService).t;

  readonly icon = input('layers');
  readonly title = input('ui.empty.title');
  readonly description = input('');
  readonly actionLabel = input('');
  readonly actionIcon = input('plus');

  readonly action = output<void>();
}

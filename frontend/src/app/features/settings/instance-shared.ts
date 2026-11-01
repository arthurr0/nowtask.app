import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { I18nService } from '../../core/i18n/i18n.service';
import { Icon } from '../../ui/icon';
import { pageCount } from './instance-utils';

export interface SegmentOption {
  value: string;
  label: string;
}

@Component({
  selector: 'app-instance-segmented',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      role="group"
      class="inline-flex h-[34px] items-center rounded-field border border-line bg-surface-2 p-0.5"
      [attr.aria-label]="t(ariaLabel())"
    >
      @for (option of options(); track option.value) {
        <button
          type="button"
          class="flex h-full items-center rounded-[6px] px-2.5 text-xs"
          [class.bg-surface]="value() === option.value"
          [class.font-medium]="value() === option.value"
          [class.text-ink-2]="value() !== option.value"
          [attr.aria-pressed]="value() === option.value"
          (click)="picked.emit(option.value)"
        >
          {{ t(option.label) }}
        </button>
      }
    </div>
  `,
})
export class InstanceSegmented {
  protected readonly t = inject(I18nService).t;

  readonly options = input.required<readonly SegmentOption[]>();
  readonly value = input('');
  readonly ariaLabel = input('');

  readonly picked = output<string>();
}

@Component({
  selector: 'app-instance-pager',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  template: `
    @if (pages() > 1) {
      <div class="flex items-center justify-center gap-2">
        <button
          type="button"
          class="flex h-8 items-center gap-1.5 rounded-field border border-line bg-surface-2 px-2.5 text-xs text-ink-2 disabled:opacity-50"
          [disabled]="page() === 0"
          (click)="pageChange.emit(page() - 1)"
        >
          <ui-icon name="chevron-right" [size]="14" class="rotate-180" />
          {{ t('organization.previous') }}
        </button>
        <span class="font-mono text-[11px] text-ink-3">{{ page() + 1 }} / {{ pages() }}</span>
        <button
          type="button"
          class="flex h-8 items-center gap-1.5 rounded-field border border-line bg-surface-2 px-2.5 text-xs text-ink-2 disabled:opacity-50"
          [disabled]="page() + 1 >= pages()"
          (click)="pageChange.emit(page() + 1)"
        >
          {{ t('organization.next') }}
          <ui-icon name="chevron-right" [size]="14" />
        </button>
      </div>
    }
  `,
})
export class InstancePager {
  protected readonly t = inject(I18nService).t;

  readonly page = input(0);
  readonly total = input(0);
  readonly size = input(25);

  readonly pageChange = output<number>();

  protected readonly pages = computed(() => pageCount(this.total(), this.size()));
}

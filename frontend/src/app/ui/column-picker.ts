import { ChangeDetectionStrategy, Component, computed, inject, model } from '@angular/core';
import { I18nService } from '../core/i18n/i18n.service';
import type { ListColumn } from '../core/api-types';
import { LIST_COLUMNS, ViewState } from '../data/view-state';
import { Dialog } from './dialog';
import { Icon } from './icon';
import { Switch } from './switch';

@Component({
  selector: 'ui-column-picker',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Dialog, Icon, Switch],
  template: `
    <ui-dialog
      [(open)]="open"
      size="sm"
      title="common.columns"
      description="columns.hint"
      (closed)="open.set(false)"
    >
      <div class="flex flex-col gap-0.5">
        @for (column of rows(); track column.code; let index = $index) {
          <div class="flex h-10 items-center gap-2 border-b border-line last:border-b-0">
            <span class="flex-1 text-[13px]" [class.text-ink-3]="column.hidden">{{
              t(column.label)
            }}</span>

            <button
              type="button"
              class="hoverable flex h-7 w-7 items-center justify-center rounded-[6px] text-ink-3 disabled:opacity-40"
              [disabled]="index === 0"
              [attr.aria-label]="t('common.moveUp')"
              (click)="view.moveColumn(column.code, -1)"
            >
              <ui-icon name="up" [size]="14" />
            </button>
            <button
              type="button"
              class="hoverable flex h-7 w-7 items-center justify-center rounded-[6px] text-ink-3 disabled:opacity-40"
              [disabled]="index === rows().length - 1"
              [attr.aria-label]="t('common.moveDown')"
              (click)="view.moveColumn(column.code, 1)"
            >
              <ui-icon name="down" [size]="14" />
            </button>

            <ui-switch
              [checked]="!column.hidden"
              [label]="t(column.label)"
              (toggled)="view.toggleColumn(column.code)"
            />
          </div>
        }
      </div>

      <div dialogFooter class="flex w-full items-center gap-2">
        <button
          type="button"
          class="flex h-9 items-center gap-1.5 rounded-[7px] px-2.5 text-[13px] text-ink-2"
          (click)="view.resetColumns()"
        >
          <ui-icon name="sliders" [size]="15" />
          {{ t('nav.restoreDefaults') }}
        </button>
        <span class="flex-1"></span>
        <button
          type="button"
          class="flex h-9 items-center rounded-[7px] bg-inv px-3.5 text-[13px] font-medium text-inv-ink"
          (click)="open.set(false)"
        >
          {{ t('common.close') }}
        </button>
      </div>
    </ui-dialog>
  `,
})
export class ColumnPicker {
  protected readonly view = inject(ViewState);
  protected readonly t = inject(I18nService).t;

  readonly open = model(false);

  protected readonly rows = computed(() =>
    this.view
      .columns()
      .filter((column) => this.view.columnAvailable(column.code))
      .map((column) => ({
        code: column.code,
        hidden: column.hidden,
        label: labelOf(column.code),
      })),
  );
}

function labelOf(code: ListColumn): string {
  return LIST_COLUMNS.find((column) => column.code === code)?.label ?? code;
}

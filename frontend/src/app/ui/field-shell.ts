import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { I18nService } from '../core/i18n/i18n.service';
import { Icon } from './icon';

let fieldCounter = 0;

export function nextFieldId(prefix: string): string {
  fieldCounter += 1;
  return `${prefix}-${fieldCounter}`;
}

@Component({
  selector: 'ui-field-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  template: `
    <div class="flex min-w-0 flex-col gap-1.5">
      @if (label()) {
        <label class="flex items-center gap-1 text-xs text-ink-2" [attr.for]="controlId()">
          <span>{{ t(label()) }}</span>
          @if (required()) {
            <span class="text-warn" [attr.title]="t('ui.field.required')">*</span>
          }
        </label>
      }

      <ng-content />

      @if (error()) {
        <p
          class="flex items-center gap-1 text-[11px] text-warn"
          [attr.id]="messageId()"
          role="alert"
        >
          <ui-icon name="alert" [size]="13" />
          <span>{{ t(error()) }}</span>
        </p>
      } @else if (hint()) {
        <p class="text-[11px] text-ink-3" [attr.id]="messageId()">{{ t(hint()) }}</p>
      }
    </div>
  `,
})
export class FieldShell {
  protected readonly t = inject(I18nService).t;

  readonly label = input('');
  readonly hint = input('');
  readonly error = input('');
  readonly required = input(false);
  readonly controlId = input('');
  readonly messageId = input('');
}

import {
  ChangeDetectionStrategy,
  Component,
  computed,
  forwardRef,
  inject,
  input,
  model,
  output,
  signal,
} from '@angular/core';
import { type ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { I18nService } from '../core/i18n/i18n.service';
import { FieldShell, nextFieldId } from './field-shell';
import { Icon } from './icon';

@Component({
  selector: 'ui-date-field',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FieldShell, Icon],
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => DateField), multi: true },
  ],
  template: `
    <ui-field-shell
      [label]="label()"
      [hint]="hint()"
      [error]="error()"
      [required]="required()"
      [controlId]="controlId"
      [messageId]="messageId"
    >
      <div
        class="focus-ring flex items-center gap-2 rounded-field border bg-surface px-3"
        [style.height]="'var(--row-h)'"
        [class.border-warn]="!!error()"
        [class.border-line]="!error()"
        [class.bg-surface-2]="isDisabled()"
      >
        <input
          [id]="controlId"
          [type]="withTime() ? 'datetime-local' : 'date'"
          [value]="value()"
          [attr.min]="min() || null"
          [attr.max]="max() || null"
          [attr.aria-label]="label() ? null : t(ariaLabel())"
          [attr.aria-invalid]="error() ? 'true' : null"
          [attr.aria-describedby]="describedBy()"
          [attr.aria-required]="required() ? 'true' : null"
          [disabled]="isDisabled()"
          class="ui-date min-w-0 flex-1 bg-transparent text-[13px] text-ink outline-none"
          (input)="update($any($event.target).value)"
          (blur)="handleBlur()"
        />

        @if (clearable() && value() && !isDisabled()) {
          <button
            type="button"
            class="flex-none text-ink-3"
            [attr.aria-label]="t('ui.field.clear')"
            (click)="update('')"
          >
            <ui-icon name="x" [size]="14" />
          </button>
        }
      </div>
    </ui-field-shell>
  `,
  styles: `
    input::-webkit-calendar-picker-indicator {
      opacity: 0.55;
      cursor: pointer;
    }
  `,
})
export class DateField implements ControlValueAccessor {
  protected readonly t = inject(I18nService).t;

  readonly value = model('');
  readonly label = input('');
  readonly ariaLabel = input('');
  readonly hint = input('');
  readonly error = input('');
  readonly min = input('');
  readonly max = input('');
  readonly withTime = input(false);
  readonly required = input(false);
  readonly disabled = input(false);
  readonly clearable = input(true);

  readonly blurred = output<void>();

  protected readonly controlId = nextFieldId('ui-date');
  protected readonly messageId = `${this.controlId}-message`;

  private readonly controlDisabled = signal(false);
  protected readonly isDisabled = computed(() => this.disabled() || this.controlDisabled());
  protected readonly describedBy = computed(() =>
    this.error() || this.hint() ? this.messageId : null,
  );

  private onChange: (value: string) => void = () => {};
  private onTouched: () => void = () => {};

  writeValue(value: string | null): void {
    this.value.set(value ?? '');
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.controlDisabled.set(isDisabled);
  }

  protected update(next: string): void {
    this.value.set(next);
    this.onChange(next);
  }

  protected handleBlur(): void {
    this.onTouched();
    this.blurred.emit();
  }
}

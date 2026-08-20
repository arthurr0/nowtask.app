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

@Component({
  selector: 'ui-textarea-field',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FieldShell],
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TextareaField), multi: true },
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
        class="focus-ring flex flex-col rounded-field border bg-surface px-3 py-2"
        [class.border-warn]="!!error()"
        [class.border-line]="!error()"
        [class.bg-surface-2]="isDisabled()"
      >
        <textarea
          [id]="controlId"
          [rows]="rows()"
          [value]="value()"
          [attr.placeholder]="placeholder() ? t(placeholder()) : null"
          [attr.maxlength]="maxLength() > 0 ? maxLength() : null"
          [attr.aria-label]="label() ? null : t(ariaLabel())"
          [attr.aria-invalid]="error() ? 'true' : null"
          [attr.aria-describedby]="describedBy()"
          [attr.aria-required]="required() ? 'true' : null"
          [disabled]="isDisabled()"
          [readOnly]="readOnly()"
          class="min-w-0 flex-1 resize-y bg-transparent text-[13px] text-ink outline-none placeholder:text-ink-3"
          (input)="update($any($event.target).value)"
          (blur)="handleBlur()"
        ></textarea>

        @if (maxLength() > 0) {
          <span class="mt-1 self-end font-mono text-[10px] text-ink-3">
            {{ value().length }}/{{ maxLength() }}
          </span>
        }
      </div>
    </ui-field-shell>
  `,
})
export class TextareaField implements ControlValueAccessor {
  protected readonly t = inject(I18nService).t;

  readonly value = model('');
  readonly label = input('');
  readonly ariaLabel = input('');
  readonly placeholder = input('');
  readonly hint = input('');
  readonly error = input('');
  readonly rows = input(4);
  readonly maxLength = input(0);
  readonly required = input(false);
  readonly disabled = input(false);
  readonly readOnly = input(false);

  readonly blurred = output<void>();

  protected readonly controlId = nextFieldId('ui-textarea');
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

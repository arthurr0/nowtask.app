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

export type TextFieldType = 'text' | 'email' | 'password' | 'search' | 'tel' | 'url' | 'number';

@Component({
  selector: 'ui-text-field',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FieldShell, Icon],
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TextField), multi: true },
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
        @if (icon()) {
          <ui-icon [name]="icon()" [size]="15" class="flex-none text-ink-3" />
        }

        <input
          [id]="controlId"
          [type]="type()"
          [value]="value()"
          [attr.placeholder]="placeholder() ? t(placeholder()) : null"
          [attr.autocomplete]="autocomplete() || null"
          [attr.maxlength]="maxLength() > 0 ? maxLength() : null"
          [attr.aria-label]="label() ? null : t(ariaLabel())"
          [attr.aria-invalid]="error() ? 'true' : null"
          [attr.aria-describedby]="describedBy()"
          [attr.aria-required]="required() ? 'true' : null"
          [disabled]="isDisabled()"
          [readOnly]="readOnly()"
          class="min-w-0 flex-1 bg-transparent text-[13px] text-ink outline-none placeholder:text-ink-3"
          (input)="update($any($event.target).value)"
          (blur)="handleBlur()"
          (keydown.enter)="submitted.emit(value())"
        />

        @if (clearable() && value() && !isDisabled() && !readOnly()) {
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
})
export class TextField implements ControlValueAccessor {
  protected readonly t = inject(I18nService).t;

  readonly value = model('');
  readonly label = input('');
  readonly ariaLabel = input('');
  readonly placeholder = input('');
  readonly hint = input('');
  readonly error = input('');
  readonly icon = input('');
  readonly type = input<TextFieldType>('text');
  readonly autocomplete = input('');
  readonly maxLength = input(0);
  readonly required = input(false);
  readonly disabled = input(false);
  readonly readOnly = input(false);
  readonly clearable = input(false);

  readonly blurred = output<void>();
  readonly submitted = output<string>();

  protected readonly controlId = nextFieldId('ui-text');
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

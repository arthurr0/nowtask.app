import {
  ChangeDetectionStrategy,
  Component,
  computed,
  forwardRef,
  input,
  model,
  output,
  signal,
} from '@angular/core';
import { NG_VALUE_ACCESSOR, type ControlValueAccessor } from '@angular/forms';
import { ComboField, type ComboOption, type ComboValue } from './combo-field';

export interface SelectOption {
  value: string;
  label: string;
  disabled?: boolean;
  icon?: string;
  hint?: string;
}

@Component({
  selector: 'ui-select-field',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ComboField],
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => SelectField), multi: true },
  ],
  template: `
    <ui-combo-field
      [value]="value()"
      (valueChange)="pick($event)"
      [options]="comboOptions()"
      [label]="label()"
      [ariaLabel]="ariaLabel()"
      [placeholder]="placeholder()"
      [hint]="hint()"
      [error]="error()"
      [required]="required()"
      [disabled]="isDisabled()"
      [allowClear]="false"
      (blurred)="blurred.emit()"
    />
  `,
})
export class SelectField implements ControlValueAccessor {
  readonly value = model('');
  readonly options = input<readonly SelectOption[]>([]);
  readonly label = input('');
  readonly ariaLabel = input('');
  readonly placeholder = input('ui.select.placeholder');
  readonly hint = input('');
  readonly error = input('');
  readonly required = input(false);
  readonly disabled = input(false);

  readonly blurred = output<void>();

  protected readonly controlDisabled = signal(false);
  protected readonly isDisabled = computed(() => this.disabled() || this.controlDisabled());

  protected readonly comboOptions = computed<ComboOption[]>(() =>
    this.options().map((option) => ({
      value: option.value,
      label: option.label,
      disabled: option.disabled,
      icon: option.icon,
      hint: option.hint,
    })),
  );

  private onChange: (value: string) => void = () => {};
  private onTouched: () => void = () => {};

  protected pick(next: ComboValue): void {
    const value = typeof next === 'string' ? next : '';
    this.value.set(value);
    this.onChange(value);
    this.onTouched();
  }

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
}

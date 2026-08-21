import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

@Component({
  selector: 'ui-switch',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      type="button"
      role="switch"
      [attr.aria-checked]="checked()"
      [attr.aria-label]="label()"
      [disabled]="disabled()"
      class="flex h-5 w-[34px] items-center rounded-full p-0.5 transition-colors disabled:cursor-not-allowed disabled:opacity-40"
      [class.bg-inv]="checked()"
      [class.justify-end]="checked()"
      [class.bg-surface-3]="!checked()"
      [class.justify-start]="!checked()"
      (click)="toggled.emit()"
    >
      <span
        class="h-4 w-4 rounded-full"
        [class.bg-inv-ink]="checked()"
        [class.bg-surface]="!checked()"
      ></span>
    </button>
  `,
})
export class Switch {
  readonly checked = input(false);
  readonly label = input('');
  readonly disabled = input(false);
  readonly toggled = output<void>();
}

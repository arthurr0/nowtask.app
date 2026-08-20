import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { I18nService } from '../core/i18n/i18n.service';
import { Icon } from './icon';

export type ToastKind = 'success' | 'error' | 'info';

export interface ToastAction {
  label: string;
  run: () => void;
}

export interface ToastOptions {
  kind?: ToastKind;
  message: string;
  description?: string;
  duration?: number;
  action?: ToastAction;
}

export interface ToastItem {
  id: number;
  kind: ToastKind;
  message: string;
  description: string;
  action: ToastAction | null;
}

const ICONS: Record<ToastKind, string> = {
  success: 'check',
  error: 'alert',
  info: 'bell',
};

@Component({
  selector: 'ui-toast',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  template: `
    <div
      class="flex w-[min(92vw,380px)] items-start gap-2.5 rounded-card border border-line bg-surface p-3 shadow-lift"
      [attr.role]="item().kind === 'error' ? 'alert' : 'status'"
    >
      <span
        class="flex h-6 w-6 flex-none items-center justify-center rounded-full"
        [class.bg-warn-soft]="item().kind === 'error'"
        [class.text-warn]="item().kind === 'error'"
        [class.bg-surface-3]="item().kind !== 'error'"
        [class.text-done-ink]="item().kind === 'success'"
        [class.text-ink-2]="item().kind === 'info'"
      >
        <ui-icon [name]="icon()" [size]="14" />
      </span>

      <div class="flex min-w-0 flex-1 flex-col gap-0.5">
        <p class="text-[13px] font-medium">{{ t(item().message) }}</p>
        @if (item().description) {
          <p class="text-[12px] text-ink-2">{{ t(item().description) }}</p>
        }
        @if (item().action; as action) {
          <button
            type="button"
            class="mt-1 self-start rounded-[6px] border border-line-strong bg-surface px-2 py-1 text-[12px] font-medium"
            (click)="actionTriggered.emit()"
          >
            {{ t(action.label) }}
          </button>
        }
      </div>

      <button
        type="button"
        class="hoverable -mr-1 -mt-1 flex h-6 w-6 flex-none items-center justify-center rounded-[6px] text-ink-3"
        [attr.aria-label]="t('ui.toast.dismiss')"
        (click)="dismissed.emit()"
      >
        <ui-icon name="x" [size]="14" />
      </button>
    </div>
  `,
  styles: `
    :host {
      display: block;
    }
  `,
})
export class Toast {
  protected readonly t = inject(I18nService).t;

  readonly item = input.required<ToastItem>();

  readonly dismissed = output<void>();
  readonly actionTriggered = output<void>();

  protected icon(): string {
    return ICONS[this.item().kind];
  }
}

@Component({
  selector: 'ui-toast-host',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Toast],
  template: `
    <div
      class="flex flex-col items-end gap-2"
      role="region"
      aria-live="polite"
      [attr.aria-label]="t('ui.toast.region')"
    >
      @for (item of items(); track item.id) {
        <ui-toast
          class="ui-slide-in pointer-events-auto"
          [item]="item"
          (dismissed)="dismissed.emit(item.id)"
          (actionTriggered)="actioned.emit(item.id)"
        />
      }
    </div>
  `,
})
export class ToastHost {
  protected readonly t = inject(I18nService).t;

  readonly items = input<readonly ToastItem[]>([]);

  readonly dismissed = output<number>();
  readonly actioned = output<number>();
}

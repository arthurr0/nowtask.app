import {
  ChangeDetectionStrategy,
  Component,
  Injectable,
  Injector,
  inject,
  signal,
} from '@angular/core';
import { CdkTrapFocus } from '@angular/cdk/a11y';
import {
  type OverlayRef,
  createBlockScrollStrategy,
  createGlobalPositionStrategy,
  createOverlayRef,
} from '@angular/cdk/overlay';
import { ComponentPortal } from '@angular/cdk/portal';
import { I18nService } from '../core/i18n/i18n.service';
import { Icon } from './icon';

export interface ConfirmOptions {
  title?: string;
  message?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  destructive?: boolean;
  icon?: string;
}

interface ResolvedConfirmOptions {
  title: string;
  message: string;
  confirmLabel: string;
  cancelLabel: string;
  destructive: boolean;
  icon: string;
}

const DEFAULTS: ResolvedConfirmOptions = {
  title: 'ui.confirm.title',
  message: 'ui.confirm.message',
  confirmLabel: 'ui.confirm.confirm',
  cancelLabel: 'ui.confirm.cancel',
  destructive: false,
  icon: '',
};

let confirmCounter = 0;

@Component({
  selector: 'ui-confirm-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, CdkTrapFocus],
  template: `
    <div
      cdkTrapFocus
      [cdkTrapFocusAutoCapture]="true"
      role="alertdialog"
      aria-modal="true"
      [attr.aria-labelledby]="titleId"
      [attr.aria-describedby]="messageId"
      class="ui-rise flex w-full flex-col gap-4 rounded-panel border border-line bg-surface p-4 shadow-lift"
    >
      <div class="flex items-start gap-3">
        <span
          class="flex h-9 w-9 flex-none items-center justify-center rounded-full"
          [class.bg-warn-soft]="options().destructive"
          [class.text-warn]="options().destructive"
          [class.bg-surface-3]="!options().destructive"
          [class.text-ink-2]="!options().destructive"
        >
          <ui-icon [name]="iconName()" [size]="18" />
        </span>
        <div class="flex min-w-0 flex-1 flex-col gap-1">
          <h2 [id]="titleId" class="text-[15px] font-medium">{{ t(options().title) }}</h2>
          <p [id]="messageId" class="text-[13px] text-ink-2">{{ t(options().message) }}</p>
        </div>
      </div>

      <div class="flex flex-wrap items-center justify-end gap-2">
        <button
          type="button"
          class="flex items-center rounded-[7px] border border-line-strong bg-surface px-3.5 text-[13px]"
          [style.height]="'calc(var(--row-h) - 8px)'"
          [attr.cdkFocusInitial]="options().destructive ? '' : null"
          (click)="finish(false)"
        >
          {{ t(options().cancelLabel) }}
        </button>
        <button
          type="button"
          class="flex items-center gap-1.5 rounded-[7px] px-3.5 text-[13px] font-medium"
          [style.height]="'calc(var(--row-h) - 8px)'"
          [class.border]="options().destructive"
          [class.border-warn]="options().destructive"
          [class.text-warn]="options().destructive"
          [class.bg-inv]="!options().destructive"
          [class.text-inv-ink]="!options().destructive"
          [attr.cdkFocusInitial]="options().destructive ? null : ''"
          (click)="finish(true)"
        >
          @if (options().destructive) {
            <ui-icon name="trash" [size]="15" />
          }
          {{ t(options().confirmLabel) }}
        </button>
      </div>
    </div>
  `,
})
export class ConfirmDialog {
  protected readonly t = inject(I18nService).t;

  readonly options = signal<ResolvedConfirmOptions>(DEFAULTS);

  finished: (result: boolean) => void = () => {};

  protected readonly titleId = `ui-confirm-title-${++confirmCounter}`;
  protected readonly messageId = `ui-confirm-message-${confirmCounter}`;

  protected iconName(): string {
    const custom = this.options().icon;
    if (custom) return custom;
    return this.options().destructive ? 'alert' : 'check';
  }

  protected finish(result: boolean): void {
    this.finished(result);
  }
}

@Injectable({ providedIn: 'root' })
export class ConfirmService {
  private readonly injector = inject(Injector);
  private readonly i18n = inject(I18nService);

  private overlayRef: OverlayRef | null = null;

  ask(options: ConfirmOptions = {}): Promise<boolean> {
    this.dismiss();

    const overlayRef = createOverlayRef(this.injector, {
      positionStrategy: createGlobalPositionStrategy(this.injector)
        .centerHorizontally()
        .centerVertically(),
      scrollStrategy: createBlockScrollStrategy(this.injector),
      hasBackdrop: true,
      backdropClass: 'ui-scrim',
      width: 'min(92vw, 440px)',
      disposeOnNavigation: true,
    });

    this.overlayRef = overlayRef;

    const componentRef = overlayRef.attach(new ComponentPortal(ConfirmDialog, null, this.injector));
    componentRef.instance.options.set({ ...DEFAULTS, ...options });

    return new Promise<boolean>((resolve) => {
      let settled = false;
      const settle = (result: boolean): void => {
        if (settled) return;
        settled = true;
        this.dismiss();
        resolve(result);
      };

      componentRef.instance.finished = settle;
      overlayRef.backdropClick().subscribe(() => settle(false));
      overlayRef.keydownEvents().subscribe((event) => {
        if (event.key === 'Escape') {
          event.preventDefault();
          settle(false);
        }
      });
    });
  }

  askDelete(name = ''): Promise<boolean> {
    return this.ask({
      title: 'ui.confirm.deleteTitle',
      message: name ? this.i18n.t('ui.confirm.deleteNamed', { name }) : 'ui.confirm.deleteMessage',
      confirmLabel: 'common.delete',
      destructive: true,
    });
  }

  private dismiss(): void {
    if (!this.overlayRef) return;
    this.overlayRef.dispose();
    this.overlayRef = null;
  }
}

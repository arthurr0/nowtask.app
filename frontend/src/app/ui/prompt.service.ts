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

export interface PromptOptions {
  title?: string;
  message?: string;
  label?: string;
  placeholder?: string;
  value?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  multiline?: boolean;
  required?: boolean;
}

interface ResolvedPromptOptions extends Required<Omit<PromptOptions, 'message'>> {
  message: string;
}

const DEFAULTS: ResolvedPromptOptions = {
  title: 'ui.prompt.title',
  message: '',
  label: '',
  placeholder: '',
  value: '',
  confirmLabel: 'common.save',
  cancelLabel: 'common.cancel',
  multiline: false,
  required: true,
};

let promptCounter = 0;

@Component({
  selector: 'ui-prompt-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, CdkTrapFocus],
  template: `
    <form
      cdkTrapFocus
      [cdkTrapFocusAutoCapture]="true"
      role="dialog"
      aria-modal="true"
      [attr.aria-labelledby]="titleId"
      class="ui-rise flex w-full flex-col gap-4 rounded-panel border border-line bg-surface p-4 shadow-lift"
      (submit)="$event.preventDefault(); finish(true)"
    >
      <div class="flex items-start gap-3">
        <div class="flex min-w-0 flex-1 flex-col gap-1">
          <h2 [id]="titleId" class="text-[15px] font-medium">{{ t(options().title) }}</h2>
          @if (options().message) {
            <p class="text-[13px] text-ink-2">{{ t(options().message) }}</p>
          }
        </div>
        <button
          type="button"
          class="hoverable -mr-1 flex h-7 w-7 flex-none items-center justify-center rounded-[6px] text-ink-2"
          [attr.aria-label]="t('common.close')"
          (click)="finish(false)"
        >
          <ui-icon name="x" [size]="16" />
        </button>
      </div>

      <label class="flex flex-col gap-1.5">
        @if (options().label) {
          <span class="kap">{{ t(options().label) }}</span>
        }
        @if (options().multiline) {
          <textarea
            cdkFocusInitial
            class="focus-ring min-h-24 rounded-field border border-line bg-surface px-3 py-2 text-[13px] outline-none"
            [placeholder]="t(options().placeholder)"
            [value]="draft()"
            (input)="draft.set($any($event.target).value)"
          ></textarea>
        } @else {
          <input
            cdkFocusInitial
            class="focus-ring h-9 rounded-field border border-line bg-surface px-3 text-[13px] outline-none"
            [placeholder]="t(options().placeholder)"
            [value]="draft()"
            (input)="draft.set($any($event.target).value)"
          />
        }
      </label>

      <div class="flex items-center justify-end gap-2">
        <button
          type="button"
          class="flex h-9 items-center rounded-[7px] border border-line-strong bg-surface px-3.5 text-[13px]"
          (click)="finish(false)"
        >
          {{ t(options().cancelLabel) }}
        </button>
        <button
          type="submit"
          class="flex h-9 items-center rounded-[7px] bg-inv px-3.5 text-[13px] font-medium text-inv-ink"
          [disabled]="options().required && !draft().trim()"
        >
          {{ t(options().confirmLabel) }}
        </button>
      </div>
    </form>
  `,
})
export class PromptDialog {
  protected readonly t = inject(I18nService).t;

  readonly options = signal<ResolvedPromptOptions>(DEFAULTS);
  readonly draft = signal('');

  finished: (result: string | null) => void = () => {};

  protected readonly titleId = `ui-prompt-title-${++promptCounter}`;

  protected finish(accept: boolean): void {
    if (!accept) {
      this.finished(null);
      return;
    }
    const value = this.draft().trim();
    if (this.options().required && !value) return;
    this.finished(value);
  }
}

@Injectable({ providedIn: 'root' })
export class PromptService {
  private readonly injector = inject(Injector);

  private overlayRef: OverlayRef | null = null;

  ask(options: PromptOptions = {}): Promise<string | null> {
    this.dismiss();

    const overlayRef = createOverlayRef(this.injector, {
      positionStrategy: createGlobalPositionStrategy(this.injector)
        .centerHorizontally()
        .centerVertically(),
      scrollStrategy: createBlockScrollStrategy(this.injector),
      hasBackdrop: true,
      backdropClass: 'ui-scrim',
      width: 'min(92vw, 460px)',
      disposeOnNavigation: true,
    });

    this.overlayRef = overlayRef;

    const componentRef = overlayRef.attach(new ComponentPortal(PromptDialog, null, this.injector));
    componentRef.instance.options.set({ ...DEFAULTS, ...options });
    componentRef.instance.draft.set(options.value ?? '');

    return new Promise<string | null>((resolve) => {
      let settled = false;
      const settle = (result: string | null): void => {
        if (settled) return;
        settled = true;
        this.dismiss();
        resolve(result);
      };

      componentRef.instance.finished = settle;
      overlayRef.backdropClick().subscribe(() => settle(null));
      overlayRef.keydownEvents().subscribe((event) => {
        if (event.key === 'Escape') {
          event.preventDefault();
          settle(null);
        }
      });
    });
  }

  private dismiss(): void {
    if (!this.overlayRef) return;
    this.overlayRef.dispose();
    this.overlayRef = null;
  }
}

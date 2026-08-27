import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  Injector,
  TemplateRef,
  ViewContainerRef,
  computed,
  effect,
  inject,
  input,
  model,
  output,
  untracked,
  viewChild,
} from '@angular/core';
import { CdkTrapFocus } from '@angular/cdk/a11y';
import {
  type OverlayRef,
  createBlockScrollStrategy,
  createGlobalPositionStrategy,
  createOverlayRef,
} from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { I18nService } from '../core/i18n/i18n.service';
import { Icon } from './icon';

export type DialogSize = 'sm' | 'md' | 'lg' | 'xl';

const WIDTHS: Record<DialogSize, string> = {
  sm: 'min(92vw, 420px)',
  md: 'min(92vw, 580px)',
  lg: 'min(94vw, 820px)',
  xl: 'min(96vw, 1180px)',
};

let dialogCounter = 0;

@Component({
  selector: 'ui-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, CdkTrapFocus],
  template: `
    <ng-template #panelTemplate>
      <div
        cdkTrapFocus
        [cdkTrapFocusAutoCapture]="true"
        role="dialog"
        aria-modal="true"
        [attr.aria-labelledby]="showHeader() ? titleId : null"
        [attr.aria-label]="showHeader() ? null : t(title())"
        [attr.aria-describedby]="showHeader() && description() ? descriptionId : null"
        class="ui-rise flex max-h-[86vh] w-full flex-col overflow-hidden rounded-panel border border-line bg-surface shadow-lift"
        [style.height]="fill() ? '86vh' : null"
      >
        @if (showHeader()) {
          <header class="flex flex-none items-start gap-3 border-b border-line px-4 py-3">
            <div class="flex min-w-0 flex-1 flex-col gap-0.5">
              <h2 [id]="titleId" class="truncate text-[15px] font-medium">{{ t(title()) }}</h2>
              @if (description()) {
                <p [id]="descriptionId" class="text-[12px] text-ink-2">{{ t(description()) }}</p>
              }
            </div>
            @if (dismissible()) {
              <button
                type="button"
                class="hoverable -mr-1 flex h-7 w-7 flex-none items-center justify-center rounded-[6px] text-ink-2"
                [attr.aria-label]="t('ui.dialog.close')"
                (click)="requestClose()"
              >
                <ui-icon name="x" [size]="16" />
              </button>
            }
          </header>
        }

        <div [class]="bodyClass()">
          <ng-content />
        </div>

        <footer
          class="flex flex-none flex-wrap items-center justify-end gap-2 border-t border-line bg-surface-2 px-4 py-3"
          [class.hidden]="!showFooter()"
        >
          <ng-content select="[dialogFooter]" />
        </footer>
      </div>
    </ng-template>
  `,
})
export class Dialog {
  private readonly injector = inject(Injector);
  private readonly viewContainerRef = inject(ViewContainerRef);
  protected readonly t = inject(I18nService).t;

  readonly open = model(false);
  readonly title = input('');
  readonly description = input('');
  readonly size = input<DialogSize>('md');
  readonly dismissible = input(true);
  readonly closeOnBackdrop = input(true);
  readonly showFooter = input(true);
  readonly showHeader = input(true);
  readonly flush = input(false);
  readonly fill = input(false);
  readonly closeOnNavigation = input(true);

  readonly closed = output<void>();

  private readonly panelTemplate = viewChild.required<TemplateRef<unknown>>('panelTemplate');

  protected readonly titleId = `ui-dialog-title-${++dialogCounter}`;
  protected readonly descriptionId = `ui-dialog-desc-${dialogCounter}`;

  private readonly width = computed(() => WIDTHS[this.size()]);

  protected readonly bodyClass = computed(() =>
    this.flush()
      ? 'flex min-h-0 flex-1 flex-col overflow-hidden'
      : 'min-h-0 flex-1 overflow-y-auto px-4 py-3.5 text-[13px] scroll-thin',
  );

  private overlayRef: OverlayRef | null = null;

  constructor() {
    effect(() => {
      const shouldOpen = this.open();
      untracked(() => (shouldOpen ? this.attach() : this.detach()));
    });
    inject(DestroyRef).onDestroy(() => this.detach());
  }

  protected requestClose(): void {
    this.open.set(false);
    this.closed.emit();
  }

  private attach(): void {
    if (this.overlayRef) return;

    const overlayRef = createOverlayRef(this.injector, {
      positionStrategy: createGlobalPositionStrategy(this.injector)
        .centerHorizontally()
        .centerVertically(),
      scrollStrategy: createBlockScrollStrategy(this.injector),
      hasBackdrop: true,
      backdropClass: 'ui-scrim',
      width: this.width(),
      disposeOnNavigation: this.closeOnNavigation(),
    });

    overlayRef.attach(new TemplatePortal(this.panelTemplate(), this.viewContainerRef));

    overlayRef.keydownEvents().subscribe((event) => {
      if (event.key === 'Escape' && this.dismissible()) {
        event.preventDefault();
        this.requestClose();
      }
    });

    overlayRef.backdropClick().subscribe(() => {
      if (this.closeOnBackdrop() && this.dismissible()) this.requestClose();
    });

    this.overlayRef = overlayRef;
  }

  private detach(): void {
    if (!this.overlayRef) return;
    this.overlayRef.dispose();
    this.overlayRef = null;
  }
}

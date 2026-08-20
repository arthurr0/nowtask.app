import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  TemplateRef,
  ViewContainerRef,
  computed,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import {
  type ConnectedPosition,
  type OverlayRef,
  createFlexibleConnectedPositionStrategy,
  createOverlayRef,
  createRepositionScrollStrategy,
} from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { I18nService } from '../core/i18n/i18n.service';
import { Icon } from './icon';

export interface MenuItem {
  id: string;
  label: string;
  icon?: string;
  shortcut?: string;
  checked?: boolean;
  disabled?: boolean;
  danger?: boolean;
  separatorBefore?: boolean;
  keepOpen?: boolean;
}

let menuCounter = 0;

const BELOW_START: ConnectedPosition[] = [
  { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top', offsetY: 6 },
  { originX: 'start', originY: 'top', overlayX: 'start', overlayY: 'bottom', offsetY: -6 },
  { originX: 'end', originY: 'bottom', overlayX: 'end', overlayY: 'top', offsetY: 6 },
  { originX: 'end', originY: 'top', overlayX: 'end', overlayY: 'bottom', offsetY: -6 },
];

const BELOW_END: ConnectedPosition[] = [
  { originX: 'end', originY: 'bottom', overlayX: 'end', overlayY: 'top', offsetY: 6 },
  { originX: 'end', originY: 'top', overlayX: 'end', overlayY: 'bottom', offsetY: -6 },
  { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top', offsetY: 6 },
  { originX: 'start', originY: 'top', overlayX: 'start', overlayY: 'bottom', offsetY: -6 },
];

@Component({
  selector: 'ui-menu',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  template: `
    <button
      #trigger
      type="button"
      [class]="triggerClass()"
      [style.min-height]="triggerHeight()"
      [disabled]="disabled()"
      [attr.aria-haspopup]="'menu'"
      [attr.aria-expanded]="isOpen()"
      [attr.aria-controls]="isOpen() ? panelId : null"
      [attr.aria-label]="triggerLabel() || !ariaLabel() ? null : t(ariaLabel())"
      (click)="toggle()"
      (keydown)="onTriggerKeydown($event)"
    >
      @if (triggerIcon()) {
        <ui-icon [name]="triggerIcon()" [size]="15" />
      }
      @if (triggerLabel()) {
        <span class="truncate">{{ t(triggerLabel()) }}</span>
      }
      <ng-content />
      @if (chevron()) {
        <ui-icon name="chevron-down" [size]="14" class="text-ink-3" />
      }
    </button>

    <ng-template #panelTemplate>
      <div
        data-ui-menu-panel
        tabindex="-1"
        role="menu"
        [id]="panelId"
        [attr.aria-label]="t(triggerLabel() || ariaLabel())"
        [attr.aria-activedescendant]="activeDescendant()"
        class="ui-rise flex max-h-[min(420px,68vh)] flex-col overflow-y-auto rounded-card border border-line bg-surface p-1 shadow-lift outline-none scroll-thin"
        (keydown)="onPanelKeydown($event)"
      >
        @for (item of items(); track item.id; let i = $index) {
          @if (item.separatorBefore && i > 0) {
            <div role="separator" class="my-1 h-px flex-none bg-line"></div>
          }
          <button
            type="button"
            [id]="panelId + '-' + i"
            [attr.role]="item.checked === undefined ? 'menuitem' : 'menuitemcheckbox'"
            [attr.aria-checked]="item.checked === undefined ? null : item.checked"
            [attr.aria-disabled]="item.disabled ? 'true' : null"
            [disabled]="item.disabled"
            class="flex flex-none items-center gap-2.5 rounded-[6px] px-2.5 py-1.5 text-left text-[13px]"
            [style.min-height]="'calc(var(--row-h) - 12px)'"
            [class.bg-surface-2]="activeIndex() === i && !item.disabled"
            [class.text-warn]="item.danger && !item.disabled"
            [class.text-ink-3]="item.disabled"
            (click)="choose(item)"
            (mouseenter)="activeIndex.set(i)"
          >
            @if (item.icon) {
              <ui-icon
                [name]="item.icon"
                [size]="15"
                class="flex-none"
                [class.text-ink-3]="!item.danger"
              />
            }
            <span class="flex-1 truncate">{{ t(item.label) }}</span>
            @if (item.shortcut) {
              <span class="kap flex-none">{{ item.shortcut }}</span>
            }
            @if (item.checked) {
              <ui-icon name="check" [size]="15" class="flex-none" />
            }
          </button>
        }

        @if (items().length === 0) {
          <p class="px-2.5 py-2 text-[12px] text-ink-3">{{ t('ui.menu.empty') }}</p>
        }
      </div>
    </ng-template>
  `,
  styles: `
    :host {
      display: inline-flex;
    }
  `,
})
export class Menu {
  private readonly injector = inject(Injector);
  private readonly viewContainerRef = inject(ViewContainerRef);
  protected readonly t = inject(I18nService).t;

  readonly items = input.required<readonly MenuItem[]>();
  readonly triggerClass = input(
    'flex items-center justify-center gap-1.5 rounded-[7px] border border-line bg-surface-2 px-2.5 text-[13px] text-ink-2',
  );
  readonly triggerHeight = input('var(--nav-h)');
  readonly triggerIcon = input('');
  readonly triggerLabel = input('');
  readonly chevron = input(false);
  readonly ariaLabel = input('common.more');
  readonly align = input<'start' | 'end'>('start');
  readonly minWidth = input(200);
  readonly disabled = input(false);

  readonly selected = output<MenuItem>();
  readonly openChange = output<boolean>();

  private readonly trigger = viewChild.required<ElementRef<HTMLButtonElement>>('trigger');
  private readonly panelTemplate = viewChild.required<TemplateRef<unknown>>('panelTemplate');

  protected readonly panelId = `ui-menu-${++menuCounter}`;
  protected readonly isOpen = signal(false);
  protected readonly activeIndex = signal(-1);

  protected readonly activeDescendant = computed(() => {
    const index = this.activeIndex();
    return this.isOpen() && index >= 0 ? `${this.panelId}-${index}` : null;
  });

  private readonly selectableIndexes = computed(() =>
    this.items()
      .map((item, index) => (item.disabled ? -1 : index))
      .filter((index) => index >= 0),
  );

  private overlayRef: OverlayRef | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.detach());
  }

  open(): void {
    this.attach(false);
  }

  close(): void {
    this.detach();
  }

  protected toggle(): void {
    if (this.isOpen()) {
      this.detach();
      return;
    }
    this.attach(false);
  }

  protected onTriggerKeydown(event: KeyboardEvent): void {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      if (this.isOpen()) {
        this.moveActive(event.key === 'ArrowDown' ? 1 : -1);
      } else {
        this.attach(event.key === 'ArrowUp');
      }
    }
  }

  protected onPanelKeydown(event: KeyboardEvent): void {
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.moveActive(1);
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.moveActive(-1);
        break;
      case 'Home':
        event.preventDefault();
        this.setFirstActive(false);
        break;
      case 'End':
        event.preventDefault();
        this.setFirstActive(true);
        break;
      case 'Enter':
      case ' ':
        event.preventDefault();
        this.chooseActive();
        break;
      case 'Tab':
        this.detach(true);
        break;
      default:
        break;
    }
  }

  protected choose(item: MenuItem): void {
    if (item.disabled) return;
    this.selected.emit(item);
    if (!item.keepOpen) this.detach(true);
  }

  private chooseActive(): void {
    const item = this.items()[this.activeIndex()];
    if (item) this.choose(item);
  }

  private attach(startAtEnd: boolean): void {
    if (this.disabled() || this.overlayRef) return;

    const overlayRef = createOverlayRef(this.injector, {
      positionStrategy: createFlexibleConnectedPositionStrategy(
        this.injector,
        this.trigger().nativeElement,
      )
        .withPositions(this.align() === 'end' ? BELOW_END : BELOW_START)
        .withFlexibleDimensions(false)
        .withPush(true),
      scrollStrategy: createRepositionScrollStrategy(this.injector),
      hasBackdrop: false,
      minWidth: this.minWidth(),
      disposeOnNavigation: true,
    });

    overlayRef.attach(new TemplatePortal(this.panelTemplate(), this.viewContainerRef));
    overlayRef.outsidePointerEvents().subscribe((event) => this.onOutsidePointer(event));
    overlayRef.keydownEvents().subscribe((event) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        this.detach(true);
      }
    });

    this.overlayRef = overlayRef;
    this.isOpen.set(true);
    this.openChange.emit(true);
    this.setFirstActive(startAtEnd);

    const panel = overlayRef.overlayElement.querySelector<HTMLElement>('[data-ui-menu-panel]');
    panel?.focus();
  }

  private detach(focusTrigger = false): void {
    if (!this.overlayRef) return;
    this.overlayRef.dispose();
    this.overlayRef = null;
    this.activeIndex.set(-1);
    this.isOpen.set(false);
    this.openChange.emit(false);
    if (focusTrigger) this.trigger().nativeElement.focus();
  }

  private onOutsidePointer(event: MouseEvent): void {
    const target = event.target as Node | null;
    if (target && this.trigger().nativeElement.contains(target)) return;
    this.detach();
  }

  private setFirstActive(fromEnd: boolean): void {
    const indexes = this.selectableIndexes();
    if (indexes.length === 0) {
      this.activeIndex.set(-1);
      return;
    }
    this.activeIndex.set(fromEnd ? indexes[indexes.length - 1] : indexes[0]);
  }

  private moveActive(step: number): void {
    const indexes = this.selectableIndexes();
    if (indexes.length === 0) return;
    const current = indexes.indexOf(this.activeIndex());
    const next =
      current === -1
        ? step > 0
          ? 0
          : indexes.length - 1
        : (current + step + indexes.length) % indexes.length;
    this.activeIndex.set(indexes[next]);
  }
}

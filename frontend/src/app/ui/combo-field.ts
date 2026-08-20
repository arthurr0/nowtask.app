import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  TemplateRef,
  ViewContainerRef,
  computed,
  forwardRef,
  inject,
  input,
  model,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { type ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import {
  type ConnectedPosition,
  type OverlayRef,
  createFlexibleConnectedPositionStrategy,
  createOverlayRef,
  createRepositionScrollStrategy,
} from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import { I18nService } from '../core/i18n/i18n.service';
import { FieldShell, nextFieldId } from './field-shell';
import { Icon } from './icon';

export interface ComboOption {
  value: string;
  label: string;
  hint?: string;
  icon?: string;
  disabled?: boolean;
}

export type ComboValue = string | readonly string[] | null;

const POSITIONS: ConnectedPosition[] = [
  { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top', offsetY: 4 },
  { originX: 'start', originY: 'top', overlayX: 'start', overlayY: 'bottom', offsetY: -4 },
];

@Component({
  selector: 'ui-combo-field',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FieldShell, Icon],
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => ComboField), multi: true },
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
        class="focus-ring flex items-center rounded-field border bg-surface pr-2"
        [style.height]="'var(--row-h)'"
        [class.border-warn]="!!error()"
        [class.border-line]="!error()"
        [class.bg-surface-2]="isDisabled()"
      >
        <button
          #trigger
          type="button"
          [id]="controlId"
          class="flex h-full min-w-0 flex-1 items-center gap-2 px-3 text-left text-[13px] outline-none"
          [attr.aria-haspopup]="'listbox'"
          [attr.aria-expanded]="isOpen()"
          [attr.aria-controls]="isOpen() ? listboxId : null"
          [attr.aria-activedescendant]="isOpen() && !showSearch() ? activeDescendant() : null"
          [attr.aria-label]="label() ? null : t(ariaLabel())"
          [attr.aria-invalid]="error() ? 'true' : null"
          [attr.aria-describedby]="describedBy()"
          [attr.aria-required]="required() ? 'true' : null"
          [disabled]="isDisabled()"
          (click)="toggle()"
          (keydown)="onTriggerKeydown($event)"
        >
          @if (selectedOptions().length === 0) {
            <span class="truncate text-ink-3">{{ t(placeholder()) }}</span>
          } @else {
            @if (selectedOptions()[0].icon; as optionIcon) {
              <ui-icon [name]="optionIcon" [size]="15" class="flex-none text-ink-3" />
            }
            <span class="truncate">{{ t(selectedOptions()[0].label) }}</span>
            @if (selectedOptions().length > 1) {
              <span class="flex-none rounded-full bg-surface-3 px-1.5 text-[11px] text-ink-2">
                +{{ selectedOptions().length - 1 }}
              </span>
            }
          }
        </button>

        @if (allowClear() && selectedOptions().length > 0 && !isDisabled()) {
          <button
            type="button"
            class="flex-none text-ink-3"
            [attr.aria-label]="t('ui.field.clear')"
            (click)="clear()"
          >
            <ui-icon name="x" [size]="14" />
          </button>
        }

        <ui-icon name="chevron-down" [size]="15" class="ml-1 flex-none text-ink-3" />
      </div>
    </ui-field-shell>

    <ng-template #panelTemplate>
      <div
        class="ui-rise flex flex-col overflow-hidden rounded-card border border-line bg-surface shadow-lift"
      >
        @if (showSearch()) {
          <div
            class="flex flex-none items-center gap-2 border-b border-line px-2.5"
            [style.height]="'var(--row-h)'"
          >
            <ui-icon name="search" [size]="15" class="flex-none text-ink-3" />
            <input
              #search
              type="text"
              role="combobox"
              autocomplete="off"
              [value]="query()"
              [attr.placeholder]="t(searchPlaceholder())"
              [attr.aria-label]="t(searchPlaceholder())"
              [attr.aria-expanded]="true"
              [attr.aria-controls]="listboxId"
              [attr.aria-activedescendant]="activeDescendant()"
              class="min-w-0 flex-1 bg-transparent text-[13px] text-ink outline-none placeholder:text-ink-3"
              (input)="onQuery($any($event.target).value)"
              (keydown)="onSearchKeydown($event)"
            />
          </div>
        }

        <div
          role="listbox"
          [id]="listboxId"
          [attr.aria-multiselectable]="multiple() ? 'true' : null"
          class="max-h-[min(320px,48vh)] overflow-y-auto p-1 scroll-thin"
        >
          @for (option of filtered(); track option.value; let i = $index) {
            <div
              [id]="listboxId + '-' + i"
              role="option"
              [attr.aria-selected]="isSelected(option.value)"
              [attr.aria-disabled]="option.disabled ? 'true' : null"
              class="flex cursor-pointer items-center gap-2.5 rounded-[6px] px-2.5 py-1.5 text-[13px]"
              [style.min-height]="'calc(var(--row-h) - 12px)'"
              [class.bg-surface-2]="activeIndex() === i"
              [class.text-ink-3]="option.disabled"
              (click)="choose(option)"
              (mouseenter)="activeIndex.set(i)"
            >
              @if (option.icon) {
                <ui-icon [name]="option.icon" [size]="15" class="flex-none text-ink-3" />
              }
              <span class="flex-1 truncate">{{ t(option.label) }}</span>
              @if (option.hint) {
                <span class="flex-none text-[11px] text-ink-3">{{ t(option.hint) }}</span>
              }
              @if (isSelected(option.value)) {
                <ui-icon name="check" [size]="15" class="flex-none" />
              }
            </div>
          } @empty {
            <p class="px-2.5 py-3 text-center text-[12px] text-ink-3">{{ t(emptyText()) }}</p>
          }
        </div>
      </div>
    </ng-template>
  `,
})
export class ComboField implements ControlValueAccessor {
  private readonly injector = inject(Injector);
  private readonly viewContainerRef = inject(ViewContainerRef);
  protected readonly t = inject(I18nService).t;

  readonly value = model<ComboValue>(null);
  readonly options = input<readonly ComboOption[]>([]);
  readonly label = input('');
  readonly ariaLabel = input('');
  readonly placeholder = input('ui.select.placeholder');
  readonly searchPlaceholder = input('ui.combo.search');
  readonly searchFrom = input(8);
  readonly emptyText = input('ui.combo.empty');
  readonly hint = input('');
  readonly error = input('');
  readonly multiple = input(false);
  readonly allowClear = input(true);
  readonly required = input(false);
  readonly disabled = input(false);

  protected readonly showSearch = computed(() => this.options().length >= this.searchFrom());

  readonly blurred = output<void>();
  readonly openChange = output<boolean>();

  private readonly trigger = viewChild.required<ElementRef<HTMLButtonElement>>('trigger');
  private readonly panelTemplate = viewChild.required<TemplateRef<unknown>>('panelTemplate');

  protected readonly controlId = nextFieldId('ui-combo');
  protected readonly messageId = `${this.controlId}-message`;
  protected readonly listboxId = `${this.controlId}-listbox`;

  protected readonly isOpen = signal(false);
  protected readonly query = signal('');
  protected readonly activeIndex = signal(-1);

  private readonly controlDisabled = signal(false);
  protected readonly isDisabled = computed(() => this.disabled() || this.controlDisabled());
  protected readonly describedBy = computed(() =>
    this.error() || this.hint() ? this.messageId : null,
  );

  private readonly selectedValues = computed<readonly string[]>(() => {
    const current = this.value();
    if (current === null || current === undefined) return [];
    return Array.isArray(current) ? current : [current as string];
  });

  protected readonly selectedOptions = computed(() =>
    this.options().filter((option) => this.selectedValues().includes(option.value)),
  );

  protected readonly filtered = computed(() => {
    const needle = this.query().trim().toLowerCase();
    if (!needle) return this.options();
    return this.options().filter((option) => {
      const label = this.t(option.label).toLowerCase();
      return label.includes(needle) || option.value.toLowerCase().includes(needle);
    });
  });

  protected readonly activeDescendant = computed(() => {
    const index = this.activeIndex();
    return index >= 0 ? `${this.listboxId}-${index}` : null;
  });

  private overlayRef: OverlayRef | null = null;

  private onChange: (value: ComboValue) => void = () => {};
  private onTouched: () => void = () => {};

  constructor() {
    inject(DestroyRef).onDestroy(() => this.detach());
  }

  writeValue(value: ComboValue): void {
    this.value.set(value ?? (this.multiple() ? [] : null));
  }

  registerOnChange(fn: (value: ComboValue) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.controlDisabled.set(isDisabled);
  }

  protected isSelected(optionValue: string): boolean {
    return this.selectedValues().includes(optionValue);
  }

  protected toggle(): void {
    if (this.isOpen()) {
      this.detach(true);
      return;
    }
    this.attach();
  }

  protected clear(): void {
    this.commit(this.multiple() ? [] : null);
  }

  protected onQuery(next: string): void {
    this.query.set(next);
    this.activeIndex.set(this.filtered().length > 0 ? 0 : -1);
  }

  protected onTriggerKeydown(event: KeyboardEvent): void {
    if (this.isOpen() && !this.showSearch()) {
      this.onSearchKeydown(event);
      return;
    }
    if (event.key === 'ArrowDown' || event.key === 'Enter' || event.key === ' ') {
      if (this.isOpen()) return;
      event.preventDefault();
      this.attach();
    }
  }

  protected onSearchKeydown(event: KeyboardEvent): void {
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
        this.activeIndex.set(this.filtered().length > 0 ? 0 : -1);
        break;
      case 'End':
        event.preventDefault();
        this.activeIndex.set(this.filtered().length - 1);
        break;
      case 'Enter': {
        event.preventDefault();
        const option = this.filtered()[this.activeIndex()];
        if (option) this.choose(option);
        break;
      }
      case 'Tab':
        this.detach(true);
        break;
      default:
        break;
    }
  }

  protected choose(option: ComboOption): void {
    if (option.disabled) return;

    if (!this.multiple()) {
      this.commit(option.value);
      this.detach(true);
      return;
    }

    const current = this.selectedValues();
    const next = current.includes(option.value)
      ? current.filter((entry) => entry !== option.value)
      : [...current, option.value];
    this.commit(next);
  }

  private commit(next: ComboValue): void {
    this.value.set(next);
    this.onChange(next);
    this.onTouched();
  }

  private moveActive(step: number): void {
    const options = this.filtered();
    if (options.length === 0) return;
    const current = this.activeIndex();
    const next =
      current === -1
        ? step > 0
          ? 0
          : options.length - 1
        : (current + step + options.length) % options.length;
    this.activeIndex.set(next);
  }

  private attach(): void {
    if (this.isDisabled() || this.overlayRef) return;

    const triggerElement = this.trigger().nativeElement;
    const fieldElement = triggerElement.parentElement ?? triggerElement;
    const overlayRef = createOverlayRef(this.injector, {
      positionStrategy: createFlexibleConnectedPositionStrategy(this.injector, fieldElement)
        .withPositions(POSITIONS)
        .withFlexibleDimensions(false)
        .withPush(true),
      scrollStrategy: createRepositionScrollStrategy(this.injector),
      hasBackdrop: false,
      width: Math.max(fieldElement.getBoundingClientRect().width, 220),
      disposeOnNavigation: true,
    });

    overlayRef.attach(new TemplatePortal(this.panelTemplate(), this.viewContainerRef));
    overlayRef.outsidePointerEvents().subscribe((event) => {
      const target = event.target as Node | null;
      if (target && fieldElement.contains(target)) return;
      this.detach();
    });
    overlayRef.keydownEvents().subscribe((event) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        this.detach(true);
      }
    });

    this.overlayRef = overlayRef;
    this.query.set('');
    this.activeIndex.set(this.filtered().length > 0 ? 0 : -1);
    this.isOpen.set(true);
    this.openChange.emit(true);

    overlayRef.overlayElement.querySelector<HTMLInputElement>('input')?.focus();
  }

  private detach(focusTrigger = false): void {
    if (!this.overlayRef) return;
    this.overlayRef.dispose();
    this.overlayRef = null;
    this.isOpen.set(false);
    this.activeIndex.set(-1);
    this.openChange.emit(false);
    this.onTouched();
    this.blurred.emit();
    if (focusTrigger) this.trigger().nativeElement.focus();
  }
}

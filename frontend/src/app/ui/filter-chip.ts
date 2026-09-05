import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  TemplateRef,
  ViewContainerRef,
  afterNextRender,
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
import type { FilterConditionDto, FilterNodeDto } from '../core/api-types';
import { I18nService } from '../core/i18n/i18n.service';
import { isGroup } from '../core/task-filter';
import { FilterFields } from '../data/filter-fields';
import { ConditionEditor } from './condition-editor';
import { Icon } from './icon';

const POSITIONS: ConnectedPosition[] = [
  { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top', offsetY: 6 },
  { originX: 'start', originY: 'top', overlayX: 'start', overlayY: 'bottom', offsetY: -6 },
];

@Component({
  selector: 'ui-filter-chip',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, ConditionEditor],
  template: `
    <span
      class="flex h-7 max-w-72 flex-none items-center gap-1 rounded-full border border-ink bg-inv pr-1 pl-2.5 text-xs font-medium text-inv-ink whitespace-nowrap"
    >
      <button
        #trigger
        type="button"
        class="flex min-w-0 items-center gap-1.5"
        [attr.aria-label]="t('filters.editCondition')"
        [attr.aria-expanded]="isOpen()"
        (click)="toggle()"
      >
        @if (group()) {
          <ui-icon name="layers" [size]="12" />
        }
        <span class="truncate">{{ text() }}</span>
      </button>
      <button
        type="button"
        class="flex h-5 w-5 flex-none items-center justify-center rounded-full opacity-70 hover:opacity-100"
        [attr.aria-label]="t('filters.remove')"
        (click)="removed.emit()"
      >
        <ui-icon name="x" [size]="11" />
      </button>
    </span>

    <ng-template #panelTemplate>
      <div
        class="ui-rise flex flex-col gap-2 rounded-card border border-line bg-surface p-3 shadow-lift"
        (keydown.escape)="close()"
      >
        @if (condition(); as condition) {
          <span class="text-[11px] font-medium text-ink-3">{{
            catalog.label(condition.field)
          }}</span>
          <ui-condition-editor
            [node]="condition"
            [removable]="false"
            (changed)="changed.emit($event)"
          />
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
export class FilterChip {
  private readonly injector = inject(Injector);
  private readonly viewContainerRef = inject(ViewContainerRef);
  protected readonly catalog = inject(FilterFields);
  protected readonly t = inject(I18nService).t;

  readonly node = input.required<FilterNodeDto>();
  readonly autoOpen = input(false);
  readonly changed = output<FilterConditionDto>();
  readonly opened = output<void>();
  readonly removed = output<void>();
  readonly openAdvanced = output<void>();

  private readonly trigger = viewChild.required<ElementRef<HTMLButtonElement>>('trigger');
  private readonly panelTemplate = viewChild.required<TemplateRef<unknown>>('panelTemplate');

  protected readonly isOpen = signal(false);
  private overlayRef: OverlayRef | null = null;

  protected readonly group = computed(() => isGroup(this.node()));
  protected readonly condition = computed<FilterConditionDto | null>(() => {
    const node = this.node();
    return isGroup(node) ? null : node;
  });
  protected readonly text = computed(() => {
    const node = this.node();
    return isGroup(node) ? this.catalog.groupSummary(node) : this.catalog.describe(node);
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => this.close());
    afterNextRender(() => {
      if (this.autoOpen() && !this.group()) {
        this.toggle();
        this.opened.emit();
      }
    });
  }

  protected toggle(): void {
    if (this.group()) {
      this.openAdvanced.emit();
      return;
    }
    if (this.overlayRef) {
      this.close();
      return;
    }
    const overlayRef = createOverlayRef(this.injector, {
      positionStrategy: createFlexibleConnectedPositionStrategy(
        this.injector,
        this.trigger().nativeElement,
      )
        .withPositions(POSITIONS)
        .withFlexibleDimensions(false)
        .withPush(true),
      scrollStrategy: createRepositionScrollStrategy(this.injector),
      hasBackdrop: false,
      minWidth: 320,
      disposeOnNavigation: true,
    });
    overlayRef.attach(new TemplatePortal(this.panelTemplate(), this.viewContainerRef));
    overlayRef.outsidePointerEvents().subscribe((event) => {
      const target = event.target as Node | null;
      if (target && this.trigger().nativeElement.contains(target)) return;
      if (target && document.querySelector('.cdk-overlay-container')?.contains(target)) {
        if (!overlayRef.overlayElement.contains(target)) return;
      }
      this.close();
    });
    overlayRef.keydownEvents().subscribe((event) => {
      if (event.key === 'Escape') this.close();
    });
    this.overlayRef = overlayRef;
    this.isOpen.set(true);
  }

  close(): void {
    this.overlayRef?.dispose();
    this.overlayRef = null;
    this.isOpen.set(false);
  }
}

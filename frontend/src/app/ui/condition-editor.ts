import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import type { FilterConditionDto } from '../core/api-types';
import { I18nService } from '../core/i18n/i18n.service';
import {
  RELATIVE_DATES,
  condition,
  defaultOp,
  isMultiValue,
  isRelativeDate,
  needsValues,
  opsFor,
  type FilterFieldMeta,
} from '../core/task-filter';
import { FilterFields } from '../data/filter-fields';
import { Icon } from './icon';
import { Menu, type MenuItem } from './menu';

const BOX =
  'flex h-8 items-center gap-1.5 rounded-field border border-line bg-surface-2 px-2.5 text-xs text-ink-2 whitespace-nowrap';
const INPUT =
  'focus-ring h-8 min-w-0 rounded-field border border-line bg-surface px-2.5 text-xs outline-none';

@Component({
  selector: 'ui-condition-editor',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, Menu, NgTemplateOutlet],
  template: `
    <div class="flex flex-wrap items-center gap-2">
      @if (showField()) {
        <ui-menu
          [items]="fieldItems()"
          [triggerClass]="box"
          triggerHeight="32px"
          ariaLabel="filters.pickField"
          [minWidth]="220"
          (selected)="setField($event.id)"
        >
          @if (meta(); as meta) {
            <ui-icon [name]="meta.icon" [size]="13" class="text-ink-3" />
            <span>{{ t(meta.label) }}</span>
          } @else {
            <span class="text-ink-3">{{ t('filters.pickField') }}</span>
          }
          <ui-icon name="chevron-down" [size]="12" class="text-ink-3" />
        </ui-menu>
      }

      @if (meta(); as meta) {
        <ui-menu
          [items]="opItems()"
          [triggerClass]="box"
          triggerHeight="32px"
          ariaLabel="filters.editCondition"
          [minWidth]="180"
          (selected)="setOp($event.id)"
        >
          <span>{{ t('filters.op.' + node().op) }}</span>
          <ui-icon name="chevron-down" [size]="12" class="text-ink-3" />
        </ui-menu>

        @if (needsValues(node().op)) {
          @switch (meta.kind) {
            @case ('boolean') {
              <ui-menu
                [items]="boolItems()"
                [triggerClass]="box"
                triggerHeight="32px"
                ariaLabel="filters.valuePlaceholder"
                [minWidth]="120"
                (selected)="setValues([$event.id])"
              >
                <span>{{ t(value(0) === 'false' ? 'filters.false' : 'filters.true') }}</span>
                <ui-icon name="chevron-down" [size]="12" class="text-ink-3" />
              </ui-menu>
            }
            @case ('date') {
              <span class="text-xs text-ink-3">{{ multi() ? t('filters.from') : '' }}</span>
              <ng-container *ngTemplateOutlet="datePicker; context: { $implicit: 0 }" />
              @if (multi()) {
                <span class="text-xs text-ink-3">{{ t('filters.to') }}</span>
                <ng-container *ngTemplateOutlet="datePicker; context: { $implicit: 1 }" />
              }
            }
            @case ('number') {
              <input
                [class]="input"
                class="w-24"
                type="number"
                [value]="value(0)"
                [placeholder]="t('filters.valuePlaceholder')"
                (input)="setValue(0, $any($event.target).value)"
              />
              @if (multi()) {
                <span class="text-xs text-ink-3">{{ t('filters.to') }}</span>
                <input
                  [class]="input"
                  class="w-24"
                  type="number"
                  [value]="value(1)"
                  [placeholder]="t('filters.valuePlaceholder')"
                  (input)="setValue(1, $any($event.target).value)"
                />
              }
            }
            @case ('text') {
              <input
                [class]="input"
                class="w-44 flex-1"
                type="text"
                [value]="value(0)"
                [placeholder]="t('filters.valuePlaceholder')"
                (input)="setValue(0, $any($event.target).value)"
              />
            }
            @default {
              @if (options().length) {
                <ui-menu
                  [items]="optionItems()"
                  [triggerClass]="box"
                  triggerHeight="32px"
                  ariaLabel="filters.valuePlaceholder"
                  [minWidth]="220"
                  (selected)="toggleOption($event.id)"
                >
                  <span class="max-w-56 truncate">{{ valueSummary() }}</span>
                  <ui-icon name="chevron-down" [size]="12" class="text-ink-3" />
                </ui-menu>
              } @else {
                <input
                  [class]="input"
                  class="w-44 flex-1"
                  type="text"
                  [value]="node().values.join(', ')"
                  [placeholder]="t('filters.valuesPlaceholder')"
                  (input)="setList($any($event.target).value)"
                />
              }
            }
          }
        }
      }

      @if (removable()) {
        <button
          type="button"
          class="hoverable flex h-7 w-7 flex-none items-center justify-center rounded-[6px] text-ink-3"
          [attr.aria-label]="t('filters.remove')"
          (click)="removed.emit()"
        >
          <ui-icon name="x" [size]="14" />
        </button>
      }
    </div>

    <ng-template #datePicker let-index>
      <ui-menu
        [items]="dateItems(index)"
        [triggerClass]="box"
        triggerHeight="32px"
        ariaLabel="filters.date.relative"
        [minWidth]="180"
        (selected)="pickDate(index, $event.id)"
      >
        <ui-icon name="clock" [size]="13" class="text-ink-3" />
        <span>{{ dateLabel(index) }}</span>
        <ui-icon name="chevron-down" [size]="12" class="text-ink-3" />
      </ui-menu>
      @if (!isRelative(value(index))) {
        <input
          [class]="input"
          type="date"
          [value]="value(index)"
          (input)="setValue(index, $any($event.target).value)"
        />
      }
    </ng-template>
  `,
})
export class ConditionEditor {
  private readonly catalog = inject(FilterFields);
  protected readonly t = inject(I18nService).t;

  readonly node = input.required<FilterConditionDto>();
  readonly showField = input(false);
  readonly removable = input(true);
  readonly changed = output<FilterConditionDto>();
  readonly removed = output<void>();

  protected readonly box = BOX;
  protected readonly input = INPUT;
  protected readonly needsValues = needsValues;
  protected readonly isRelative = isRelativeDate;

  protected readonly meta = computed<FilterFieldMeta | null>(() =>
    this.catalog.meta(this.node().field),
  );

  protected readonly multi = computed(() => {
    const meta = this.meta();
    return meta ? isMultiValue(meta, this.node().op) : false;
  });

  protected readonly options = computed(() => {
    const meta = this.meta();
    return meta ? this.catalog.options(meta) : [];
  });

  protected readonly fieldItems = computed<MenuItem[]>(() => {
    const items: MenuItem[] = [];
    const current = this.node().field;
    for (const meta of this.catalog.systemFields()) {
      items.push({
        id: meta.key,
        label: meta.label,
        icon: meta.icon,
        checked: meta.key === current,
      });
    }
    const custom = this.catalog.customFields();
    custom.forEach((meta, index) => {
      items.push({
        id: meta.key,
        label: meta.label,
        icon: meta.icon,
        checked: meta.key === current,
        separatorBefore: index === 0,
      });
    });
    return items;
  });

  protected readonly opItems = computed<MenuItem[]>(() => {
    const meta = this.meta();
    if (!meta) return [];
    return opsFor(meta).map((op) => ({
      id: op,
      label: 'filters.op.' + op,
      checked: op === this.node().op,
    }));
  });

  protected readonly boolItems = computed<MenuItem[]>(() => [
    { id: 'true', label: 'filters.true', checked: this.value(0) !== 'false' },
    { id: 'false', label: 'filters.false', checked: this.value(0) === 'false' },
  ]);

  protected readonly optionItems = computed<MenuItem[]>(() =>
    this.options().map((option) => ({
      id: option.value,
      label: option.label,
      icon: option.icon,
      checked: this.node().values.includes(option.value),
      keepOpen: this.multi(),
    })),
  );

  protected readonly valueSummary = computed(() => {
    const meta = this.meta();
    const values = this.node().values;
    if (!meta || !values.length) return this.t('filters.valuePlaceholder');
    const labels = values.map((value) => this.catalog.valueLabel(meta, value));
    return labels.length > 2
      ? `${labels.slice(0, 2).join(', ')} +${labels.length - 2}`
      : labels.join(', ');
  });

  protected value(index: number): string {
    return this.node().values[index] ?? '';
  }

  protected dateItems(index: number): MenuItem[] {
    const current = this.value(index);
    const items: MenuItem[] = RELATIVE_DATES.map((preset) => ({
      id: preset.value,
      label: preset.label,
      checked: current === preset.value,
    }));
    items.push({
      id: '__custom',
      label: 'filters.date.custom',
      icon: 'calendar',
      checked: current !== '' && !isRelativeDate(current),
      separatorBefore: true,
    });
    return items;
  }

  protected dateLabel(index: number): string {
    const value = this.value(index);
    const meta = this.meta();
    if (!value) return this.t('filters.date.custom');
    if (isRelativeDate(value)) return meta ? this.catalog.valueLabel(meta, value) : value;
    return this.t('filters.date.custom');
  }

  protected pickDate(index: number, id: string): void {
    this.setValue(index, id === '__custom' ? '' : id);
  }

  protected setField(field: string): void {
    const meta = this.catalog.meta(field);
    if (!meta) return;
    this.changed.emit(condition(field, defaultOp(meta), []));
  }

  protected setOp(op: string): void {
    const meta = this.meta();
    const current = this.node();
    const keep = meta && isMultiValue(meta, op) === isMultiValue(meta, current.op);
    this.changed.emit(condition(current.field, op, keep ? current.values : []));
  }

  protected setValues(values: string[]): void {
    this.changed.emit(condition(this.node().field, this.node().op, values));
  }

  protected setValue(index: number, value: string): void {
    const values = [...this.node().values];
    while (values.length <= index) values.push('');
    values[index] = value;
    this.setValues(values);
  }

  protected setList(raw: string): void {
    this.setValues(
      raw
        .split(',')
        .map((value) => value.trim())
        .filter(Boolean),
    );
  }

  protected toggleOption(value: string): void {
    const values = this.node().values;
    if (!this.multi()) {
      this.setValues([value]);
      return;
    }
    this.setValues(
      values.includes(value) ? values.filter((item) => item !== value) : [...values, value],
    );
  }
}

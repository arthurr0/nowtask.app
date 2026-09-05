import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  model,
  signal,
  untracked,
} from '@angular/core';
import type { FilterConditionDto, FilterGroupDto, FilterNodeDto } from '../core/api-types';
import { I18nService } from '../core/i18n/i18n.service';
import { cloneGroup, condition, emptyGroup, isGroup, pruneGroup } from '../core/task-filter';
import { ViewState } from '../data/view-state';
import { ConditionEditor } from './condition-editor';
import { Dialog } from './dialog';
import { Icon } from './icon';
import { Menu, type MenuItem } from './menu';

const JOIN_CLASS =
  'flex h-7 items-center gap-1.5 rounded-field border border-line bg-surface-2 px-2.5 font-mono text-[11px] text-ink-2';

@Component({
  selector: 'ui-filter-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Dialog, Icon, Menu, ConditionEditor],
  template: `
    <ui-dialog
      [(open)]="open"
      size="lg"
      title="filters.advancedTitle"
      description="filters.advancedHint"
      (closed)="open.set(false)"
    >
      <div class="flex flex-col gap-2.5">
        <div class="flex items-center gap-2.5">
          <span class="kap flex-1">{{ t('rules.conditions') }}</span>
          <ui-menu
            [items]="joinItems(draft().join)"
            [triggerClass]="joinClass"
            triggerHeight="28px"
            ariaLabel="rules.join"
            align="end"
            (selected)="setJoin(null, $any($event.id))"
          >
            <span>{{ t(draft().join === 'or' ? 'filters.matchAny' : 'filters.matchAll') }}</span>
          </ui-menu>
        </div>

        @for (node of draft().conditions; track $index; let index = $index) {
          @if (isGroup(node)) {
            <div
              class="flex flex-col gap-2 rounded-card border border-dashed border-line-strong p-2.5"
            >
              <div class="flex items-center gap-2">
                <ui-icon name="layers" [size]="13" class="text-ink-3" />
                <span class="text-[11px] font-medium text-ink-3">{{ t('filters.group') }}</span>
                <span class="flex-1"></span>
                <ui-menu
                  [items]="joinItems(node.join)"
                  [triggerClass]="joinClass"
                  triggerHeight="28px"
                  ariaLabel="rules.join"
                  align="end"
                  (selected)="setJoin(index, $any($event.id))"
                >
                  <span>{{ t(node.join === 'or' ? 'filters.matchAny' : 'filters.matchAll') }}</span>
                </ui-menu>
                <button
                  type="button"
                  class="hoverable flex h-7 w-7 items-center justify-center rounded-[6px] text-ink-3"
                  [attr.aria-label]="t('common.delete')"
                  (click)="remove(index, null)"
                >
                  <ui-icon name="x" [size]="14" />
                </button>
              </div>
              @for (child of node.conditions; track $index; let childIndex = $index) {
                @if (!isGroup(child)) {
                  <div class="rounded-card border border-line p-2">
                    <ui-condition-editor
                      [node]="child"
                      [showField]="true"
                      (changed)="replace(index, childIndex, $event)"
                      (removed)="remove(index, childIndex)"
                    />
                  </div>
                }
              }
              <button
                type="button"
                class="flex h-8 w-fit items-center gap-1.5 rounded-field border border-dashed border-line-strong px-2.5 text-xs text-ink-3"
                (click)="addCondition(index)"
              >
                <ui-icon name="plus" [size]="14" />
                {{ t('filters.addCondition') }}
              </button>
            </div>
          } @else {
            <div class="rounded-card border border-line p-2">
              <ui-condition-editor
                [node]="node"
                [showField]="true"
                (changed)="replace(index, null, $event)"
                (removed)="remove(index, null)"
              />
            </div>
          }
        } @empty {
          <p class="text-xs text-ink-3">{{ t('rules.noConditions') }}</p>
        }

        <div class="flex flex-wrap gap-2">
          <button
            type="button"
            class="flex h-8 items-center gap-1.5 rounded-field border border-dashed border-line-strong px-2.5 text-xs text-ink-3"
            (click)="addCondition(null)"
          >
            <ui-icon name="plus" [size]="14" />
            {{ t('filters.addCondition') }}
          </button>
          <button
            type="button"
            class="flex h-8 items-center gap-1.5 rounded-field border border-dashed border-line-strong px-2.5 text-xs text-ink-3"
            (click)="addGroup()"
          >
            <ui-icon name="layers" [size]="14" />
            {{ t('filters.addGroup') }}
          </button>
        </div>
      </div>

      <div dialogFooter class="flex w-full items-center gap-2">
        <button
          type="button"
          class="flex h-9 items-center gap-1.5 rounded-[7px] px-2.5 text-[13px] text-ink-2"
          (click)="clear()"
        >
          <ui-icon name="x" [size]="14" />
          {{ t('filters.clear') }}
        </button>
        <span class="flex-1"></span>
        <button
          type="button"
          class="flex h-9 items-center rounded-[7px] border border-line bg-surface-2 px-3 text-[13px] text-ink-2"
          (click)="open.set(false)"
        >
          {{ t('common.cancel') }}
        </button>
        <button
          type="button"
          class="flex h-9 items-center gap-1.5 rounded-[7px] bg-inv px-3 text-[13px] font-medium text-inv-ink"
          (click)="apply()"
        >
          <ui-icon name="check" [size]="14" />
          {{ t('filters.apply') }}
        </button>
      </div>
    </ui-dialog>
  `,
})
export class FilterDialog {
  private readonly view = inject(ViewState);
  protected readonly t = inject(I18nService).t;

  readonly open = model(false);
  protected readonly draft = signal<FilterGroupDto>(emptyGroup());
  protected readonly isGroup = isGroup;
  protected readonly joinClass = JOIN_CLASS;

  constructor() {
    effect(() => {
      if (this.open()) {
        untracked(() => this.draft.set(cloneGroup(this.view.filter())));
      }
    });
  }

  protected readonly count = computed(() => this.draft().conditions.length);

  protected joinItems(join: 'and' | 'or'): MenuItem[] {
    return [
      { id: 'and', label: 'filters.matchAll', checked: join === 'and' },
      { id: 'or', label: 'filters.matchAny', checked: join === 'or' },
    ];
  }

  protected setJoin(index: number | null, join: 'and' | 'or'): void {
    this.draft.update((group) => {
      if (index === null) return { ...group, join };
      return {
        ...group,
        conditions: group.conditions.map((node, position) =>
          position === index && isGroup(node) ? { ...node, join } : node,
        ),
      };
    });
  }

  protected addCondition(groupIndex: number | null): void {
    const fresh = condition('', 'in', []);
    this.insert(groupIndex, fresh);
  }

  protected addGroup(): void {
    this.insert(null, { join: 'or', conditions: [condition('', 'in', [])] });
  }

  private insert(groupIndex: number | null, node: FilterNodeDto): void {
    this.draft.update((group) => {
      if (groupIndex === null) return { ...group, conditions: [...group.conditions, node] };
      return {
        ...group,
        conditions: group.conditions.map((current, position) =>
          position === groupIndex && isGroup(current)
            ? { ...current, conditions: [...current.conditions, node] }
            : current,
        ),
      };
    });
  }

  protected replace(index: number, childIndex: number | null, next: FilterConditionDto): void {
    this.draft.update((group) => ({
      ...group,
      conditions: group.conditions.map((node, position) => {
        if (position !== index) return node;
        if (childIndex === null || !isGroup(node)) return next;
        return {
          ...node,
          conditions: node.conditions.map((child, childPosition) =>
            childPosition === childIndex ? next : child,
          ),
        };
      }),
    }));
  }

  protected remove(index: number, childIndex: number | null): void {
    this.draft.update((group) => ({
      ...group,
      conditions: group.conditions
        .map((node, position) => {
          if (position !== index) return node;
          if (childIndex === null || !isGroup(node)) return null;
          return {
            ...node,
            conditions: node.conditions.filter((_, childPosition) => childPosition !== childIndex),
          };
        })
        .filter((node): node is FilterNodeDto => node !== null),
    }));
  }

  protected clear(): void {
    this.draft.set(emptyGroup());
  }

  protected apply(): void {
    this.view.filter.set(pruneGroup(this.draft()));
    this.open.set(false);
  }
}

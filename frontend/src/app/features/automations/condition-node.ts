import {
  ChangeDetectionStrategy,
  Component,
  computed,
  forwardRef,
  inject,
  input,
} from '@angular/core';
import { I18nService } from '../../core/i18n/i18n.service';
import type { RuleConditionDto, RuleConditionGroupDto } from '../../core/api-types';
import { Icon } from '../../ui/icon';

@Component({
  selector: 'app-condition-node',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, forwardRef(() => ConditionNode)],
  template: `
    @if (group(); as group) {
      <div
        class="flex flex-col gap-2"
        [class.rounded-card]="depth() > 0"
        [class.border]="depth() > 0"
        [class.border-dashed]="depth() > 0"
        [class.border-line-strong]="depth() > 0"
        [class.p-2.5]="depth() > 0"
      >
        @for (child of group.children; track child.id) {
          <div class="flex items-start gap-2.5">
            <span class="w-[34px] flex-none pt-2 text-right font-mono text-[11px] text-ink-3">
              {{ group.join === 'and' ? 'AND' : 'OR' }}
            </span>
            <div class="min-w-0 flex-1">
              <app-condition-node [node]="child" [depth]="depth() + 1" />
            </div>
          </div>
        } @empty {
          <p class="pl-[44px] text-xs text-ink-3">{{ t('common.none') }}</p>
        }

        <div class="flex items-center gap-2.5 pl-[44px] text-ink-3">
          <ui-icon name="plus" [size]="14" />
          <span class="text-xs">{{ t('auto.addCondition') }}</span>
        </div>
      </div>
    } @else if (condition(); as condition) {
      <div
        class="flex min-h-9 flex-wrap items-center gap-2 rounded-card border border-line bg-surface-2 px-3 py-1.5"
      >
        <span class="text-[13px] text-ink-2">{{ t(condition.fieldKey) }}</span>
        <span class="text-[13px] text-ink-3">{{ t('cond.' + condition.op) }}</span>
        @for (value of condition.values; track value) {
          @if (isKey(value)) {
            <span class="rounded-full border border-line-strong px-2.5 py-0.5 text-xs">{{
              t(value)
            }}</span>
          } @else {
            <span class="font-mono text-[13px]">{{ value }}</span>
          }
        }
        <span class="flex-1"></span>
        <ui-icon name="dots" [size]="15" class="text-ink-3" />
      </div>
    }
  `,
})
export class ConditionNode {
  protected readonly t = inject(I18nService).t;

  readonly node = input.required<RuleConditionDto | RuleConditionGroupDto>();
  readonly depth = input(0);

  protected readonly group = computed(() => {
    const node = this.node();
    return node.kind === 'group' ? node : null;
  });

  protected readonly condition = computed(() => {
    const node = this.node();
    return node.kind === 'condition' ? node : null;
  });

  isKey(value: string): boolean {
    return value.includes('.') && !value.includes(' ');
  }
}

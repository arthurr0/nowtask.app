import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import type { UserDto } from '../core/api-types';
import { I18nService } from '../core/i18n/i18n.service';
import { Icon } from './icon';

@Component({
  selector: 'ui-avatar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  template: `
    @if (user()) {
      <span
        class="inline-flex items-center justify-center rounded-full bg-surface-3 font-mono text-ink-2"
        [style.width.px]="size()"
        [style.height.px]="size()"
        [style.fontSize.px]="fontSize()"
        [attr.title]="user()!.name"
        >{{ user()!.initials }}</span
      >
    } @else if (placeholder()) {
      <span
        class="inline-flex items-center justify-center rounded-full border border-dashed border-line-strong text-ink-3"
        [style.width.px]="size()"
        [style.height.px]="size()"
        [attr.title]="t('common.unassigned')"
      >
        <ui-icon name="user" [size]="iconSize()" />
      </span>
    }
  `,
})
export class Avatar {
  protected readonly t = inject(I18nService).t;

  readonly user = input<UserDto | null>(null);
  readonly size = input(22);
  readonly placeholder = input(true);
  readonly fontSize = computed(() => Math.round(this.size() * 0.42));
  protected readonly iconSize = computed(() => Math.round(this.size() * 0.55));
}

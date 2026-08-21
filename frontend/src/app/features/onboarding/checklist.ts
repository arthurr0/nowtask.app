import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { I18nService } from '../../core/i18n/i18n.service';
import { OnboardingService } from '../../core/onboarding.service';
import { Icon } from '../../ui/icon';

@Component({
  selector: 'app-onboarding-checklist',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  template: `
    @if (onboarding.checklistOpen()) {
      <div class="mx-3 mb-3 flex flex-col gap-2 rounded-card border border-line bg-surface-2 p-3">
        <div class="flex items-center gap-2">
          <span class="kap flex-1">{{ t('onboarding.checklistTitle') }}</span>
          <span class="font-mono text-[11px] text-ink-3">
            {{ onboarding.doneCount() }}/{{ onboarding.state()?.checklist?.length ?? 0 }}
          </span>
          <button
            type="button"
            class="hoverable flex h-5 w-5 items-center justify-center rounded-[5px] text-ink-3"
            [attr.aria-label]="t('onboarding.hideChecklist')"
            (click)="dismiss()"
          >
            <ui-icon name="x" [size]="13" />
          </button>
        </div>

        <ul class="flex flex-col gap-1">
          @for (item of onboarding.state()?.checklist ?? []; track item.code) {
            <li class="flex items-center gap-2 text-[12px]" [class.text-ink-3]="item.done">
              @if (item.done) {
                <ui-icon name="check" [size]="14" class="text-done" />
              } @else {
                <span class="h-[13px] w-[13px] rounded-full border border-line-strong"></span>
              }
              <span [class.line-through]="item.done">{{ t('onboarding.item.' + item.code) }}</span>
            </li>
          }
        </ul>
      </div>
    }
  `,
})
export class OnboardingChecklist {
  protected readonly onboarding = inject(OnboardingService);
  protected readonly t = inject(I18nService).t;

  protected async dismiss(): Promise<void> {
    await this.onboarding.dismiss();
  }
}

import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { I18nService } from '../../core/i18n/i18n.service';
import { inject } from '@angular/core';
import { Logo } from '../../ui/logo';
import { ViewControls } from '../../ui/view-controls';

@Component({
  selector: 'app-wizard-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Logo, ViewControls],
  template: `
    <div class="flex min-h-dvh flex-col bg-bg text-ink">
      <header class="flex h-14 flex-none items-center gap-3 border-b border-line bg-surface px-6">
        <ui-logo [size]="22" [tile]="false" />
        <span class="text-[13px] font-semibold lowercase tracking-[-0.02em]">{{
          t('app.name')
        }}</span>
        <span class="flex-1"></span>
        <ui-view-controls />
      </header>

      <main class="flex flex-1 items-start justify-center px-6 py-10 lg:py-16">
        <div class="flex w-full max-w-[560px] flex-col gap-8">
          <div class="flex flex-col gap-3">
            <div class="flex items-center gap-3">
              <span class="font-mono text-[11px] tracking-[0.08em] text-ink-3 uppercase">
                {{ t('onboarding.stepOf', { step: step(), total: total() }) }}
              </span>
              <div class="flex flex-1 items-center gap-1.5">
                @for (dot of dots(); track dot) {
                  <span
                    class="h-1.5 flex-1 rounded-full"
                    [class.bg-inv]="dot <= step()"
                    [class.bg-line]="dot > step()"
                  ></span>
                }
              </div>
            </div>

            <h1 class="text-[26px] leading-[1.2] font-semibold tracking-[-0.02em] text-pretty">
              {{ heading() }}
            </h1>
            @if (subheading()) {
              <p class="text-sm leading-[1.7] text-ink-2">{{ subheading() }}</p>
            }
          </div>

          <ng-content />
        </div>
      </main>
    </div>
  `,
})
export class WizardShell {
  protected readonly t = inject(I18nService).t;

  readonly step = input.required<number>();
  readonly total = input(5);
  readonly heading = input.required<string>();
  readonly subheading = input<string | null>(null);

  protected dots(): number[] {
    return Array.from({ length: this.total() }, (_, index) => index + 1);
  }
}

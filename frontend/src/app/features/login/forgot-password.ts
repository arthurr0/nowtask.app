import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { Icon } from '../../ui/icon';
import { Logo } from '../../ui/logo';
import { ViewControls } from '../../ui/view-controls';

@Component({
  selector: 'app-forgot-password',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, Icon, Logo, ViewControls],
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

      <main class="flex flex-1 items-center justify-center px-6 py-10">
        @if (sent()) {
          <div class="flex w-full max-w-[420px] flex-col items-center gap-4 text-center">
            <ui-icon name="check" [size]="28" class="text-done" />
            <h1 class="text-xl font-semibold tracking-[-0.015em]">{{ t('forgot.sentTitle') }}</h1>
            <p class="text-[13px] leading-[1.7] text-ink-2">{{ t('forgot.sentBody') }}</p>
            <a
              routerLink="/login"
              class="mt-1 flex h-[42px] w-full items-center justify-center rounded-card border border-line text-sm"
            >
              {{ t('forgot.backToLogin') }}
            </a>
          </div>
        } @else {
          <form class="flex w-full max-w-[372px] flex-col gap-5" (submit)="submit($event)">
            <header class="flex flex-col gap-1.5">
              <h1 class="text-xl font-semibold tracking-[-0.015em]">{{ t('forgot.title') }}</h1>
              <p class="text-[13px] leading-[1.7] text-ink-2">{{ t('forgot.body') }}</p>
            </header>

            <label class="flex flex-col gap-1.5">
              <span class="text-xs text-ink-2">{{ t('login.email') }}</span>
              <input
                type="email"
                name="email"
                autocomplete="email"
                class="h-11 rounded-card border border-line-strong bg-surface px-3.5 text-sm outline-none"
                [ngModel]="email()"
                (ngModelChange)="email.set($event)"
              />
            </label>

            @if (error()) {
              <p
                class="flex items-start gap-2 rounded-card border border-warn bg-warn-soft px-3.5 py-2.5 text-[13px] text-warn"
              >
                <ui-icon name="alert" [size]="15" />
                <span>{{ error() }}</span>
              </p>
            }

            <button
              type="submit"
              class="flex h-[46px] items-center justify-center rounded-card bg-inv text-sm font-medium text-inv-ink disabled:opacity-60"
              [disabled]="submitting() || !complete()"
            >
              {{ submitting() ? t('state.loading') : t('forgot.submit') }}
            </button>

            <a
              routerLink="/login"
              class="text-[13px] text-ink-2 underline-offset-2 hover:underline"
            >
              {{ t('forgot.backToLogin') }}
            </a>
          </form>
        }
      </main>
    </div>
  `,
})
export class ForgotPassword {
  private readonly auth = inject(AuthService);
  protected readonly t = inject(I18nService).t;

  protected readonly email = signal('');
  protected readonly submitting = signal(false);
  protected readonly sent = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly complete = computed(() => this.email().trim().includes('@'));

  async submit(event: Event): Promise<void> {
    event.preventDefault();
    if (this.submitting() || !this.complete()) {
      return;
    }

    this.submitting.set(true);
    this.error.set(null);

    try {
      await this.auth.requestPasswordReset(this.email().trim());
      this.sent.set(true);
    } catch {
      this.error.set(this.t('state.errorTitle'));
    } finally {
      this.submitting.set(false);
    }
  }
}

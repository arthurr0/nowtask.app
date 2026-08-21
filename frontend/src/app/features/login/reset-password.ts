import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { Icon } from '../../ui/icon';
import { Logo } from '../../ui/logo';
import { ViewControls } from '../../ui/view-controls';

const MIN_PASSWORD_LENGTH = 10;

@Component({
  selector: 'app-reset-password',
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
        @if (done()) {
          <div class="flex w-full max-w-[420px] flex-col items-center gap-4 text-center">
            <ui-icon name="check" [size]="28" class="text-done" />
            <h1 class="text-xl font-semibold tracking-[-0.015em]">{{ t('reset.doneTitle') }}</h1>
            <p class="text-[13px] leading-[1.7] text-ink-2">{{ t('reset.doneBody') }}</p>
            <a
              routerLink="/login"
              class="mt-1 flex h-[42px] w-full items-center justify-center rounded-card bg-inv text-sm font-medium text-inv-ink"
            >
              {{ t('login.signIn') }}
            </a>
          </div>
        } @else {
          <form class="flex w-full max-w-[372px] flex-col gap-5" (submit)="submit($event)">
            <header class="flex flex-col gap-1.5">
              <h1 class="text-xl font-semibold tracking-[-0.015em]">{{ t('reset.title') }}</h1>
              <p class="text-[13px] leading-[1.7] text-ink-2">{{ t('reset.body') }}</p>
            </header>

            <div class="flex flex-col gap-3.5">
              <label class="flex flex-col gap-1.5">
                <span class="text-xs text-ink-2">{{ t('reset.password') }}</span>
                <div
                  class="flex h-11 items-center gap-2.5 rounded-card border border-line-strong bg-surface px-3.5"
                >
                  <input
                    [type]="showPassword() ? 'text' : 'password'"
                    name="password"
                    autocomplete="new-password"
                    class="min-w-0 flex-1 bg-transparent text-sm outline-none"
                    [ngModel]="password()"
                    (ngModelChange)="password.set($event)"
                  />
                  <button
                    type="button"
                    class="text-ink-3"
                    [attr.aria-label]="t('login.showPassword')"
                    [attr.aria-pressed]="showPassword()"
                    (click)="showPassword.set(!showPassword())"
                  >
                    <ui-icon name="eye" [size]="17" />
                  </button>
                </div>
                <span class="text-[11px] text-ink-3">{{ t('signup.passwordHint') }}</span>
              </label>

              <label class="flex flex-col gap-1.5">
                <span class="text-xs text-ink-2">{{ t('reset.confirm') }}</span>
                <input
                  type="password"
                  name="confirm"
                  autocomplete="new-password"
                  class="h-11 rounded-card border border-line bg-surface px-3.5 text-sm outline-none"
                  [ngModel]="confirm()"
                  (ngModelChange)="confirm.set($event)"
                />
              </label>
            </div>

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
              {{ submitting() ? t('state.loading') : t('reset.submit') }}
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
export class ResetPassword {
  private readonly auth = inject(AuthService);
  protected readonly t = inject(I18nService).t;

  readonly token = input<string>('');

  protected readonly password = signal('');
  protected readonly confirm = signal('');
  protected readonly showPassword = signal(false);
  protected readonly submitting = signal(false);
  protected readonly done = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly complete = computed(
    () => this.password().length >= MIN_PASSWORD_LENGTH && this.confirm().length > 0,
  );

  async submit(event: Event): Promise<void> {
    event.preventDefault();
    if (this.submitting() || !this.complete()) {
      return;
    }

    if (this.password() !== this.confirm()) {
      this.error.set(this.t('reset.mismatch'));
      return;
    }

    if (!this.token()) {
      this.error.set(this.t('reset.missingToken'));
      return;
    }

    this.submitting.set(true);
    this.error.set(null);

    try {
      await this.auth.resetPassword(this.token(), this.password());
      this.done.set(true);
    } catch (error) {
      this.error.set(this.t(messageKey(error)));
    } finally {
      this.submitting.set(false);
    }
  }
}

function messageKey(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) return 'state.errorTitle';
  const code = (error.error as { code?: string } | null)?.code;

  if (code === 'TOKEN_EXPIRED') return 'reset.expired';
  if (code === 'TOKEN_USED') return 'reset.used';
  if (code === 'TOKEN_INVALID') return 'reset.invalid';
  if (code === 'PASSWORD_TOO_COMMON') return 'signup.passwordCommon';
  if (error.status === 400) return 'reset.tooShort';
  return 'state.errorTitle';
}

import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { Icon } from '../../ui/icon';
import { Logo } from '../../ui/logo';
import { ViewControls } from '../../ui/view-controls';

@Component({
  selector: 'app-verify-email',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon, Logo, ViewControls],
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
        <div class="flex w-full max-w-[420px] flex-col items-center gap-4 text-center">
          @if (state() === 'pending') {
            <p class="text-[13px] text-ink-2">{{ t('state.loading') }}</p>
          } @else if (state() === 'done') {
            <ui-icon name="check" [size]="28" class="text-done" />
            <h1 class="text-xl font-semibold tracking-[-0.015em]">
              {{ t(changing() ? 'verify.emailChangedTitle' : 'verify.doneTitle') }}
            </h1>
            <p class="text-[13px] text-ink-2">
              {{ t(changing() ? 'verify.emailChangedBody' : 'verify.doneBody') }}
            </p>
            @if (changing()) {
              <a
                routerLink="/login"
                class="flex h-[42px] w-full items-center justify-center rounded-card bg-inv text-sm font-medium text-inv-ink"
              >
                {{ t('login.signIn') }}
              </a>
            } @else {
              <a
                routerLink="/app/board"
                class="flex h-[42px] w-full items-center justify-center rounded-card bg-inv text-sm font-medium text-inv-ink"
              >
                {{ t('verify.goToBoard') }}
              </a>
            }
          } @else {
            <ui-icon name="alert" [size]="26" class="text-warn" />
            <h1 class="text-xl font-semibold tracking-[-0.015em]">
              {{ t(changing() ? 'verify.emailChangeFailed' : 'verify.failedTitle') }}
            </h1>
            <p class="text-[13px] text-ink-2">{{ message() }}</p>
            <a
              routerLink="/login"
              class="flex h-[42px] w-full items-center justify-center rounded-card border border-line text-sm"
            >
              {{ t('login.signIn') }}
            </a>
          }
        </div>
      </main>
    </div>
  `,
})
export class VerifyEmail implements OnInit {
  private readonly auth = inject(AuthService);
  protected readonly t = inject(I18nService).t;

  readonly token = input<string>('');
  readonly mode = input<string>('');

  protected readonly state = signal<'pending' | 'done' | 'failed'>('pending');
  protected readonly message = signal('');
  protected readonly changing = computed(() => this.mode() === 'change');

  ngOnInit(): void {
    void this.verify();
  }

  private async verify(): Promise<void> {
    const token = this.token();

    if (!token) {
      this.state.set('failed');
      this.message.set(this.t('verify.missingToken'));
      return;
    }

    try {
      if (this.changing()) {
        await this.auth.confirmEmailChange(token);
      } else {
        await this.auth.verifyEmail(token);
      }
      this.state.set('done');
    } catch (error) {
      this.state.set('failed');
      this.message.set(this.t(messageKey(error)));
    }
  }
}

function messageKey(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) return 'state.errorTitle';
  const code = (error.error as { code?: string } | null)?.code;

  if (code === 'TOKEN_EXPIRED') return 'verify.expired';
  if (code === 'TOKEN_USED') return 'verify.used';
  return 'verify.invalid';
}

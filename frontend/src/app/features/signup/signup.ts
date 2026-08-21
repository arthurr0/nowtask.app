import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import type { SuggestedOrgDto } from '../../core/api-types';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { Icon } from '../../ui/icon';
import { Logo } from '../../ui/logo';
import { ViewControls } from '../../ui/view-controls';

const MIN_PASSWORD_LENGTH = 10;

const STRENGTH_LABELS = [
  'signup.strengthWeak',
  'signup.strengthFair',
  'signup.strengthGood',
  'signup.strengthStrong',
] as const;

function strengthOf(password: string): number {
  if (password.length < MIN_PASSWORD_LENGTH) return 0;

  const variety = [/[a-z]/, /[A-Z]/, /[0-9]/, /[^A-Za-z0-9]/].filter((pattern) =>
    pattern.test(password),
  ).length;

  if (password.length >= 16 && variety >= 3) return 3;
  if (password.length >= 12 && variety >= 2) return 2;
  return 1;
}

@Component({
  selector: 'app-signup',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, Icon, Logo, ViewControls],
  templateUrl: './signup.html',
})
export class Signup {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly t = inject(I18nService).t;

  protected readonly name = signal('');
  protected readonly email = signal('');
  protected readonly password = signal('');
  protected readonly showPassword = signal(false);
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly suggestion = signal<SuggestedOrgDto | null>(null);

  protected readonly minPasswordLength = MIN_PASSWORD_LENGTH;

  protected readonly strength = computed(() => strengthOf(this.password()));

  protected readonly strengthLabel = computed(() => this.t(STRENGTH_LABELS[this.strength()]));

  protected readonly complete = computed(
    () =>
      this.name().trim().length > 0 &&
      this.email().trim().length > 0 &&
      this.password().length >= MIN_PASSWORD_LENGTH,
  );

  async submit(event: Event): Promise<void> {
    event.preventDefault();
    if (this.submitting() || !this.complete()) {
      return;
    }

    this.submitting.set(true);
    this.error.set(null);

    try {
      const result = await this.auth.signup(this.name(), this.email(), this.password());

      if (result.suggestOrg) {
        this.suggestion.set(result.suggestOrg);
        return;
      }

      await this.router.navigate(['/orgs/new']);
    } catch (error) {
      this.error.set(this.t(messageKey(error)));
    } finally {
      this.submitting.set(false);
    }
  }

  async continueToOwnOrg(): Promise<void> {
    await this.router.navigate(['/orgs/new']);
  }
}

function messageKey(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) return 'state.errorTitle';
  if (error.status === 409) return 'signup.emailTaken';
  if (error.status === 422) return 'signup.passwordCommon';
  if (error.status === 400) return 'signup.invalid';
  return 'state.errorTitle';
}

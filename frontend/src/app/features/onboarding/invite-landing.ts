import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { AcceptedInviteDto, InvitePreviewDto } from '../../core/api-types';
import { ActiveOrgService } from '../../core/active-org';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { OnboardingService } from '../../core/onboarding.service';
import { OrgService } from '../../core/org.service';
import { WorkspaceStore } from '../../data/workspace.store';
import { Icon } from '../../ui/icon';
import { Logo } from '../../ui/logo';
import { ViewControls } from '../../ui/view-controls';

const MIN_PASSWORD_LENGTH = 10;

@Component({
  selector: 'app-invite-landing',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, Icon, Logo, ViewControls],
  templateUrl: './invite-landing.html',
})
export class InviteLanding {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly activeOrg = inject(ActiveOrgService);
  private readonly orgs = inject(OrgService);
  private readonly onboarding = inject(OnboardingService);
  private readonly store = inject(WorkspaceStore);
  private readonly router = inject(Router);
  protected readonly t = inject(I18nService).t;

  readonly token = input.required<string>();

  protected readonly preview = signal<InvitePreviewDto | null>(null);
  protected readonly loading = signal(true);
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly renewalSent = signal(false);

  protected readonly name = signal('');
  protected readonly password = signal('');
  protected readonly showPassword = signal(false);

  protected readonly signedInEmail = computed(() => this.auth.user()?.email ?? null);
  protected readonly minPasswordLength = MIN_PASSWORD_LENGTH;

  protected readonly sameAccount = computed(() => {
    const preview = this.preview();
    const email = this.signedInEmail();
    if (!preview || !email) return false;
    return maskMatches(preview.maskedEmail, email);
  });

  protected readonly needsPassword = computed(() => {
    const preview = this.preview();
    return preview !== null && preview.state === 'open' && !this.sameAccount();
  });

  protected readonly complete = computed(() => {
    const preview = this.preview();
    if (!preview || preview.state !== 'open') return false;
    if (this.sameAccount()) return true;
    if (this.password().length < MIN_PASSWORD_LENGTH) return false;
    return preview.accountExists || this.name().trim().length > 1;
  });

  constructor() {
    void this.load();
  }

  private async load(): Promise<void> {
    this.loading.set(true);

    try {
      await this.auth.restore();
      const preview = await firstValueFrom(
        this.http.get<InvitePreviewDto>(`/api/invites/${encodeURIComponent(this.token())}`),
      );
      this.preview.set(preview);
    } catch {
      this.preview.set(null);
    } finally {
      this.loading.set(false);
    }
  }

  async accept(event?: Event): Promise<void> {
    event?.preventDefault();
    if (this.submitting() || !this.complete()) return;

    this.submitting.set(true);
    this.error.set(null);

    try {
      await firstValueFrom(this.http.get('/api/meta'));

      const result = await firstValueFrom(
        this.http.post<AcceptedInviteDto>(
          `/api/invites/${encodeURIComponent(this.token())}/accept`,
          this.sameAccount()
            ? {}
            : { name: this.name().trim() || null, password: this.password() },
        ),
      );

      await this.auth.reload();
      this.activeOrg.set(result.organizationId);
      this.orgs.clear();
      this.onboarding.clear();
      await this.orgs.refresh(true);
      await this.onboarding.refresh(true);
      this.store.clear();
      await this.store.load(true);

      await this.router.navigate(['/app/board'], {
        queryParams: { joined: result.organizationId },
      });
    } catch (error) {
      this.error.set(this.t(messageKey(error)));
    } finally {
      this.submitting.set(false);
    }
  }

  async requestNew(): Promise<void> {
    this.submitting.set(true);
    this.error.set(null);

    try {
      await firstValueFrom(this.http.get('/api/meta'));
      await firstValueFrom(
        this.http.post(`/api/invites/${encodeURIComponent(this.token())}/request-new`, {}),
      );
      this.renewalSent.set(true);
    } catch (error) {
      this.error.set(this.t(messageKey(error)));
    } finally {
      this.submitting.set(false);
    }
  }

  async signOut(): Promise<void> {
    await this.auth.logout();
    await this.router.navigate(['/invite', this.token()]);
  }
}

function maskMatches(masked: string, email: string): boolean {
  const at = masked.indexOf('@');
  if (at < 1) return false;

  const domain = masked.slice(at);
  const firstLetter = masked.slice(0, 1).toLowerCase();
  const lower = email.toLowerCase();

  return lower.endsWith(domain.toLowerCase()) && lower.startsWith(firstLetter);
}

function messageKey(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) return 'state.errorTitle';
  const code = (error.error as { code?: string } | null)?.code;

  switch (code) {
    case 'INVITE_EXPIRED':
      return 'invite.expired';
    case 'INVITE_REVOKED':
    case 'INVITE_ACCEPTED':
      return 'invite.used';
    case 'ALREADY_MEMBER':
      return 'invite.alreadyMember';
    case 'EMAIL_MISMATCH':
      return 'invite.wrongAccount';
    case 'BAD_CREDENTIALS':
      return 'login.badCredentials';
    case 'ALREADY_REQUESTED':
      return 'invite.renewalAlready';
    default:
      return error.status === 401 ? 'login.badCredentials' : 'state.errorTitle';
  }
}

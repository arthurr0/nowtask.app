import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type {
  BulkInviteResultDto,
  PresetDetailDto,
  PresetSummaryDto,
  ProjectDto,
} from '../../core/api-types';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { OnboardingService } from '../../core/onboarding.service';
import { WorkspaceStore } from '../../data/workspace.store';
import { Icon } from '../../ui/icon';
import { WizardShell } from './wizard-shell';

const PRESET_STORAGE_KEY = 'nowtask.onboarding.preset';

interface InviteRow {
  email: string;
  role: string;
  failed: boolean;
}

function projectCode(name: string): string {
  return name
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, '')
    .slice(0, 4);
}

@Component({
  selector: 'app-onboarding',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, Icon, WizardShell],
  templateUrl: './onboarding.html',
})
export class OnboardingWizard {
  private readonly http = inject(HttpClient);
  private readonly onboarding = inject(OnboardingService);
  private readonly store = inject(WorkspaceStore);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly t = inject(I18nService).t;

  protected readonly presets = signal<PresetSummaryDto[]>([]);
  protected readonly preview = signal<PresetDetailDto | null>(null);
  protected readonly previewOpen = signal<string | null>(null);
  protected readonly chosenPreset = signal(readStoredPreset());

  protected readonly projectName = signal('');
  protected readonly projectCodeValue = signal('');
  protected readonly projectCodeTouched = signal(false);

  protected readonly rows = signal<InviteRow[]>([
    { email: '', role: 'member', failed: false },
    { email: '', role: 'member', failed: false },
  ]);
  protected readonly inviteErrors = signal<string[]>([]);

  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly step = computed(() => this.onboarding.state()?.step ?? 'preset');
  protected readonly emailVerified = computed(() => this.auth.user()?.emailVerified ?? false);
  protected readonly roles = ['manager', 'member', 'guest'];

  protected readonly stepNumber = computed(() => {
    switch (this.step()) {
      case 'preset':
        return 2;
      case 'project':
        return 3;
      case 'invite':
        return 4;
      default:
        return 5;
    }
  });

  protected readonly heading = computed(() => {
    switch (this.step()) {
      case 'preset':
        return this.t('onboarding.presetTitle');
      case 'project':
        return this.t('onboarding.projectTitle');
      default:
        return this.t('onboarding.inviteTitle');
    }
  });

  protected readonly subheading = computed(() => {
    switch (this.step()) {
      case 'preset':
        return this.t('onboarding.presetSubtitle');
      case 'project':
        return this.t('onboarding.projectSubtitle');
      default:
        return this.t('onboarding.inviteSubtitle');
    }
  });

  protected readonly completeProject = computed(
    () => this.projectName().trim().length > 1 && this.projectCodeValue().length > 0,
  );

  protected readonly filledRows = computed(() =>
    this.rows().filter((row) => row.email.trim().length > 0),
  );

  constructor() {
    void this.init();
  }

  private async init(): Promise<void> {
    await this.onboarding.refresh();

    if (!this.onboarding.state()) {
      await this.router.navigate(['/orgs/new']);
      return;
    }

    if (this.step() === 'done') {
      await this.finish();
      return;
    }

    try {
      const presets = await firstValueFrom(this.http.get<PresetSummaryDto[]>('/api/presets'));
      this.presets.set(presets);
    } catch {
      this.presets.set([]);
    }
  }

  protected presetLabel(code: string): string {
    return this.t('preset.' + code);
  }

  protected presetSummary(code: string): string {
    return this.t('preset.' + code + '.summary');
  }

  protected choose(code: string): void {
    this.chosenPreset.set(code);
    storePreset(code);
  }

  private async rememberPresetOnOrganization(): Promise<void> {
    try {
      await firstValueFrom(
        this.http.patch('/api/orgs/current', { defaultPresetCode: this.chosenPreset() }),
      );
    } catch {
      this.error.set(null);
    }
  }

  async togglePreview(code: string): Promise<void> {
    if (this.previewOpen() === code) {
      this.previewOpen.set(null);
      return;
    }

    this.previewOpen.set(code);
    this.preview.set(null);

    try {
      const detail = await firstValueFrom(
        this.http.get<PresetDetailDto>(`/api/presets/${code}`),
      );
      this.preview.set(detail);
    } catch {
      this.preview.set(null);
    }
  }

  protected statusLabel(code: string, fallback: string): string {
    const key = 'status.' + code;
    const translated = this.t(key);
    return translated === key ? fallback : translated;
  }

  async toProject(): Promise<void> {
    if (this.submitting()) return;
    this.submitting.set(true);
    this.error.set(null);

    try {
      await this.rememberPresetOnOrganization();
      await this.onboarding.setStep('project');
    } catch (error) {
      this.error.set(this.t(messageKey(error)));
    } finally {
      this.submitting.set(false);
    }
  }

  async backToPreset(): Promise<void> {
    await this.onboarding.setStep('preset');
  }

  async backToProject(): Promise<void> {
    await this.onboarding.setStep('project');
  }

  protected onProjectName(value: string): void {
    this.projectName.set(value);
    if (!this.projectCodeTouched()) {
      this.projectCodeValue.set(projectCode(value));
    }
  }

  protected onProjectCode(value: string): void {
    this.projectCodeTouched.set(true);
    this.projectCodeValue.set(projectCode(value));
  }

  async createProject(): Promise<void> {
    if (this.submitting() || !this.completeProject()) return;

    this.submitting.set(true);
    this.error.set(null);

    try {
      await firstValueFrom(
        this.http.post<ProjectDto>('/api/projects', {
          name: this.projectName().trim(),
          code: this.projectCodeValue(),
          presetCode: this.chosenPreset(),
        }),
      );
      await this.onboarding.setStep('invite');
    } catch (error) {
      this.error.set(this.t(messageKey(error)));
    } finally {
      this.submitting.set(false);
    }
  }

  protected addRow(): void {
    this.rows.update((rows) => [...rows, { email: '', role: 'member', failed: false }]);
  }

  protected removeRow(index: number): void {
    this.rows.update((rows) => rows.filter((_, position) => position !== index));
  }

  protected setEmail(index: number, value: string): void {
    this.rows.update((rows) =>
      rows.map((row, position) =>
        position === index ? { ...row, email: value, failed: false } : row,
      ),
    );

    if (value.includes(',') || value.includes('\n')) {
      this.explode();
    }
  }

  protected setRole(index: number, value: string): void {
    this.rows.update((rows) =>
      rows.map((row, position) => (position === index ? { ...row, role: value } : row)),
    );
  }

  private explode(): void {
    this.rows.update((rows) => {
      const exploded: InviteRow[] = [];
      for (const row of rows) {
        const parts = row.email
          .split(/[,\n;]+/)
          .map((part) => part.trim())
          .filter((part) => part.length > 0);

        if (parts.length === 0) {
          exploded.push(row);
          continue;
        }

        for (const part of parts) {
          exploded.push({ email: part, role: row.role, failed: false });
        }
      }
      return exploded;
    });
  }

  async sendInvites(): Promise<void> {
    if (this.submitting() || this.filledRows().length === 0) return;

    this.submitting.set(true);
    this.error.set(null);
    this.inviteErrors.set([]);

    try {
      const byRole = new Map<string, string[]>();
      for (const row of this.filledRows()) {
        const emails = byRole.get(row.role) ?? [];
        emails.push(row.email.trim());
        byRole.set(row.role, emails);
      }

      const failed: string[] = [];

      for (const [role, emails] of byRole) {
        const result = await firstValueFrom(
          this.http.post<BulkInviteResultDto>('/api/admin/invites/bulk', { emails, role }),
        );
        for (const item of result.failed) {
          failed.push(`${item.email}: ${this.t(item.messageKey)}`);
        }
      }

      this.inviteErrors.set(failed);

      if (failed.length === 0) {
        await this.finish();
      }
    } catch (error) {
      this.error.set(this.t(messageKey(error)));
    } finally {
      this.submitting.set(false);
    }
  }

  async skipInvites(): Promise<void> {
    await this.finish();
  }

  async resendVerification(): Promise<void> {
    try {
      await this.auth.resendVerification();
      this.error.set(this.t('onboarding.verificationSent'));
    } catch (error) {
      this.error.set(this.t(messageKey(error)));
    }
  }

  private async finish(): Promise<void> {
    await this.onboarding.setStep('done');
    clearStoredPreset();
    await this.store.load(true);
    await this.router.navigate(['/app/board']);
  }
}

function readStoredPreset(): string {
  if (typeof sessionStorage === 'undefined') return 'kanban';
  return sessionStorage.getItem(PRESET_STORAGE_KEY) ?? 'kanban';
}

function storePreset(code: string): void {
  if (typeof sessionStorage === 'undefined') return;
  sessionStorage.setItem(PRESET_STORAGE_KEY, code);
}

function clearStoredPreset(): void {
  if (typeof sessionStorage === 'undefined') return;
  sessionStorage.removeItem(PRESET_STORAGE_KEY);
}

function messageKey(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) return 'state.errorTitle';
  const code = (error.error as { code?: string } | null)?.code;

  if (code === 'EMAIL_NOT_VERIFIED') return 'onboarding.verifyFirst';
  if (code === 'LIMIT_REACHED') return 'invite.error.LIMIT_REACHED';
  if (error.status === 409) return 'onboarding.projectCodeTaken';
  if (error.status === 422) return 'onboarding.projectCodeTaken';
  if (error.status === 403) return 'state.forbidden';
  return 'state.errorTitle';
}

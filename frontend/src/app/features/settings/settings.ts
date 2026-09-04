import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { IntegrationsStore } from '../../data/feature.stores';
import { AccountService } from '../../core/account.service';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { OrgService } from '../../core/org.service';
import { PrefsService } from '../../core/prefs.service';
import { dateTime } from '../../core/format';
import type { Accent, Density, Lang, RadiusStep, ThemeChoice } from '../../core/models';
import type {
  NotificationPrefDto,
  SessionDto,
  TaskOpenMode,
  TaskViewCode,
} from '../../core/api-types';
import { TASK_VIEWS } from '../../core/task-views';
import { WorkspaceStore } from '../../data/workspace.store';
import { ConfirmService } from '../../ui/confirm.service';
import { Avatar } from '../../ui/avatar';
import { Icon } from '../../ui/icon';
import { Switch } from '../../ui/switch';
import { ToastService } from '../../ui/toast.service';
import { Topbar } from '../../ui/topbar';
import { ViewControls } from '../../ui/view-controls';
import { TextField } from '../../ui/text-field';
import { DeleteAccountDialog, EmailChangeDialog, type EmailChangeDraft } from './account-dialogs';

type Section =
  'profile' | 'security' | 'notifications' | 'github' | 'appearance' | 'views' | 'language';

const MIN_PASSWORD_LENGTH = 10;

@Component({
  selector: 'app-settings',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    Icon,
    Avatar,
    Switch,
    Topbar,
    ViewControls,
    TextField,
    EmailChangeDialog,
    DeleteAccountDialog,
  ],
  templateUrl: './settings.html',
})
export class Settings implements OnInit {
  protected readonly store = inject(WorkspaceStore);
  protected readonly auth = inject(AuthService);
  protected readonly account = inject(AccountService);
  protected readonly orgs = inject(OrgService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  protected readonly integrations = inject(IntegrationsStore);
  protected readonly prefs = inject(PrefsService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  protected readonly i18n = inject(I18nService);
  protected readonly t = this.i18n.t;

  protected readonly section = signal<Section>('profile');

  protected readonly accountSections: readonly { id: Section; icon: string; label: string }[] = [
    { id: 'profile', icon: 'user', label: 'account.profile' },
    { id: 'security', icon: 'lock', label: 'account.security' },
    { id: 'notifications', icon: 'bell', label: 'account.notifications' },
    { id: 'github', icon: 'git-branch', label: 'account.github' },
  ];

  protected readonly personalSections: readonly { id: Section; icon: string; label: string }[] = [
    { id: 'appearance', icon: 'sun', label: 'settings.appearance' },
    { id: 'views', icon: 'board', label: 'settings.views' },
    { id: 'language', icon: 'globe', label: 'settings.language' },
  ];

  protected readonly themeChoices: readonly { id: ThemeChoice; label: string }[] = [
    { id: 'light', label: 'settings.light' },
    { id: 'dark', label: 'settings.dark' },
    { id: 'system', label: 'settings.system' },
  ];

  protected readonly accents: readonly { id: Accent; color: string }[] = [
    { id: 'graphite', color: 'oklch(0.46 0.012 265)' },
    { id: 'blue', color: 'oklch(0.58 0.09 252)' },
    { id: 'clay', color: 'oklch(0.58 0.09 42)' },
    { id: 'moss', color: 'oklch(0.58 0.09 146)' },
    { id: 'plum', color: 'oklch(0.58 0.09 318)' },
  ];

  protected readonly densities: readonly { id: Density; label: string }[] = [
    { id: 'compact', label: 'settings.compact' },
    { id: 'cozy', label: 'settings.standard' },
    { id: 'roomy', label: 'settings.roomy' },
  ];

  protected readonly radii: readonly RadiusStep[] = ['0', '4', '8', '14'];

  protected readonly viewChoices = computed(() =>
    TASK_VIEWS.filter((view) => this.store.taskViewEnabled(view.code, null)),
  );

  protected readonly defaultViewSaving = signal(false);

  async selectDefaultView(code: TaskViewCode): Promise<void> {
    if (this.store.defaultView() === code || this.defaultViewSaving()) return;

    this.defaultViewSaving.set(true);
    try {
      await this.store.saveDefaultView(code);
      this.toast.success(this.t('common.saved'));
    } catch {
      this.toast.error(this.t('common.actionFailed'));
    } finally {
      this.defaultViewSaving.set(false);
    }
  }

  protected readonly taskOpenChoices: readonly {
    id: TaskOpenMode;
    icon: string;
    label: string;
    hint: string;
  }[] = [
    {
      id: 'dialog',
      icon: 'columns',
      label: 'settings.taskOpenDialog',
      hint: 'settings.taskOpenDialogHint',
    },
    {
      id: 'page',
      icon: 'arrow-right',
      label: 'settings.taskOpenPage',
      hint: 'settings.taskOpenPageHint',
    },
  ];

  protected readonly taskOpenSaving = signal(false);

  async selectTaskOpenMode(mode: TaskOpenMode): Promise<void> {
    if (this.store.taskOpenMode() === mode || this.taskOpenSaving()) return;

    this.taskOpenSaving.set(true);
    try {
      await this.store.saveTaskOpenMode(mode);
      this.toast.success(this.t('common.saved'));
    } catch {
      this.toast.error(this.t('common.actionFailed'));
    } finally {
      this.taskOpenSaving.set(false);
    }
  }

  protected readonly previewRows = computed(() =>
    this.store
      .tasks()
      .slice(0, 3)
      .map((task) => ({ title: task.title, key: task.key })),
  );

  protected readonly languages = [
    { code: 'pl', name: 'Polski', coverage: 100, isDefault: true },
    { code: 'en', name: 'English', coverage: 100, isDefault: false },
    { code: 'de', name: 'Deutsch', coverage: 100, isDefault: false },
  ];

  protected readonly profileName = signal('');
  protected readonly profileInitials = signal('');
  protected readonly profileSaving = signal(false);

  protected readonly currentPassword = signal('');
  protected readonly newPassword = signal('');
  protected readonly repeatPassword = signal('');
  protected readonly passwordError = signal('');
  protected readonly passwordSaving = signal(false);

  protected readonly emailDialogOpen = signal(false);
  protected readonly emailError = signal('');
  protected readonly deleteDialogOpen = signal(false);
  protected readonly deleteError = signal('');
  protected readonly orgDeleteDialogOpen = signal(false);
  protected readonly orgDeleteError = signal('');
  protected readonly ownsOrganization = computed(() => this.orgs.active()?.owner ?? false);

  protected readonly profileDirty = computed(() => {
    const user = this.auth.user();
    if (!user) return false;
    return (
      this.profileName().trim() !== user.name || this.profileInitials().trim() !== user.initials
    );
  });

  protected readonly passwordReady = computed(
    () =>
      this.currentPassword().length > 0 &&
      this.newPassword().length >= MIN_PASSWORD_LENGTH &&
      this.newPassword() === this.repeatPassword(),
  );

  protected readonly otherSessions = computed(
    () => this.account.sessions().filter((session) => !session.current).length,
  );

  constructor() {
    effect(() => {
      const user = this.auth.user();
      if (!user) return;
      untracked(() => {
        this.profileName.set(user.name);
        this.profileInitials.set(user.initials);
      });
    });
  }

  ngOnInit(): void {
    void this.loadAccount();
    void this.integrations.loadGitHubAccount().catch(() => undefined);

    const params = this.route.snapshot.queryParamMap;
    const code = params.get('code');
    const state = params.get('state');

    if (code && state) {
      this.section.set('github');
      void this.finishGitHubLink(code, state);
    }
  }

  async startGitHubLink(): Promise<void> {
    try {
      window.location.href = await this.integrations.startGitHubAccountLink();
    } catch {
      this.toast.error(this.t('common.actionFailed'));
    }
  }

  async unlinkGitHub(): Promise<void> {
    const confirmed = await this.confirm.ask({
      title: 'account.github.unlink',
      message: this.t('account.github.unlinkLead'),
      confirmLabel: 'account.github.unlink',
      destructive: true,
    });
    if (!confirmed) return;

    try {
      await this.integrations.unlinkGitHubAccount();
      this.toast.success(this.t('common.saved'));
    } catch {
      this.toast.error(this.t('common.actionFailed'));
    }
  }

  private async finishGitHubLink(code: string, state: string): Promise<void> {
    try {
      const account = await this.integrations.linkGitHubAccount(code, state);
      this.toast.success(this.t('account.github.linked', { login: account.login }));
    } catch (error) {
      this.toast.error(this.errorText(error));
    } finally {
      void this.router.navigate([], { relativeTo: this.route, queryParams: {} });
    }
  }

  selectLanguage(code: string): void {
    if (code === 'pl' || code === 'en' || code === 'de') {
      this.i18n.setLang(code as Lang);
    }
  }

  async loadAccount(): Promise<void> {
    try {
      await this.account.load();
    } catch {
      this.toast.error(this.t('common.actionFailed'));
    }
  }

  async saveProfile(): Promise<void> {
    if (!this.profileDirty() || this.profileSaving()) return;

    this.profileSaving.set(true);
    try {
      const initials = this.profileInitials().trim();
      const user = await this.account.updateProfile(
        initials
          ? { name: this.profileName().trim(), initials }
          : { name: this.profileName().trim() },
      );
      this.auth.setUser(user);
      this.toast.success(this.t('common.saved'));
    } catch (error) {
      this.toast.error(this.errorText(error));
    } finally {
      this.profileSaving.set(false);
    }
  }

  resetProfile(): void {
    const user = this.auth.user();
    if (!user) return;
    this.profileName.set(user.name);
    this.profileInitials.set(user.initials);
  }

  async changePassword(): Promise<void> {
    if (!this.passwordReady() || this.passwordSaving()) return;

    this.passwordSaving.set(true);
    this.passwordError.set('');
    try {
      await this.account.changePassword(this.currentPassword(), this.newPassword());
      this.currentPassword.set('');
      this.newPassword.set('');
      this.repeatPassword.set('');
      this.toast.success(this.t('account.passwordSaved'));
    } catch (error) {
      this.passwordError.set(this.accountErrorText(error));
    } finally {
      this.passwordSaving.set(false);
    }
  }

  openEmailDialog(): void {
    this.emailError.set('');
    this.emailDialogOpen.set(true);
  }

  async submitEmailChange(draft: EmailChangeDraft): Promise<void> {
    try {
      const change = await this.account.requestEmailChange(draft.email, draft.password);
      this.emailDialogOpen.set(false);
      this.toast.success(this.t('account.emailChangeSent', { email: change.newEmail }));
    } catch (error) {
      this.emailError.set(this.accountErrorText(error));
    }
  }

  async cancelEmailChange(): Promise<void> {
    try {
      await this.account.cancelEmailChange();
      this.toast.success(this.t('account.emailChangeCancelled'));
    } catch (error) {
      this.toast.error(this.errorText(error));
    }
  }

  async resendVerification(): Promise<void> {
    try {
      await this.auth.resendVerification();
      this.toast.success(this.t('account.verificationSent'));
    } catch (error) {
      this.toast.error(this.errorText(error));
    }
  }

  async toggleNotification(kind: string, channel: 'inApp' | 'email'): Promise<void> {
    const next: NotificationPrefDto[] = this.account
      .notificationPrefs()
      .map((pref) => (pref.kind === kind ? { ...pref, [channel]: !pref[channel] } : pref));

    try {
      await this.account.saveNotificationPrefs(next);
      this.toast.success(this.t('common.saved'));
    } catch (error) {
      this.toast.error(this.errorText(error));
    }
  }

  sessionName(session: SessionDto): string {
    return describeAgent(session.userAgent) || this.t('account.unknownDevice');
  }

  sessionSeen(session: SessionDto): string {
    return dateTime(session.lastSeenAt);
  }

  async revokeSession(session: SessionDto): Promise<void> {
    const confirmed = await this.confirm.ask({
      title: 'account.revokeSession',
      message: 'account.revokeSessionHint',
      confirmLabel: 'account.revoke',
      destructive: true,
    });
    if (!confirmed) return;

    try {
      await this.account.revokeSession(session.id);
      this.toast.success(this.t('account.sessionRevoked'));
    } catch (error) {
      this.toast.error(this.errorText(error));
    }
  }

  async revokeOtherSessions(): Promise<void> {
    const confirmed = await this.confirm.ask({
      title: 'account.revokeOthers',
      message: 'account.revokeOthersHint',
      confirmLabel: 'account.revoke',
      destructive: true,
    });
    if (!confirmed) return;

    try {
      const closed = await this.account.revokeOtherSessions();
      this.toast.success(this.t('account.sessionsRevoked', { count: closed }));
    } catch (error) {
      this.toast.error(this.errorText(error));
    }
  }

  async leaveOrganization(): Promise<void> {
    const confirmed = await this.confirm.ask({
      title: 'account.leaveTitle',
      message: 'account.leaveHint',
      confirmLabel: 'account.leave',
      destructive: true,
    });
    if (!confirmed) return;

    try {
      const memberships = await this.orgs.leaveActive();

      if (memberships.length) {
        window.location.assign('/app');
      } else {
        await this.router.navigate(['/orgs/new']);
      }
    } catch (error) {
      this.toast.error(this.errorText(error));
    }
  }

  openDeleteDialog(): void {
    this.deleteError.set('');
    this.deleteDialogOpen.set(true);
  }

  openOrgDeleteDialog(): void {
    this.orgDeleteError.set('');
    this.orgDeleteDialogOpen.set(true);
  }

  async deleteOrganization(password: string): Promise<void> {
    try {
      const memberships = await this.orgs.deleteActive(password);
      this.orgDeleteDialogOpen.set(false);

      if (memberships.length) {
        window.location.assign('/app');
      } else {
        await this.router.navigate(['/orgs/new']);
      }
    } catch (error) {
      this.orgDeleteError.set(this.accountErrorText(error));
    }
  }

  async deleteAccount(password: string): Promise<void> {
    try {
      await this.account.deleteAccount(password);
      this.deleteDialogOpen.set(false);
      this.auth.markSignedOut();
      window.location.assign('/');
    } catch (error) {
      this.deleteError.set(this.accountErrorText(error));
    }
  }

  private accountErrorText(error: unknown): string {
    const code = errorCode(error);
    if (code === 'PASSWORD_INVALID') return this.t('account.passwordWrong');
    if (code === 'OWNER_CANNOT_LEAVE') return this.t('account.ownerCannotLeave');
    if (code === 'OWNER_ONLY') return this.t('account.ownerOnly');
    if (code === 'PASSWORD_REUSED') return this.t('account.passwordReused');
    if (code === 'PASSWORD_TOO_COMMON') return this.t('account.passwordTooCommon');
    if (code === 'PASSWORD_NOT_SET') return this.t('account.passwordNotSet');
    if (code === 'EMAIL_TAKEN') return this.t('account.emailTaken');
    if (code === 'EMAIL_UNCHANGED') return this.t('account.emailUnchanged');
    if (code === 'EMAIL_DISPOSABLE') return this.t('account.emailDisposable');
    if (code === 'RATE_LIMITED') return this.t('account.tooManyRequests');
    return this.errorText(error);
  }

  private errorText(error: unknown): string {
    if (typeof error === 'object' && error !== null && 'error' in error) {
      const body = (error as { error?: { message?: string } }).error;
      if (body?.message) return body.message;
    }
    return this.t('common.actionFailed');
  }
}

function errorCode(error: unknown): string {
  if (typeof error !== 'object' || error === null || !('error' in error)) return '';
  const body = (error as { error?: { code?: string } }).error;
  return body?.code ?? '';
}

function describeAgent(userAgent: string | null): string {
  if (!userAgent) return '';

  const browser = /Edg\//.test(userAgent)
    ? 'Edge'
    : /Firefox\//.test(userAgent)
      ? 'Firefox'
      : /Chrome\//.test(userAgent)
        ? 'Chrome'
        : /Safari\//.test(userAgent)
          ? 'Safari'
          : '';

  const system = /Android/.test(userAgent)
    ? 'Android'
    : /iPhone|iPad/.test(userAgent)
      ? 'iOS'
      : /Mac OS X/.test(userAgent)
        ? 'macOS'
        : /Windows/.test(userAgent)
          ? 'Windows'
          : /Linux/.test(userAgent)
            ? 'Linux'
            : '';

  if (!browser && !system) return '';
  if (!system) return browser;
  if (!browser) return system;
  return `${browser}, ${system}`;
}

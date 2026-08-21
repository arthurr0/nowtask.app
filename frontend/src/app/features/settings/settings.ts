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
import { Router } from '@angular/router';
import { AccountService } from '../../core/account.service';
import { AuthService } from '../../core/auth.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { OrgService } from '../../core/org.service';
import { PrefsService } from '../../core/prefs.service';
import { dateTime } from '../../core/format';
import type { Accent, Density, Lang, RadiusStep, ThemeChoice } from '../../core/models';
import type {
  CustomFieldDto,
  NotificationPrefDto,
  SessionDto,
  StatusDto,
  WorkspaceSettingsDto,
} from '../../core/api-types';
import { TASK_FIELDS, customFieldKey } from '../../core/task-fields';
import { ViewState } from '../../data/view-state';
import { WorkspaceStore } from '../../data/workspace.store';
import { SettingsStore } from '../../data/feature.stores';
import { ConfirmService } from '../../ui/confirm.service';
import { Avatar } from '../../ui/avatar';
import { Icon } from '../../ui/icon';
import { Menu, type MenuItem } from '../../ui/menu';
import { PromptService } from '../../ui/prompt.service';
import { Switch } from '../../ui/switch';
import { ToastService } from '../../ui/toast.service';
import { Topbar } from '../../ui/topbar';
import { ViewControls } from '../../ui/view-controls';
import { PageState } from '../../ui/page-state';
import { TextField } from '../../ui/text-field';
import {
  CustomFieldDialog,
  StatusDialog,
  type CustomFieldDraft,
  type StatusDraft,
} from './settings-dialogs';
import { DeleteAccountDialog, EmailChangeDialog, type EmailChangeDraft } from './account-dialogs';

type Section =
  | 'profile'
  | 'security'
  | 'notifications'
  | 'appearance'
  | 'language'
  | 'fields'
  | 'taskFields'
  | 'flow'
  | 'epics';

interface TaskFieldRow {
  key: string;
  label: string;
  enabled: boolean;
  inherited: boolean;
}

const MIN_PASSWORD_LENGTH = 10;

const DATE_FORMATS = ['dd.MM.yyyy', 'yyyy-MM-dd', 'MM/dd/yyyy', 'd MMMM yyyy'];
const TIME_FORMATS = ['HH:mm', 'h:mm a'];
const WEEK_DAYS = [1, 7];
const TIME_ZONES = ['Europe/Warsaw', 'Europe/Berlin', 'Europe/London', 'UTC', 'America/New_York'];
const CURRENCIES = ['PLN', 'EUR', 'USD', 'GBP'];

@Component({
  selector: 'app-settings',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    Icon,
    Avatar,
    Switch,
    Menu,
    Topbar,
    ViewControls,
    PageState,
    TextField,
    CustomFieldDialog,
    StatusDialog,
    EmailChangeDialog,
    DeleteAccountDialog,
  ],
  templateUrl: './settings.html',
})
export class Settings implements OnInit {
  protected readonly store = inject(WorkspaceStore);
  protected readonly auth = inject(AuthService);
  protected readonly account = inject(AccountService);
  private readonly orgs = inject(OrgService);
  private readonly router = inject(Router);
  protected readonly view = inject(ViewState);
  protected readonly settings = inject(SettingsStore);
  protected readonly prefs = inject(PrefsService);
  private readonly confirm = inject(ConfirmService);
  private readonly prompt = inject(PromptService);
  private readonly toast = inject(ToastService);
  protected readonly i18n = inject(I18nService);
  protected readonly t = this.i18n.t;

  protected readonly section = signal<Section>('profile');
  protected readonly workspaceSettings = signal<WorkspaceSettingsDto | null>(null);

  protected readonly fieldScope = signal<string>('');

  protected readonly fieldScopeItems = computed<MenuItem[]>(() => [
    {
      id: '',
      label: this.t('settings.taskFieldsOrg'),
      checked: this.fieldScope() === '',
    },
    ...this.store.activeProjects().map((project) => ({
      id: project.id,
      label: `${project.name} (${project.code})`,
      checked: this.fieldScope() === project.id,
    })),
  ]);

  protected readonly fieldScopeLabel = computed(() => {
    const project = this.store.project(this.fieldScope() || null);
    return project ? `${project.name} (${project.code})` : this.t('settings.taskFieldsOrg');
  });

  protected readonly taskFieldRows = computed<TaskFieldRow[]>(() => {
    const scope = this.fieldScope() || null;
    const builtin = TASK_FIELDS.map((field) =>
      this.taskFieldRow(field.key, this.t(field.label), scope),
    );
    const custom = this.settings
      .customFields()
      .map((field) => this.taskFieldRow(customFieldKey(field.fieldKey), field.name, scope));
    return [...builtin, ...custom];
  });

  private taskFieldRow(key: string, label: string, scope: string | null): TaskFieldRow {
    const settings = this.store.taskFieldSettings();
    const own = settings.find(
      (setting) => setting.fieldKey === key && (setting.projectId ?? null) === scope,
    );
    const shared = settings.find(
      (setting) => setting.fieldKey === key && setting.projectId === null,
    );

    return {
      key,
      label,
      enabled: own ? own.enabled : shared ? shared.enabled : true,
      inherited: scope !== null && !own,
    };
  }

  protected async toggleTaskField(row: TaskFieldRow): Promise<void> {
    await this.saveTaskField(row.key, !row.enabled);
  }

  protected async inheritTaskField(row: TaskFieldRow): Promise<void> {
    await this.saveTaskField(row.key, null);
  }

  private async saveTaskField(key: string, value: boolean | null): Promise<void> {
    try {
      await this.store.updateTaskFieldSettings(this.fieldScope() || null, { [key]: value });
      this.toast.success(this.t('settings.saved'));
    } catch (error) {
      this.toast.error(this.errorText(error));
    }
  }

  protected readonly fieldDialogOpen = signal(false);
  protected readonly editedField = signal<CustomFieldDto | null>(null);
  protected readonly statusDialogOpen = signal(false);
  protected readonly transitionFrom = signal('');
  protected readonly transitionTo = signal('');
  protected readonly editedStatus = signal<StatusDto | null>(null);

  protected readonly accountSections: readonly { id: Section; icon: string; label: string }[] = [
    { id: 'profile', icon: 'user', label: 'account.profile' },
    { id: 'security', icon: 'lock', label: 'account.security' },
    { id: 'notifications', icon: 'bell', label: 'account.notifications' },
  ];

  protected readonly personalSections: readonly { id: Section; icon: string; label: string }[] = [
    { id: 'appearance', icon: 'sun', label: 'settings.appearance' },
    { id: 'language', icon: 'globe', label: 'settings.langRegion' },
  ];

  protected readonly structureSections: readonly { id: Section; icon: string; label: string }[] = [
    { id: 'fields', icon: 'sliders', label: 'settings.customFields' },
    { id: 'taskFields', icon: 'filter', label: 'settings.taskFields' },
    { id: 'flow', icon: 'board', label: 'settings.statusesFlow' },
    { id: 'epics', icon: 'layers', label: 'settings.epics' },
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

  protected readonly fieldMenu: readonly MenuItem[] = [
    { id: 'edit', label: 'common.edit', icon: 'pencil' },
    { id: 'delete', label: 'common.delete', icon: 'trash', danger: true, separatorBefore: true },
  ];

  protected readonly epicMenu: readonly MenuItem[] = [
    { id: 'rename', label: 'nav.renameView', icon: 'pencil' },
    { id: 'delete', label: 'common.delete', icon: 'trash', danger: true, separatorBefore: true },
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
    void this.settings.load();
    void this.loadWorkspaceSettings();
    void this.loadAccount();
  }

  reload(): void {
    void this.settings.load();
  }

  selectLanguage(code: string): void {
    if (code === 'pl' || code === 'en' || code === 'de') {
      this.i18n.setLang(code as Lang);
    }
  }

  statusMenu(index: number, total: number): MenuItem[] {
    return [
      { id: 'edit', label: 'common.edit', icon: 'pencil' },
      { id: 'up', label: 'settings.moveUp', icon: 'up', disabled: index === 0 },
      { id: 'down', label: 'settings.moveDown', icon: 'down', disabled: index === total - 1 },
      { id: 'delete', label: 'common.delete', icon: 'trash', danger: true, separatorBefore: true },
    ];
  }

  dateFormatItems(): MenuItem[] {
    return DATE_FORMATS.map((format) => ({
      id: format,
      label: format,
      checked: this.workspaceSettings()?.dateFormat === format,
    }));
  }

  timeFormatItems(): MenuItem[] {
    return TIME_FORMATS.map((format) => ({
      id: format,
      label: format,
      checked: this.workspaceSettings()?.timeFormat === format,
    }));
  }

  weekDayItems(): MenuItem[] {
    return WEEK_DAYS.map((day) => ({
      id: String(day),
      label: this.t(day === 1 ? 'settings.monday' : 'settings.sunday'),
      checked: this.workspaceSettings()?.firstDayOfWeek === day,
    }));
  }

  weekDayLabel(day: number): string {
    return this.t(day === 1 ? 'settings.monday' : 'settings.sunday');
  }

  timeZoneItems(): MenuItem[] {
    return TIME_ZONES.map((zone) => ({
      id: zone,
      label: zone,
      checked: this.workspaceSettings()?.timeZone === zone,
    }));
  }

  currencyItems(): MenuItem[] {
    return CURRENCIES.map((currency) => ({
      id: currency,
      label: currency,
      checked: this.workspaceSettings()?.currency === currency,
    }));
  }

  async changeSprint(current: string | null): Promise<void> {
    const sprint = await this.prompt.ask({
      title: 'settings.currentSprint',
      message: 'settings.currentSprintHint',
      label: 'settings.currentSprint',
      value: current ?? '',
      required: false,
    });
    if (sprint === null || sprint === (current ?? '')) return;
    await this.patchWorkspace({ currentSprint: sprint });
  }

  async patchWorkspace(body: Record<string, unknown>): Promise<void> {
    try {
      this.workspaceSettings.set(await this.store.patchWorkspaceSettings(body));
      this.toast.success(this.t('settings.saved'));
    } catch {
      this.toast.error(this.t('common.actionFailed'));
    }
  }

  openFieldDialog(field: CustomFieldDto | null): void {
    this.editedField.set(field);
    this.fieldDialogOpen.set(true);
  }

  closeFieldDialog(): void {
    this.fieldDialogOpen.set(false);
    this.editedField.set(null);
  }

  async saveField(draft: CustomFieldDraft): Promise<void> {
    const edited = this.editedField();
    try {
      if (edited) {
        await this.store.updateCustomField(edited.id, {
          name: draft.name,
          type: draft.type,
          scopeLabel: draft.scopeLabel,
          requiredPermission: draft.requiredPermission,
        });
      } else {
        await this.store.createCustomField(draft);
      }
      await this.settings.load();
      this.closeFieldDialog();
      this.toast.success(this.t('settings.saved'));
    } catch (error) {
      this.toast.error(this.errorText(error));
    }
  }

  async onFieldMenu(item: MenuItem, field: CustomFieldDto): Promise<void> {
    if (item.id === 'edit') {
      this.openFieldDialog(field);
      return;
    }
    const confirmed = await this.confirm.askDelete(field.name);
    if (!confirmed) return;
    try {
      await this.store.deleteCustomField(field.id);
      await this.settings.load();
      this.toast.success(this.t('settings.fieldDeleted', { name: field.name }));
    } catch (error) {
      this.toast.error(this.errorText(error));
    }
  }

  openStatusDialog(status: StatusDto | null): void {
    this.editedStatus.set(status);
    this.statusDialogOpen.set(true);
  }

  closeStatusDialog(): void {
    this.statusDialogOpen.set(false);
    this.editedStatus.set(null);
  }

  async saveStatus(draft: StatusDraft): Promise<void> {
    const edited = this.editedStatus();
    try {
      if (edited) {
        await this.store.updateStatus(edited.id, {
          label: draft.label,
          category: draft.category,
          wipLimit: draft.wipLimit,
        });
      } else {
        await this.store.createStatus(draft);
      }
      this.closeStatusDialog();
      this.toast.success(this.t('settings.saved'));
    } catch (error) {
      this.toast.error(this.errorText(error));
    }
  }

  async onStatusMenu(item: MenuItem, status: StatusDto, index: number): Promise<void> {
    if (item.id === 'edit') {
      this.openStatusDialog(status);
      return;
    }
    if (item.id === 'up' || item.id === 'down') {
      const ids = this.store.boardStatuses().map((entry) => entry.id);
      const target = item.id === 'up' ? index - 1 : index + 1;
      if (target < 0 || target >= ids.length) return;
      [ids[index], ids[target]] = [ids[target], ids[index]];
      try {
        await this.store.reorderStatuses(ids);
      } catch (error) {
        this.toast.error(this.errorText(error));
      }
      return;
    }
    const confirmed = await this.confirm.askDelete(this.store.statusName(status));
    if (!confirmed) return;
    try {
      await this.store.deleteStatus(status.id);
      this.toast.success(this.t('settings.statusDeleted'));
    } catch (error) {
      this.toast.error(this.errorText(error));
    }
  }

  async addTransition(): Promise<void> {
    const from = this.transitionFrom();
    const to = this.transitionTo();
    if (!from || !to) {
      this.toast.error(this.t('settings.transitionPick'));
      return;
    }
    if (from === to) {
      this.toast.error(this.t('settings.transitionSame'));
      return;
    }
    try {
      await this.store.createTransition(from, to, null);
      this.transitionFrom.set('');
      this.transitionTo.set('');
      this.toast.success(this.t('settings.saved'));
    } catch (error) {
      this.toast.error(this.errorText(error));
    }
  }

  statusPickItems(current: string): MenuItem[] {
    return this.store.boardStatuses().map((status) => ({
      id: status.id,
      label: this.store.statusName(status),
      checked: status.id === current,
    }));
  }

  statusName(id: string): string {
    const status = this.store.status(id);
    return status ? this.store.statusName(status) : this.t('ui.select.placeholder');
  }

  async removeTransition(id: string): Promise<void> {
    const confirmed = await this.confirm.ask({
      title: 'settings.removeTransition',
      message: 'settings.removeTransitionHint',
      confirmLabel: 'common.delete',
      destructive: true,
    });
    if (!confirmed) return;
    try {
      await this.store.deleteTransition(id);
      this.toast.success(this.t('settings.saved'));
    } catch (error) {
      this.toast.error(this.errorText(error));
    }
  }

  async addEpic(): Promise<void> {
    const name = await this.prompt.ask({
      title: 'settings.addEpic',
      label: 'settings.epicName',
      placeholder: 'settings.epicNamePlaceholder',
    });
    if (!name) return;
    try {
      await this.store.createEpic(name, this.view.projectId());
      this.toast.success(this.t('settings.saved'));
    } catch (error) {
      this.toast.error(this.errorText(error));
    }
  }

  async onEpicMenu(item: MenuItem, epicId: string, name: string): Promise<void> {
    if (item.id === 'rename') {
      const next = await this.prompt.ask({
        title: 'nav.renameView',
        label: 'settings.epicName',
        value: name,
      });
      if (!next) return;
      try {
        await this.store.renameEpic(epicId, next);
        this.toast.success(this.t('settings.saved'));
      } catch (error) {
        this.toast.error(this.errorText(error));
      }
      return;
    }
    const confirmed = await this.confirm.askDelete(name);
    if (!confirmed) return;
    try {
      await this.store.deleteEpic(epicId);
      this.toast.success(this.t('settings.epicDeleted'));
    } catch (error) {
      this.toast.error(this.errorText(error));
    }
  }

  epicTaskCount(epicId: string): number {
    return this.store.tasks().filter((task) => task.epicId === epicId).length;
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
      this.toast.success(this.t('settings.saved'));
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
      this.toast.success(this.t('settings.saved'));
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
      await this.account.leaveOrganization();
      const memberships = await this.orgs.refresh(true);

      if (memberships.length) {
        window.location.assign('/app/board');
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
    if (code === 'PASSWORD_REUSED') return this.t('account.passwordReused');
    if (code === 'PASSWORD_TOO_COMMON') return this.t('account.passwordTooCommon');
    if (code === 'PASSWORD_NOT_SET') return this.t('account.passwordNotSet');
    if (code === 'EMAIL_TAKEN') return this.t('account.emailTaken');
    if (code === 'EMAIL_UNCHANGED') return this.t('account.emailUnchanged');
    if (code === 'EMAIL_DISPOSABLE') return this.t('account.emailDisposable');
    if (code === 'RATE_LIMITED') return this.t('account.tooManyRequests');
    return this.errorText(error);
  }

  private async loadWorkspaceSettings(): Promise<void> {
    try {
      this.workspaceSettings.set(await this.store.loadWorkspaceSettings());
    } catch {
      this.workspaceSettings.set(null);
    }
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

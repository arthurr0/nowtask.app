import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { I18nService } from '../../core/i18n/i18n.service';
import { dateTime, shortDate } from '../../core/format';
import type {
  ApiKeyDto,
  CustomFieldDto,
  IntegrationDto,
  PermissionDto,
  RoleDto,
  StatusDto,
  TaskViewCode,
  TeamDto,
  UserDto,
  WorkspaceSettingsDto,
} from '../../core/api-types';
import { TASK_FIELDS, customFieldKey } from '../../core/task-fields';
import { TASK_VIEWS } from '../../core/task-views';
import {
  OrganizationStore,
  IntegrationsStore,
  MetricsStore,
  SettingsStore,
} from '../../data/feature.stores';
import { ViewState } from '../../data/view-state';
import { WorkspaceStore } from '../../data/workspace.store';
import { Avatar } from '../../ui/avatar';
import { ConfirmService } from '../../ui/confirm.service';
import { Icon } from '../../ui/icon';
import { Menu, type MenuItem } from '../../ui/menu';
import { PromptService } from '../../ui/prompt.service';
import { Switch } from '../../ui/switch';
import { ToastService } from '../../ui/toast.service';
import { Topbar } from '../../ui/topbar';
import { ViewControls } from '../../ui/view-controls';
import { PageState } from '../../ui/page-state';
import { InviteDialog, type InviteDraft } from './organization-dialogs';
import { IntegrationDialog, type IntegrationDraft } from './integration-dialog';
import {
  CustomFieldDialog,
  StatusDialog,
  type CustomFieldDraft,
  type StatusDraft,
} from './workspace-dialogs';
import { ApiKeyDialog, type ApiKeyDraft } from '../agents/api-key-dialog';

type Section =
  | 'people'
  | 'roles'
  | 'fields'
  | 'taskFields'
  | 'taskViews'
  | 'flow'
  | 'epics'
  | 'regional'
  | 'keys'
  | 'integrations'
  | 'audit';

interface SectionItem {
  id: Section;
  icon: string;
  label: string;
}

interface TaskFieldRow {
  key: string;
  label: string;
  enabled: boolean;
  inherited: boolean;
}

interface TaskViewRow {
  code: TaskViewCode;
  label: string;
  icon: string;
  enabled: boolean;
  inherited: boolean;
}

const SECTION_GROUPS: readonly { label: string; items: readonly SectionItem[] }[] = [
  {
    label: 'organization.people',
    items: [
      { id: 'people', icon: 'users', label: 'organization.peopleRoles' },
      { id: 'roles', icon: 'shield', label: 'organization.rolePermissions' },
    ],
  },
  {
    label: 'organization.workStructure',
    items: [
      { id: 'fields', icon: 'sliders', label: 'organization.customFields' },
      { id: 'taskFields', icon: 'filter', label: 'organization.taskFields' },
      { id: 'taskViews', icon: 'calendar', label: 'organization.taskViews' },
      { id: 'flow', icon: 'board', label: 'organization.statusesFlow' },
      { id: 'epics', icon: 'layers', label: 'organization.epics' },
    ],
  },
  {
    label: 'nav.organization',
    items: [
      { id: 'regional', icon: 'globe', label: 'organization.regional' },
      { id: 'keys', icon: 'key', label: 'organization.apiKeys' },
      { id: 'integrations', icon: 'link', label: 'organization.integrations' },
      { id: 'audit', icon: 'log', label: 'organization.auditLog' },
    ],
  },
];

const SECTIONS: readonly SectionItem[] = SECTION_GROUPS.flatMap((group) => [...group.items]);

const DATE_FORMATS = ['dd.MM.yyyy', 'yyyy-MM-dd', 'MM/dd/yyyy', 'd MMMM yyyy'];
const TIME_FORMATS = ['HH:mm', 'h:mm a'];
const WEEK_DAYS = [1, 7];
const TIME_ZONES = ['Europe/Warsaw', 'Europe/Berlin', 'Europe/London', 'UTC', 'America/New_York'];
const CURRENCIES = ['PLN', 'EUR', 'USD', 'GBP'];

@Component({
  selector: 'app-organization',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    Icon,
    Avatar,
    Menu,
    Switch,
    Topbar,
    ViewControls,
    PageState,
    InviteDialog,
    ApiKeyDialog,
    IntegrationDialog,
    CustomFieldDialog,
    StatusDialog,
    RouterLink,
  ],
  templateUrl: './organization.html',
})
export class Organization implements OnInit {
  protected readonly store = inject(WorkspaceStore);
  protected readonly organization = inject(OrganizationStore);
  protected readonly metrics = inject(MetricsStore);
  protected readonly integrations = inject(IntegrationsStore);
  protected readonly fields = inject(SettingsStore);
  private readonly view = inject(ViewState);
  private readonly confirm = inject(ConfirmService);
  private readonly prompt = inject(PromptService);
  private readonly toast = inject(ToastService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  protected readonly t = inject(I18nService).t;

  protected readonly section = signal<Section>('people');
  protected readonly search = signal('');
  protected readonly inviteOpen = signal(false);
  protected readonly keyOpen = signal(false);
  protected readonly integrationOpen = signal(false);
  protected readonly issuedKey = signal<string | null>(null);
  protected readonly teamMembers = signal<Record<string, string[]>>({});

  protected readonly workspaceSettings = signal<WorkspaceSettingsDto | null>(null);
  protected readonly fieldScope = signal<string>('');
  protected readonly viewScope = signal<string>('');
  protected readonly fieldDialogOpen = signal(false);
  protected readonly editedField = signal<CustomFieldDto | null>(null);
  protected readonly statusDialogOpen = signal(false);
  protected readonly editedStatus = signal<StatusDto | null>(null);
  protected readonly transitionFrom = signal('');
  protected readonly transitionTo = signal('');

  protected readonly sectionGroups = SECTION_GROUPS;
  protected readonly sections = SECTIONS;

  protected readonly fieldMenu: readonly MenuItem[] = [
    { id: 'edit', label: 'common.edit', icon: 'pencil' },
    { id: 'delete', label: 'common.delete', icon: 'trash', danger: true, separatorBefore: true },
  ];

  protected readonly epicMenu: readonly MenuItem[] = [
    { id: 'rename', label: 'nav.renameView', icon: 'pencil' },
    { id: 'delete', label: 'common.delete', icon: 'trash', danger: true, separatorBefore: true },
  ];

  protected readonly roles = computed<RoleDto[]>(() => this.organization.roles());
  protected readonly isAdmin = computed(() => this.store.currentUser()?.role?.code === 'admin');

  protected readonly visibleMembers = computed(() => {
    const needle = this.search().trim().toLowerCase();
    if (!needle) return this.organization.members();
    return this.organization
      .members()
      .filter(
        (user) =>
          user.name.toLowerCase().includes(needle) || user.email.toLowerCase().includes(needle),
      );
  });

  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    const requested = params.get('section');
    const installation = params.get('installation_id');

    void this.organization.load();
    void this.metrics.load();
    void this.fields.load();

    this.setSection(
      SECTIONS.some((item) => item.id === requested)
        ? (requested as Section)
        : installation
          ? 'integrations'
          : 'people',
    );

    if (installation) {
      void this.finishGitHubInstall(installation, params.get('code') ?? '');
    }
  }

  workloadOf(userId: string): { points: number; capacity: number } | null {
    return this.metrics.overview()?.workload.find((row) => row.userId === userId) ?? null;
  }

  isOverloaded(userId: string): boolean {
    const row = this.workloadOf(userId);
    return row !== null && row.capacity > 0 && row.points > row.capacity;
  }

  invitedLabel(user: UserDto): string {
    return user.invitedOn
      ? this.t('organization.invitedOn', { date: shortDate(user.invitedOn) })
      : this.t('organization.invitePending');
  }

  workloadPercent(userId: string): number {
    const row = this.workloadOf(userId);
    if (!row || row.capacity <= 0) return 0;
    return Math.min(100, Math.round((row.points / row.capacity) * 100));
  }

  reload(): void {
    void this.organization.load();
  }

  setSection(section: Section): void {
    this.section.set(section);
    if (section === 'audit' && !this.organization.audit()) {
      void this.loadAudit(0);
    }
    if (section === 'integrations') {
      void this.integrations.load();
      void this.integrations.loadGitHub().catch(() => undefined);
    }
    if (section === 'flow' || section === 'regional') {
      void this.loadWorkspaceSettings();
    }
  }

  reloadFields(): void {
    void this.fields.load();
  }

  integrationMenu(integration: IntegrationDto): MenuItem[] {
    return [
      { id: 'test', label: 'organization.integrationTest', icon: 'play' },
      {
        id: 'toggle',
        label: integration.enabled
          ? 'organization.integrationDisable'
          : 'organization.integrationEnable',
        icon: integration.enabled ? 'lock' : 'check',
      },
      {
        id: 'remove',
        label: 'organization.integrationRemove',
        icon: 'trash',
        danger: true,
        separatorBefore: true,
      },
    ];
  }

  async onIntegrationMenu(item: MenuItem, integration: IntegrationDto): Promise<void> {
    if (item.id === 'test') {
      try {
        const result = await this.integrations.test(integration.id);
        if (result.ok) {
          this.toast.success(this.t('organization.integrationTestOk', { detail: result.detail }));
        } else {
          this.toast.error(this.t('organization.integrationTestFailed', { detail: result.detail }));
        }
      } catch (error) {
        this.toast.error(this.errorText(error));
      }
      return;
    }

    if (item.id === 'toggle') {
      await this.run(() =>
        this.integrations.update(integration.id, { enabled: !integration.enabled }),
      );
      return;
    }

    const confirmed = await this.confirm.ask({
      title: 'organization.integrationRemove',
      message: this.t('organization.integrationRemoveLead', { name: integration.name }),
      confirmLabel: 'common.delete',
      destructive: true,
    });
    if (!confirmed) return;

    await this.run(() => this.integrations.remove(integration.id));
  }

  async createIntegration(draft: IntegrationDraft): Promise<void> {
    this.integrationOpen.set(false);
    await this.run(() =>
      this.integrations.create({ kind: draft.kind, name: draft.name, config: draft.config }),
    );
  }

  integrationTarget(integration: IntegrationDto): string {
    const config = integration.config ?? {};

    if (integration.kind === 'github') {
      const repos = Array.isArray(config['repos']) ? (config['repos'] as unknown[]) : [];
      const issueRepo = config['issueRepo'];
      const named = [...repos.map(String), issueRepo ? String(issueRepo) : ''].filter(Boolean);
      return named.length ? [...new Set(named)].join(', ') : this.t('organization.github.allRepos');
    }

    const value = integration.kind === 'webhook' ? config['url'] : config['to'];
    return value === undefined || value === null ? '' : String(value);
  }

  integrationIcon(integration: IntegrationDto): string {
    if (integration.kind === 'github') return 'git-branch';
    return integration.kind === 'webhook' ? 'link' : 'message';
  }

  protected shortDateTime = dateTime;
  protected shortDate = shortDate;

  grants(role: RoleDto, permission: PermissionDto): boolean {
    return role.permissions.includes(permission.code);
  }

  protected readonly permissionGroups = computed<{ group: string; items: PermissionDto[] }[]>(
    () => {
      const groups = new Map<string, PermissionDto[]>();
      for (const permission of this.organization.permissions()) {
        const items = groups.get(permission.group) ?? [];
        items.push(permission);
        groups.set(permission.group, items);
      }
      return [...groups].map(([group, items]) => ({ group, items }));
    },
  );

  memberMenu(user: UserDto): MenuItem[] {
    const items: MenuItem[] = this.organization.roles().map((role) => ({
      id: 'role:' + role.code,
      label: this.t('organization.setRole', { role: role.name }),
      checked: user.role?.code === role.code,
    }));
    items.push({
      id: 'capacity',
      label: 'organization.setCapacity',
      icon: 'chart',
      separatorBefore: true,
    });
    items.push({
      id: 'toggle',
      label: user.pending ? 'organization.activate' : 'organization.suspend',
      icon: user.pending ? 'check' : 'lock',
    });
    items.push({
      id: 'remove',
      label: 'organization.removeMember',
      icon: 'trash',
      danger: true,
      separatorBefore: true,
    });
    return items;
  }

  teamMenu(): MenuItem[] {
    return [
      { id: 'rename', label: 'nav.renameView', icon: 'pencil' },
      { id: 'members', label: 'organization.manageMembers', icon: 'users' },
      { id: 'delete', label: 'common.delete', icon: 'trash', danger: true, separatorBefore: true },
    ];
  }

  keyMenu(key: ApiKeyDto): MenuItem[] {
    return [
      { id: 'copy', label: 'organization.copyPrefix', icon: 'copy' },
      {
        id: 'revoke',
        label: 'organization.revokeKey',
        icon: 'trash',
        danger: true,
        disabled: key.revokedAt !== null,
        separatorBefore: true,
      },
    ];
  }

  async onMemberMenu(item: MenuItem, user: UserDto): Promise<void> {
    if (item.id.startsWith('role:')) {
      const role = item.id.slice('role:'.length);
      if (role === user.role?.code) return;
      await this.run(() => this.organization.updateMember(user.id, { role }));
      return;
    }
    if (item.id === 'capacity') {
      const value = await this.prompt.ask({
        title: 'organization.setCapacity',
        message: 'organization.capacityHint',
        label: 'organization.capacity',
        value: String(user.capacity),
      });
      if (value === null) return;
      const parsed = Number.parseInt(value, 10);
      if (!Number.isFinite(parsed) || parsed < 0) {
        this.toast.error(this.t('task.numberInvalid'));
        return;
      }
      await this.run(() => this.organization.updateMember(user.id, { capacity: parsed }));
      return;
    }
    if (item.id === 'toggle') {
      await this.run(() => this.organization.updateMember(user.id, { pending: !user.pending }));
      return;
    }
    const confirmed = await this.confirm.ask({
      title: 'organization.removeMember',
      message: this.t('organization.removeMemberHint', { name: user.name }),
      confirmLabel: 'common.delete',
      destructive: true,
    });
    if (!confirmed) return;
    await this.run(() => this.organization.removeMember(user.id));
  }

  async invite(draft: InviteDraft): Promise<void> {
    try {
      const created = await this.organization.invite(draft.name, draft.email, draft.role);
      this.inviteOpen.set(false);
      this.toast.success(this.t('organization.invited', { email: created.email }));
    } catch (error) {
      this.toast.error(this.errorText(error));
    }
  }

  async addTeam(): Promise<void> {
    const name = await this.prompt.ask({
      title: 'organization.newTeam',
      label: 'organization.teamName',
      placeholder: 'organization.teamNamePlaceholder',
    });
    if (!name) return;
    await this.run(() => this.organization.createTeam(name));
  }

  async onTeamMenu(item: MenuItem, team: TeamDto): Promise<void> {
    if (item.id === 'rename') {
      const name = await this.prompt.ask({
        title: 'nav.renameView',
        label: 'organization.teamName',
        value: team.name,
      });
      if (!name) return;
      await this.run(() => this.organization.renameTeam(team.id, name));
      return;
    }
    if (item.id === 'members') {
      await this.loadTeamMembers(team.id);
      return;
    }
    const confirmed = await this.confirm.askDelete(team.name);
    if (!confirmed) return;
    await this.run(() => this.organization.deleteTeam(team.id));
  }

  async loadTeamMembers(teamId: string): Promise<void> {
    if (this.teamMembers()[teamId]) {
      this.teamMembers.update((current) => {
        const next = { ...current };
        delete next[teamId];
        return next;
      });
      return;
    }
    try {
      const members = await this.organization.teamMembers(teamId);
      this.teamMembers.update((current) => ({ ...current, [teamId]: members }));
    } catch (error) {
      this.toast.error(this.errorText(error));
    }
  }

  isInTeam(teamId: string, userId: string): boolean {
    return (this.teamMembers()[teamId] ?? []).includes(userId);
  }

  expandedTeam(teamId: string): boolean {
    return teamId in this.teamMembers();
  }

  async toggleTeamMember(teamId: string, userId: string): Promise<void> {
    const inTeam = this.isInTeam(teamId, userId);
    try {
      if (inTeam) await this.organization.removeTeamMember(teamId, userId);
      else await this.organization.addTeamMember(teamId, userId);
      this.teamMembers.update((current) => ({
        ...current,
        [teamId]: inTeam
          ? (current[teamId] ?? []).filter((id) => id !== userId)
          : [...(current[teamId] ?? []), userId],
      }));
    } catch (error) {
      this.toast.error(this.errorText(error));
    }
  }

  async createKey(draft: ApiKeyDraft): Promise<void> {
    try {
      const issued = await this.organization.createApiKey(
        draft.label,
        draft.scopes,
        draft.expiresInDays,
      );
      this.keyOpen.set(false);
      this.issuedKey.set(issued.key);
    } catch (error) {
      this.toast.error(this.errorText(error));
    }
  }

  async copyIssuedKey(): Promise<void> {
    const key = this.issuedKey();
    if (!key) return;
    try {
      await navigator.clipboard.writeText(key);
      this.toast.success(this.t('organization.keyCopied'));
    } catch {
      this.toast.error(this.t('task.linkCopyFailed'));
    }
  }

  async onKeyMenu(item: MenuItem, key: ApiKeyDto): Promise<void> {
    if (item.id === 'copy') {
      try {
        await navigator.clipboard.writeText(key.prefix);
        this.toast.success(this.t('organization.keyCopied'));
      } catch {
        this.toast.error(this.t('task.linkCopyFailed'));
      }
      return;
    }
    const confirmed = await this.confirm.ask({
      title: 'organization.revokeKey',
      message: this.t('organization.revokeKeyHint', { label: key.label }),
      confirmLabel: 'organization.revokeKey',
      destructive: true,
    });
    if (!confirmed) return;
    await this.run(() => this.organization.revokeApiKey(key.id));
  }

  async loadAudit(page: number): Promise<void> {
    try {
      await this.organization.loadAudit(page);
    } catch (error) {
      this.toast.error(this.errorText(error));
    }
  }

  auditPages(): number {
    const total = this.organization.audit()?.total ?? 0;
    return Math.max(1, Math.ceil(total / 50));
  }

  protected readonly fieldScopeItems = computed<MenuItem[]>(() => [
    {
      id: '',
      label: this.t('organization.taskFieldsOrg'),
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
    return project ? `${project.name} (${project.code})` : this.t('organization.taskFieldsOrg');
  });

  protected readonly taskFieldRows = computed<TaskFieldRow[]>(() => {
    const scope = this.fieldScope() || null;
    const builtin = TASK_FIELDS.map((field) =>
      this.taskFieldRow(field.key, this.t(field.label), scope),
    );
    const custom = this.fields
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

  async toggleTaskField(row: TaskFieldRow): Promise<void> {
    await this.saveTaskField(row.key, !row.enabled);
  }

  async inheritTaskField(row: TaskFieldRow): Promise<void> {
    await this.saveTaskField(row.key, null);
  }

  private async saveTaskField(key: string, value: boolean | null): Promise<void> {
    await this.run(() =>
      this.store.updateTaskFieldSettings(this.fieldScope() || null, { [key]: value }),
    );
  }

  protected readonly viewScopeItems = computed<MenuItem[]>(() => [
    {
      id: '',
      label: this.t('organization.taskFieldsOrg'),
      checked: this.viewScope() === '',
    },
    ...this.store.activeProjects().map((project) => ({
      id: project.id,
      label: `${project.name} (${project.code})`,
      checked: this.viewScope() === project.id,
    })),
  ]);

  protected readonly viewScopeLabel = computed(() => {
    const project = this.store.project(this.viewScope() || null);
    return project ? `${project.name} (${project.code})` : this.t('organization.taskFieldsOrg');
  });

  protected readonly taskViewRows = computed<TaskViewRow[]>(() => {
    const scope = this.viewScope() || null;
    const settings = this.store.taskViewSettings();

    return TASK_VIEWS.map((view) => {
      const own = settings.find(
        (setting) => setting.viewCode === view.code && (setting.projectId ?? null) === scope,
      );
      const shared = settings.find(
        (setting) => setting.viewCode === view.code && setting.projectId === null,
      );

      return {
        code: view.code,
        label: this.t(view.label),
        icon: view.icon,
        enabled: own ? own.enabled : shared ? shared.enabled : true,
        inherited: scope !== null && !own,
      };
    });
  });

  protected readonly lastEnabledView = computed(
    () => this.taskViewRows().filter((row) => row.enabled).length <= 1,
  );

  async toggleTaskView(row: TaskViewRow): Promise<void> {
    await this.saveTaskView(row.code, !row.enabled);
  }

  async inheritTaskView(row: TaskViewRow): Promise<void> {
    await this.saveTaskView(row.code, null);
  }

  private async saveTaskView(code: TaskViewCode, value: boolean | null): Promise<void> {
    await this.run(() =>
      this.store.updateTaskViewSettings(this.viewScope() || null, { [code]: value }),
    );
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
      await this.fields.load();
      this.closeFieldDialog();
      this.toast.success(this.t('common.saved'));
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
      await this.fields.load();
      this.toast.success(this.t('organization.fieldDeleted', { name: field.name }));
    } catch (error) {
      this.toast.error(this.errorText(error));
    }
  }

  statusMenu(index: number, total: number): MenuItem[] {
    return [
      { id: 'edit', label: 'common.edit', icon: 'pencil' },
      { id: 'up', label: 'common.moveUp', icon: 'up', disabled: index === 0 },
      { id: 'down', label: 'common.moveDown', icon: 'down', disabled: index === total - 1 },
      { id: 'delete', label: 'common.delete', icon: 'trash', danger: true, separatorBefore: true },
    ];
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
      this.toast.success(this.t('common.saved'));
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
      this.toast.success(this.t('organization.statusDeleted'));
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

  async addTransition(): Promise<void> {
    const from = this.transitionFrom();
    const to = this.transitionTo();
    if (!from || !to) {
      this.toast.error(this.t('organization.transitionPick'));
      return;
    }
    if (from === to) {
      this.toast.error(this.t('organization.transitionSame'));
      return;
    }
    try {
      await this.store.createTransition(from, to, null);
      this.transitionFrom.set('');
      this.transitionTo.set('');
      this.toast.success(this.t('common.saved'));
    } catch (error) {
      this.toast.error(this.errorText(error));
    }
  }

  async removeTransition(id: string): Promise<void> {
    const confirmed = await this.confirm.ask({
      title: 'organization.removeTransition',
      message: 'organization.removeTransitionHint',
      confirmLabel: 'common.delete',
      destructive: true,
    });
    if (!confirmed) return;
    await this.run(() => this.store.deleteTransition(id));
  }

  async addEpic(): Promise<void> {
    const name = await this.prompt.ask({
      title: 'organization.addEpic',
      label: 'organization.epicName',
      placeholder: 'organization.epicNamePlaceholder',
    });
    if (!name) return;
    await this.run(() => this.store.createEpic(name, this.view.projectId()));
  }

  async onEpicMenu(item: MenuItem, epicId: string, name: string): Promise<void> {
    if (item.id === 'rename') {
      const next = await this.prompt.ask({
        title: 'nav.renameView',
        label: 'organization.epicName',
        value: name,
      });
      if (!next) return;
      await this.run(() => this.store.renameEpic(epicId, next));
      return;
    }
    const confirmed = await this.confirm.askDelete(name);
    if (!confirmed) return;
    try {
      await this.store.deleteEpic(epicId);
      this.toast.success(this.t('organization.epicDeleted'));
    } catch (error) {
      this.toast.error(this.errorText(error));
    }
  }

  epicTaskCount(epicId: string): number {
    return this.store.tasks().filter((task) => task.epicId === epicId).length;
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
      label: this.weekDayLabel(day),
      checked: this.workspaceSettings()?.firstDayOfWeek === day,
    }));
  }

  weekDayLabel(day: number): string {
    return this.t(day === 1 ? 'organization.monday' : 'organization.sunday');
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
      title: 'organization.currentSprint',
      message: 'organization.currentSprintHint',
      label: 'organization.currentSprint',
      value: current ?? '',
      required: false,
    });
    if (sprint === null || sprint === (current ?? '')) return;
    await this.patchWorkspace({ currentSprint: sprint });
  }

  async patchWorkspace(body: Record<string, unknown>): Promise<void> {
    try {
      this.workspaceSettings.set(await this.store.patchWorkspaceSettings(body));
      this.toast.success(this.t('common.saved'));
    } catch (error) {
      this.toast.error(this.errorText(error));
    }
  }

  private async loadWorkspaceSettings(): Promise<void> {
    try {
      this.workspaceSettings.set(await this.store.loadWorkspaceSettings());
    } catch {
      this.workspaceSettings.set(null);
    }
  }

  installGitHub(): void {
    const url = this.integrations.gitHub()?.installUrl;
    if (url) window.location.href = url;
  }

  async disconnectGitHub(): Promise<void> {
    const confirmed = await this.confirm.ask({
      title: 'organization.github.disconnect',
      message: this.t('organization.github.disconnectLead'),
      confirmLabel: 'organization.github.disconnect',
      destructive: true,
    });
    if (!confirmed) return;

    await this.run(() => this.integrations.disconnectGitHub());
  }

  private async finishGitHubInstall(installationId: string, code: string): Promise<void> {
    try {
      const status = await this.integrations.connectGitHub(installationId, code);
      this.toast.success(this.t('organization.github.connected', { account: status.account }));
    } catch (error) {
      this.toast.error(this.errorText(error));
    } finally {
      void this.router.navigate([], {
        relativeTo: this.route,
        queryParams: { section: 'integrations' },
      });
    }
  }

  private async run(work: () => Promise<unknown>): Promise<void> {
    try {
      await work();
      this.toast.success(this.t('common.saved'));
    } catch (error) {
      this.toast.error(this.errorText(error));
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

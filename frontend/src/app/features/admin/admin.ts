import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { I18nService } from '../../core/i18n/i18n.service';
import { dateTime, shortDate } from '../../core/format';
import type {
  ApiKeyDto,
  IntegrationDto,
  PermissionDto,
  RoleDto,
  TeamDto,
  UserDto,
} from '../../core/api-types';
import { AdminStore, IntegrationsStore, MetricsStore } from '../../data/feature.stores';
import { WorkspaceStore } from '../../data/workspace.store';
import { Avatar } from '../../ui/avatar';
import { ConfirmService } from '../../ui/confirm.service';
import { Icon } from '../../ui/icon';
import { Menu, type MenuItem } from '../../ui/menu';
import { PromptService } from '../../ui/prompt.service';
import { ToastService } from '../../ui/toast.service';
import { Topbar } from '../../ui/topbar';
import { ViewControls } from '../../ui/view-controls';
import { PageState } from '../../ui/page-state';
import { InviteDialog, type InviteDraft } from './admin-dialogs';
import { IntegrationDialog, type IntegrationDraft } from './integration-dialog';
import { ApiKeyDialog, type ApiKeyDraft } from '../agents/api-key-dialog';

type Section = 'people' | 'security' | 'keys' | 'integrations' | 'audit';



@Component({
  selector: 'app-admin',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    Icon,
    Avatar,
    Menu,
    Topbar,
    ViewControls,
    PageState,
    InviteDialog,
    ApiKeyDialog,
    IntegrationDialog,
    RouterLink,
  ],
  templateUrl: './admin.html',
})
export class Admin implements OnInit {
  protected readonly store = inject(WorkspaceStore);
  protected readonly admin = inject(AdminStore);
  protected readonly metrics = inject(MetricsStore);
  protected readonly integrations = inject(IntegrationsStore);
  private readonly confirm = inject(ConfirmService);
  private readonly prompt = inject(PromptService);
  private readonly toast = inject(ToastService);
  private readonly route = inject(ActivatedRoute);
  protected readonly t = inject(I18nService).t;

  protected readonly section = signal<Section>('people');
  protected readonly search = signal('');
  protected readonly inviteOpen = signal(false);
  protected readonly keyOpen = signal(false);
  protected readonly integrationOpen = signal(false);
  protected readonly issuedKey = signal<string | null>(null);
  protected readonly teamMembers = signal<Record<string, string[]>>({});

  protected readonly sections = [
    { id: 'people', icon: 'users', label: 'admin.peopleRoles' },
    { id: 'security', icon: 'shield', label: 'admin.security' },
    { id: 'keys', icon: 'key', label: 'admin.apiKeys' },
    { id: 'integrations', icon: 'link', label: 'admin.integrations' },
    { id: 'audit', icon: 'log', label: 'admin.auditLog' },
  ] as const;

  protected readonly roles = computed<RoleDto[]>(() => this.admin.roles());
  protected readonly isAdmin = computed(() => this.store.currentUser()?.role?.code === 'admin');

  protected readonly visibleMembers = computed(() => {
    const needle = this.search().trim().toLowerCase();
    if (!needle) return this.admin.members();
    return this.admin
      .members()
      .filter(
        (user) =>
          user.name.toLowerCase().includes(needle) || user.email.toLowerCase().includes(needle),
      );
  });

  ngOnInit(): void {
    const requested = this.route.snapshot.queryParamMap.get('section');
    if (this.sections.some((item) => item.id === requested)) {
      this.section.set(requested as Section);
    }
    void this.admin.load();
    void this.metrics.load();
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
      ? this.t('admin.invitedOn', { date: shortDate(user.invitedOn) })
      : this.t('admin.invitePending');
  }

  workloadPercent(userId: string): number {
    const row = this.workloadOf(userId);
    if (!row || row.capacity <= 0) return 0;
    return Math.min(100, Math.round((row.points / row.capacity) * 100));
  }

  reload(): void {
    void this.admin.load();
  }

  setSection(section: Section): void {
    this.section.set(section);
    if (section === 'audit' && !this.admin.audit()) {
      void this.loadAudit(0);
    }
    if (section === 'integrations') {
      void this.integrations.load();
    }
  }

  integrationMenu(integration: IntegrationDto): MenuItem[] {
    return [
      { id: 'test', label: 'admin.integrationTest', icon: 'play' },
      {
        id: 'toggle',
        label: integration.enabled ? 'admin.integrationDisable' : 'admin.integrationEnable',
        icon: integration.enabled ? 'lock' : 'check',
      },
      {
        id: 'remove',
        label: 'admin.integrationRemove',
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
          this.toast.success(this.t('admin.integrationTestOk', { detail: result.detail }));
        } else {
          this.toast.error(this.t('admin.integrationTestFailed', { detail: result.detail }));
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
      title: 'admin.integrationRemove',
      message: this.t('admin.integrationRemoveLead', { name: integration.name }),
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
    const value = integration.kind === 'webhook' ? config['url'] : config['to'];
    return value === undefined || value === null ? '' : String(value);
  }

  protected shortDateTime = dateTime;
  protected shortDate = shortDate;

  grants(role: RoleDto, permission: PermissionDto): boolean {
    return role.permissions.includes(permission.code);
  }

  protected readonly permissionGroups = computed<{ group: string; items: PermissionDto[] }[]>(() => {
    const groups = new Map<string, PermissionDto[]>();
    for (const permission of this.admin.permissions()) {
      const items = groups.get(permission.group) ?? [];
      items.push(permission);
      groups.set(permission.group, items);
    }
    return [...groups].map(([group, items]) => ({ group, items }));
  });

  memberMenu(user: UserDto): MenuItem[] {
    const items: MenuItem[] = this.admin.roles().map((role) => ({
      id: 'role:' + role.code,
      label: this.t('admin.setRole', { role: role.name }),
      checked: user.role?.code === role.code,
    }));
    items.push({
      id: 'capacity',
      label: 'admin.setCapacity',
      icon: 'chart',
      separatorBefore: true,
    });
    items.push({
      id: 'toggle',
      label: user.pending ? 'admin.activate' : 'admin.suspend',
      icon: user.pending ? 'check' : 'lock',
    });
    items.push({
      id: 'remove',
      label: 'admin.removeMember',
      icon: 'trash',
      danger: true,
      separatorBefore: true,
    });
    return items;
  }

  teamMenu(): MenuItem[] {
    return [
      { id: 'rename', label: 'nav.renameView', icon: 'pencil' },
      { id: 'members', label: 'admin.manageMembers', icon: 'users' },
      { id: 'delete', label: 'common.delete', icon: 'trash', danger: true, separatorBefore: true },
    ];
  }

  keyMenu(key: ApiKeyDto): MenuItem[] {
    return [
      { id: 'copy', label: 'admin.copyPrefix', icon: 'copy' },
      {
        id: 'revoke',
        label: 'admin.revokeKey',
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
      await this.run(() => this.admin.updateMember(user.id, { role }));
      return;
    }
    if (item.id === 'capacity') {
      const value = await this.prompt.ask({
        title: 'admin.setCapacity',
        message: 'admin.capacityHint',
        label: 'admin.capacity',
        value: String(user.capacity),
      });
      if (value === null) return;
      const parsed = Number.parseInt(value, 10);
      if (!Number.isFinite(parsed) || parsed < 0) {
        this.toast.error(this.t('task.numberInvalid'));
        return;
      }
      await this.run(() => this.admin.updateMember(user.id, { capacity: parsed }));
      return;
    }
    if (item.id === 'toggle') {
      await this.run(() => this.admin.updateMember(user.id, { pending: !user.pending }));
      return;
    }
    const confirmed = await this.confirm.ask({
      title: 'admin.removeMember',
      message: this.t('admin.removeMemberHint', { name: user.name }),
      confirmLabel: 'common.delete',
      destructive: true,
    });
    if (!confirmed) return;
    await this.run(() => this.admin.removeMember(user.id));
  }

  async invite(draft: InviteDraft): Promise<void> {
    try {
      const created = await this.admin.invite(draft.name, draft.email, draft.role);
      this.inviteOpen.set(false);
      this.toast.success(this.t('admin.invited', { email: created.email }));
    } catch (error) {
      this.toast.error(this.errorText(error));
    }
  }

  async addTeam(): Promise<void> {
    const name = await this.prompt.ask({
      title: 'admin.newTeam',
      label: 'admin.teamName',
      placeholder: 'admin.teamNamePlaceholder',
    });
    if (!name) return;
    await this.run(() => this.admin.createTeam(name));
  }

  async onTeamMenu(item: MenuItem, team: TeamDto): Promise<void> {
    if (item.id === 'rename') {
      const name = await this.prompt.ask({
        title: 'nav.renameView',
        label: 'admin.teamName',
        value: team.name,
      });
      if (!name) return;
      await this.run(() => this.admin.renameTeam(team.id, name));
      return;
    }
    if (item.id === 'members') {
      await this.loadTeamMembers(team.id);
      return;
    }
    const confirmed = await this.confirm.askDelete(team.name);
    if (!confirmed) return;
    await this.run(() => this.admin.deleteTeam(team.id));
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
      const members = await this.admin.teamMembers(teamId);
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
      if (inTeam) await this.admin.removeTeamMember(teamId, userId);
      else await this.admin.addTeamMember(teamId, userId);
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
      const issued = await this.admin.createApiKey(draft.label, draft.scopes, draft.expiresInDays);
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
      this.toast.success(this.t('admin.keyCopied'));
    } catch {
      this.toast.error(this.t('task.linkCopyFailed'));
    }
  }

  async onKeyMenu(item: MenuItem, key: ApiKeyDto): Promise<void> {
    if (item.id === 'copy') {
      try {
        await navigator.clipboard.writeText(key.prefix);
        this.toast.success(this.t('admin.keyCopied'));
      } catch {
        this.toast.error(this.t('task.linkCopyFailed'));
      }
      return;
    }
    const confirmed = await this.confirm.ask({
      title: 'admin.revokeKey',
      message: this.t('admin.revokeKeyHint', { label: key.label }),
      confirmLabel: 'admin.revokeKey',
      destructive: true,
    });
    if (!confirmed) return;
    await this.run(() => this.admin.revokeApiKey(key.id));
  }

  async loadAudit(page: number): Promise<void> {
    try {
      await this.admin.loadAudit(page);
    } catch (error) {
      this.toast.error(this.errorText(error));
    }
  }

  auditPages(): number {
    const total = this.admin.audit()?.total ?? 0;
    return Math.max(1, Math.ceil(total / 50));
  }

  private async run(work: () => Promise<unknown>): Promise<void> {
    try {
      await work();
      this.toast.success(this.t('settings.saved'));
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

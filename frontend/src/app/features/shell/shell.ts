import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  OnInit,
  computed,
  inject,
  isDevMode,
  signal,
} from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { I18nService } from '../../core/i18n/i18n.service';
import type { NavItemCode, NavItemDto, ProjectDto, SavedViewDto } from '../../core/api-types';
import { ViewState } from '../../data/view-state';
import { RulesStore, SettingsStore } from '../../data/feature.stores';
import { WorkspaceStore } from '../../data/workspace.store';
import { AuthService } from '../../core/auth.service';
import { ActiveOrgService } from '../../core/active-org';
import { TASK_VIEWS, taskViewMeta } from '../../core/task-views';
import { OrgService } from '../../core/org.service';
import { OnboardingService } from '../../core/onboarding.service';
import { RealtimeService } from '../../core/realtime.service';
import { Avatar } from '../../ui/avatar';
import { CommandPalette, CommandPaletteDialog } from '../../ui/command-palette';
import { ConfirmService } from '../../ui/confirm.service';
import { Icon } from '../../ui/icon';
import { Logo } from '../../ui/logo';
import { Menu, type MenuItem } from '../../ui/menu';
import { PageState } from '../../ui/page-state';
import { PromptService } from '../../ui/prompt.service';
import { ToastService } from '../../ui/toast.service';
import { TaskComposerDialog } from '../task-composer/task-composer';
import { TaskDialog } from '../task-detail/task-dialog';
import { OnboardingChecklist } from '../onboarding/checklist';
import { OnboardingTour } from '../onboarding/tour';

interface NavEntry {
  code: NavItemCode;
  path: string;
  icon: string;
  label: string;
}

const NAV_ENTRIES: readonly NavEntry[] = [
  { code: 'overview', path: '/app/overview', icon: 'home', label: 'nav.overview' },
  { code: 'my-tasks', path: '/app/my-tasks', icon: 'check', label: 'nav.myTasks' },
  { code: 'board', path: '/app/board', icon: 'board', label: 'nav.board' },
  { code: 'list', path: '/app/list', icon: 'list', label: 'nav.list' },
  { code: 'timeline', path: '/app/timeline', icon: 'timeline', label: 'nav.timeline' },
  { code: 'calendar', path: '/app/calendar', icon: 'calendar', label: 'nav.calendar' },
  { code: 'automations', path: '/app/automations', icon: 'bolt', label: 'nav.automations' },
  { code: 'agents', path: '/app/agents', icon: 'agent', label: 'nav.agents' },
  { code: 'reports', path: '/app/reports', icon: 'chart', label: 'nav.reports' },
];

const MOBILE_NAV: readonly {
  path: string;
  icon: string;
  label: string;
  code: NavItemCode | null;
}[] = [
  { path: '/app/overview', icon: 'home', label: 'nav.overview', code: 'overview' },
  { path: '/app/board', icon: 'board', label: 'nav.board', code: 'board' },
  { path: '/app/automations', icon: 'bolt', label: 'mobile.rules', code: 'automations' },
  { path: '/app/settings', icon: 'user', label: 'mobile.me', code: null },
];

const CREATE_ORG = 'create-org';

function entry(code: NavItemCode): NavEntry | null {
  return NAV_ENTRIES.find((item) => item.code === code) ?? null;
}

function defaultNavigation(): NavItemDto[] {
  return NAV_ENTRIES.map((item) => ({ code: item.code, hidden: false }));
}

@Component({
  selector: 'app-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    Icon,
    Logo,
    Avatar,
    Menu,
    PageState,
    TaskComposerDialog,
    TaskDialog,
    CommandPaletteDialog,
    OnboardingChecklist,
    OnboardingTour,
  ],
  templateUrl: './shell.html',
})
export class Shell implements OnInit, OnDestroy {
  protected readonly store = inject(WorkspaceStore);
  protected readonly rules = inject(RulesStore);
  private readonly settings = inject(SettingsStore);
  protected readonly state = inject(ViewState);
  protected readonly palette = inject(CommandPalette);
  protected readonly auth = inject(AuthService);
  private readonly orgs = inject(OrgService);
  private readonly activeOrg = inject(ActiveOrgService);
  protected readonly onboarding = inject(OnboardingService);
  private readonly realtime = inject(RealtimeService);
  private readonly confirm = inject(ConfirmService);
  private readonly prompt = inject(PromptService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  protected readonly i18n = inject(I18nService);
  protected readonly t = this.i18n.t;
  protected readonly devMode = isDevMode();

  protected readonly organization = computed(() => {
    const memberships = this.orgs.memberships();
    const active = this.activeOrg.id();
    return memberships.find((item) => item.organizationId === active) ?? memberships[0] ?? null;
  });

  protected readonly orgHandle = computed(() => {
    const organization = this.organization();
    return organization ? `@${organization.slug}` : '';
  });

  protected readonly orgMenu = computed<MenuItem[]>(() => [
    ...this.orgs.memberships().map((item) => ({
      id: item.organizationId,
      label: `@${item.slug}`,
      checked: item.organizationId === this.organization()?.organizationId,
    })),
    {
      id: CREATE_ORG,
      label: 'nav.createOrg',
      icon: 'plus',
      separatorBefore: true,
    },
  ]);

  private readonly navItems = computed<NavItemDto[]>(() => {
    const stored = this.store.navigation().filter((item) => entry(item.code) !== null);
    const missing = NAV_ENTRIES.filter(
      (known) => !stored.some((item) => item.code === known.code),
    ).map((known) => ({ code: known.code, hidden: false }));
    return stored.length ? [...stored, ...missing] : defaultNavigation();
  });

  private readonly hiddenCodes = computed(
    () =>
      new Set(
        this.navItems()
          .filter((item) => item.hidden)
          .map((item) => item.code),
      ),
  );

  private available(code: NavItemCode): boolean {
    const view = TASK_VIEWS.find((item) => item.code === code);
    return view === undefined || this.store.taskViewEnabled(view.code, null);
  }

  protected readonly nav = computed<NavEntry[]>(() =>
    this.navItems()
      .filter((item) => !item.hidden && this.available(item.code))
      .map((item) => entry(item.code))
      .filter((item): item is NavEntry => item !== null),
  );

  protected readonly mobileNav = computed(() =>
    MOBILE_NAV.filter(
      (item) =>
        item.code === null || (!this.hiddenCodes().has(item.code) && this.available(item.code)),
    ),
  );

  protected readonly navMenu = computed<MenuItem[]>(() => {
    const items: MenuItem[] = this.navItems().flatMap((item) => {
      const known = entry(item.code);
      if (!known || !this.available(item.code)) return [];
      return [
        {
          id: known.code,
          label: known.label,
          icon: known.icon,
          checked: !item.hidden,
          keepOpen: true,
        },
      ];
    });
    return [
      ...items,
      { id: 'reset', label: 'nav.restoreDefaults', icon: 'sliders', separatorBefore: true },
    ];
  });

  protected readonly activeRuleCount = computed(() =>
    this.rules.loaded() ? this.rules.activeCount() : this.store.activeRuleCount(),
  );

  protected readonly showArchived = signal(false);

  protected readonly archivedProjects = computed(() =>
    this.store.projects().filter((project) => project.archived),
  );

  viewMenu(view: SavedViewDto): MenuItem[] {
    const views = this.store.savedViews();
    const position = views.findIndex((item) => item.id === view.id);
    const items: MenuItem[] = [{ id: 'open', label: 'nav.openView', icon: 'arrow-right' }];
    if (!view.builtin) {
      items.push({ id: 'rename', label: 'nav.renameView', icon: 'pencil' });
    }
    items.push({ id: 'duplicate', label: 'filters.duplicateView', icon: 'copy' });
    if (!view.builtin) {
      items.push({
        id: 'share',
        label: view.shared ? 'filters.unshareView' : 'filters.shareView',
        icon: view.shared ? 'lock' : 'users',
      });
    }
    items.push(
      {
        id: 'up',
        label: 'common.moveUp',
        icon: 'up',
        disabled: position <= 0,
        separatorBefore: true,
      },
      {
        id: 'down',
        label: 'common.moveDown',
        icon: 'down',
        disabled: position >= views.length - 1,
      },
    );
    if (!view.builtin) {
      items.push({
        id: 'delete',
        label: 'common.delete',
        icon: 'trash',
        danger: true,
        separatorBefore: true,
      });
    }
    return items;
  }

  private async moveView(view: SavedViewDto, step: -1 | 1): Promise<void> {
    const ids = this.store.savedViews().map((item) => item.id);
    const index = ids.indexOf(view.id);
    const target = index + step;
    if (index < 0 || target < 0 || target >= ids.length) return;
    const [moved] = ids.splice(index, 1);
    ids.splice(target, 0, moved);
    try {
      await this.store.reorderViews(ids);
    } catch (error) {
      this.toast.error(this.errorText(error));
    }
  }

  viewName(view: SavedViewDto): string {
    return view.name || this.t(view.code ?? '');
  }

  viewIcon(view: SavedViewDto): string {
    return taskViewMeta(view.query.layout ?? 'list').icon;
  }

  ngOnInit(): void {
    void this.store.load();
    void this.settings.load();
    void this.onboarding.refresh(true);
    this.realtime.start();
  }

  ngOnDestroy(): void {
    this.realtime.stop();
  }

  reload(): void {
    void this.store.load(true);
  }

  async onOrgMenu(item: MenuItem): Promise<void> {
    if (item.id === CREATE_ORG) {
      window.location.assign('/orgs/new');
      return;
    }
    if (item.id === this.organization()?.organizationId) return;
    try {
      await this.orgs.switchTo(item.id);
      window.location.assign('/app');
    } catch (error) {
      this.toast.error(this.errorText(error));
    }
  }

  navItemMenu(code: NavItemCode): MenuItem[] {
    const visible = this.navItems().filter((item) => !item.hidden);
    const position = visible.findIndex((item) => item.code === code);
    return [
      { id: 'up', label: 'common.moveUp', icon: 'up', disabled: position <= 0 },
      {
        id: 'down',
        label: 'common.moveDown',
        icon: 'down',
        disabled: position === visible.length - 1,
      },
      { id: 'hide', label: 'nav.hide', icon: 'eye', separatorBefore: true },
    ];
  }

  onNavMenu(item: MenuItem): void {
    if (item.id === 'reset') {
      void this.saveNavigation(defaultNavigation());
      return;
    }
    this.toggleNavItem(item.id as NavItemCode);
  }

  onNavItemMenu(item: MenuItem, code: NavItemCode): void {
    if (item.id === 'hide') {
      this.toggleNavItem(code);
      return;
    }

    const items = [...this.navItems()];
    const index = items.findIndex(({ code: current }) => current === code);
    if (index < 0) return;

    const step = item.id === 'up' ? -1 : 1;
    let target = index + step;
    while (target >= 0 && target < items.length && items[target].hidden) {
      target += step;
    }
    if (target < 0 || target >= items.length) return;

    const [moved] = items.splice(index, 1);
    items.splice(target, 0, moved);
    void this.saveNavigation(items);
  }

  private toggleNavItem(code: NavItemCode): void {
    void this.saveNavigation(
      this.navItems().map((item) =>
        item.code === code ? { ...item, hidden: !item.hidden } : item,
      ),
    );
  }

  private async saveNavigation(items: NavItemDto[]): Promise<void> {
    try {
      await this.store.saveNavigation(items);
    } catch (error) {
      this.toast.error(this.errorText(error));
    }
  }

  async newView(): Promise<void> {
    const name = await this.prompt.ask({
      title: 'nav.newView',
      label: 'filters.viewName',
      placeholder: 'filters.viewNamePlaceholder',
      value: this.t('filters.newView'),
    });
    if (!name) return;
    try {
      const created = await this.store.createView(name, {
        layout: this.state.layout(),
        filter: { join: 'and', conditions: [] },
        groupBy: 'status',
        sort: 'manual',
      });
      this.toast.success(this.t('filters.viewSaved', { name }));
      void this.router.navigate(['/app/views', created.id]);
    } catch {
      this.toast.error(this.t('filters.viewFailed'));
    }
  }

  async onViewMenu(item: MenuItem, view: SavedViewDto): Promise<void> {
    switch (item.id) {
      case 'open':
        void this.router.navigate(['/app/views', view.id]);
        break;
      case 'rename': {
        const name = await this.prompt.ask({
          title: 'nav.renameView',
          label: 'filters.viewName',
          value: view.name ?? '',
        });
        if (!name) return;
        try {
          await this.store.updateView(view.id, { name });
          this.toast.success(this.t('filters.viewSaved', { name }));
        } catch {
          this.toast.error(this.t('filters.viewFailed'));
        }
        break;
      }
      case 'duplicate': {
        const name = await this.prompt.ask({
          title: 'filters.duplicateView',
          label: 'filters.viewName',
          value: this.t('filters.copyOf', { name: this.viewName(view) }),
        });
        if (!name) return;
        try {
          const created = await this.store.createView(name, view.query);
          this.toast.success(this.t('filters.viewSaved', { name }));
          void this.router.navigate(['/app/views', created.id]);
        } catch {
          this.toast.error(this.t('filters.viewFailed'));
        }
        break;
      }
      case 'share':
        await this.run(() => this.store.updateView(view.id, { shared: !view.shared }));
        break;
      case 'up':
        await this.moveView(view, -1);
        break;
      case 'down':
        await this.moveView(view, 1);
        break;
      case 'delete': {
        const confirmed = await this.confirm.askDelete(this.viewName(view));
        if (!confirmed) return;
        try {
          await this.store.deleteView(view.id);
          if (this.state.activeViewId() === view.id) {
            this.state.reset();
            void this.router.navigate(['/app/board']);
          }
          this.toast.success(this.t('filters.viewDeleted'));
        } catch {
          this.toast.error(this.t('common.actionFailed'));
        }
        break;
      }
      default:
        break;
    }
  }

  projectTaskCount(projectId: string): number {
    return this.store.tasks().filter((task) => task.projectId === projectId).length;
  }

  projectMenu(project: ProjectDto): MenuItem[] {
    return [
      { id: 'open', label: 'projects.showTasks', icon: 'board', disabled: project.archived },
      { id: 'rename', label: 'nav.renameView', icon: 'pencil' },
      {
        id: 'archive',
        label: project.archived ? 'projects.restore' : 'projects.archive',
        icon: 'archive',
      },
      { id: 'delete', label: 'common.delete', icon: 'trash', danger: true, separatorBefore: true },
    ];
  }

  selectProject(project: ProjectDto): void {
    const already = this.state.projectId() === project.id;
    this.state.projectId.set(already ? null : project.id);
    if (!already) void this.router.navigate(['/app']);
  }

  clearProject(): void {
    this.state.projectId.set(null);
  }

  async addProject(): Promise<void> {
    const name = await this.prompt.ask({
      title: 'projects.new',
      message: 'projects.newHint',
      label: 'projects.name',
      placeholder: 'projects.namePlaceholder',
    });
    if (!name) return;

    const code = await this.prompt.ask({
      title: 'projects.new',
      message: 'projects.codeHint',
      label: 'projects.code',
      placeholder: 'NOW',
      value: suggestCode(name),
    });
    if (code === null) return;

    try {
      const created = await this.store.createProject(name, code);
      this.state.projectId.set(created.id);
      this.toast.success(this.t('projects.created', { name: created.name, code: created.code }));
    } catch (error) {
      this.toast.error(this.errorText(error));
    }
  }

  async onProjectMenu(item: MenuItem, project: ProjectDto): Promise<void> {
    switch (item.id) {
      case 'open':
        this.selectProject(project);
        break;
      case 'rename': {
        const name = await this.prompt.ask({
          title: 'nav.renameView',
          label: 'projects.name',
          value: project.name,
        });
        if (!name) return;
        await this.run(() => this.store.updateProject(project.id, { name }));
        break;
      }
      case 'archive':
        await this.run(() => this.store.updateProject(project.id, { archived: !project.archived }));
        if (this.state.projectId() === project.id) this.state.projectId.set(null);
        break;
      case 'delete': {
        const confirmed = await this.confirm.ask({
          title: 'projects.deleteTitle',
          message: this.t('projects.deleteHint', { name: project.name }),
          confirmLabel: 'common.delete',
          destructive: true,
        });
        if (!confirmed) return;
        await this.run(() => this.store.deleteProject(project.id));
        if (this.state.projectId() === project.id) this.state.projectId.set(null);
        break;
      }
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

  signOut(): void {
    void this.auth.logout();
  }
}

function suggestCode(name: string): string {
  return name
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[\u0141\u0142]/g, 'l')
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, '')
    .slice(0, 4);
}

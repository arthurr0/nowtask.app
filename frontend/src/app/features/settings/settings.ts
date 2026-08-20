import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { I18nService } from '../../core/i18n/i18n.service';
import { PrefsService } from '../../core/prefs.service';
import type { Accent, Density, Lang, RadiusStep, ThemeChoice } from '../../core/models';
import type { CustomFieldDto, StatusDto, WorkspaceSettingsDto } from '../../core/api-types';
import { ViewState } from '../../data/view-state';
import { WorkspaceStore } from '../../data/workspace.store';
import { SettingsStore } from '../../data/feature.stores';
import { ConfirmService } from '../../ui/confirm.service';
import { Icon } from '../../ui/icon';
import { Menu, type MenuItem } from '../../ui/menu';
import { PromptService } from '../../ui/prompt.service';
import { Switch } from '../../ui/switch';
import { ToastService } from '../../ui/toast.service';
import { Topbar } from '../../ui/topbar';
import { ViewControls } from '../../ui/view-controls';
import { PageState } from '../../ui/page-state';
import {
  CustomFieldDialog,
  StatusDialog,
  type CustomFieldDraft,
  type StatusDraft,
} from './settings-dialogs';

type Section = 'appearance' | 'language' | 'fields' | 'flow' | 'epics';

const DATE_FORMATS = ['dd.MM.yyyy', 'yyyy-MM-dd', 'MM/dd/yyyy', 'd MMMM yyyy'];
const TIME_FORMATS = ['HH:mm', 'h:mm a'];
const WEEK_DAYS = [1, 7];
const TIME_ZONES = ['Europe/Warsaw', 'Europe/Berlin', 'Europe/London', 'UTC', 'America/New_York'];
const CURRENCIES = ['PLN', 'EUR', 'USD', 'GBP'];

@Component({
  selector: 'app-settings',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, Switch, Menu, Topbar, ViewControls, PageState, CustomFieldDialog, StatusDialog],
  templateUrl: './settings.html',
})
export class Settings implements OnInit {
  protected readonly store = inject(WorkspaceStore);
  protected readonly view = inject(ViewState);
  protected readonly settings = inject(SettingsStore);
  protected readonly prefs = inject(PrefsService);
  private readonly confirm = inject(ConfirmService);
  private readonly prompt = inject(PromptService);
  private readonly toast = inject(ToastService);
  protected readonly i18n = inject(I18nService);
  protected readonly t = this.i18n.t;

  protected readonly section = signal<Section>('appearance');
  protected readonly workspaceSettings = signal<WorkspaceSettingsDto | null>(null);

  protected readonly fieldDialogOpen = signal(false);
  protected readonly editedField = signal<CustomFieldDto | null>(null);
  protected readonly statusDialogOpen = signal(false);
  protected readonly transitionFrom = signal('');
  protected readonly transitionTo = signal('');
  protected readonly editedStatus = signal<StatusDto | null>(null);

  protected readonly personalSections: readonly { id: Section; icon: string; label: string }[] = [
    { id: 'appearance', icon: 'sun', label: 'settings.appearance' },
    { id: 'language', icon: 'globe', label: 'settings.langRegion' },
  ];

  protected readonly structureSections: readonly { id: Section; icon: string; label: string }[] = [
    { id: 'fields', icon: 'sliders', label: 'settings.customFields' },
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

  ngOnInit(): void {
    void this.settings.load();
    void this.loadWorkspaceSettings();
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

  async changeSprint(current: string): Promise<void> {
    const sprint = await this.prompt.ask({
      title: 'settings.currentSprint',
      message: 'settings.currentSprintHint',
      label: 'settings.currentSprint',
      value: current,
    });
    if (!sprint) return;
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
          restrictedToRole: draft.restrictedToRole,
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

import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { I18nService } from '../../core/i18n/i18n.service';
import { ConfirmService } from '../../ui/confirm.service';
import { Menu, type MenuItem } from '../../ui/menu';
import { PromptService } from '../../ui/prompt.service';
import { ToastService } from '../../ui/toast.service';
import { dateTime, shortDate } from '../../core/format';
import type { RuleDto } from '../../core/api-types';
import { RulesStore } from '../../data/feature.stores';
import { WorkspaceStore } from '../../data/workspace.store';
import { Icon } from '../../ui/icon';
import { Switch } from '../../ui/switch';
import { Topbar } from '../../ui/topbar';
import { ViewControls } from '../../ui/view-controls';
import { PageState } from '../../ui/page-state';
import { ConditionNode } from './condition-node';
import { RuleDialog, type RuleDraft } from './rule-dialog';

type RuleFilter = 'all' | 'active' | 'drafts';

@Component({
  selector: 'app-automations',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, Switch, Menu, Topbar, ViewControls, ConditionNode, PageState, RuleDialog],
  templateUrl: './automations.html',
})
export class Automations implements OnInit {
  protected readonly store = inject(WorkspaceStore);
  protected readonly rulesStore = inject(RulesStore);
  private readonly confirm = inject(ConfirmService);
  private readonly prompt = inject(PromptService);
  private readonly toast = inject(ToastService);
  protected readonly t = inject(I18nService).t;

  protected readonly search = signal('');
  protected readonly dialogOpen = signal(false);
  protected readonly editedRule = signal<RuleDto | null>(null);
  protected readonly running = signal(false);

  protected readonly filter = signal<RuleFilter>('all');
  protected readonly selectedId = signal<string | null>(null);
  protected readonly mobilePanel = signal<'list' | 'rule'>('list');

  protected readonly filters: readonly { id: RuleFilter; label: string }[] = [
    { id: 'all', label: 'common.all' },
    { id: 'active', label: 'common.active' },
    { id: 'drafts', label: 'common.drafts' },
  ];

  protected readonly visibleRules = computed(() => {
    const needle = this.search().trim().toLowerCase();
    const rules = needle
      ? this.rulesStore
          .rules()
          .filter(
            (rule) =>
              rule.name.toLowerCase().includes(needle) ||
              rule.summary.toLowerCase().includes(needle),
          )
      : this.rulesStore.rules();

    switch (this.filter()) {
      case 'active':
        return rules.filter((rule) => rule.enabled && !rule.draft);
      case 'drafts':
        return rules.filter((rule) => rule.draft);
      default:
        return rules;
    }
  });

  protected readonly selected = computed<RuleDto | null>(() => {
    const id = this.selectedId() ?? this.rulesStore.rules()[0]?.id ?? null;
    return id ? this.rulesStore.rule(id) : null;
  });

  protected readonly runs = computed(() => {
    const id = this.selected()?.id;
    return id ? this.rulesStore.runsFor(id) : [];
  });

  protected readonly editor = computed(() => {
    const rule = this.selected();
    return rule?.editedById ? this.store.user(rule.editedById) : null;
  });

  ngOnInit(): void {
    void this.load();
  }

  async load(): Promise<void> {
    await this.rulesStore.load();
    const first = this.rulesStore.rules()[0];
    if (first) {
      await this.rulesStore.loadRuns(first.id);
    }
  }

  protected dateTime = dateTime;
  protected shortDate = shortDate;

  select(ruleId: string): void {
    this.selectedId.set(ruleId);
    this.mobilePanel.set('rule');
    void this.rulesStore.loadRuns(ruleId);
  }

  toggle(ruleId: string, event: Event): void {
    event.stopPropagation();
    void this.rulesStore.toggle(ruleId);
  }

  ruleMenu(rule: RuleDto): MenuItem[] {
    return [
      { id: 'duplicate', label: 'board.duplicate', icon: 'copy' },
      {
        id: 'draft',
        label: rule.draft ? 'auto.publishRule' : 'auto.makeDraft',
        icon: rule.draft ? 'check' : 'pencil',
      },
      { id: 'delete', label: 'common.delete', icon: 'trash', danger: true, separatorBefore: true },
    ];
  }

  openDialog(rule: RuleDto | null): void {
    this.editedRule.set(rule);
    this.dialogOpen.set(true);
  }

  closeDialog(): void {
    this.dialogOpen.set(false);
    this.editedRule.set(null);
  }

  async saveRule(draft: RuleDraft): Promise<void> {
    const edited = this.editedRule();
    try {
      if (edited) {
        await this.rulesStore.update(edited.id, { ...draft });
      } else {
        const created = await this.rulesStore.create({ ...draft });
        this.selectedId.set(created.id);
      }
      this.closeDialog();
      this.toast.success(this.t('settings.saved'));
    } catch (error) {
      this.toast.error(this.errorText(error));
    }
  }

  async onRuleMenu(item: MenuItem, rule: RuleDto): Promise<void> {
    if (item.id === 'duplicate') {
      try {
        const created = await this.rulesStore.create({
          name: this.t('board.copyOf', { title: rule.name }),
          summary: rule.summary,
          scopeLabel: rule.scopeLabel,
          trigger: rule.trigger,
          conditions: rule.conditions,
          actions: rule.actions,
          draft: true,
        });
        this.selectedId.set(created.id);
        this.toast.success(this.t('settings.saved'));
      } catch (error) {
        this.toast.error(this.errorText(error));
      }
      return;
    }
    if (item.id === 'draft') {
      try {
        await this.rulesStore.update(rule.id, { draft: !rule.draft, enabled: rule.draft });
        this.toast.success(this.t('settings.saved'));
      } catch (error) {
        this.toast.error(this.errorText(error));
      }
      return;
    }
    const confirmed = await this.confirm.askDelete(rule.name);
    if (!confirmed) return;
    try {
      await this.rulesStore.remove(rule.id);
      this.selectedId.set(null);
      this.toast.success(this.t('auto.ruleDeleted'));
    } catch (error) {
      this.toast.error(this.errorText(error));
    }
  }

  async runOnTask(rule: RuleDto): Promise<void> {
    const key = await this.prompt.ask({
      title: 'auto.testOnTask',
      message: 'auto.testHint',
      label: 'task.relatedKey',
      placeholder: 'task.relatedKeyPlaceholder',
    });
    if (!key) return;
    await this.execute(rule, key.trim().toUpperCase());
  }

  async runOnAll(rule: RuleDto): Promise<void> {
    const confirmed = await this.confirm.ask({
      title: 'auto.runAll',
      message: this.t('auto.runAllHint', { name: rule.name }),
      confirmLabel: 'auto.runAll',
      icon: 'bolt',
    });
    if (!confirmed) return;
    await this.execute(rule, null);
  }

  private async execute(rule: RuleDto, taskKey: string | null): Promise<void> {
    this.running.set(true);
    try {
      const report = await this.rulesStore.run(rule.id, taskKey);
      await this.store.reloadTasks();
      if (report.matched === 0) {
        this.toast.info(this.t('auto.runNoMatch'));
      } else {
        this.toast.success(
          this.t('auto.runDone', {
            matched: report.matched,
            applied: report.applied,
            skipped: report.skipped,
          }),
        );
      }
    } catch (error) {
      this.toast.error(this.errorText(error));
    } finally {
      this.running.set(false);
    }
  }

  private errorText(error: unknown): string {
    if (typeof error === 'object' && error !== null && 'error' in error) {
      const body = (error as { error?: { message?: string } }).error;
      if (body?.message) return body.message;
    }
    return this.t('common.actionFailed');
  }

  triggerValue(rule: RuleDto): string {
    const value = rule.trigger.value;
    return value.includes('.') && !value.includes(' ') ? this.t(value) : value;
  }
}

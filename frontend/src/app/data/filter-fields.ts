import { Injectable, computed, inject } from '@angular/core';
import type { FilterConditionDto, FilterGroupDto, FilterNodeDto } from '../core/api-types';
import { I18nService } from '../core/i18n/i18n.service';
import {
  PRIORITIES,
  RELATIVE_DATES,
  STATUS_CATEGORIES,
  SYSTEM_FIELDS,
  countConditions,
  customFieldMeta,
  isGroup,
  needsValues,
  type FilterFieldMeta,
} from '../core/task-filter';
import { SettingsStore } from './feature.stores';
import { ViewState } from './view-state';
import { WorkspaceStore } from './workspace.store';

export interface FilterOption {
  value: string;
  label: string;
  icon?: string;
  hint?: string;
  swatch?: string;
}

@Injectable({ providedIn: 'root' })
export class FilterFields {
  private readonly store = inject(WorkspaceStore);
  private readonly settings = inject(SettingsStore);
  private readonly view = inject(ViewState);
  private readonly t = inject(I18nService).t;

  readonly systemFields = computed<FilterFieldMeta[]>(() =>
    SYSTEM_FIELDS.filter((meta) => this.enabled(meta)),
  );

  readonly customFields = computed<FilterFieldMeta[]>(() =>
    this.settings
      .customFields()
      .map((field) => customFieldMeta(field))
      .filter((meta) => this.enabled(meta)),
  );

  readonly fields = computed<FilterFieldMeta[]>(() => [
    ...this.systemFields(),
    ...this.customFields(),
  ]);

  readonly quickFields = computed(() =>
    this.systemFields().filter((meta) => meta.quick && this.hasOptions(meta)),
  );

  private enabled(meta: FilterFieldMeta): boolean {
    if (meta.key === 'project') return this.store.activeProjects().length > 1;
    if (!meta.gate) return true;
    return this.store.taskFieldEnabled(meta.gate, this.view.projectId());
  }

  private hasOptions(meta: FilterFieldMeta): boolean {
    if (meta.key === 'sprint') return this.store.sprints().length > 0;
    if (meta.key === 'epic') return this.store.epics().length > 0;
    return true;
  }

  meta(key: string): FilterFieldMeta | null {
    return (
      this.fields().find((meta) => meta.key === key) ??
      SYSTEM_FIELDS.find((meta) => meta.key === key) ??
      this.settings
        .customFields()
        .map((field) => customFieldMeta(field))
        .find((meta) => meta.key === key) ??
      null
    );
  }

  label(key: string): string {
    const meta = this.meta(key);
    return meta ? this.t(meta.label) : key;
  }

  options(meta: FilterFieldMeta): FilterOption[] {
    switch (meta.key) {
      case 'assignee':
      case 'reviewer':
        return this.people();
      case 'status':
        return this.store.boardStatuses().map((status) => ({
          value: status.id,
          label: this.store.statusName(status),
          swatch: status.swatch,
        }));
      case 'statusCategory':
        return STATUS_CATEGORIES.map((category) => ({
          value: category,
          label: this.t('statusCategory.' + category),
        }));
      case 'priority':
        return PRIORITIES.map((priority) => ({
          value: priority,
          label: this.t('priority.' + priority),
          icon: priority === 'high' || priority === 'critical' ? 'flag' : undefined,
        }));
      case 'project':
        return this.store.activeProjects().map((project) => ({
          value: project.id,
          label: project.name,
          hint: project.code,
        }));
      case 'epic':
        return this.store.epicsOfProject(this.view.projectId()).map((epic) => ({
          value: epic.id,
          label: epic.name,
        }));
      case 'sprint': {
        const current = this.store.currentSprint();
        const options: FilterOption[] = [];
        if (current)
          options.push({ value: 'current', label: this.t('filters.currentSprint'), hint: current });
        for (const sprint of this.store.sprints()) options.push({ value: sprint, label: sprint });
        return options;
      }
      case 'labels':
        return this.store.allLabels().map((label) => ({ value: label, label }));
      default:
        break;
    }
    if (meta.kind === 'ref' && meta.key.startsWith('custom:')) {
      const type = this.settings
        .customFields()
        .find((field) => 'custom:' + field.fieldKey === meta.key)?.type;
      if (type === 'person') return this.people();
    }
    return [];
  }

  private people(): FilterOption[] {
    const me = this.store.currentUser();
    const options: FilterOption[] = [];
    if (me)
      options.push({ value: 'me', label: this.t('filters.me'), icon: 'user', hint: me.shortName });
    for (const user of this.store.activeMembers()) {
      options.push({ value: user.id, label: user.name, hint: user.shortName });
    }
    return options;
  }

  valueLabel(meta: FilterFieldMeta, value: string): string {
    if (meta.kind === 'date') {
      const preset = RELATIVE_DATES.find((item) => item.value === value);
      return preset ? this.t(preset.label) : value;
    }
    if (meta.kind === 'boolean')
      return this.t(value === 'false' ? 'filters.false' : 'filters.true');
    const option = this.options(meta).find((item) => item.value === value);
    if (option) return option.label;
    if (meta.key === 'assignee' || meta.key === 'reviewer') {
      return this.store.user(value)?.shortName ?? value;
    }
    return value;
  }

  summary(node: FilterConditionDto): string {
    const meta = this.meta(node.field);
    if (!meta) return node.field;
    const op = this.t('filters.op.' + node.op);
    if (!needsValues(node.op)) return op;
    const values = node.values.filter((value) => value.trim() !== '');
    if (!values.length) return op;
    if (node.op === 'between') {
      return `${this.valueLabel(meta, values[0])} … ${this.valueLabel(meta, values[1] ?? '')}`;
    }
    const labels = values.map((value) => this.valueLabel(meta, value));
    const shown = labels.slice(0, 2).join(', ');
    const rest = labels.length > 2 ? ` +${labels.length - 2}` : '';
    const prefix =
      node.op === 'in' || node.op === 'is' || node.op === 'eq' || node.op === 'on' ? '' : op + ' ';
    return prefix + shown + rest;
  }

  describe(node: FilterNodeDto): string {
    if (isGroup(node)) {
      return this.t('filters.groupSummary', { count: countConditions(node) });
    }
    return `${this.label(node.field)}: ${this.summary(node)}`;
  }

  groupSummary(group: FilterGroupDto): string {
    return group.conditions
      .map((node) => (isGroup(node) ? '(' + this.groupSummary(node) + ')' : this.describe(node)))
      .join(group.join === 'or' ? ' OR ' : ' AND ');
  }
}

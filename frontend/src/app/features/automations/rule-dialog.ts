import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { I18nService } from '../../core/i18n/i18n.service';
import type { RuleActionDto, RuleConditionDto, RuleDto } from '../../core/api-types';
import { WorkspaceStore } from '../../data/workspace.store';
import { Dialog } from '../../ui/dialog';
import { Icon } from '../../ui/icon';
import { Menu, type MenuItem } from '../../ui/menu';
import { SelectField, type SelectOption } from '../../ui/select-field';
import { TextField } from '../../ui/text-field';

export interface RuleDraft {
  name: string;
  summary: string;
  scopeLabel: string;
  draft: boolean;
  trigger: { kind: string; value: string };
  conditions: {
    id: string;
    kind: 'group';
    join: 'and' | 'or';
    children: RuleConditionDto[];
  };
  actions: RuleActionDto[];
}

const TRIGGERS = ['statusChanged', 'assigned', 'manual', 'schedule'] as const;
const CONDITION_FIELDS = [
  'priority',
  'status',
  'label',
  'estimate',
  'assignee',
  'dueIn',
  'github',
] as const;
const CONDITION_OPS = [
  'isOneOf',
  'isNoneOf',
  'contains',
  'notContains',
  'greaterThan',
  'lessThan',
  'isBefore',
  'isEmpty',
  'isNotEmpty',
] as const;
const ACTION_KINDS = [
  'setStatus',
  'setPriority',
  'assign',
  'assignReviewer',
  'addLabel',
  'setDueDate',
  'comment',
  'githubComment',
  'githubCloseIssue',
  'githubLabel',
] as const;

const ACTION_ICONS: Record<string, string> = {
  setStatus: 'board',
  setPriority: 'flag',
  assign: 'user',
  assignReviewer: 'eye',
  addLabel: 'flag',
  setDueDate: 'calendar',
  comment: 'message',
  githubComment: 'git-branch',
  githubCloseIssue: 'git-branch',
  githubLabel: 'git-branch',
};

let sequence = 0;

@Component({
  selector: 'app-rule-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Dialog, TextField, SelectField, Menu, Icon],
  template: `
    <ui-dialog
      [open]="open()"
      size="lg"
      [title]="rule() ? 'rules.editRule' : 'rules.newRule'"
      description="rules.dialogLead"
      (closed)="cancelled.emit()"
    >
      <div class="flex flex-col gap-5">
        <div class="grid gap-4 sm:grid-cols-2">
          <ui-text-field
            [value]="name()"
            (valueChange)="name.set($event)"
            label="rules.name"
            placeholder="rules.namePlaceholder"
            [required]="true"
            [error]="error()"
          />
          <ui-text-field
            [value]="scopeLabel()"
            (valueChange)="scopeLabel.set($event)"
            label="rules.scope"
            placeholder="rules.scopePlaceholder"
          />
        </div>

        <ui-text-field
          [value]="summary()"
          (valueChange)="summary.set($event)"
          label="rules.summary"
          placeholder="rules.summaryPlaceholder"
        />

        <section class="flex flex-col gap-2">
          <h3 class="kap">{{ t('rules.trigger') }}</h3>
          <div class="grid gap-3 sm:grid-cols-2">
            <ui-select-field
              [value]="triggerKind()"
              (valueChange)="triggerKind.set($event)"
              [options]="triggerOptions()"
              label="rules.triggerKind"
            />
            @if (triggerKind() === 'statusChanged') {
              <ui-select-field
                [value]="triggerValue()"
                (valueChange)="triggerValue.set($event)"
                [options]="statusOptions()"
                label="rules.triggerStatus"
              />
            } @else if (triggerKind() === 'schedule') {
              <ui-text-field
                [value]="triggerValue()"
                (valueChange)="triggerValue.set($event)"
                label="rules.triggerTime"
                placeholder="07:00"
              />
            }
          </div>
          @if (triggerKind() === 'manual' || triggerKind() === 'schedule') {
            <p class="flex items-start gap-1.5 text-[11px] text-ink-3">
              <ui-icon name="alert" [size]="13" />
              <span>{{ t('rules.manualHint') }}</span>
            </p>
          }
        </section>

        <section class="flex flex-col gap-2">
          <div class="flex items-center gap-2.5">
            <h3 class="kap">{{ t('rules.conditions') }}</h3>
            <span class="h-px flex-1 bg-line"></span>
            <ui-menu
              [items]="joinItems()"
              triggerClass="flex h-7 items-center gap-1.5 rounded-field border border-line bg-surface-2 px-2.5 font-mono text-[11px] text-ink-2"
              triggerHeight="28px"
              ariaLabel="rules.join"
              align="end"
              (selected)="join.set($any($event.id))"
            >
              <span>{{ join() === 'and' ? 'AND' : 'OR' }}</span>
            </ui-menu>
          </div>

          @if (nested()) {
            <p
              class="flex items-start gap-1.5 rounded-card border border-dashed border-line-strong p-2.5 text-[11px] text-ink-3"
            >
              <ui-icon name="alert" [size]="13" />
              <span>{{ t('rules.nestedWarning') }}</span>
            </p>
          }

          @for (condition of conditions(); track condition.id) {
            <div class="flex flex-wrap items-center gap-2 rounded-card border border-line p-2">
              <ui-menu
                [items]="fieldItems(condition)"
                triggerClass="flex h-8 items-center gap-1.5 rounded-field border border-line bg-surface-2 px-2.5 text-xs text-ink-2"
                triggerHeight="32px"
                ariaLabel="rules.conditionField"
                (selected)="setField(condition, $event.id)"
              >
                <span>{{ t('cond.' + fieldOf(condition)) }}</span>
              </ui-menu>

              <ui-menu
                [items]="opItems(condition)"
                triggerClass="flex h-8 items-center gap-1.5 rounded-field border border-line bg-surface-2 px-2.5 text-xs text-ink-2"
                triggerHeight="32px"
                ariaLabel="rules.conditionOp"
                (selected)="setOp(condition, $event.id)"
              >
                <span>{{ t('op.' + condition.op) }}</span>
              </ui-menu>

              @if (condition.op !== 'isEmpty' && condition.op !== 'isNotEmpty') {
                <input
                  class="focus-ring h-8 min-w-0 flex-1 rounded-field border border-line bg-surface px-2.5 text-xs outline-none"
                  [placeholder]="t('rules.valuePlaceholder')"
                  [value]="condition.values.join(', ')"
                  (input)="setValues(condition, $any($event.target).value)"
                />
              } @else {
                <span class="flex-1"></span>
              }

              <button
                type="button"
                class="hoverable flex h-7 w-7 flex-none items-center justify-center rounded-[6px] text-ink-3"
                [attr.aria-label]="t('common.delete')"
                (click)="removeCondition(condition.id)"
              >
                <ui-icon name="x" [size]="14" />
              </button>
            </div>
          } @empty {
            <p class="text-xs text-ink-3">{{ t('rules.noConditions') }}</p>
          }

          <button
            type="button"
            class="flex h-8 w-fit items-center gap-1.5 rounded-field border border-dashed border-line-strong px-2.5 text-xs text-ink-3"
            (click)="addCondition()"
          >
            <ui-icon name="plus" [size]="14" />
            {{ t('rules.addCondition') }}
          </button>
        </section>

        <section class="flex flex-col gap-2">
          <div class="flex items-center gap-2.5">
            <h3 class="kap">{{ t('rules.actions') }}</h3>
            <span class="h-px flex-1 bg-line"></span>
          </div>

          @for (action of actions(); track action.id) {
            <div class="flex flex-wrap items-center gap-2 rounded-card border border-line p-2">
              <ui-menu
                [items]="actionItems(action)"
                triggerClass="flex h-8 items-center gap-1.5 rounded-field border border-line bg-surface-2 px-2.5 text-xs text-ink-2"
                triggerHeight="32px"
                ariaLabel="rules.actionKind"
                (selected)="setActionKind(action, $event.id)"
              >
                <ui-icon [name]="action.icon || 'bolt'" [size]="14" />
                <span>{{ t('action.' + action.kind) }}</span>
              </ui-menu>

              @if (action.kind === 'setStatus') {
                <ui-menu
                  [items]="statusItems(action)"
                  triggerClass="flex h-8 min-w-32 items-center gap-1.5 rounded-field border border-line bg-surface px-2.5 text-xs"
                  triggerHeight="32px"
                  ariaLabel="common.status"
                  (selected)="setActionValue(action, $event.id)"
                >
                  <span class="truncate">{{ action.value || t('ui.select.placeholder') }}</span>
                </ui-menu>
              } @else if (action.kind === 'setPriority') {
                <ui-menu
                  [items]="priorityItems(action)"
                  triggerClass="flex h-8 min-w-32 items-center gap-1.5 rounded-field border border-line bg-surface px-2.5 text-xs"
                  triggerHeight="32px"
                  ariaLabel="list.priority"
                  (selected)="setActionValue(action, $event.id)"
                >
                  <span class="truncate">{{ action.value || t('ui.select.placeholder') }}</span>
                </ui-menu>
              } @else if (action.kind === 'assign' || action.kind === 'assignReviewer') {
                <ui-menu
                  [items]="userItems(action)"
                  triggerClass="flex h-8 min-w-32 items-center gap-1.5 rounded-field border border-line bg-surface px-2.5 text-xs"
                  triggerHeight="32px"
                  ariaLabel="common.assignee"
                  (selected)="setActionValue(action, $event.id)"
                >
                  <span class="truncate">{{ action.value || t('ui.select.placeholder') }}</span>
                </ui-menu>
              } @else {
                <input
                  class="focus-ring h-8 min-w-0 flex-1 rounded-field border border-line bg-surface px-2.5 text-xs outline-none"
                  [placeholder]="actionPlaceholder(action.kind)"
                  [value]="action.value"
                  (input)="setActionValue(action, $any($event.target).value)"
                />
              }

              <button
                type="button"
                class="hoverable flex h-7 w-7 flex-none items-center justify-center rounded-[6px] text-ink-3"
                [attr.aria-label]="t('common.delete')"
                (click)="removeAction(action.id)"
              >
                <ui-icon name="x" [size]="14" />
              </button>
            </div>
          } @empty {
            <p class="text-xs text-warn">{{ t('rules.noActions') }}</p>
          }

          <button
            type="button"
            class="flex h-8 w-fit items-center gap-1.5 rounded-field border border-dashed border-line-strong px-2.5 text-xs text-ink-3"
            (click)="addAction()"
          >
            <ui-icon name="plus" [size]="14" />
            {{ t('rules.addAction') }}
          </button>
        </section>

        <label class="flex items-center gap-2.5 text-[13px]">
          <input
            type="checkbox"
            [checked]="draft()"
            (change)="draft.set($any($event.target).checked)"
          />
          {{ t('rules.keepDraft') }}
        </label>
      </div>

      <div dialogFooter class="flex w-full items-center justify-end gap-2">
        <button
          type="button"
          class="flex h-9 items-center rounded-[7px] border border-line-strong bg-surface px-3.5 text-[13px]"
          (click)="cancelled.emit()"
        >
          {{ t('common.cancel') }}
        </button>
        <button
          type="button"
          class="flex h-9 items-center gap-1.5 rounded-[7px] bg-inv px-3.5 text-[13px] font-medium text-inv-ink"
          (click)="submit()"
        >
          <ui-icon name="save" [size]="15" />
          {{ t('common.save') }}
        </button>
      </div>
    </ui-dialog>
  `,
})
export class RuleDialog {
  private readonly store = inject(WorkspaceStore);
  private readonly i18n = inject(I18nService);
  protected readonly t = this.i18n.t;

  readonly open = input(false);
  readonly rule = input<RuleDto | null>(null);

  readonly saved = output<RuleDraft>();
  readonly cancelled = output<void>();

  protected readonly name = signal('');
  protected readonly summary = signal('');
  protected readonly scopeLabel = signal('');
  protected readonly draft = signal(false);
  protected readonly triggerKind = signal<string>('statusChanged');
  protected readonly triggerValue = signal('');
  protected readonly join = signal<'and' | 'or'>('and');
  protected readonly conditions = signal<RuleConditionDto[]>([]);
  protected readonly actions = signal<RuleActionDto[]>([]);
  protected readonly nested = signal(false);
  protected readonly error = signal('');

  constructor() {
    effect(() => {
      if (!this.open()) return;
      untracked(() => this.reset());
    });
  }

  protected readonly triggerOptions = computed<SelectOption[]>(() =>
    TRIGGERS.map((kind) => ({ value: kind, label: this.t('trigger.' + kind) })),
  );

  protected readonly statusOptions = computed<SelectOption[]>(() =>
    this.store.boardStatuses().map((status) => ({
      value: 'status.' + status.code,
      label: this.store.statusName(status),
    })),
  );

  protected joinItems(): MenuItem[] {
    return [
      { id: 'and', label: 'rules.joinAnd', checked: this.join() === 'and' },
      { id: 'or', label: 'rules.joinOr', checked: this.join() === 'or' },
    ];
  }

  protected fieldOf(condition: RuleConditionDto): string {
    return condition.fieldKey.startsWith('cond.')
      ? condition.fieldKey.slice('cond.'.length)
      : condition.fieldKey;
  }

  protected fieldItems(condition: RuleConditionDto): MenuItem[] {
    return CONDITION_FIELDS.map((field) => ({
      id: field,
      label: this.t('cond.' + field),
      checked: this.fieldOf(condition) === field,
    }));
  }

  protected opItems(condition: RuleConditionDto): MenuItem[] {
    return CONDITION_OPS.map((op) => ({
      id: op,
      label: this.t('op.' + op),
      checked: condition.op === op,
    }));
  }

  protected statusItems(action: RuleActionDto): MenuItem[] {
    return this.store.boardStatuses().map((status) => ({
      id: 'status.' + status.code,
      label: this.store.statusName(status),
      checked: action.value === 'status.' + status.code,
    }));
  }

  protected priorityItems(action: RuleActionDto): MenuItem[] {
    return ['low', 'medium', 'high', 'critical'].map((priority) => ({
      id: 'priority.' + priority,
      label: this.t('priority.' + priority),
      checked: action.value === 'priority.' + priority,
    }));
  }

  protected userItems(action: RuleActionDto): MenuItem[] {
    return this.store.activeMembers().map((user) => ({
      id: user.name,
      label: user.name,
      checked: action.value === user.name,
    }));
  }

  protected actionItems(action: RuleActionDto): MenuItem[] {
    return ACTION_KINDS.map((kind) => ({
      id: kind,
      label: this.t('action.' + kind),
      icon: ACTION_ICONS[kind],
      checked: action.kind === kind,
    }));
  }

  protected actionPlaceholder(kind: string): string {
    if (kind === 'addLabel') return this.t('composer.newLabelPlaceholder');
    if (kind === 'setDueDate') return this.t('rules.dueDatePlaceholder');
    if (kind === 'comment') return this.t('task.writeComment');
    return this.t('rules.valuePlaceholder');
  }

  protected addCondition(): void {
    sequence += 1;
    this.conditions.update((current) => [
      ...current,
      {
        id: 'c' + sequence,
        kind: 'condition',
        fieldKey: 'cond.priority',
        op: 'isOneOf',
        values: [],
      },
    ]);
  }

  protected removeCondition(id: string): void {
    this.conditions.update((current) => current.filter((entry) => entry.id !== id));
  }

  protected setField(condition: RuleConditionDto, field: string): void {
    this.conditions.update((current) =>
      current.map((entry) =>
        entry.id === condition.id ? { ...entry, fieldKey: 'cond.' + field } : entry,
      ),
    );
  }

  protected setOp(condition: RuleConditionDto, op: string): void {
    this.conditions.update((current) =>
      current.map((entry) => (entry.id === condition.id ? { ...entry, op } : entry)),
    );
  }

  protected setValues(condition: RuleConditionDto, raw: string): void {
    const values = raw
      .split(',')
      .map((value) => value.trim())
      .filter((value) => value.length > 0);
    this.conditions.update((current) =>
      current.map((entry) => (entry.id === condition.id ? { ...entry, values } : entry)),
    );
  }

  protected addAction(): void {
    sequence += 1;
    this.actions.update((current) => [
      ...current,
      { id: 'a' + sequence, kind: 'setStatus', value: '', icon: ACTION_ICONS['setStatus'] },
    ]);
  }

  protected removeAction(id: string): void {
    this.actions.update((current) => current.filter((entry) => entry.id !== id));
  }

  protected setActionKind(action: RuleActionDto, kind: string): void {
    this.actions.update((current) =>
      current.map((entry) =>
        entry.id === action.id
          ? { ...entry, kind, value: '', icon: ACTION_ICONS[kind] ?? 'bolt' }
          : entry,
      ),
    );
  }

  protected setActionValue(action: RuleActionDto, value: string): void {
    this.actions.update((current) =>
      current.map((entry) => (entry.id === action.id ? { ...entry, value } : entry)),
    );
  }

  protected submit(): void {
    const name = this.name().trim();
    if (!name) {
      this.error.set(this.t('rules.nameRequired'));
      return;
    }
    this.saved.emit({
      name,
      summary: this.summary().trim(),
      scopeLabel: this.scopeLabel().trim(),
      draft: this.draft(),
      trigger: { kind: this.triggerKind(), value: this.triggerValue() },
      conditions: {
        id: 'g1',
        kind: 'group',
        join: this.join(),
        children: this.conditions(),
      },
      actions: this.actions(),
    });
  }

  private reset(): void {
    const rule = this.rule();
    this.name.set(rule?.name ?? '');
    this.summary.set(rule?.summary ?? '');
    this.scopeLabel.set(rule?.scopeLabel ?? '');
    this.draft.set(rule?.draft ?? false);
    this.triggerKind.set(rule?.trigger.kind ?? 'statusChanged');
    this.triggerValue.set(rule?.trigger.value ?? '');
    this.join.set(rule?.conditions.join ?? 'and');
    this.error.set('');

    const children = rule?.conditions.children ?? [];
    const flat = children.filter((child): child is RuleConditionDto => child.kind === 'condition');
    this.nested.set(children.length !== flat.length);
    this.conditions.set(flat.map((child) => ({ ...child, values: [...child.values] })));
    this.actions.set((rule?.actions ?? []).map((action) => ({ ...action })));
  }
}

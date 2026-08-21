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
import type { CustomFieldDto, StatusDto } from '../../core/api-types';
import { Dialog } from '../../ui/dialog';
import { Icon } from '../../ui/icon';
import { SelectField, type SelectOption } from '../../ui/select-field';
import { TextField } from '../../ui/text-field';

export interface CustomFieldDraft {
  name: string;
  fieldKey: string;
  type: string;
  scopeLabel: string;
  requiredPermission: string | null;
}

export interface StatusDraft {
  code: string;
  label: string;
  category: string;
  wipLimit: number | null;
}

const FIELD_TYPES = [
  'text',
  'number',
  'currency',
  'date',
  'select',
  'toggle',
  'person',
  'url',
  'relation',
] as const;

const FIELD_PERMISSIONS = ['fields.view_protected', 'fields.manage', 'settings.manage'] as const;
const CATEGORIES = ['notStarted', 'inFlight', 'done'] as const;

@Component({
  selector: 'app-custom-field-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Dialog, TextField, SelectField, Icon],
  template: `
    <ui-dialog
      [open]="open()"
      size="md"
      [title]="field() ? 'organization.editField' : 'organization.addField'"
      description="organization.fieldDialogLead"
      (closed)="cancelled.emit()"
    >
      <div class="flex flex-col gap-4">
        <ui-text-field
          [value]="name()"
          (valueChange)="onName($event)"
          label="organization.fieldName"
          [required]="true"
          [error]="error()"
        />
        <ui-text-field
          [value]="fieldKey()"
          (valueChange)="fieldKey.set($event)"
          label="organization.fieldKey"
          hint="organization.fieldKeyHint"
          [required]="true"
          [disabled]="field() !== null"
        />
        <div class="grid gap-4 sm:grid-cols-2">
          <ui-select-field
            [value]="type()"
            (valueChange)="type.set($event)"
            [options]="typeOptions()"
            label="organization.fieldType"
            [required]="true"
          />
          <ui-select-field
            [value]="requiredPermission()"
            (valueChange)="requiredPermission.set($event)"
            [options]="permissionOptions()"
            label="organization.fieldVisibility"
          />
        </div>
        <ui-text-field
          [value]="scopeLabel()"
          (valueChange)="scopeLabel.set($event)"
          label="organization.fieldScope"
          hint="organization.fieldScopeHint"
        />
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
export class CustomFieldDialog {
  protected readonly i18n = inject(I18nService);
  protected readonly t = this.i18n.t;

  readonly open = input(false);
  readonly field = input<CustomFieldDto | null>(null);

  readonly saved = output<CustomFieldDraft>();
  readonly cancelled = output<void>();

  protected readonly name = signal('');
  protected readonly fieldKey = signal('');
  protected readonly type = signal('text');
  protected readonly scopeLabel = signal('');
  protected readonly requiredPermission = signal('');
  protected readonly error = signal('');
  private touchedKey = false;

  constructor() {
    effect(() => {
      if (!this.open()) return;
      untracked(() => {
        const field = this.field();
        this.name.set(field?.name ?? '');
        this.fieldKey.set(field?.fieldKey ?? '');
        this.type.set(field?.type ?? 'text');
        this.scopeLabel.set(field?.scopeLabel ?? '');
        this.requiredPermission.set(field?.requiredPermission ?? '');
        this.error.set('');
        this.touchedKey = field !== null;
      });
    });
  }

  protected readonly typeOptions = computed<SelectOption[]>(() =>
    FIELD_TYPES.map((type) => ({ value: type, label: this.t('fieldType.' + type) })),
  );

  protected readonly permissionOptions = computed<SelectOption[]>(() => [
    { value: '', label: this.t('common.everyone') },
    ...FIELD_PERMISSIONS.map((permission) => ({
      value: permission,
      label: this.t('organization.permissionOnly', {
        permission: this.t('permission.' + permission),
      }),
    })),
  ]);

  protected onName(value: string): void {
    this.name.set(value);
    if (!this.touchedKey) {
      this.fieldKey.set(slug(value));
    }
  }

  protected submit(): void {
    const name = this.name().trim();
    const fieldKey = slug(this.fieldKey());
    if (!name || !fieldKey) {
      this.error.set(this.t('organization.fieldNameRequired'));
      return;
    }
    this.saved.emit({
      name,
      fieldKey,
      type: this.type(),
      scopeLabel: this.scopeLabel().trim(),
      requiredPermission: this.requiredPermission() || null,
    });
  }
}

@Component({
  selector: 'app-status-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Dialog, TextField, SelectField, Icon],
  template: `
    <ui-dialog
      [open]="open()"
      size="md"
      [title]="status() ? 'organization.editStatus' : 'organization.addStatus'"
      description="organization.statusDialogLead"
      (closed)="cancelled.emit()"
    >
      <div class="flex flex-col gap-4">
        <ui-text-field
          [value]="label()"
          (valueChange)="onLabel($event)"
          label="organization.statusLabel"
          [required]="true"
          [error]="error()"
        />
        <div class="grid gap-4 sm:grid-cols-2">
          <ui-text-field
            [value]="code()"
            (valueChange)="code.set($event)"
            label="organization.statusCode"
            hint="organization.statusCodeHint"
            [required]="true"
            [disabled]="status() !== null"
          />
          <ui-select-field
            [value]="category()"
            (valueChange)="category.set($event)"
            [options]="categoryOptions()"
            label="organization.statusCategory"
            [required]="true"
          />
        </div>
        <ui-text-field
          [value]="wipLimit()"
          (valueChange)="wipLimit.set($event)"
          label="organization.wipLimit"
          hint="organization.wipHint"
          type="number"
        />
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
export class StatusDialog {
  protected readonly i18n = inject(I18nService);
  protected readonly t = this.i18n.t;

  readonly open = input(false);
  readonly status = input<StatusDto | null>(null);

  readonly saved = output<StatusDraft>();
  readonly cancelled = output<void>();

  protected readonly code = signal('');
  protected readonly label = signal('');
  protected readonly category = signal('notStarted');
  protected readonly wipLimit = signal('');
  protected readonly error = signal('');
  private touchedCode = false;

  constructor() {
    effect(() => {
      if (!this.open()) return;
      untracked(() => {
        const status = this.status();
        this.code.set(status?.code ?? '');
        this.label.set(status ? this.i18n.label('status.' + status.code, status.label) : '');
        this.category.set(status?.category ?? 'notStarted');
        this.wipLimit.set(status?.wipLimit === null || !status ? '' : String(status.wipLimit));
        this.error.set('');
        this.touchedCode = status !== null;
      });
    });
  }

  protected readonly categoryOptions = computed<SelectOption[]>(() =>
    CATEGORIES.map((category) => ({
      value: category,
      label: this.t('statusCategory.' + category),
    })),
  );

  protected onLabel(value: string): void {
    this.label.set(value);
    if (!this.touchedCode) {
      this.code.set(slug(value));
    }
  }

  protected submit(): void {
    const label = this.label().trim();
    const code = slug(this.code());
    if (!label || !code) {
      this.error.set(this.t('organization.statusLabelRequired'));
      return;
    }
    const parsed = Number.parseInt(this.wipLimit(), 10);
    this.saved.emit({
      code,
      label,
      category: this.category(),
      wipLimit: Number.isFinite(parsed) && parsed > 0 ? parsed : null,
    });
  }
}

function slug(value: string): string {
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[\u0141\u0142]/g, 'l')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '');
}

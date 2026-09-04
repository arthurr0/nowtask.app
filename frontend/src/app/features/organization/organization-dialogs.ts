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
import type { PermissionDto, RoleDto } from '../../core/api-types';
import { Dialog } from '../../ui/dialog';
import { Icon } from '../../ui/icon';
import { SelectField, type SelectOption } from '../../ui/select-field';
import { TextField } from '../../ui/text-field';

export interface InviteDraft {
  name: string;
  email: string;
  role: string;
}

export interface RoleDraft {
  code: string;
  name: string;
  permissions: string[];
}

const DEFAULT_INVITE_ROLE = 'member';
const ROLE_CODE = /^[a-z0-9][a-z0-9_-]{0,38}$/;

@Component({
  selector: 'app-invite-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Dialog, TextField, SelectField, Icon],
  template: `
    <ui-dialog
      [open]="open()"
      size="md"
      title="organization.invite"
      description="organization.inviteLead"
      (closed)="cancelled.emit()"
    >
      <div class="flex flex-col gap-4">
        <ui-text-field
          [value]="name()"
          (valueChange)="name.set($event)"
          label="organization.personName"
          placeholder="organization.personNamePlaceholder"
          [required]="true"
          [error]="error()"
        />
        <ui-text-field
          [value]="email()"
          (valueChange)="email.set($event)"
          label="organization.personEmail"
          type="email"
          placeholder="organization.personEmailPlaceholder"
          [required]="true"
        />
        <ui-select-field
          [value]="role()"
          (valueChange)="role.set($event)"
          [options]="roleOptions()"
          label="organization.role"
          [required]="true"
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
          <ui-icon name="plus" [size]="15" />
          {{ t('organization.invite') }}
        </button>
      </div>
    </ui-dialog>
  `,
})
export class InviteDialog {
  private readonly i18n = inject(I18nService);
  protected readonly t = this.i18n.t;

  readonly open = input(false);
  readonly roles = input<readonly RoleDto[]>([]);
  readonly saved = output<InviteDraft>();
  readonly cancelled = output<void>();

  protected readonly name = signal('');
  protected readonly email = signal('');
  protected readonly role = signal(DEFAULT_INVITE_ROLE);
  protected readonly error = signal('');

  constructor() {
    effect(() => {
      if (!this.open()) return;
      untracked(() => {
        this.name.set('');
        this.email.set('');
        this.role.set(this.defaultRole());
        this.error.set('');
      });
    });
  }

  protected readonly roleOptions = computed<SelectOption[]>(() =>
    this.roles().map((role) => ({ value: role.code, label: role.name })),
  );

  private defaultRole(): string {
    const roles = this.roles();
    if (roles.some((role) => role.code === DEFAULT_INVITE_ROLE)) return DEFAULT_INVITE_ROLE;
    return roles.find((role) => !role.isProtected)?.code ?? roles[0]?.code ?? '';
  }

  protected submit(): void {
    const name = this.name().trim();
    const email = this.email().trim();
    if (!name || !email.includes('@')) {
      this.error.set(this.t('organization.inviteInvalid'));
      return;
    }
    this.saved.emit({ name, email, role: this.role() });
  }
}

@Component({
  selector: 'app-role-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Dialog, TextField, Icon],
  template: `
    <ui-dialog
      [open]="open()"
      size="md"
      title="organization.newRole"
      description="organization.roleDialogLead"
      (closed)="cancelled.emit()"
    >
      <div class="flex flex-col gap-4">
        <ui-text-field
          [value]="name()"
          (valueChange)="onName($event)"
          label="organization.roleName"
          placeholder="organization.roleNamePlaceholder"
          [required]="true"
          [error]="error()"
        />
        <ui-text-field
          [value]="code()"
          (valueChange)="onCode($event)"
          label="organization.roleCode"
          hint="organization.roleCodeHint"
          [required]="true"
        />

        <div class="flex flex-col gap-3">
          @for (group of groups(); track group.group) {
            <div class="flex flex-col gap-1.5">
              <span class="kap text-ink-3">{{ t('permissionGroup.' + group.group) }}</span>
              <div class="grid gap-x-4 gap-y-1.5 sm:grid-cols-2">
                @for (permission of group.items; track permission.code) {
                  <label class="flex items-center gap-2.5 text-[13px]">
                    <input
                      type="checkbox"
                      [checked]="selected().has(permission.code)"
                      (change)="toggle(permission.code)"
                    />
                    {{ t('permission.' + permission.code) }}
                  </label>
                }
              </div>
            </div>
          }
        </div>
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
          <ui-icon name="plus" [size]="15" />
          {{ t('organization.newRole') }}
        </button>
      </div>
    </ui-dialog>
  `,
})
export class RoleDialog {
  private readonly i18n = inject(I18nService);
  protected readonly t = this.i18n.t;

  readonly open = input(false);
  readonly permissions = input<readonly PermissionDto[]>([]);
  readonly saved = output<RoleDraft>();
  readonly cancelled = output<void>();

  protected readonly name = signal('');
  protected readonly code = signal('');
  protected readonly selected = signal<ReadonlySet<string>>(new Set());
  protected readonly error = signal('');
  private touchedCode = false;

  constructor() {
    effect(() => {
      if (!this.open()) return;
      untracked(() => {
        this.name.set('');
        this.code.set('');
        this.selected.set(new Set());
        this.error.set('');
        this.touchedCode = false;
      });
    });
  }

  protected readonly groups = computed<{ group: string; items: PermissionDto[] }[]>(() => {
    const groups = new Map<string, PermissionDto[]>();
    for (const permission of this.permissions()) {
      const items = groups.get(permission.group) ?? [];
      items.push(permission);
      groups.set(permission.group, items);
    }
    return [...groups].map(([group, items]) => ({ group, items }));
  });

  protected onName(value: string): void {
    this.name.set(value);
    if (!this.touchedCode) this.code.set(slug(value));
  }

  protected onCode(value: string): void {
    this.touchedCode = true;
    this.code.set(value);
  }

  protected toggle(code: string): void {
    this.selected.update((current) => {
      const next = new Set(current);
      if (next.has(code)) next.delete(code);
      else next.add(code);
      return next;
    });
  }

  protected submit(): void {
    const name = this.name().trim();
    const code = slug(this.code());
    if (!name || !ROLE_CODE.test(code)) {
      this.error.set(this.t('organization.roleInvalid'));
      return;
    }
    this.saved.emit({ code, name, permissions: [...this.selected()] });
  }
}

@Component({
  selector: 'app-delete-role-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Dialog, SelectField, Icon],
  template: `
    <ui-dialog
      [open]="open()"
      size="sm"
      title="organization.deleteRole"
      [description]="lead()"
      (closed)="cancelled.emit()"
    >
      <ui-select-field
        [value]="target()"
        (valueChange)="target.set($event)"
        [options]="targetOptions()"
        label="organization.reassignTo"
        [required]="true"
      />

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
          class="flex h-9 items-center gap-1.5 rounded-[7px] border border-warn px-3.5 text-[13px] font-medium text-warn disabled:opacity-40"
          [disabled]="!target()"
          (click)="confirmed.emit(target())"
        >
          <ui-icon name="trash" [size]="15" />
          {{ t('common.delete') }}
        </button>
      </div>
    </ui-dialog>
  `,
})
export class DeleteRoleDialog {
  private readonly i18n = inject(I18nService);
  protected readonly t = this.i18n.t;

  readonly open = input(false);
  readonly role = input<RoleDto | null>(null);
  readonly roles = input<readonly RoleDto[]>([]);
  readonly confirmed = output<string>();
  readonly cancelled = output<void>();

  protected readonly target = signal('');

  constructor() {
    effect(() => {
      if (!this.open()) return;
      untracked(() => this.target.set(this.targetOptions()[0]?.value ?? ''));
    });
  }

  protected readonly lead = computed(() => {
    const role = this.role();
    if (!role) return '';
    return this.t('organization.deleteRoleLead', { name: role.name, count: role.memberCount });
  });

  protected readonly targetOptions = computed<SelectOption[]>(() =>
    this.roles()
      .filter((role) => role.id !== this.role()?.id)
      .map((role) => ({ value: role.id, label: role.name })),
  );
}

function slug(value: string): string {
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[\u0141\u0142]/g, 'l')
    .toLowerCase()
    .replace(/[^a-z0-9_-]+/g, '_')
    .replace(/^[_-]+|[_-]+$/g, '');
}

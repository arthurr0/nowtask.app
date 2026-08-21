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
import { Dialog } from '../../ui/dialog';
import { Icon } from '../../ui/icon';
import { SelectField, type SelectOption } from '../../ui/select-field';
import { TextField } from '../../ui/text-field';

export interface InviteDraft {
  name: string;
  email: string;
  role: string;
}

const ROLES = ['admin', 'manager', 'member', 'guest'] as const;

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
  readonly saved = output<InviteDraft>();
  readonly cancelled = output<void>();

  protected readonly name = signal('');
  protected readonly email = signal('');
  protected readonly role = signal('member');
  protected readonly error = signal('');

  constructor() {
    effect(() => {
      if (!this.open()) return;
      untracked(() => {
        this.name.set('');
        this.email.set('');
        this.role.set('member');
        this.error.set('');
      });
    });
  }

  protected readonly roleOptions = computed<SelectOption[]>(() =>
    ROLES.map((role) => ({ value: role, label: this.t('role.' + role) })),
  );

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

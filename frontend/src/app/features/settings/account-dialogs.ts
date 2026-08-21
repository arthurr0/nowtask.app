import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { I18nService } from '../../core/i18n/i18n.service';
import { Dialog } from '../../ui/dialog';
import { Icon } from '../../ui/icon';
import { TextField } from '../../ui/text-field';

export interface EmailChangeDraft {
  email: string;
  password: string;
}

@Component({
  selector: 'app-email-change-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Dialog, TextField, Icon],
  template: `
    <ui-dialog
      [open]="open()"
      size="sm"
      title="account.changeEmail"
      description="account.changeEmailLead"
      (closed)="cancelled.emit()"
    >
      <div class="flex flex-col gap-4">
        <ui-text-field
          [value]="email()"
          (valueChange)="email.set($event)"
          label="account.newEmail"
          type="email"
          autocomplete="email"
          [required]="true"
          [error]="error()"
        />
        @if (passwordRequired()) {
          <ui-text-field
            [value]="password()"
            (valueChange)="password.set($event)"
            label="account.currentPassword"
            type="password"
            autocomplete="current-password"
            [required]="true"
          />
        }
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
          [disabled]="!ready()"
          [class.opacity-50]="!ready()"
          (click)="submit()"
        >
          <ui-icon name="check" [size]="15" />
          {{ t('account.sendConfirmation') }}
        </button>
      </div>
    </ui-dialog>
  `,
})
export class EmailChangeDialog {
  protected readonly t = inject(I18nService).t;

  readonly open = input(false);
  readonly passwordRequired = input(true);
  readonly error = input('');

  readonly saved = output<EmailChangeDraft>();
  readonly cancelled = output<void>();

  protected readonly email = signal('');
  protected readonly password = signal('');

  protected readonly ready = computed(
    () => this.email().includes('@') && (!this.passwordRequired() || this.password().length > 0),
  );

  constructor() {
    effect(() => {
      if (this.open()) return;
      this.email.set('');
      this.password.set('');
    });
  }

  protected submit(): void {
    if (!this.ready()) return;
    this.saved.emit({ email: this.email().trim(), password: this.password() });
  }
}

@Component({
  selector: 'app-delete-account-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Dialog, TextField, Icon],
  template: `
    <ui-dialog
      [open]="open()"
      size="sm"
      title="account.deleteTitle"
      description="account.deleteLead"
      (closed)="cancelled.emit()"
    >
      <div class="flex flex-col gap-4">
        <p class="rounded-panel border border-warn/40 bg-warn/5 p-3 text-[13px] text-ink-2">
          {{ t('account.deleteWarning') }}
        </p>
        <ui-text-field
          [value]="confirmation()"
          (valueChange)="confirmation.set($event)"
          label="account.deleteConfirmLabel"
          [hint]="email()"
          [required]="true"
        />
        @if (passwordRequired()) {
          <ui-text-field
            [value]="password()"
            (valueChange)="password.set($event)"
            label="account.currentPassword"
            type="password"
            autocomplete="current-password"
            [required]="true"
            [error]="error()"
          />
        }
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
          class="flex h-9 items-center gap-1.5 rounded-[7px] bg-warn px-3.5 text-[13px] font-medium text-white"
          [disabled]="!ready()"
          [class.opacity-50]="!ready()"
          (click)="submit()"
        >
          <ui-icon name="trash" [size]="15" />
          {{ t('account.deleteConfirm') }}
        </button>
      </div>
    </ui-dialog>
  `,
})
export class DeleteAccountDialog {
  protected readonly t = inject(I18nService).t;

  readonly open = input(false);
  readonly email = input('');
  readonly passwordRequired = input(true);
  readonly error = input('');

  readonly confirmed = output<string>();
  readonly cancelled = output<void>();

  protected readonly confirmation = signal('');
  protected readonly password = signal('');

  protected readonly ready = computed(
    () =>
      this.confirmation().trim().toLowerCase() === this.email().toLowerCase() &&
      (!this.passwordRequired() || this.password().length > 0),
  );

  constructor() {
    effect(() => {
      if (this.open()) return;
      this.confirmation.set('');
      this.password.set('');
    });
  }

  protected submit(): void {
    if (!this.ready()) return;
    this.confirmed.emit(this.password());
  }
}

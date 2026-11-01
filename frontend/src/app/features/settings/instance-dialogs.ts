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

@Component({
  selector: 'app-delete-organization-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Dialog, TextField, Icon],
  template: `
    <ui-dialog
      [open]="open()"
      size="sm"
      title="instance.orgs.deleteTitle"
      [description]="lead()"
      [dismissible]="!busy()"
      (closed)="cancelled.emit()"
    >
      <div class="flex flex-col gap-4">
        <p class="rounded-panel border border-warn/40 bg-warn/5 p-3 text-[13px] text-ink-2">
          {{ t('instance.orgs.deleteWarning') }}
        </p>
        <ui-text-field
          [value]="confirmation()"
          (valueChange)="confirmation.set($event)"
          label="instance.orgs.deleteConfirmLabel"
          [hint]="slug()"
          [required]="true"
          [error]="error()"
          (submitted)="submit()"
        />
      </div>

      <div dialogFooter class="flex w-full items-center justify-end gap-2">
        <button
          type="button"
          class="flex h-9 items-center rounded-[7px] border border-line-strong bg-surface px-3.5 text-[13px] disabled:opacity-50"
          [disabled]="busy()"
          (click)="cancelled.emit()"
        >
          {{ t('common.cancel') }}
        </button>
        <button
          type="button"
          class="flex h-9 items-center gap-1.5 rounded-[7px] bg-warn px-3.5 text-[13px] font-medium text-white"
          [disabled]="!ready() || busy()"
          [class.opacity-50]="!ready() || busy()"
          (click)="submit()"
        >
          <ui-icon name="trash" [size]="15" />
          {{ t('instance.orgs.deleteConfirm') }}
        </button>
      </div>
    </ui-dialog>
  `,
})
export class DeleteOrganizationDialog {
  protected readonly t = inject(I18nService).t;

  readonly open = input(false);
  readonly name = input('');
  readonly slug = input('');
  readonly error = input('');
  readonly busy = input(false);

  readonly confirmed = output<string>();
  readonly cancelled = output<void>();

  protected readonly confirmation = signal('');

  protected readonly lead = computed(() =>
    this.t('instance.orgs.deleteLead', { name: this.name() }),
  );

  protected readonly ready = computed(
    () => this.slug() !== '' && this.confirmation().trim() === this.slug(),
  );

  constructor() {
    effect(() => {
      if (this.open()) return;
      this.confirmation.set('');
    });
  }

  protected submit(): void {
    if (!this.ready() || this.busy()) return;
    this.confirmed.emit(this.confirmation().trim());
  }
}

import {
  ChangeDetectionStrategy,
  Component,
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
import { TextField } from '../../ui/text-field';

export interface ApiKeyDraft {
  label: string;
  scopes: string[];
  expiresInDays: number | null;
}

const SCOPES = [
  'tasks:read',
  'tasks:write',
  'tasks:delete',
  'rules:read',
  'rules:run',
  'rules:write',
  'metrics:read',
  'workspace:read',
] as const;

const DEFAULT_SCOPES = ['tasks:read', 'workspace:read', 'rules:read', 'metrics:read'];

@Component({
  selector: 'app-api-key-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Dialog, TextField, Icon],
  template: `
    <ui-dialog
      [open]="open()"
      size="md"
      title="admin.newKey"
      description="admin.newKeyLead"
      (closed)="cancelled.emit()"
    >
      <div class="flex flex-col gap-4">
        <ui-text-field
          [value]="label()"
          (valueChange)="label.set($event)"
          label="admin.keyLabel"
          placeholder="admin.keyLabelPlaceholder"
          [required]="true"
          [error]="error()"
        />

        <div class="flex flex-col gap-2">
          <span class="kap">{{ t('admin.scopes') }}</span>
          <div class="flex flex-wrap gap-1.5">
            @for (scope of scopes; track scope) {
              <button
                type="button"
                class="flex h-7 items-center gap-1.5 rounded-full border px-2.5 font-mono text-[11px]"
                [class.bg-inv]="selected().includes(scope)"
                [class.text-inv-ink]="selected().includes(scope)"
                [class.border-inv]="selected().includes(scope)"
                [class.border-line]="!selected().includes(scope)"
                [class.text-ink-2]="!selected().includes(scope)"
                [attr.aria-pressed]="selected().includes(scope)"
                (click)="toggle(scope)"
              >
                @if (selected().includes(scope)) {
                  <ui-icon name="check" [size]="11" />
                }
                {{ scope }}
              </button>
            }
          </div>
          @if (selected().includes('tasks:delete')) {
            <p class="flex items-center gap-1.5 text-[11px] text-warn">
              <ui-icon name="alert" [size]="13" />
              {{ t('admin.deleteScopeWarning') }}
            </p>
          }
        </div>

        <ui-text-field
          [value]="expiresInDays()"
          (valueChange)="expiresInDays.set($event)"
          label="admin.keyExpiry"
          hint="admin.keyExpiryHint"
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
          <ui-icon name="key" [size]="15" />
          {{ t('admin.createKey') }}
        </button>
      </div>
    </ui-dialog>
  `,
})
export class ApiKeyDialog {
  private readonly i18n = inject(I18nService);
  protected readonly t = this.i18n.t;

  readonly open = input(false);
  readonly saved = output<ApiKeyDraft>();
  readonly cancelled = output<void>();

  protected readonly scopes = SCOPES;
  protected readonly label = signal('');
  protected readonly selected = signal<string[]>([...DEFAULT_SCOPES]);
  protected readonly expiresInDays = signal('');
  protected readonly error = signal('');

  constructor() {
    effect(() => {
      if (!this.open()) return;
      untracked(() => {
        this.label.set('');
        this.selected.set([...DEFAULT_SCOPES]);
        this.expiresInDays.set('');
        this.error.set('');
      });
    });
  }

  protected toggle(scope: string): void {
    this.selected.update((current) =>
      current.includes(scope) ? current.filter((entry) => entry !== scope) : [...current, scope],
    );
  }

  protected submit(): void {
    const label = this.label().trim();
    if (!label) {
      this.error.set(this.t('admin.keyLabelRequired'));
      return;
    }
    if (this.selected().length === 0) {
      this.error.set(this.t('admin.scopesRequired'));
      return;
    }
    const parsed = Number.parseInt(this.expiresInDays(), 10);
    this.saved.emit({
      label,
      scopes: [...this.selected()],
      expiresInDays: Number.isFinite(parsed) && parsed > 0 ? parsed : null,
    });
  }
}

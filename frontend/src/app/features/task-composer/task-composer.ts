import {
  ChangeDetectionStrategy,
  Component,
  Injectable,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { Router } from '@angular/router';
import { I18nService } from '../../core/i18n/i18n.service';
import type { Priority } from '../../core/models';
import { ViewState } from '../../data/view-state';
import { WorkspaceStore } from '../../data/workspace.store';
import { ComboField, type ComboOption } from '../../ui/combo-field';
import { DateField } from '../../ui/date-field';
import { Dialog } from '../../ui/dialog';
import { Icon } from '../../ui/icon';
import { SelectField, type SelectOption } from '../../ui/select-field';
import { TextField } from '../../ui/text-field';
import { TextareaField } from '../../ui/textarea-field';
import { ToastService } from '../../ui/toast.service';

export interface TaskComposerPrefill {
  statusId?: string;
  projectId?: string;
  assigneeId?: string | null;
  epicId?: string | null;
  labels?: string[];
  dueDate?: string | null;
  priority?: Priority;
}

@Injectable({ providedIn: 'root' })
export class TaskComposer {
  private readonly visibleSignal = signal(false);
  private readonly prefillSignal = signal<TaskComposerPrefill>({});

  readonly visible = this.visibleSignal.asReadonly();
  readonly prefill = this.prefillSignal.asReadonly();

  open(prefill: TaskComposerPrefill = {}): void {
    this.prefillSignal.set(prefill);
    this.visibleSignal.set(true);
  }

  close(): void {
    this.visibleSignal.set(false);
  }
}

const PRIORITIES: readonly Priority[] = ['low', 'medium', 'high', 'critical'];

@Component({
  selector: 'app-task-composer',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Dialog, TextField, TextareaField, SelectField, ComboField, DateField, Icon],
  template: `
    <ui-dialog
      [open]="composer.visible()"
      size="lg"
      title="composer.title"
      description="composer.description"
      (closed)="composer.close()"
    >
      <form class="flex flex-col gap-4" (submit)="$event.preventDefault(); submit(false)">
        <ui-text-field
          [value]="title()"
          (valueChange)="title.set($event)"
          label="composer.taskTitle"
          placeholder="composer.titlePlaceholder"
          [required]="true"
          [error]="titleError()"
          [maxLength]="180"
        />

        <ui-textarea-field
          [value]="description()"
          (valueChange)="description.set($event)"
          label="task.description"
          placeholder="composer.descriptionPlaceholder"
          [rows]="4"
          [maxLength]="4000"
        />

        <div class="grid gap-4 sm:grid-cols-2">
          @if (projectOptions().length > 1) {
            <ui-select-field
              [value]="projectId()"
              (valueChange)="projectId.set($event)"
              [options]="projectOptions()"
              label="composer.project"
              [required]="true"
            />
          }
          <ui-select-field
            [value]="statusId()"
            (valueChange)="statusId.set($event)"
            [options]="statusOptions()"
            label="common.status"
            [required]="true"
          />
          <ui-select-field
            [value]="priority()"
            (valueChange)="priority.set($event)"
            [options]="priorityOptions()"
            label="list.priority"
            [required]="true"
          />
          <ui-combo-field
            [value]="assigneeId()"
            (valueChange)="assigneeId.set($any($event))"
            [options]="userOptions()"
            label="common.assignee"
            placeholder="common.unassigned"
          />
          <ui-combo-field
            [value]="reviewerId()"
            (valueChange)="reviewerId.set($any($event))"
            [options]="userOptions()"
            label="task.reviewer"
            placeholder="common.none"
          />
          <ui-date-field
            [value]="dueDate()"
            (valueChange)="dueDate.set($event)"
            label="task.dueDate"
            [min]="today"
          />
          <ui-text-field
            [value]="estimate()"
            (valueChange)="estimate.set($event)"
            label="task.estimate"
            type="number"
            placeholder="composer.estimatePlaceholder"
          />
          <ui-combo-field
            [value]="epicId()"
            (valueChange)="epicId.set($any($event))"
            [options]="epicOptions()"
            label="task.epic"
            placeholder="common.none"
          />
          <ui-combo-field
            [value]="labels()"
            (valueChange)="labels.set($any($event))"
            [options]="labelOptions()"
            label="list.labels"
            [multiple]="true"
            placeholder="composer.labelsPlaceholder"
          />
        </div>

        <label class="flex flex-col gap-1.5">
          <span class="kap">{{ t('composer.newLabel') }}</span>
          <span class="flex items-center gap-2">
            <input
              class="focus-ring h-9 min-w-0 flex-1 rounded-field border border-line bg-surface px-3 text-[13px] outline-none"
              [placeholder]="t('composer.newLabelPlaceholder')"
              [value]="labelDraft()"
              (input)="labelDraft.set($any($event.target).value)"
              (keydown.enter)="$event.preventDefault(); addLabel()"
            />
            <button
              type="button"
              class="flex h-9 w-9 flex-none items-center justify-center rounded-field border border-line bg-surface-2 text-ink-2"
              [attr.aria-label]="t('composer.addLabel')"
              (click)="addLabel()"
            >
              <ui-icon name="plus" [size]="16" />
            </button>
          </span>
        </label>
      </form>

      <div dialogFooter class="flex w-full items-center gap-2">
        @if (error()) {
          <p class="flex min-w-0 flex-1 items-center gap-1.5 text-[12px] text-warn">
            <ui-icon name="alert" [size]="14" />
            <span class="truncate">{{ error() }}</span>
          </p>
        } @else {
          <span class="flex-1"></span>
        }
        <button
          type="button"
          class="flex h-9 items-center rounded-[7px] border border-line-strong bg-surface px-3.5 text-[13px]"
          (click)="composer.close()"
        >
          {{ t('common.cancel') }}
        </button>
        <button
          type="button"
          class="flex h-9 items-center gap-1.5 rounded-[7px] border border-line-strong bg-surface px-3.5 text-[13px]"
          [disabled]="saving()"
          (click)="submit(true)"
        >
          <ui-icon name="arrow-right" [size]="15" />
          {{ t('composer.createAndOpen') }}
        </button>
        <button
          type="button"
          class="flex h-9 items-center gap-1.5 rounded-[7px] bg-inv px-3.5 text-[13px] font-medium text-inv-ink"
          [disabled]="saving()"
          (click)="submit(false)"
        >
          <ui-icon name="plus" [size]="15" />
          {{ saving() ? t('composer.saving') : t('composer.create') }}
        </button>
      </div>
    </ui-dialog>
  `,
})
export class TaskComposerDialog {
  protected readonly composer = inject(TaskComposer);
  protected readonly store = inject(WorkspaceStore);
  private readonly view = inject(ViewState);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);
  protected readonly t = this.i18n.t;

  protected readonly title = signal('');
  protected readonly description = signal('');
  protected readonly statusId = signal('');
  protected readonly projectId = signal('');
  protected readonly priority = signal<string>('medium');
  protected readonly assigneeId = signal<string | null>(null);
  protected readonly reviewerId = signal<string | null>(null);
  protected readonly dueDate = signal('');
  protected readonly estimate = signal('');
  protected readonly epicId = signal<string | null>(null);
  protected readonly labels = signal<readonly string[]>([]);
  protected readonly labelDraft = signal('');
  protected readonly extraLabels = signal<readonly string[]>([]);

  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected readonly titleError = signal('');

  protected readonly today = new Date().toISOString().slice(0, 10);

  constructor() {
    effect(() => {
      if (this.composer.visible()) {
        untracked(() => this.reset());
      }
    });
  }

  protected readonly projectOptions = computed<SelectOption[]>(() =>
    this.store.activeProjects().map((project) => ({
      value: project.id,
      label: `${project.name} (${project.code})`,
    })),
  );

  protected readonly statusOptions = computed<SelectOption[]>(() =>
    this.store.boardStatuses().map((status) => ({
      value: status.id,
      label: this.store.statusName(status),
    })),
  );

  protected readonly priorityOptions = computed<SelectOption[]>(() =>
    PRIORITIES.map((priority) => ({ value: priority, label: this.t('priority.' + priority) })),
  );

  protected readonly userOptions = computed<ComboOption[]>(() =>
    this.store.activeMembers().map((user) => ({
      value: user.id,
      label: user.name,
      hint: this.t('role.' + user.role),
    })),
  );

  protected readonly epicOptions = computed<ComboOption[]>(() =>
    this.store
      .epicsOfProject(this.projectId() || null)
      .map((epic) => ({ value: epic.id, label: epic.name })),
  );

  protected readonly labelOptions = computed<ComboOption[]>(() => {
    const merged = new Set([...this.store.allLabels(), ...this.extraLabels()]);
    return [...merged].map((label) => ({ value: label, label }));
  });

  protected addLabel(): void {
    const label = this.labelDraft().trim();
    if (!label) return;
    this.extraLabels.update((list) => (list.includes(label) ? list : [...list, label]));
    this.labels.update((list) => (list.includes(label) ? list : [...list, label]));
    this.labelDraft.set('');
  }

  protected async submit(openAfter: boolean): Promise<void> {
    const title = this.title().trim();
    if (!title) {
      this.titleError.set(this.t('composer.titleRequired'));
      return;
    }
    const statusId = this.statusId();
    if (!statusId) {
      this.error.set(this.t('composer.statusRequired'));
      return;
    }

    this.titleError.set('');
    this.error.set('');
    this.saving.set(true);

    const parsedEstimate = Number.parseInt(this.estimate(), 10);

    try {
      const created = await this.store.createTask({
        title,
        description: this.description().trim(),
        statusId,
        projectId: this.projectId() || null,
        priority: this.priority(),
        assigneeId: this.assigneeId(),
        reviewerId: this.reviewerId(),
        dueDate: this.dueDate() || null,
        estimate: Number.isFinite(parsedEstimate) ? parsedEstimate : null,
        epicId: this.epicId(),
        labels: [...this.labels()],
      });

      this.composer.close();
      this.toast.success(this.t('composer.created', { key: created.key }), {
        action: openAfter
          ? undefined
          : { label: this.t('composer.openTask'), run: () => this.openTask(created.key) },
      });

      if (openAfter) this.openTask(created.key);
    } catch (error) {
      this.error.set(describeError(error, this.t('composer.failed')));
    } finally {
      this.saving.set(false);
    }
  }

  private openTask(key: string): void {
    void this.router.navigate(['/app/tasks', key]);
  }

  private reset(): void {
    const prefill = this.composer.prefill();
    this.title.set('');
    this.description.set('');
    this.statusId.set(prefill.statusId ?? this.store.boardStatuses()[0]?.id ?? '');
    this.projectId.set(
      prefill.projectId ?? this.view.projectId() ?? this.store.activeProjects()[0]?.id ?? '',
    );
    this.priority.set(prefill.priority ?? 'medium');
    this.assigneeId.set(prefill.assigneeId ?? null);
    this.reviewerId.set(null);
    this.dueDate.set(prefill.dueDate ?? '');
    this.estimate.set('');
    this.epicId.set(prefill.epicId ?? null);
    this.labels.set(prefill.labels ?? []);
    this.labelDraft.set('');
    this.extraLabels.set([]);
    this.error.set('');
    this.titleError.set('');
    this.saving.set(false);
  }
}

export function describeError(error: unknown, fallback: string): string {
  if (typeof error === 'object' && error !== null && 'error' in error) {
    const body = (error as { error?: { message?: string } }).error;
    if (body?.message) return body.message;
  }
  return fallback;
}

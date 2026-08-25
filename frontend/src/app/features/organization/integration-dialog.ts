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
import { WorkspaceStore } from '../../data/workspace.store';
import type { IntegrationKind, WebhookFormat } from '../../core/api-types';
import { Dialog } from '../../ui/dialog';
import { Icon } from '../../ui/icon';
import { SelectField, type SelectOption } from '../../ui/select-field';
import { TextField } from '../../ui/text-field';

export interface IntegrationDraft {
  kind: IntegrationKind;
  name: string;
  config: Record<string, unknown>;
}

const KINDS: readonly IntegrationKind[] = ['webhook', 'email', 'github'];
const FORMATS: readonly WebhookFormat[] = ['generic', 'discord', 'slack'];
const EVENTS = ['taskCreated', 'taskStatusChanged', 'taskAssigned', 'ruleNotify'] as const;
const GITHUB_SWITCHES = [
  'syncIssues',
  'commentOnPull',
  'commentOnPush',
  'commentOnIssueComment',
  'commentOnReview',
  'commentOnWorkflow',
  'commentOnRelease',
  'commentOnBranch',
  'assignFromPull',
  'linkCommits',
] as const;
const GITHUB_SWITCHES_ON: readonly string[] = [
  'syncIssues',
  'commentOnPull',
  'commentOnPush',
  'commentOnIssueComment',
  'commentOnReview',
  'commentOnRelease',
  'linkCommits',
];
const REPO = /^[A-Za-z0-9._-]+\/[A-Za-z0-9._-]+$/;
const PROJECT = /^[A-Za-z][A-Za-z0-9]*$/;
const GITHUB_STATUSES = [
  'statusOnBranchCreated',
  'statusOnBranchPush',
  'statusOnPullOpen',
  'statusOnReviewApproved',
  'statusOnReviewChangesRequested',
  'statusOnPullMerged',
  'statusOnWorkflowFailure',
  'statusOnIssueClosed',
  'statusOnIssueReopened',
] as const;

@Component({
  selector: 'app-integration-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Dialog, TextField, SelectField, Icon],
  template: `
    <ui-dialog
      [open]="open()"
      size="md"
      title="organization.newIntegration"
      description="organization.integrationsLead"
      (closed)="cancelled.emit()"
    >
      <div class="flex flex-col gap-4">
        <ui-select-field
          [value]="kind()"
          (valueChange)="kind.set($any($event))"
          [options]="kindOptions()"
          label="organization.integrationKind"
          [required]="true"
        />
        <ui-text-field
          [value]="name()"
          (valueChange)="name.set($event)"
          label="organization.integrationName"
          placeholder="organization.integrationNamePlaceholder"
          [required]="true"
          [error]="error()"
        />
        @if (kind() === 'webhook') {
          <ui-select-field
            [value]="format()"
            (valueChange)="format.set($any($event))"
            [options]="formatOptions()"
            label="organization.integrationFormat"
            hint="organization.integrationFormatHint"
          />
          <ui-text-field
            [value]="url()"
            (valueChange)="url.set($event)"
            label="organization.integrationUrl"
            [placeholder]="urlPlaceholder()"
            [required]="true"
          />
          <ui-text-field
            [value]="secret()"
            (valueChange)="secret.set($event)"
            label="organization.integrationSecret"
            placeholder="organization.integrationSecretPlaceholder"
          />
        } @else if (kind() === 'email') {
          <ui-text-field
            [value]="to()"
            (valueChange)="to.set($event)"
            label="organization.integrationTo"
            type="email"
            placeholder="organization.integrationToPlaceholder"
            [required]="true"
          />
        } @else {
          <ui-text-field
            [value]="repos()"
            (valueChange)="repos.set($event)"
            label="organization.integrationRepos"
            placeholder="organization.integrationReposPlaceholder"
            hint="organization.integrationReposHint"
          />
          <ui-text-field
            [value]="projects()"
            (valueChange)="projects.set($event)"
            label="organization.integrationProjects"
            placeholder="organization.integrationProjectsPlaceholder"
            hint="organization.integrationProjectsHint"
          />
          <ui-text-field
            [value]="issueRepo()"
            (valueChange)="issueRepo.set($event)"
            label="organization.integrationIssueRepo"
            placeholder="organization.integrationReposPlaceholder"
            hint="organization.integrationIssueRepoHint"
          />

          <div class="flex flex-col gap-2">
            <span class="text-xs text-ink-2">{{ t('organization.integrationGithubDoes') }}</span>
            <div class="flex flex-wrap gap-1.5">
              @for (option of githubSwitches; track option) {
                <button
                  type="button"
                  class="rounded-full border px-2.5 py-1 text-xs"
                  [class.border-line]="!switches().includes(option)"
                  [class.text-ink-3]="!switches().includes(option)"
                  [class.border-accent]="switches().includes(option)"
                  [class.text-ink]="switches().includes(option)"
                  (click)="toggleSwitch(option)"
                >
                  {{ t('organization.github.' + option) }}
                </button>
              }
            </div>
          </div>

          @for (field of githubStatuses; track field) {
            <ui-select-field
              [value]="statusFor(field)"
              (valueChange)="setStatus(field, $event)"
              [options]="statusOptions()"
              [label]="'organization.github.' + field"
            />
          }

          <ui-select-field
            [value]="closingStatus()"
            (valueChange)="closingStatus.set($event)"
            [options]="statusOptions()"
            label="organization.github.issueClosingStatus"
            hint="organization.github.issueClosingStatusHint"
          />
        }

        @if (kind() !== 'github') {
          <div class="flex flex-col gap-2">
            <span class="text-xs text-ink-2">{{ t('organization.integrationEvents') }}</span>
            <div class="flex flex-wrap gap-1.5">
              @for (event of events; track event) {
                <button
                  type="button"
                  class="rounded-full border px-2.5 py-1 text-xs"
                  [class.border-line]="!selectedEvents().includes(event)"
                  [class.text-ink-3]="!selectedEvents().includes(event)"
                  [class.border-accent]="selectedEvents().includes(event)"
                  [class.text-ink]="selectedEvents().includes(event)"
                  (click)="toggleEvent(event)"
                >
                  {{ t('organization.event.' + event) }}
                </button>
              }
            </div>
            <span class="text-xs text-ink-3">{{ t('organization.integrationEventsHint') }}</span>
          </div>
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
          (click)="submit()"
        >
          <ui-icon name="plus" [size]="15" />
          {{ t('organization.newIntegration') }}
        </button>
      </div>
    </ui-dialog>
  `,
})
export class IntegrationDialog {
  private readonly i18n = inject(I18nService);
  protected readonly t = this.i18n.t;

  readonly open = input(false);
  readonly saved = output<IntegrationDraft>();
  readonly cancelled = output<void>();

  private readonly workspace = inject(WorkspaceStore);

  protected readonly events = EVENTS;
  protected readonly githubSwitches = GITHUB_SWITCHES;
  protected readonly githubStatuses = GITHUB_STATUSES;
  protected readonly kind = signal<IntegrationKind>('webhook');
  protected readonly format = signal<WebhookFormat>('generic');
  protected readonly name = signal('');
  protected readonly url = signal('');
  protected readonly secret = signal('');
  protected readonly to = signal('');
  protected readonly repos = signal('');
  protected readonly projects = signal('');
  protected readonly issueRepo = signal('');
  protected readonly closingStatus = signal('');
  protected readonly statuses = signal<Record<string, string>>({});
  protected readonly switches = signal<string[]>([...GITHUB_SWITCHES_ON]);
  protected readonly selectedEvents = signal<string[]>([...EVENTS]);
  protected readonly error = signal('');

  constructor() {
    effect(() => {
      if (!this.open()) return;
      untracked(() => {
        this.kind.set('webhook');
        this.format.set('generic');
        this.name.set('');
        this.url.set('');
        this.secret.set('');
        this.to.set('');
        this.repos.set('');
        this.projects.set('');
        this.issueRepo.set('');
        this.closingStatus.set('');
        this.statuses.set({});
        this.switches.set([...GITHUB_SWITCHES_ON]);
        this.selectedEvents.set([...EVENTS]);
        this.error.set('');
      });
    });
  }

  protected readonly kindOptions = computed<SelectOption[]>(() =>
    KINDS.map((kind) => ({ value: kind, label: this.t('organization.integrationKind.' + kind) })),
  );

  protected readonly formatOptions = computed<SelectOption[]>(() =>
    FORMATS.map((format) => ({
      value: format,
      label: this.t('organization.integrationFormat.' + format),
    })),
  );

  protected readonly urlPlaceholder = computed(() =>
    this.format() === 'generic'
      ? 'organization.integrationUrlPlaceholder'
      : 'organization.integrationUrlPlaceholder.' + this.format(),
  );

  protected readonly statusOptions = computed<SelectOption[]>(() => [
    { value: '', label: this.t('organization.github.statusUnchanged') },
    ...this.workspace.statuses().map((status) => ({ value: status.code, label: status.label })),
  ]);

  protected statusFor(field: string): string {
    return this.statuses()[field] ?? '';
  }

  protected setStatus(field: string, value: string): void {
    this.statuses.update((current) => ({ ...current, [field]: value }));
  }

  protected toggleSwitch(option: string): void {
    this.switches.update((current) =>
      current.includes(option) ? current.filter((item) => item !== option) : [...current, option],
    );
  }

  protected toggleEvent(event: string): void {
    this.selectedEvents.update((current) =>
      current.includes(event) ? current.filter((item) => item !== event) : [...current, event],
    );
  }

  protected submit(): void {
    const name = this.name().trim();
    if (!name) {
      this.error.set(this.t('organization.integrationNameRequired'));
      return;
    }

    const config: Record<string, unknown> =
      this.kind() === 'github' ? {} : { events: this.selectedEvents() };

    if (this.kind() === 'webhook') {
      const url = this.url().trim();
      if (!url.startsWith('http')) {
        this.error.set(this.t('organization.integrationUrlRequired'));
        return;
      }
      config['url'] = url;
      config['format'] = this.format();
      if (this.secret().trim()) config['secret'] = this.secret().trim();
    } else if (this.kind() === 'email') {
      const to = this.to().trim();
      if (!to.includes('@')) {
        this.error.set(this.t('organization.integrationToRequired'));
        return;
      }
      config['to'] = to;
    } else {
      const repos = this.repos()
        .split(',')
        .map((repo) => repo.trim())
        .filter(Boolean);
      const issueRepo = this.issueRepo().trim();

      if (![...repos, issueRepo].filter(Boolean).every((repo) => REPO.test(repo))) {
        this.error.set(this.t('organization.integrationRepoInvalid'));
        return;
      }

      const projects = this.projects()
        .split(',')
        .map((project) => project.trim())
        .filter(Boolean);

      if (!projects.every((project) => PROJECT.test(project))) {
        this.error.set(this.t('organization.integrationProjectInvalid'));
        return;
      }

      config['repos'] = repos;
      config['projects'] = projects;
      if (issueRepo) config['issueRepo'] = issueRepo;
      if (this.closingStatus()) config['issueClosingStatuses'] = [this.closingStatus()];

      for (const field of GITHUB_STATUSES) {
        const status = this.statusFor(field);
        if (status) config[field] = status;
      }
      for (const option of GITHUB_SWITCHES) {
        config[option] = this.switches().includes(option);
      }
    }

    this.saved.emit({ kind: this.kind(), name, config });
  }
}

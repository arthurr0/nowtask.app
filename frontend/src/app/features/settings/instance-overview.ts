import { ChangeDetectionStrategy, Component, OnInit, computed, inject } from '@angular/core';
import { I18nService } from '../../core/i18n/i18n.service';
import { InstanceStore } from '../../data/instance.store';
import { Icon } from '../../ui/icon';

interface Tile {
  label: string;
  value: number;
  note: string;
}

interface HealthRow {
  label: string;
  state: string;
  good: boolean;
}

@Component({
  selector: 'app-instance-overview',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  template: `
    @if (store.overview(); as overview) {
      <div class="flex flex-col gap-6">
        <div class="grid grid-cols-2 gap-3 lg:grid-cols-3">
          @for (tile of tiles(); track tile.label) {
            <div class="flex flex-col gap-1 rounded-panel border border-line bg-surface p-4">
              <span class="kap">{{ t(tile.label) }}</span>
              <span class="font-mono text-2xl font-semibold tracking-[-0.02em]">{{
                tile.value
              }}</span>
              <span class="min-h-4 text-[11px] text-ink-3">{{ tile.note }}</span>
            </div>
          }
        </div>

        <div class="grid gap-3 lg:grid-cols-3">
          <div class="flex flex-col gap-2 rounded-panel border border-line bg-surface p-4">
            <span class="kap">{{ t('instance.overview.signupMode') }}</span>
            <span class="text-[15px] font-medium">{{
              t('instance.signup.' + overview.signupMode.effective)
            }}</span>
            <span class="text-xs text-ink-3">{{ sourceLabel() }}</span>
          </div>

          <div class="flex flex-col gap-2 rounded-panel border border-line bg-surface p-4">
            <span class="kap">{{ t('instance.overview.health') }}</span>
            @for (row of health(); track row.label) {
              <div class="flex items-center gap-2 text-[13px]">
                <span
                  class="h-2 w-2 flex-none rounded-full"
                  [class.bg-done]="row.good"
                  [class.bg-warn]="!row.good"
                ></span>
                <span class="flex-1">{{ t(row.label) }}</span>
                <span class="text-xs text-ink-2">{{ t(row.state) }}</span>
              </div>
            }
          </div>

          <div class="flex flex-col gap-2 rounded-panel border border-line bg-surface p-4">
            <span class="kap">{{ t('instance.overview.build') }}</span>
            <div class="flex items-center gap-2 text-[13px]">
              <span class="flex-1">{{ t('instance.overview.version') }}</span>
              <span class="font-mono text-xs text-ink-2">{{ overview.version }}</span>
            </div>
            <div class="flex items-center gap-2 text-[13px]">
              <span class="flex-1">{{ t('instance.overview.schema') }}</span>
              <span class="font-mono text-xs text-ink-2">{{ overview.schemaVersion }}</span>
            </div>
          </div>
        </div>
      </div>
    } @else if (store.error()) {
      <div class="flex flex-col items-start gap-3 rounded-panel border border-line p-4">
        <p class="flex items-center gap-2 text-[13px] text-ink-2">
          <ui-icon name="alert" [size]="15" />
          {{ t('instance.loadFailed') }}
        </p>
        <button
          type="button"
          class="flex h-8 items-center rounded-field border border-line bg-surface-2 px-2.5 text-xs text-ink-2"
          (click)="load()"
        >
          {{ t('instance.retry') }}
        </button>
      </div>
    }
  `,
})
export class InstanceOverview implements OnInit {
  protected readonly store = inject(InstanceStore);
  protected readonly t = inject(I18nService).t;

  protected readonly tiles = computed<Tile[]>(() => {
    const overview = this.store.overview();
    if (!overview) return [];
    const { counts } = overview;
    return [
      {
        label: 'instance.overview.organizations',
        value: counts.organizations,
        note: counts.organizationsSuspended
          ? this.t('instance.overview.suspended', { count: counts.organizationsSuspended })
          : '',
      },
      {
        label: 'instance.overview.users',
        value: counts.users,
        note: counts.usersDisabled
          ? this.t('instance.overview.disabled', { count: counts.usersDisabled })
          : '',
      },
      { label: 'instance.overview.admins', value: counts.instanceAdmins, note: '' },
      { label: 'instance.overview.tasks', value: counts.tasks, note: '' },
      { label: 'instance.overview.sessions', value: counts.activeSessions, note: '' },
      { label: 'instance.overview.signups', value: overview.signupsLast30Days, note: '' },
    ];
  });

  protected readonly health = computed<HealthRow[]>(() => {
    const overview = this.store.overview();
    if (!overview) return [];
    const { health } = overview;
    return [
      {
        label: 'instance.overview.database',
        state: `instance.health.${health.database}`,
        good: health.database === 'up',
      },
      {
        label: 'instance.overview.mail',
        state: `instance.health.${health.mail}`,
        good: health.mail === 'configured',
      },
      {
        label: 'instance.overview.github',
        state: `instance.health.${health.github}`,
        good: health.github === 'configured',
      },
    ];
  });

  protected readonly sourceLabel = computed(() =>
    this.store.overview()?.signupMode.source === 'settings'
      ? this.t('instance.overview.sourceSettings')
      : this.t('instance.overview.sourceEnv'),
  );

  ngOnInit(): void {
    void this.load();
  }

  protected load(): Promise<void> {
    return this.store.loadOverview();
  }
}

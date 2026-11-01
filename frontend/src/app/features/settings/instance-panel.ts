import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { I18nService } from '../../core/i18n/i18n.service';
import { InstanceAudit } from './instance-audit';
import { InstanceOrganizations } from './instance-organizations';
import { InstanceOverview } from './instance-overview';
import { InstanceSettings } from './instance-settings';
import { InstanceUsers } from './instance-users';

export type InstanceTab = 'overview' | 'organizations' | 'users' | 'settings' | 'audit';

@Component({
  selector: 'app-instance-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    InstanceOverview,
    InstanceOrganizations,
    InstanceUsers,
    InstanceSettings,
    InstanceAudit,
  ],
  template: `
    <section class="flex min-w-0 flex-col gap-6">
      <header class="flex flex-col gap-1">
        <h1 class="text-[17px] font-semibold tracking-[-0.01em]">{{ t('instance.title') }}</h1>
        <p class="max-w-[720px] text-[13px] text-ink-2">{{ t('instance.lead') }}</p>
      </header>

      <div
        role="tablist"
        class="flex gap-1 overflow-x-auto border-b border-line"
        [attr.aria-label]="t('instance.tabs')"
      >
        @for (item of tabs; track item.id) {
          <button
            type="button"
            role="tab"
            class="-mb-px flex h-10 flex-none items-center border-b-2 px-3 text-[13px]"
            [class.border-ink]="tab() === item.id"
            [class.font-medium]="tab() === item.id"
            [class.border-transparent]="tab() !== item.id"
            [class.text-ink-2]="tab() !== item.id"
            [attr.aria-selected]="tab() === item.id"
            (click)="tab.set(item.id)"
          >
            {{ t(item.label) }}
          </button>
        }
      </div>

      @switch (tab()) {
        @case ('overview') {
          <app-instance-overview />
        }
        @case ('organizations') {
          <app-instance-organizations />
        }
        @case ('users') {
          <app-instance-users />
        }
        @case ('settings') {
          <app-instance-settings />
        }
        @case ('audit') {
          <app-instance-audit />
        }
      }
    </section>
  `,
})
export class InstancePanel {
  protected readonly t = inject(I18nService).t;

  protected readonly tab = signal<InstanceTab>('overview');

  protected readonly tabs: readonly { id: InstanceTab; label: string }[] = [
    { id: 'overview', label: 'instance.tab.overview' },
    { id: 'organizations', label: 'instance.tab.organizations' },
    { id: 'users', label: 'instance.tab.users' },
    { id: 'settings', label: 'instance.tab.settings' },
    { id: 'audit', label: 'instance.tab.audit' },
  ];
}

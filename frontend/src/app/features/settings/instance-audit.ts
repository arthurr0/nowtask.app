import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import type { InstanceAuditDto } from '../../core/api-types';
import { dateTime } from '../../core/format';
import { I18nService } from '../../core/i18n/i18n.service';
import { INSTANCE_PAGE_SIZE, InstanceStore } from '../../data/instance.store';
import { Icon } from '../../ui/icon';
import { InstancePager } from './instance-shared';

@Component({
  selector: 'app-instance-audit',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, InstancePager],
  template: `
    <section class="flex min-w-0 flex-col gap-3">
      <div class="flex flex-wrap items-center gap-2.5">
        <h2 class="kap">{{ t('instance.tab.audit') }}</h2>
        <span class="h-px flex-1 bg-line"></span>
        <span class="font-mono text-[11px] text-ink-3">{{
          t('instance.audit.total', { count: store.audit()?.total ?? 0 })
        }}</span>
        <button
          type="button"
          class="flex h-8 items-center gap-1.5 rounded-field border border-line bg-surface-2 px-2.5 text-xs text-ink-2"
          (click)="load(page())"
        >
          <ui-icon name="log" [size]="14" />
          {{ t('organization.refresh') }}
        </button>
      </div>

      @if (store.error() && !store.audit()) {
        <div class="flex flex-col items-start gap-3 rounded-panel border border-line p-4">
          <p class="flex items-center gap-2 text-[13px] text-ink-2">
            <ui-icon name="alert" [size]="15" />
            {{ t('instance.loadFailed') }}
          </p>
          <button
            type="button"
            class="flex h-8 items-center rounded-field border border-line bg-surface-2 px-2.5 text-xs text-ink-2"
            (click)="load(page())"
          >
            {{ t('instance.retry') }}
          </button>
        </div>
      } @else {
        <div class="overflow-x-auto scroll-thin">
          <div class="min-w-[760px]">
            <div class="kap flex h-9 items-center gap-4 border-b border-line">
              <span class="w-[132px]">{{ t('task.when') }}</span>
              <span class="w-[150px]">{{ t('task.author') }}</span>
              <span class="w-[220px]">{{ t('organization.action') }}</span>
              <span class="flex-1">{{ t('instance.audit.target') }}</span>
            </div>
            @for (entry of store.audit()?.items ?? []; track entry.id) {
              <div class="flex min-h-10 items-center gap-4 border-b border-line py-1.5 text-xs">
                <span class="w-[132px] flex-none font-mono text-ink-3">{{
                  formatDateTime(entry.createdAt)
                }}</span>
                <span class="w-[150px] flex-none truncate text-ink-2">{{
                  entry.actor?.name ?? t('instance.audit.system')
                }}</span>
                <span class="w-[220px] flex-none truncate text-ink-2">{{
                  actionLabel(entry)
                }}</span>
                <span class="flex min-w-0 flex-1 flex-col">
                  <span class="truncate text-ink-2">{{ targetLabel(entry) }}</span>
                  @if (targetKind(entry)) {
                    <span class="truncate text-[10px] text-ink-3">{{ targetKind(entry) }}</span>
                  }
                </span>
              </div>
            } @empty {
              @if (!store.loading()) {
                <p class="py-8 text-center text-xs text-ink-3">{{ t('instance.audit.empty') }}</p>
              }
            }
          </div>
        </div>

        <app-instance-pager
          [page]="page()"
          [total]="store.audit()?.total ?? 0"
          [size]="pageSize"
          (pageChange)="load($event)"
        />
      }
    </section>
  `,
})
export class InstanceAudit implements OnInit {
  protected readonly store = inject(InstanceStore);
  private readonly i18n = inject(I18nService);
  protected readonly t = this.i18n.t;

  protected readonly pageSize = INSTANCE_PAGE_SIZE;
  protected readonly formatDateTime = dateTime;
  protected readonly page = signal(0);

  ngOnInit(): void {
    void this.load(0);
  }

  protected load(page: number): Promise<void> {
    this.page.set(page);
    return this.store.loadAudit(page);
  }

  protected actionLabel(entry: InstanceAuditDto): string {
    return this.i18n.label(`instance.action.${entry.action}`, entry.action);
  }

  protected targetLabel(entry: InstanceAuditDto): string {
    return entry.targetLabel || entry.targetId || '';
  }

  protected targetKind(entry: InstanceAuditDto): string {
    if (!entry.targetType) return '';
    return this.i18n.label(`instance.target.${entry.targetType}`, entry.targetType);
  }
}

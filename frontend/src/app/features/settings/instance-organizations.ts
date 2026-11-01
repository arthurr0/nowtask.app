import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import type { InstanceOrganizationDto, InstanceOrganizationState } from '../../core/api-types';
import { fullDate } from '../../core/format';
import { I18nService } from '../../core/i18n/i18n.service';
import { INSTANCE_PAGE_SIZE, InstanceStore } from '../../data/instance.store';
import { ConfirmService } from '../../ui/confirm.service';
import { Icon } from '../../ui/icon';
import { Menu, type MenuItem } from '../../ui/menu';
import { ToastService } from '../../ui/toast.service';
import { DeleteOrganizationDialog } from './instance-dialogs';
import { InstancePager, InstanceSegmented, type SegmentOption } from './instance-shared';
import { errorStatus, instanceErrorMessage } from './instance-utils';

const SEARCH_DELAY = 300;

@Component({
  selector: 'app-instance-organizations',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, Menu, InstanceSegmented, InstancePager, DeleteOrganizationDialog],
  templateUrl: './instance-organizations.html',
})
export class InstanceOrganizations implements OnInit {
  protected readonly store = inject(InstanceStore);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly t = inject(I18nService).t;

  protected readonly pageSize = INSTANCE_PAGE_SIZE;
  protected readonly formatDate = fullDate;

  protected readonly search = signal(this.store.organizationQuery().q);
  protected readonly deleteTarget = signal<InstanceOrganizationDto | null>(null);
  protected readonly deleteError = signal('');
  protected readonly deleteBusy = signal(false);

  protected readonly stateOptions: readonly SegmentOption[] = [
    { value: '', label: 'common.all' },
    { value: 'active', label: 'instance.state.active' },
    { value: 'suspended', label: 'instance.state.suspended' },
  ];

  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    this.destroyRef.onDestroy(() => this.clearTimer());
  }

  ngOnInit(): void {
    void this.store.loadOrganizations();
  }

  protected onSearch(value: string): void {
    this.search.set(value);
    this.clearTimer();
    this.searchTimer = setTimeout(() => {
      this.searchTimer = null;
      void this.store.loadOrganizations({ q: value.trim(), page: 0 });
    }, SEARCH_DELAY);
  }

  protected clearSearch(): void {
    this.onSearch('');
  }

  protected setState(value: string): void {
    void this.store.loadOrganizations({ state: value as InstanceOrganizationState | '', page: 0 });
  }

  protected setPage(page: number): void {
    void this.store.loadOrganizations({ page });
  }

  protected reload(): void {
    void this.store.loadOrganizations();
  }

  protected menu(organization: InstanceOrganizationDto): MenuItem[] {
    return [
      organization.state === 'suspended'
        ? { id: 'restore', label: 'instance.orgs.restore', icon: 'play' }
        : { id: 'suspend', label: 'instance.orgs.suspend', icon: 'lock' },
      {
        id: 'delete',
        label: 'instance.orgs.delete',
        icon: 'trash',
        danger: true,
        separatorBefore: true,
      },
    ];
  }

  protected async onMenu(item: MenuItem, organization: InstanceOrganizationDto): Promise<void> {
    if (item.id === 'delete') {
      this.deleteError.set('');
      this.deleteTarget.set(organization);
      return;
    }
    if (item.id === 'suspend') {
      const confirmed = await this.confirm.ask({
        title: 'instance.orgs.suspendTitle',
        message: this.t('instance.orgs.suspendHint', { name: organization.name }),
        confirmLabel: 'instance.orgs.suspend',
        destructive: true,
      });
      if (!confirmed) return;
      await this.run(
        () => this.store.suspendOrganization(organization.id),
        'instance.orgs.suspended',
      );
      return;
    }
    await this.run(() => this.store.restoreOrganization(organization.id), 'instance.orgs.restored');
  }

  protected async deleteConfirmed(slug: string): Promise<void> {
    const target = this.deleteTarget();
    if (!target || this.deleteBusy()) return;
    this.deleteBusy.set(true);
    this.deleteError.set('');
    try {
      const reloaded = await this.store.deleteOrganization(target.id, slug);
      this.deleteTarget.set(null);
      this.toast.success(this.t('instance.orgs.deleted'));
      if (!reloaded) this.toast.error(this.t('instance.loadFailed'));
    } catch (error) {
      this.deleteError.set(
        errorStatus(error) === 422
          ? this.t('instance.error.slugMismatch')
          : instanceErrorMessage(error, this.t),
      );
    } finally {
      this.deleteBusy.set(false);
    }
  }

  private async run(work: () => Promise<boolean | void>, doneKey: string): Promise<void> {
    try {
      const reloaded = await work();
      this.toast.success(this.t(doneKey));
      if (reloaded === false) this.toast.error(this.t('instance.loadFailed'));
    } catch (error) {
      this.toast.error(instanceErrorMessage(error, this.t));
    }
  }

  private clearTimer(): void {
    if (this.searchTimer !== null) {
      clearTimeout(this.searchTimer);
      this.searchTimer = null;
    }
  }
}

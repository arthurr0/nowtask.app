import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import type { InstanceUserDto, InstanceUserState } from '../../core/api-types';
import { AuthService } from '../../core/auth.service';
import { dateTime } from '../../core/format';
import { I18nService } from '../../core/i18n/i18n.service';
import { INSTANCE_PAGE_SIZE, InstanceStore } from '../../data/instance.store';
import { ConfirmService } from '../../ui/confirm.service';
import { Icon } from '../../ui/icon';
import { Menu, type MenuItem } from '../../ui/menu';
import { ToastService } from '../../ui/toast.service';
import { InstancePager, InstanceSegmented, type SegmentOption } from './instance-shared';
import { instanceErrorMessage } from './instance-utils';

const SEARCH_DELAY = 300;

@Component({
  selector: 'app-instance-users',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, Menu, InstanceSegmented, InstancePager],
  templateUrl: './instance-users.html',
})
export class InstanceUsers implements OnInit {
  protected readonly store = inject(InstanceStore);
  private readonly auth = inject(AuthService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly t = inject(I18nService).t;

  protected readonly pageSize = INSTANCE_PAGE_SIZE;
  protected readonly formatDateTime = dateTime;

  protected readonly search = signal(this.store.userQuery().q);

  protected readonly stateOptions: readonly SegmentOption[] = [
    { value: '', label: 'common.all' },
    { value: 'active', label: 'instance.userState.active' },
    { value: 'disabled', label: 'instance.userState.disabled' },
  ];

  protected readonly adminOptions: readonly SegmentOption[] = [
    { value: '', label: 'common.all' },
    { value: 'true', label: 'instance.users.adminOnly' },
    { value: 'false', label: 'instance.users.regularOnly' },
  ];

  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    this.destroyRef.onDestroy(() => this.clearTimer());
  }

  ngOnInit(): void {
    void this.store.loadUsers();
  }

  protected isSelf(user: InstanceUserDto): boolean {
    return user.id === this.auth.user()?.id;
  }

  protected onSearch(value: string): void {
    this.search.set(value);
    this.clearTimer();
    this.searchTimer = setTimeout(() => {
      this.searchTimer = null;
      void this.store.loadUsers({ q: value.trim(), page: 0 });
    }, SEARCH_DELAY);
  }

  protected clearSearch(): void {
    this.onSearch('');
  }

  protected setState(value: string): void {
    void this.store.loadUsers({ state: value as InstanceUserState | '', page: 0 });
  }

  protected setAdmin(value: string): void {
    void this.store.loadUsers({ admin: value as 'true' | 'false' | '', page: 0 });
  }

  protected setPage(page: number): void {
    void this.store.loadUsers({ page });
  }

  protected reload(): void {
    void this.store.loadUsers();
  }

  protected menu(user: InstanceUserDto): MenuItem[] {
    if (this.isSelf(user)) return [];
    const items: MenuItem[] = [
      user.state === 'disabled'
        ? { id: 'enable', label: 'instance.users.enable', icon: 'play' }
        : { id: 'disable', label: 'instance.users.disable', icon: 'lock' },
      { id: 'sessions', label: 'instance.users.revokeSessions', icon: 'x' },
      user.instanceAdmin
        ? {
            id: 'revokeAdmin',
            label: 'instance.users.revokeAdmin',
            icon: 'shield',
            danger: true,
            separatorBefore: true,
          }
        : {
            id: 'grantAdmin',
            label: 'instance.users.grantAdmin',
            icon: 'shield',
            separatorBefore: true,
          },
    ];
    return items;
  }

  protected async onMenu(item: MenuItem, user: InstanceUserDto): Promise<void> {
    switch (item.id) {
      case 'enable':
        await this.run(() => this.store.enableUser(user.id), 'instance.users.enabled');
        return;
      case 'disable':
        if (
          await this.ask('disableTitle', 'disableHint', 'instance.users.disable', user.name, true)
        ) {
          await this.run(() => this.store.disableUser(user.id), 'instance.users.disabled');
        }
        return;
      case 'sessions':
        if (
          await this.ask(
            'revokeSessionsTitle',
            'revokeSessionsHint',
            'instance.users.revokeSessions',
            user.name,
            true,
          )
        ) {
          await this.run(
            () => this.store.revokeUserSessions(user.id),
            'instance.users.sessionsRevoked',
          );
        }
        return;
      case 'grantAdmin':
        if (
          await this.ask(
            'grantAdminTitle',
            'grantAdminHint',
            'instance.users.grantAdmin',
            user.name,
            false,
          )
        ) {
          await this.run(
            () => this.store.setInstanceAdmin(user.id, true),
            'instance.users.adminGranted',
          );
        }
        return;
      case 'revokeAdmin':
        if (
          await this.ask(
            'revokeAdminTitle',
            'revokeAdminHint',
            'instance.users.revokeAdmin',
            user.name,
            true,
          )
        ) {
          await this.run(
            () => this.store.setInstanceAdmin(user.id, false),
            'instance.users.adminRevoked',
          );
        }
        return;
    }
  }

  private ask(
    title: string,
    hint: string,
    confirmLabel: string,
    name: string,
    destructive: boolean,
  ): Promise<boolean> {
    return this.confirm.ask({
      title: `instance.users.${title}`,
      message: this.t(`instance.users.${hint}`, { name }),
      confirmLabel,
      destructive,
    });
  }

  private async run(work: () => Promise<void>, doneKey: string): Promise<void> {
    try {
      await work();
      this.toast.success(this.t(doneKey));
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

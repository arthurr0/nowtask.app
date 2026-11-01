import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import type {
  InstanceAuditDto,
  InstanceOrganizationDto,
  InstanceOrganizationQuery,
  InstanceOverviewDto,
  InstancePageDto,
  InstanceSettingsDto,
  InstanceSettingsPatch,
  InstanceUserDto,
  InstanceUserQuery,
} from '../core/api-types';
import { LoadableStore } from './feature.stores';
import { WorkspaceStore, describe } from './workspace.store';

export const INSTANCE_PAGE_SIZE = 25;

const BASE = '/api/instance';

const EMPTY_ORGANIZATION_QUERY: InstanceOrganizationQuery = {
  q: '',
  state: '',
  page: 0,
  size: INSTANCE_PAGE_SIZE,
};

const EMPTY_USER_QUERY: InstanceUserQuery = {
  q: '',
  state: '',
  admin: '',
  page: 0,
  size: INSTANCE_PAGE_SIZE,
};

export function instanceQuery(values: object): string {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(values)) {
    if (value !== '' && value !== null && value !== undefined) params.set(key, String(value));
  }
  const text = params.toString();
  return text ? `?${text}` : '';
}

type ListKey = 'organizations' | 'users' | 'audit';

@Injectable({ providedIn: 'root' })
export class InstanceStore extends LoadableStore {
  private readonly workspace = inject(WorkspaceStore);

  private readonly sequences: Record<ListKey, number> = { organizations: 0, users: 0, audit: 0 };
  private readonly pending = new Set<ListKey>();

  private readonly overviewSignal = signal<InstanceOverviewDto | null>(null);
  private readonly organizationsSignal = signal<InstancePageDto<InstanceOrganizationDto> | null>(
    null,
  );
  private readonly organizationQuerySignal =
    signal<InstanceOrganizationQuery>(EMPTY_ORGANIZATION_QUERY);
  private readonly usersSignal = signal<InstancePageDto<InstanceUserDto> | null>(null);
  private readonly userQuerySignal = signal<InstanceUserQuery>(EMPTY_USER_QUERY);
  private readonly settingsSignal = signal<InstanceSettingsDto | null>(null);
  private readonly auditSignal = signal<InstancePageDto<InstanceAuditDto> | null>(null);

  readonly overview = this.overviewSignal.asReadonly();
  readonly organizations = this.organizationsSignal.asReadonly();
  readonly organizationQuery = this.organizationQuerySignal.asReadonly();
  readonly users = this.usersSignal.asReadonly();
  readonly userQuery = this.userQuerySignal.asReadonly();
  readonly settings = this.settingsSignal.asReadonly();
  readonly audit = this.auditSignal.asReadonly();

  async loadOverview(): Promise<void> {
    await this.guard(async () => {
      this.overviewSignal.set(
        await firstValueFrom(this.http.get<InstanceOverviewDto>(`${BASE}/overview`)),
      );
    });
  }

  async loadOrganizations(patch: Partial<InstanceOrganizationQuery> = {}): Promise<boolean> {
    const query = { ...this.organizationQuerySignal(), ...patch };
    this.organizationQuerySignal.set(query);
    return this.loadList(
      'organizations',
      () =>
        firstValueFrom(
          this.http.get<InstancePageDto<InstanceOrganizationDto>>(
            `${BASE}/organizations${instanceQuery(query)}`,
          ),
        ),
      (page) => this.organizationsSignal.set(page),
    );
  }

  async suspendOrganization(id: string): Promise<boolean> {
    await firstValueFrom(this.http.post(`${BASE}/organizations/${id}/suspend`, {}));
    return this.refreshOrganizations();
  }

  async restoreOrganization(id: string): Promise<boolean> {
    await firstValueFrom(this.http.post(`${BASE}/organizations/${id}/restore`, {}));
    return this.refreshOrganizations();
  }

  async deleteOrganization(id: string, confirmSlug: string): Promise<boolean> {
    await firstValueFrom(
      this.http.delete(`${BASE}/organizations/${id}`, { body: { confirmSlug } }),
    );
    return this.refreshOrganizations();
  }

  async loadUsers(patch: Partial<InstanceUserQuery> = {}): Promise<boolean> {
    const query = { ...this.userQuerySignal(), ...patch };
    this.userQuerySignal.set(query);
    return this.loadList(
      'users',
      () =>
        firstValueFrom(
          this.http.get<InstancePageDto<InstanceUserDto>>(`${BASE}/users${instanceQuery(query)}`),
        ),
      (page) => this.usersSignal.set(page),
    );
  }

  async disableUser(id: string): Promise<boolean> {
    await firstValueFrom(this.http.post(`${BASE}/users/${id}/disable`, {}));
    return this.refreshUsers();
  }

  async enableUser(id: string): Promise<boolean> {
    await firstValueFrom(this.http.post(`${BASE}/users/${id}/enable`, {}));
    return this.refreshUsers();
  }

  async revokeUserSessions(id: string): Promise<void> {
    await firstValueFrom(this.http.post(`${BASE}/users/${id}/sessions/revoke`, {}));
  }

  async setInstanceAdmin(id: string, instanceAdmin: boolean): Promise<void> {
    const updated = await firstValueFrom(
      this.http.patch<InstanceUserDto>(`${BASE}/users/${id}`, { instanceAdmin }),
    );
    this.usersSignal.update((page) =>
      page
        ? { ...page, items: page.items.map((item) => (item.id === updated.id ? updated : item)) }
        : page,
    );
  }

  async loadSettings(): Promise<void> {
    await this.guard(async () => {
      this.settingsSignal.set(
        await firstValueFrom(this.http.get<InstanceSettingsDto>(`${BASE}/settings`)),
      );
    });
  }

  async saveSettings(patch: InstanceSettingsPatch): Promise<InstanceSettingsDto> {
    const saved = await firstValueFrom(
      this.http.patch<InstanceSettingsDto>(`${BASE}/settings`, patch),
    );
    this.settingsSignal.set(saved);
    this.workspace.setAnnouncement(saved.announcement);
    return saved;
  }

  async sendTestMail(): Promise<void> {
    await firstValueFrom(this.http.post(`${BASE}/mail/test`, {}));
  }

  async loadAudit(page = 0): Promise<boolean> {
    return this.loadList(
      'audit',
      () =>
        firstValueFrom(
          this.http.get<InstancePageDto<InstanceAuditDto>>(
            `${BASE}/audit${instanceQuery({ page, size: INSTANCE_PAGE_SIZE })}`,
          ),
        ),
      (result) => this.auditSignal.set(result),
    );
  }

  private async refreshOrganizations(): Promise<boolean> {
    const ok = await this.loadOrganizations();
    const page = this.organizationsSignal();
    const current = this.organizationQuerySignal().page;
    if (ok && page && page.items.length === 0 && current > 0) {
      return this.loadOrganizations({ page: current - 1 });
    }
    return ok;
  }

  private async refreshUsers(): Promise<boolean> {
    const ok = await this.loadUsers();
    const page = this.usersSignal();
    const current = this.userQuerySignal().page;
    if (ok && page && page.items.length === 0 && current > 0) {
      return this.loadUsers({ page: current - 1 });
    }
    return ok;
  }

  private async loadList<T>(
    key: ListKey,
    request: () => Promise<T>,
    apply: (value: T) => void,
  ): Promise<boolean> {
    const sequence = ++this.sequences[key];
    this.pending.add(key);
    this.loadingSignal.set(true);
    this.errorSignal.set(null);
    try {
      const value = await request();
      if (sequence !== this.sequences[key]) return true;
      apply(value);
      return true;
    } catch (error) {
      if (sequence !== this.sequences[key]) return true;
      this.errorSignal.set(describe(error));
      return false;
    } finally {
      if (sequence === this.sequences[key]) {
        this.pending.delete(key);
        this.loadingSignal.set(this.pending.size > 0);
      }
    }
  }

  clear(): void {
    for (const key of Object.keys(this.sequences) as ListKey[]) {
      this.sequences[key]++;
    }
    this.pending.clear();
    this.resetState();
    this.overviewSignal.set(null);
    this.organizationsSignal.set(null);
    this.organizationQuerySignal.set(EMPTY_ORGANIZATION_QUERY);
    this.usersSignal.set(null);
    this.userQuerySignal.set(EMPTY_USER_QUERY);
    this.settingsSignal.set(null);
    this.auditSignal.set(null);
  }
}

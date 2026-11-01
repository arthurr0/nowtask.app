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
import { WorkspaceStore } from './workspace.store';

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

@Injectable({ providedIn: 'root' })
export class InstanceStore extends LoadableStore {
  private readonly workspace = inject(WorkspaceStore);

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

  async loadOrganizations(patch: Partial<InstanceOrganizationQuery> = {}): Promise<void> {
    const query = { ...this.organizationQuerySignal(), ...patch };
    this.organizationQuerySignal.set(query);
    await this.guard(async () => {
      this.organizationsSignal.set(
        await firstValueFrom(
          this.http.get<InstancePageDto<InstanceOrganizationDto>>(
            `${BASE}/organizations${instanceQuery(query)}`,
          ),
        ),
      );
    });
  }

  async suspendOrganization(id: string): Promise<void> {
    await firstValueFrom(this.http.post(`${BASE}/organizations/${id}/suspend`, {}));
    await this.loadOrganizations();
  }

  async restoreOrganization(id: string): Promise<void> {
    await firstValueFrom(this.http.post(`${BASE}/organizations/${id}/restore`, {}));
    await this.loadOrganizations();
  }

  async deleteOrganization(id: string, confirmSlug: string): Promise<void> {
    await firstValueFrom(
      this.http.delete(`${BASE}/organizations/${id}`, { body: { confirmSlug } }),
    );
    await this.loadOrganizations();
  }

  async loadUsers(patch: Partial<InstanceUserQuery> = {}): Promise<void> {
    const query = { ...this.userQuerySignal(), ...patch };
    this.userQuerySignal.set(query);
    await this.guard(async () => {
      this.usersSignal.set(
        await firstValueFrom(
          this.http.get<InstancePageDto<InstanceUserDto>>(`${BASE}/users${instanceQuery(query)}`),
        ),
      );
    });
  }

  async disableUser(id: string): Promise<void> {
    await firstValueFrom(this.http.post(`${BASE}/users/${id}/disable`, {}));
    await this.loadUsers();
  }

  async enableUser(id: string): Promise<void> {
    await firstValueFrom(this.http.post(`${BASE}/users/${id}/enable`, {}));
    await this.loadUsers();
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

  async loadAudit(page = 0): Promise<void> {
    await this.guard(async () => {
      this.auditSignal.set(
        await firstValueFrom(
          this.http.get<InstancePageDto<InstanceAuditDto>>(
            `${BASE}/audit${instanceQuery({ page, size: INSTANCE_PAGE_SIZE })}`,
          ),
        ),
      );
    });
  }

  clear(): void {
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

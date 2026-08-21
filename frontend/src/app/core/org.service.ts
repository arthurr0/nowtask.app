import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ActiveOrgService } from './active-org';
import type { OrgMembershipDto } from './api-types';

@Injectable({ providedIn: 'root' })
export class OrgService {
  private readonly http = inject(HttpClient);
  private readonly activeOrg = inject(ActiveOrgService);

  private readonly membershipsSignal = signal<OrgMembershipDto[]>([]);
  private readonly loadedSignal = signal(false);

  readonly memberships = this.membershipsSignal.asReadonly();
  readonly loaded = this.loadedSignal.asReadonly();
  readonly hasOrganization = computed(() => this.membershipsSignal().length > 0);

  async refresh(force = false): Promise<OrgMembershipDto[]> {
    if (this.loadedSignal() && !force) {
      return this.membershipsSignal();
    }

    const memberships = await firstValueFrom(this.http.get<OrgMembershipDto[]>('/api/orgs'));
    this.membershipsSignal.set(memberships);
    this.loadedSignal.set(true);

    const active = this.activeOrg.id();
    const known = memberships.some((membership) => membership.organizationId === active);

    if (!known) {
      if (memberships.length) {
        this.activeOrg.set(memberships[0].organizationId);
      } else {
        this.activeOrg.clear();
      }
    }

    return memberships;
  }

  async slugAvailable(slug: string): Promise<boolean> {
    const result = await firstValueFrom(
      this.http.get<{ available: boolean }>('/api/orgs/slug-available', { params: { slug } }),
    );
    return result.available;
  }

  async create(name: string, slug: string, presetCode: string): Promise<OrgMembershipDto[]> {
    const created = await firstValueFrom(
      this.http.post<{ id: string }>('/api/orgs', { name, slug, presetCode }),
    );
    this.activeOrg.set(created.id);
    return this.refresh(true);
  }

  async switchTo(organizationId: string): Promise<void> {
    await firstValueFrom(this.http.post(`/api/orgs/${organizationId}/switch`, {}));
    this.activeOrg.set(organizationId);
    await this.refresh(true);
  }

  clear(): void {
    this.membershipsSignal.set([]);
    this.loadedSignal.set(false);
  }
}

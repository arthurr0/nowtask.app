import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { UserDto } from './api-types';
import {
  AdminStore,
  AgentsStore,
  MetricsStore,
  RulesStore,
  SettingsStore,
  TaskDetailStore,
  TimelineStore,
} from '../data/feature.stores';
import { WorkspaceStore } from '../data/workspace.store';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly stores = [
    inject(WorkspaceStore),
    inject(RulesStore),
    inject(MetricsStore),
    inject(TimelineStore),
    inject(AdminStore),
    inject(AgentsStore),
    inject(SettingsStore),
    inject(TaskDetailStore),
  ];

  private readonly userSignal = signal<UserDto | null>(null);
  private readonly checkedSignal = signal(false);

  readonly user = this.userSignal.asReadonly();
  readonly checked = this.checkedSignal.asReadonly();
  readonly isAuthenticated = computed(() => this.userSignal() !== null);

  async restore(): Promise<boolean> {
    if (this.checkedSignal()) {
      return this.isAuthenticated();
    }

    try {
      const user = await firstValueFrom(this.http.get<UserDto>('/api/auth/me'));
      this.userSignal.set(user);
      return true;
    } catch (error) {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401) {
        console.error('Nie udało się odczytać sesji', error);
      }
      this.userSignal.set(null);
      return false;
    } finally {
      this.checkedSignal.set(true);
    }
  }

  async login(email: string, password: string): Promise<void> {
    await this.ensureCsrf();
    const user = await firstValueFrom(
      this.http.post<UserDto>('/api/auth/login', { email, password }),
    );
    this.userSignal.set(user);
    this.checkedSignal.set(true);
  }

  async signup(name: string, email: string, password: string): Promise<void> {
    await this.ensureCsrf();
    const user = await firstValueFrom(
      this.http.post<UserDto>('/api/auth/signup', { name, email, password }),
    );
    this.userSignal.set(user);
    this.checkedSignal.set(true);
  }

  private async ensureCsrf(): Promise<void> {
    await firstValueFrom(this.http.get('/api/meta'));
  }

  async logout(): Promise<void> {
    try {
      await firstValueFrom(this.http.post('/api/auth/logout', {}));
    } finally {
      this.userSignal.set(null);
      this.checkedSignal.set(true);
      this.clearCachedData();
      void this.router.navigate(['/login']);
    }
  }

  private clearCachedData(): void {
    for (const store of this.stores) {
      store.clear();
    }
  }

  markSignedOut(): void {
    this.userSignal.set(null);
    this.checkedSignal.set(true);
    this.clearCachedData();
  }
}

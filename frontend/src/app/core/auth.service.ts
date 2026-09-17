import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { SignupResultDto, UserDto } from './api-types';
import {
  OrganizationStore,
  AgentsStore,
  MetricsStore,
  NotificationsStore,
  RulesStore,
  SettingsStore,
  TaskDetailStore,
  TimelineStore,
} from '../data/feature.stores';
import { WorkspaceStore } from '../data/workspace.store';
import { AccountService } from './account.service';
import { OnboardingService } from './onboarding.service';
import { OrgService } from './org.service';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly orgs = inject(OrgService);
  private readonly onboarding = inject(OnboardingService);

  private readonly stores = [
    inject(WorkspaceStore),
    inject(RulesStore),
    inject(MetricsStore),
    inject(TimelineStore),
    inject(OrganizationStore),
    inject(AgentsStore),
    inject(SettingsStore),
    inject(TaskDetailStore),
    inject(NotificationsStore),
    inject(AccountService),
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
        console.error('Could not read the session', error);
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
    this.orgs.clear();
    this.onboarding.clear();
  }

  async signup(name: string, email: string, password: string): Promise<SignupResultDto> {
    await this.ensureCsrf();
    const result = await firstValueFrom(
      this.http.post<SignupResultDto>('/api/auth/signup', { name, email, password }),
    );
    this.userSignal.set(result.user);
    this.checkedSignal.set(true);
    this.orgs.clear();
    this.onboarding.clear();
    return result;
  }

  async verifyEmail(token: string): Promise<UserDto> {
    await this.ensureCsrf();
    const user = await firstValueFrom(this.http.post<UserDto>('/api/auth/verify-email', { token }));
    if (this.userSignal()) {
      this.userSignal.set(user);
    }
    return user;
  }

  async confirmEmailChange(token: string): Promise<UserDto> {
    await this.ensureCsrf();
    const user = await firstValueFrom(
      this.http.post<UserDto>('/api/auth/confirm-email-change', { token }),
    );
    this.markSignedOut();
    return user;
  }

  async requestPasswordReset(email: string): Promise<void> {
    await this.ensureCsrf();
    await firstValueFrom(this.http.post('/api/auth/forgot-password', { email }));
  }

  async resetPassword(token: string, password: string): Promise<void> {
    await this.ensureCsrf();
    await firstValueFrom(this.http.post('/api/auth/reset-password', { token, password }));
  }

  async resendVerification(): Promise<void> {
    await firstValueFrom(this.http.post('/api/auth/resend-verification', {}));
  }

  setUser(user: UserDto): void {
    this.userSignal.set(user);
  }

  async reload(): Promise<UserDto | null> {
    this.checkedSignal.set(false);
    await this.restore();
    return this.userSignal();
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
    this.orgs.clear();
    this.onboarding.clear();
  }

  markSignedOut(): void {
    this.userSignal.set(null);
    this.checkedSignal.set(true);
    this.clearCachedData();
  }
}

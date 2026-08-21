import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import type { EmailChangeDto, NotificationPrefDto, SessionDto, UserDto } from './api-types';

@Injectable({ providedIn: 'root' })
export class AccountService {
  private readonly http = inject(HttpClient);

  private readonly sessionsSignal = signal<SessionDto[]>([]);
  private readonly prefsSignal = signal<NotificationPrefDto[]>([]);
  private readonly emailChangeSignal = signal<EmailChangeDto | null>(null);
  private readonly loadedSignal = signal(false);

  readonly sessions = this.sessionsSignal.asReadonly();
  readonly notificationPrefs = this.prefsSignal.asReadonly();
  readonly emailChange = this.emailChangeSignal.asReadonly();
  readonly loaded = this.loadedSignal.asReadonly();

  async load(): Promise<void> {
    const [sessions, prefs, change] = await Promise.all([
      firstValueFrom(this.http.get<SessionDto[]>('/api/account/sessions')),
      firstValueFrom(this.http.get<NotificationPrefDto[]>('/api/account/notifications')),
      firstValueFrom(this.http.get<EmailChangeDto | null>('/api/account/email-change')),
    ]);

    this.sessionsSignal.set(sessions);
    this.prefsSignal.set(prefs);
    this.emailChangeSignal.set(change ?? null);
    this.loadedSignal.set(true);
  }

  async updateProfile(body: Record<string, unknown>): Promise<UserDto> {
    return firstValueFrom(this.http.patch<UserDto>('/api/account/profile', body));
  }

  async changePassword(currentPassword: string, newPassword: string): Promise<void> {
    await firstValueFrom(this.http.post('/api/account/password', { currentPassword, newPassword }));
    await this.loadSessions();
  }

  async requestEmailChange(email: string, password: string): Promise<EmailChangeDto> {
    const change = await firstValueFrom(
      this.http.post<EmailChangeDto>('/api/account/email', { email, password }),
    );
    this.emailChangeSignal.set(change);
    return change;
  }

  async cancelEmailChange(): Promise<void> {
    await firstValueFrom(this.http.delete('/api/account/email-change'));
    this.emailChangeSignal.set(null);
  }

  async saveNotificationPrefs(prefs: NotificationPrefDto[]): Promise<void> {
    this.prefsSignal.set(
      await firstValueFrom(
        this.http.put<NotificationPrefDto[]>('/api/account/notifications', prefs),
      ),
    );
  }

  async revokeSession(id: string): Promise<void> {
    await firstValueFrom(this.http.delete(`/api/account/sessions/${id}`));
    await this.loadSessions();
  }

  async revokeOtherSessions(): Promise<number> {
    const result = await firstValueFrom(
      this.http.post<{ closed: number }>('/api/account/sessions/revoke-others', {}),
    );
    await this.loadSessions();
    return result.closed;
  }

  async leaveOrganization(): Promise<void> {
    await firstValueFrom(this.http.post('/api/account/leave', {}));
  }

  async deleteAccount(password: string): Promise<void> {
    await firstValueFrom(this.http.post('/api/account/delete', { password }));
  }

  clear(): void {
    this.sessionsSignal.set([]);
    this.prefsSignal.set([]);
    this.emailChangeSignal.set(null);
    this.loadedSignal.set(false);
  }

  private async loadSessions(): Promise<void> {
    this.sessionsSignal.set(
      await firstValueFrom(this.http.get<SessionDto[]>('/api/account/sessions')),
    );
  }
}

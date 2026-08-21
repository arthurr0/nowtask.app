import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import type { OnboardingDto, OnboardingStep } from './api-types';

@Injectable({ providedIn: 'root' })
export class OnboardingService {
  private readonly http = inject(HttpClient);

  private readonly stateSignal = signal<OnboardingDto | null>(null);
  private readonly loadedSignal = signal(false);

  readonly state = this.stateSignal.asReadonly();
  readonly loaded = this.loadedSignal.asReadonly();

  readonly wizardOpen = computed(() => {
    const state = this.stateSignal();
    return state !== null && state.flow === 'founder' && state.step !== 'done';
  });

  readonly checklistOpen = computed(() => {
    const state = this.stateSignal();
    if (!state) return false;
    return state.step === 'done' && !state.dismissed && !state.completed;
  });

  readonly doneCount = computed(
    () => this.stateSignal()?.checklist.filter((item) => item.done).length ?? 0,
  );

  adopt(state: OnboardingDto | null | undefined): void {
    this.stateSignal.set(state ?? null);
    this.loadedSignal.set(true);
  }

  async syncAfterActivity(): Promise<void> {
    const state = this.stateSignal();
    if (!state || state.completed || state.dismissed) {
      return;
    }
    if (state.checklist.every((item) => item.done)) {
      return;
    }

    await this.refresh(true);
  }

  async refresh(force = false): Promise<OnboardingDto | null> {
    if (this.loadedSignal() && !force) {
      return this.stateSignal();
    }

    const state = await firstValueFrom(
      this.http.get<OnboardingDto | null>('/api/onboarding', { observe: 'body' }),
    );

    this.stateSignal.set(state ?? null);
    this.loadedSignal.set(true);
    return this.stateSignal();
  }

  async start(): Promise<OnboardingDto> {
    const state = await firstValueFrom(this.http.post<OnboardingDto>('/api/onboarding/start', {}));
    this.stateSignal.set(state);
    this.loadedSignal.set(true);
    return state;
  }

  async setStep(step: OnboardingStep): Promise<OnboardingDto> {
    return this.patch({ step });
  }

  async dismiss(): Promise<OnboardingDto> {
    return this.patch({ dismissed: true });
  }

  async restore(): Promise<OnboardingDto> {
    return this.patch({ dismissed: false });
  }

  async markTourSeen(): Promise<OnboardingDto> {
    return this.patch({ tourSeen: true });
  }

  clear(): void {
    this.stateSignal.set(null);
    this.loadedSignal.set(false);
  }

  private async patch(body: Record<string, unknown>): Promise<OnboardingDto> {
    const state = await firstValueFrom(this.http.patch<OnboardingDto>('/api/onboarding', body));
    this.stateSignal.set(state);
    this.loadedSignal.set(true);
    return state;
  }
}

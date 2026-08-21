import { Injectable, signal } from '@angular/core';

const STORAGE_KEY = 'nowtask.activeOrganizationId';

@Injectable({ providedIn: 'root' })
export class ActiveOrgService {
  private readonly idSignal = signal<string | null>(read());

  readonly id = this.idSignal.asReadonly();

  set(organizationId: string): void {
    this.idSignal.set(organizationId);
    write(organizationId);
  }

  clear(): void {
    this.idSignal.set(null);
    write(null);
  }
}

function read(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

function write(value: string | null): void {
  try {
    if (value === null) {
      localStorage.removeItem(STORAGE_KEY);
    } else {
      localStorage.setItem(STORAGE_KEY, value);
    }
  } catch {
    return;
  }
}

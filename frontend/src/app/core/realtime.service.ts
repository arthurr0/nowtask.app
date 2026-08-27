import { Injectable, inject, signal } from '@angular/core';
import { NotificationsStore, TaskDetailStore } from '../data/feature.stores';
import { WorkspaceStore } from '../data/workspace.store';
import { ActiveOrgService } from './active-org';
import type { RealtimeMessageDto } from './api-types';

type RefreshTarget = 'tasks' | 'workspace' | 'detail' | 'notifications';

const COALESCE_MS = 250;
const BACKOFF_MS = [1000, 2000, 5000, 10000, 30000];
const QUIET_CHANGES = ['comment', 'watchers'];

@Injectable({ providedIn: 'root' })
export class RealtimeService {
  private readonly workspace = inject(WorkspaceStore);
  private readonly details = inject(TaskDetailStore);
  private readonly notifications = inject(NotificationsStore);
  private readonly activeOrg = inject(ActiveOrgService);

  private source: EventSource | null = null;
  private running = false;
  private paused = false;
  private opened = false;
  private attempt = 0;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private flushTimer: ReturnType<typeof setTimeout> | null = null;

  private readonly pending = new Set<RefreshTarget>();
  private readonly inFlight = new Set<RefreshTarget>();
  private readonly repeat = new Set<RefreshTarget>();

  private readonly connectedSignal = signal(false);

  readonly connected = this.connectedSignal.asReadonly();

  start(): void {
    if (this.running) return;
    this.running = true;
    this.paused = false;
    document.addEventListener('visibilitychange', this.onVisibility);
    window.addEventListener('online', this.onOnline);
    this.open();
  }

  stop(): void {
    this.running = false;
    this.paused = false;
    document.removeEventListener('visibilitychange', this.onVisibility);
    window.removeEventListener('online', this.onOnline);
    this.close();
    this.opened = false;
    this.attempt = 0;
    this.pending.clear();
    this.repeat.clear();
    if (this.reconnectTimer !== null) clearTimeout(this.reconnectTimer);
    if (this.flushTimer !== null) clearTimeout(this.flushTimer);
    this.reconnectTimer = null;
    this.flushTimer = null;
  }

  pause(): void {
    this.paused = true;
  }

  resume(): void {
    if (!this.paused) return;
    this.paused = false;
    if (this.pending.size) this.schedule();
  }

  private open(): void {
    if (!this.running || this.source) return;

    const organizationId = this.activeOrg.id();
    const url = organizationId
      ? `/api/events?org=${encodeURIComponent(organizationId)}`
      : '/api/events';

    const source = new EventSource(url, { withCredentials: true });
    this.source = source;

    source.addEventListener('open', () => {
      this.attempt = 0;
      this.connectedSignal.set(true);
      if (this.opened) this.catchUp();
      else this.queue('notifications');
      this.opened = true;
    });

    source.addEventListener('change', (event) => this.onChange(event as MessageEvent<string>));

    source.addEventListener('error', () => {
      this.close();
      this.reconnect();
    });
  }

  private close(): void {
    if (!this.source) return;
    this.source.close();
    this.source = null;
    this.connectedSignal.set(false);
  }

  private reconnect(): void {
    if (!this.running || this.reconnectTimer !== null) return;

    const delay = BACKOFF_MS[Math.min(this.attempt, BACKOFF_MS.length - 1)];
    this.attempt += 1;
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.open();
    }, delay);
  }

  private readonly onVisibility = (): void => {
    if (document.visibilityState !== 'visible') return;
    if (!this.source) {
      if (this.reconnectTimer !== null) {
        clearTimeout(this.reconnectTimer);
        this.reconnectTimer = null;
      }
      this.attempt = 0;
      this.open();
    }
    this.catchUp();
  };

  private readonly onOnline = (): void => {
    if (this.source) return;
    if (this.reconnectTimer !== null) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    this.attempt = 0;
    this.open();
  };

  private catchUp(): void {
    this.queue('tasks');
    this.queue('notifications');
    if (this.details.key()) this.queue('detail');
  }

  private onChange(event: MessageEvent<string>): void {
    let message: RealtimeMessageDto;

    try {
      message = JSON.parse(event.data) as RealtimeMessageDto;
    } catch {
      return;
    }

    switch (message.type) {
      case 'task':
        if (!QUIET_CHANGES.includes(message.change)) this.queue('tasks');
        if (message.taskKey && message.taskKey === this.details.key()) this.queue('detail');
        break;
      case 'workspace':
        this.queue('workspace');
        break;
      case 'notification':
        this.queue('notifications');
        break;
    }
  }

  private queue(target: RefreshTarget): void {
    this.pending.add(target);
    this.schedule();
  }

  private schedule(): void {
    if (this.flushTimer !== null) return;
    this.flushTimer = setTimeout(() => this.flush(), COALESCE_MS);
  }

  private flush(): void {
    this.flushTimer = null;

    if (this.paused) {
      this.schedule();
      return;
    }

    const targets = [...this.pending];
    this.pending.clear();
    for (const target of targets) void this.run(target);
  }

  private async run(target: RefreshTarget): Promise<void> {
    if (this.inFlight.has(target)) {
      this.repeat.add(target);
      return;
    }

    this.inFlight.add(target);

    try {
      await this.perform(target);
    } catch {
      this.repeat.delete(target);
    } finally {
      this.inFlight.delete(target);
    }

    if (this.repeat.delete(target)) void this.run(target);
  }

  private perform(target: RefreshTarget): Promise<void> {
    switch (target) {
      case 'tasks':
        return this.workspace.reloadTasks();
      case 'workspace':
        return this.workspace.reloadBootstrap();
      case 'detail':
        return this.details.refresh();
      case 'notifications':
        return this.notifications.refresh();
    }
  }
}

import { type ComponentRef, Injectable, Injector, inject, signal } from '@angular/core';
import {
  type OverlayRef,
  createGlobalPositionStrategy,
  createOverlayRef,
} from '@angular/cdk/overlay';
import { ComponentPortal } from '@angular/cdk/portal';
import { ToastHost, type ToastItem, type ToastKind, type ToastOptions } from './toast';

const MAX_VISIBLE = 3;

const DEFAULT_DURATION: Record<ToastKind, number> = {
  success: 4000,
  info: 4500,
  error: 7000,
};

type PlainOptions = Omit<ToastOptions, 'kind' | 'message'>;

@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly injector = inject(Injector);
  private readonly queue = signal<readonly ToastItem[]>([]);
  private readonly durations = new Map<number, number>();
  private readonly timers = new Map<number, ReturnType<typeof setTimeout>>();

  private overlayRef: OverlayRef | null = null;
  private hostRef: ComponentRef<ToastHost> | null = null;
  private counter = 0;

  success(message: string, options: PlainOptions = {}): number {
    return this.show({ ...options, kind: 'success', message });
  }

  error(message: string, options: PlainOptions = {}): number {
    return this.show({ ...options, kind: 'error', message });
  }

  info(message: string, options: PlainOptions = {}): number {
    return this.show({ ...options, kind: 'info', message });
  }

  show(options: ToastOptions): number {
    const kind = options.kind ?? 'info';
    this.counter += 1;
    const item: ToastItem = {
      id: this.counter,
      kind,
      message: options.message,
      description: options.description ?? '',
      action: options.action ?? null,
    };

    const fallback = DEFAULT_DURATION[kind] + (item.action ? 2000 : 0);
    this.durations.set(item.id, options.duration ?? fallback);
    this.queue.update((list) => [...list, item]);
    this.render();
    return item.id;
  }

  dismiss(id: number): void {
    this.clearTimer(id);
    this.durations.delete(id);
    this.queue.update((list) => list.filter((item) => item.id !== id));
    this.render();
  }

  clear(): void {
    for (const id of [...this.timers.keys()]) this.clearTimer(id);
    this.durations.clear();
    this.queue.set([]);
    this.render();
  }

  private runAction(id: number): void {
    const item = this.queue().find((entry) => entry.id === id);
    item?.action?.run();
    this.dismiss(id);
  }

  private render(): void {
    const items = this.queue().slice(0, MAX_VISIBLE);

    if (items.length === 0) {
      this.unmount();
      return;
    }

    this.mount();
    this.hostRef?.setInput('items', items);
    for (const item of items) this.startTimer(item.id);
  }

  private startTimer(id: number): void {
    if (this.timers.has(id)) return;
    const duration = this.durations.get(id) ?? 0;
    if (duration <= 0) return;
    this.timers.set(
      id,
      setTimeout(() => this.dismiss(id), duration),
    );
  }

  private clearTimer(id: number): void {
    const timer = this.timers.get(id);
    if (timer !== undefined) clearTimeout(timer);
    this.timers.delete(id);
  }

  private mount(): void {
    if (this.overlayRef) return;

    const overlayRef = createOverlayRef(this.injector, {
      positionStrategy: createGlobalPositionStrategy(this.injector).bottom('16px').right('16px'),
      hasBackdrop: false,
      panelClass: 'ui-toast-pane',
      disposeOnNavigation: false,
    });

    const hostRef = overlayRef.attach(new ComponentPortal(ToastHost, null, this.injector));
    hostRef.instance.dismissed.subscribe((id) => this.dismiss(id));
    hostRef.instance.actioned.subscribe((id) => this.runAction(id));

    this.overlayRef = overlayRef;
    this.hostRef = hostRef;
  }

  private unmount(): void {
    if (!this.overlayRef) return;
    this.overlayRef.dispose();
    this.overlayRef = null;
    this.hostRef = null;
  }
}

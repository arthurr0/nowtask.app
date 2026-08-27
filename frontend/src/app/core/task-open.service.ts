import { Injectable, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { filter, map, startWith } from 'rxjs';
import { WorkspaceStore } from '../data/workspace.store';

export const TASK_QUERY_PARAM = 'task';

const TASK_PAGE_PREFIX = '/app/tasks/';

@Injectable({ providedIn: 'root' })
export class TaskOpenService {
  private readonly router = inject(Router);
  private readonly store = inject(WorkspaceStore);

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map((event) => event.urlAfterRedirects),
      startWith(this.router.url),
    ),
    { initialValue: this.router.url },
  );

  readonly openedKey = computed<string | null>(() => {
    const value = this.router.parseUrl(this.url()).queryParams[TASK_QUERY_PARAM];
    return typeof value === 'string' && value.length > 0 ? value : null;
  });

  open(key: string): void {
    if (this.store.taskOpenMode() === 'page' || this.router.url.startsWith(TASK_PAGE_PREFIX)) {
      void this.router.navigate(['/app/tasks', key]);
      return;
    }

    const tree = this.router.parseUrl(this.router.url);
    tree.queryParams = { ...tree.queryParams, [TASK_QUERY_PARAM]: key };
    void this.router.navigateByUrl(tree);
  }

  close(): void {
    const tree = this.router.parseUrl(this.router.url);
    if (!(TASK_QUERY_PARAM in tree.queryParams)) return;

    const params = { ...tree.queryParams };
    delete params[TASK_QUERY_PARAM];
    tree.queryParams = params;
    void this.router.navigateByUrl(tree);
  }

  link(key: string): string {
    return `${window.location.origin}${TASK_PAGE_PREFIX}${key}`;
  }
}

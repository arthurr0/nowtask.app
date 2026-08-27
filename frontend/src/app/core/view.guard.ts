import { inject } from '@angular/core';
import { Router, type CanActivateFn } from '@angular/router';
import { WorkspaceStore } from '../data/workspace.store';
import type { TaskViewCode } from './api-types';
import { taskViewMeta } from './task-views';

export function taskViewGuard(code: TaskViewCode): CanActivateFn {
  return async () => {
    const store = inject(WorkspaceStore);
    const router = inject(Router);

    await store.load();

    if (store.taskViewEnabled(code, null)) return true;

    const fallback = store.enabledViews(null)[0];
    if (fallback === code) return true;

    return router.createUrlTree([taskViewMeta(fallback).path]);
  };
}

export const defaultViewGuard: CanActivateFn = async (route) => {
  const store = inject(WorkspaceStore);
  const router = inject(Router);

  await store.load();

  return router.createUrlTree([taskViewMeta(store.resolvedDefaultView()).path], {
    queryParams: route.queryParams,
  });
};

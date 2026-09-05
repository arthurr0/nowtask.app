import { inject } from '@angular/core';
import { Router, type CanActivateFn } from '@angular/router';
import { ViewState } from '../data/view-state';
import { WorkspaceStore } from '../data/workspace.store';
import type { TaskViewCode } from './api-types';
import { taskViewMeta } from './task-views';

export function layoutGuard(code: TaskViewCode): CanActivateFn {
  return () => {
    const state = inject(ViewState);
    state.leaveView();
    state.layout.set(code);
    return true;
  };
}

export function taskViewGuard(code: TaskViewCode): CanActivateFn {
  return async () => {
    const store = inject(WorkspaceStore);
    const router = inject(Router);
    const state = inject(ViewState);

    await store.load();

    const fallback = store.enabledViews(null)[0];
    if (store.taskViewEnabled(code, null) || fallback === code) {
      state.leaveView();
      state.layout.set(code);
      return true;
    }

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

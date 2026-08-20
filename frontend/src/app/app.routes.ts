import type { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: 'nowtask',
    loadComponent: () => import('./features/landing/landing').then((m) => m.Landing),
  },
  {
    path: 'login',
    title: 'nowtask',
    loadComponent: () => import('./features/login/login').then((m) => m.Login),
  },
  {
    path: 'signup',
    title: 'nowtask',
    loadComponent: () => import('./features/signup/signup').then((m) => m.Signup),
  },
  {
    path: 'app',
    canActivate: [authGuard],
    loadComponent: () => import('./features/shell/shell').then((m) => m.Shell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'board' },
      {
        path: 'overview',
        data: { mode: 'overview' },
        loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard),
      },
      {
        path: 'reports',
        data: { mode: 'reports' },
        loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard),
      },
      {
        path: 'board',
        loadComponent: () => import('./features/board/board').then((m) => m.Board),
      },
      {
        path: 'list',
        loadComponent: () => import('./features/list/list').then((m) => m.TaskList),
      },
      {
        path: 'my-tasks',
        data: { onlyMine: true },
        loadComponent: () => import('./features/list/list').then((m) => m.TaskList),
      },
      {
        path: 'timeline',
        loadComponent: () => import('./features/timeline/timeline').then((m) => m.Timeline),
      },
      {
        path: 'tasks/:key',
        loadComponent: () => import('./features/task-detail/task-detail').then((m) => m.TaskDetail),
      },
      {
        path: 'automations',
        loadComponent: () =>
          import('./features/automations/automations').then((m) => m.Automations),
      },
      {
        path: 'agents',
        loadComponent: () => import('./features/agents/agents').then((m) => m.Agents),
      },
      {
        path: 'settings',
        loadComponent: () => import('./features/settings/settings').then((m) => m.Settings),
      },
      {
        path: 'admin',
        loadComponent: () => import('./features/admin/admin').then((m) => m.Admin),
      },
      {
        path: 'system',
        loadComponent: () => import('./features/system/system').then((m) => m.System),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];

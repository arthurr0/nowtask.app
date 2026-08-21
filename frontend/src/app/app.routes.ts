import type { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { onboardingGuard, orgGuard } from './core/org.guard';
import { defaultViewGuard, taskViewGuard } from './core/view.guard';

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
    path: 'forgot-password',
    title: 'nowtask',
    loadComponent: () => import('./features/login/forgot-password').then((m) => m.ForgotPassword),
  },
  {
    path: 'reset-password',
    title: 'nowtask',
    loadComponent: () => import('./features/login/reset-password').then((m) => m.ResetPassword),
  },
  {
    path: 'invite/:token',
    title: 'nowtask',
    loadComponent: () =>
      import('./features/onboarding/invite-landing').then((m) => m.InviteLanding),
  },
  {
    path: 'verify-email',
    title: 'nowtask',
    loadComponent: () => import('./features/onboarding/verify-email').then((m) => m.VerifyEmail),
  },
  {
    path: 'orgs/new',
    title: 'nowtask',
    canActivate: [authGuard],
    loadComponent: () => import('./features/onboarding/create-org').then((m) => m.CreateOrg),
  },
  {
    path: 'onboarding',
    title: 'nowtask',
    canActivate: [authGuard, orgGuard],
    loadComponent: () => import('./features/onboarding/onboarding').then((m) => m.OnboardingWizard),
  },
  {
    path: 'app',
    canActivate: [authGuard, orgGuard, onboardingGuard],
    loadComponent: () => import('./features/shell/shell').then((m) => m.Shell),
    children: [
      {
        path: '',
        pathMatch: 'full',
        canActivate: [defaultViewGuard],
        loadComponent: () => import('./features/board/board').then((m) => m.Board),
      },
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
        canActivate: [taskViewGuard('board')],
        loadComponent: () => import('./features/board/board').then((m) => m.Board),
      },
      {
        path: 'list',
        canActivate: [taskViewGuard('list')],
        loadComponent: () => import('./features/list/list').then((m) => m.TaskList),
      },
      {
        path: 'my-tasks',
        data: { onlyMine: true },
        loadComponent: () => import('./features/list/list').then((m) => m.TaskList),
      },
      {
        path: 'timeline',
        canActivate: [taskViewGuard('timeline')],
        loadComponent: () => import('./features/timeline/timeline').then((m) => m.Timeline),
      },
      {
        path: 'calendar',
        canActivate: [taskViewGuard('calendar')],
        loadComponent: () => import('./features/calendar/calendar').then((m) => m.Calendar),
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
        path: 'organization',
        loadComponent: () =>
          import('./features/organization/organization').then((m) => m.Organization),
      },
      { path: 'admin', redirectTo: 'organization', pathMatch: 'full' },
      {
        path: 'system',
        loadComponent: () => import('./features/system/system').then((m) => m.System),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];

import { HttpErrorResponse, type HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { ActiveOrgService } from './active-org';
import { AuthService } from './auth.service';
import { I18nService } from './i18n/i18n.service';

const PUBLIC_SUFFIXES = ['/api/auth/me', '/api/auth/login', '/api/auth/signup'];
const ORG_INDEPENDENT = [
  /^\/api\/orgs(\?.*)?$/,
  /^\/api\/orgs\/slug-available/,
  /^\/api\/orgs\/[^/]+\/switch$/,
];

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const activeOrg = inject(ActiveOrgService);
  const router = inject(Router);

  const organizationId = activeOrg.id();
  const headers: Record<string, string> = {};

  if (request.url.startsWith('/api/')) {
    headers['Accept-Language'] = inject(I18nService).lang();

    if (organizationId && !ORG_INDEPENDENT.some((pattern) => pattern.test(request.url))) {
      headers['X-Org-Id'] = organizationId;
    }
  }

  const scoped = Object.keys(headers).length > 0 ? request.clone({ setHeaders: headers }) : request;

  return next(scoped).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse)) {
        return throwError(() => error);
      }

      const code = errorCode(error);

      if (error.status === 401 && !PUBLIC_SUFFIXES.some((suffix) => request.url.endsWith(suffix))) {
        auth.markSignedOut();
        activeOrg.clear();
        void router.navigate(['/login']);
      }

      if (error.status === 409 && code === 'NO_ORGANIZATION') {
        activeOrg.clear();
        if (!router.url.startsWith('/orgs/new') && !router.url.startsWith('/invite/')) {
          void router.navigate(['/orgs/new']);
        }
      }

      if (error.status === 403 && (code === 'ORG_FORBIDDEN' || code === 'ORG_ACCESS_REVOKED')) {
        activeOrg.clear();
        window.location.assign('/app');
      }

      return throwError(() => error);
    }),
  );
};

function errorCode(error: HttpErrorResponse): string | null {
  const body: unknown = error.error;
  if (body && typeof body === 'object' && 'code' in body) {
    const code = (body as { code: unknown }).code;
    return typeof code === 'string' ? code : null;
  }
  return null;
}

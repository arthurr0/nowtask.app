import { HttpErrorResponse, type HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return next(request).pipe(
    catchError((error: unknown) => {
      const isSessionCheck = request.url.endsWith('/api/auth/me');
      const isLogin = request.url.endsWith('/api/auth/login');

      if (
        error instanceof HttpErrorResponse &&
        error.status === 401 &&
        !isSessionCheck &&
        !isLogin
      ) {
        auth.markSignedOut();
        void router.navigate(['/login']);
      }

      return throwError(() => error);
    }),
  );
};

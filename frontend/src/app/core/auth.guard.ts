import { inject } from '@angular/core';
import { Router, type ActivatedRouteSnapshot, type CanActivateFn, type RouterStateSnapshot } from '@angular/router';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = async (
  _route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot,
) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (await auth.restore()) {
    return true;
  }

  const returnUrl = state.url && state.url !== '/' ? state.url : null;

  return router.createUrlTree(['/login'], {
    queryParams: returnUrl ? { returnUrl } : {},
  });
};

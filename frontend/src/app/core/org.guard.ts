import { inject } from '@angular/core';
import { Router, type CanActivateFn } from '@angular/router';
import { OnboardingService } from './onboarding.service';
import { OrgService } from './org.service';

export const orgGuard: CanActivateFn = async () => {
  const orgs = inject(OrgService);
  const router = inject(Router);

  const memberships = await orgs.refresh();

  if (memberships.length === 0) {
    return router.createUrlTree(['/orgs/new']);
  }

  return true;
};

export const onboardingGuard: CanActivateFn = async () => {
  const onboarding = inject(OnboardingService);
  const router = inject(Router);

  const state = await onboarding.refresh();

  if (state && state.flow === 'founder' && state.step !== 'done') {
    return router.createUrlTree(['/onboarding']);
  }

  return true;
};

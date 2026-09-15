import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Auth } from './auth';

/** Not wired to any route yet - the game itself is guest-playable. Ready for a future
 * account-only page (e.g. profile, friends). */
export const authGuard: CanActivateFn = () => {
  const auth = inject(Auth);
  const router = inject(Router);
  return auth.isAuthenticated() || router.createUrlTree(['/login']);
};

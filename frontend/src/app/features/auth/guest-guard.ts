import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { Auth } from './auth';

/** Login/register: pointless once logged in, so send the user home instead. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(Auth);
  const router = inject(Router);
  return auth.sessionChecked$.pipe(
    map(() => !auth.isAuthenticated() || router.createUrlTree(['/'])),
  );
};

import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { Auth } from './auth';

/**
 * Admin-only pages. Cosmetic: it spares a regular user an empty page, but the API is the
 * real gate (every /api/admin/** request is checked server-side). Waits for the session
 * restore, then sends a guest to /login and a regular user home.
 */
export const adminGuard: CanActivateFn = (_route, state) => {
  const auth = inject(Auth);
  const router = inject(Router);
  return auth.sessionChecked$.pipe(
    map(() => {
      const user = auth.currentUser();
      if (!user) {
        return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
      }
      return user.role === 'ADMIN' || router.createUrlTree(['/']);
    }),
  );
};

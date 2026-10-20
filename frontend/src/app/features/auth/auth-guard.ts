import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { Auth } from './auth';

/** Account-only pages. Waits for the bootstrap session restore before deciding. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(Auth);
  const router = inject(Router);
  return auth.sessionChecked$.pipe(
    map(
      () =>
        auth.isAuthenticated() ||
        router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } }),
    ),
  );
};

import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, defer, switchMap, throwError } from 'rxjs';
import { Auth } from './auth';

const EXCLUDED_FROM_REFRESH = ['/api/auth/login', '/api/auth/refresh'];

/**
 * An API call sent while the session is being restored waits for it: the access token
 * only lives in memory, so without waiting, a page loaded straight away (today's game at
 * "/") would be read as a guest's, the account's rounds left out. The auth calls never
 * wait: the restore itself is made of them.
 */
function waitsForSession(req: HttpRequest<unknown>, auth: Auth): boolean {
  return !auth.sessionChecked() && req.url.startsWith('/api/') && !req.url.startsWith('/api/auth/');
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(Auth);
  // Read when sent, not when the call was made: after the wait, the token is the restored one.
  const send = defer(() => {
    const token = auth.accessToken();
    return next(token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req);
  });
  const sent = waitsForSession(req, auth) ? auth.sessionChecked$.pipe(switchMap(() => send)) : send;

  return sent.pipe(
    catchError((error: unknown) => {
      const isUnauthorized = error instanceof HttpErrorResponse && error.status === 401;
      const canRetry =
        isUnauthorized && !EXCLUDED_FROM_REFRESH.some((path) => req.url.startsWith(path));
      if (!canRetry) {
        return throwError(() => error);
      }

      return auth.refreshOnce().pipe(
        switchMap((newToken) =>
          next(req.clone({ setHeaders: { Authorization: `Bearer ${newToken}` } })),
        ),
        catchError((refreshError: unknown) => {
          // Refresh itself failed - treat as reverting to guest, not a hard error;
          // the original request's failure still propagates to its caller.
          auth.accessToken.set(null);
          auth.currentUser.set(null);
          return throwError(() => refreshError);
        }),
      );
    }),
  );
};

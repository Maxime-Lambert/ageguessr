import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { Auth } from './auth';

const EXCLUDED_FROM_REFRESH = ['/api/auth/login', '/api/auth/refresh'];

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(Auth);
  const token = auth.accessToken();
  const authorizedReq = token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authorizedReq).pipe(
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

import { HttpClient } from '@angular/common/http';
import { Service, computed, inject, signal } from '@angular/core';
import { toObservable } from '@angular/core/rxjs-interop';
import {
  Observable,
  catchError,
  filter,
  finalize,
  map,
  of,
  shareReplay,
  switchMap,
  take,
  tap,
} from 'rxjs';
import { AuthUser, LoginResponse, RefreshResponse, RegisterResponse } from './models';

@Service()
export class Auth {
  private readonly http = inject(HttpClient);

  readonly currentUser = signal<AuthUser | null>(null);
  readonly accessToken = signal<string | null>(null);
  readonly isAuthenticated = computed(() => this.currentUser() !== null);

  // False until the bootstrap session restore has settled. Guards wait for it: on a
  // hard reload of an account-only page, deciding before the refresh call returns
  // would wrongly bounce a logged-in user to /login.
  readonly sessionChecked = signal(false);
  readonly sessionChecked$ = toObservable(this.sessionChecked).pipe(filter(Boolean), take(1));

  // Refresh tokens are single-use (rotated on every call). If two requests 401 at the
  // same moment and each independently called /api/auth/refresh, the first rotation
  // would succeed and the second would present an already-revoked token, causing a
  // spurious logout of a perfectly valid session. Caching the in-flight call so
  // concurrent callers share one refresh avoids that race.
  private refreshInFlight: Observable<string> | null = null;

  register(
    email: string,
    username: string,
    password: string,
    acceptTerms: boolean,
  ): Observable<RegisterResponse> {
    return this.http.post<RegisterResponse>('/api/auth/register', {
      email,
      username,
      password,
      acceptTerms,
    });
  }

  login(email: string, password: string): Observable<AuthUser> {
    return this.http
      .post<LoginResponse>('/api/auth/login', { email, password }, { withCredentials: true })
      .pipe(
        tap((response) => this.accessToken.set(response.accessToken)),
        switchMap(() => this.me()),
      );
  }

  /** Local state is cleared even if the call fails - the user asked to be logged out. */
  logout(): Observable<void> {
    return this.http
      .post<void>('/api/auth/logout', {}, { withCredentials: true })
      .pipe(finalize(() => this.clearLocalSession()));
  }

  me(): Observable<AuthUser> {
    return this.http.get<AuthUser>('/api/auth/me').pipe(tap((user) => this.currentUser.set(user)));
  }

  confirmEmail(token: string): Observable<void> {
    return this.http.post<void>('/api/auth/confirm-email', { token });
  }

  resendVerification(email: string): Observable<void> {
    return this.http.post<void>('/api/auth/resend-verification', { email });
  }

  forgotPassword(email: string): Observable<void> {
    return this.http.post<void>('/api/auth/forgot-password', { email });
  }

  /** A reset revokes every session server-side, this device's included. */
  resetPassword(token: string, newPassword: string): Observable<void> {
    return this.http
      .post<void>('/api/auth/reset-password', { token, newPassword })
      .pipe(tap(() => this.clearLocalSession()));
  }

  changeUsername(username: string): Observable<AuthUser> {
    return this.http
      .patch<AuthUser>('/api/auth/me/username', { username })
      .pipe(tap((user) => this.currentUser.set(user)));
  }

  /** withCredentials: the refresh cookie tells the backend which session to keep alive. */
  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.post<void>(
      '/api/auth/me/password',
      { currentPassword, newPassword },
      { withCredentials: true },
    );
  }

  /** Only sends the confirmation email - nothing is deleted until the link is confirmed. */
  requestAccountDeletion(password: string): Observable<void> {
    return this.http.post<void>('/api/auth/me/deletion', { password });
  }

  confirmAccountDeletion(token: string): Observable<void> {
    return this.http
      .post<void>('/api/auth/confirm-account-deletion', { token }, { withCredentials: true })
      .pipe(tap(() => this.clearLocalSession()));
  }

  private clearLocalSession(): void {
    this.accessToken.set(null);
    this.currentUser.set(null);
  }

  refreshOnce(): Observable<string> {
    if (!this.refreshInFlight) {
      this.refreshInFlight = this.http
        .post<RefreshResponse>('/api/auth/refresh', {}, { withCredentials: true })
        .pipe(
          map((response) => response.accessToken),
          tap((token) => this.accessToken.set(token)),
          finalize(() => (this.refreshInFlight = null)),
          shareReplay(1),
        );
    }
    return this.refreshInFlight;
  }

  /** Silently tries to restore a session from the HttpOnly refresh cookie on app
   * bootstrap. Failure just means "guest" - never surfaced as an error, since playing
   * without an account is a normal, expected state. */
  tryRestoreSession(): Observable<boolean> {
    return this.refreshOnce().pipe(
      switchMap(() => this.me()),
      map(() => true),
      catchError(() => of(false)),
      finalize(() => this.sessionChecked.set(true)),
    );
  }
}

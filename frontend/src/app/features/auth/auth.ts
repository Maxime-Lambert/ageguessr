import { HttpClient } from '@angular/common/http';
import { Service, computed, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, map, of, shareReplay, switchMap, tap } from 'rxjs';
import { AuthUser, LoginResponse, RefreshResponse, RegisterResponse } from './models';

@Service()
export class Auth {
  private readonly http = inject(HttpClient);

  readonly currentUser = signal<AuthUser | null>(null);
  readonly accessToken = signal<string | null>(null);
  readonly isAuthenticated = computed(() => this.currentUser() !== null);

  // Refresh tokens are single-use (rotated on every call). If two requests 401 at the
  // same moment and each independently called /api/auth/refresh, the first rotation
  // would succeed and the second would present an already-revoked token, causing a
  // spurious logout of a perfectly valid session. Caching the in-flight call so
  // concurrent callers share one refresh avoids that race.
  private refreshInFlight: Observable<string> | null = null;

  register(email: string, password: string): Observable<RegisterResponse> {
    return this.http.post<RegisterResponse>('/api/auth/register', { email, password });
  }

  login(email: string, password: string): Observable<AuthUser> {
    return this.http
      .post<LoginResponse>('/api/auth/login', { email, password }, { withCredentials: true })
      .pipe(
        tap((response) => this.accessToken.set(response.accessToken)),
        switchMap(() => this.me()),
      );
  }

  logout(): Observable<void> {
    return this.http.post<void>('/api/auth/logout', {}, { withCredentials: true }).pipe(
      tap(() => {
        this.accessToken.set(null);
        this.currentUser.set(null);
      }),
    );
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
    );
  }
}

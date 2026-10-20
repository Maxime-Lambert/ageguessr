import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  CanActivateFn,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { Observable, firstValueFrom } from 'rxjs';
import { Auth } from './auth';
import { authGuard } from './auth-guard';

describe('authGuard', () => {
  const state = { url: '/account' } as RouterStateSnapshot;
  const executeGuard: CanActivateFn = (...guardParameters) =>
    TestBed.runInInjectionContext(() => authGuard(...guardParameters));
  const run = () =>
    executeGuard({} as ActivatedRouteSnapshot, state) as Observable<boolean | UrlTree>;

  let auth: Auth;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    auth = TestBed.inject(Auth);
  });

  it('allows navigation when authenticated', async () => {
    auth.currentUser.set({ id: '1', email: 'user@x.com', username: 'Player', emailVerified: true });
    auth.sessionChecked.set(true);

    expect(await firstValueFrom(run())).toBe(true);
  });

  it('redirects to /login with the requested page as returnUrl when not authenticated', async () => {
    auth.sessionChecked.set(true);

    const result = await firstValueFrom(run());

    expect(result).toBeInstanceOf(UrlTree);
    expect((result as UrlTree).toString()).toBe('/login?returnUrl=%2Faccount');
  });

  it('waits for the session restore before deciding (hard reload of /account)', async () => {
    let decided: boolean | UrlTree | undefined;
    run().subscribe((value) => (decided = value));

    expect(decided).toBeUndefined();

    // The restore completes and turns out to be a valid session.
    auth.currentUser.set({ id: '1', email: 'user@x.com', username: 'Player', emailVerified: true });
    auth.sessionChecked.set(true);
    TestBed.tick();

    expect(decided).toBe(true);
  });
});

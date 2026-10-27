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
import { guestGuard } from './guest-guard';

describe('guestGuard', () => {
  const executeGuard: CanActivateFn = (...guardParameters) =>
    TestBed.runInInjectionContext(() => guestGuard(...guardParameters));
  const run = () =>
    executeGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot) as Observable<
      boolean | UrlTree
    >;

  let auth: Auth;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    auth = TestBed.inject(Auth);
    auth.sessionChecked.set(true);
  });

  it('lets a guest through', async () => {
    expect(await firstValueFrom(run())).toBe(true);
  });

  it('sends a logged-in user home', async () => {
    auth.currentUser.set({
      id: '1',
      email: 'user@x.com',
      username: 'Player',
      emailVerified: true,
      role: 'USER',
    });

    const result = await firstValueFrom(run());

    expect((result as UrlTree).toString()).toBe('/');
  });
});

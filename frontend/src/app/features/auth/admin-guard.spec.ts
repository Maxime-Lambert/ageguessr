import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  CanActivateFn,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { Observable, firstValueFrom } from 'rxjs';
import { Auth } from './auth';
import { adminGuard } from './admin-guard';

describe('adminGuard', () => {
  const state = { url: '/admin/curation' } as RouterStateSnapshot;
  const executeGuard: CanActivateFn = (...guardParameters) =>
    TestBed.runInInjectionContext(() => adminGuard(...guardParameters));
  const run = () =>
    executeGuard({} as ActivatedRouteSnapshot, state) as Observable<boolean | UrlTree>;

  let auth: Auth;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    auth = TestBed.inject(Auth);
    router = TestBed.inject(Router);
  });

  it('lets an admin in', async () => {
    auth.currentUser.set({
      id: '1',
      email: 'admin@x.com',
      username: 'Admin',
      emailVerified: true,
      role: 'ADMIN',
    });
    auth.sessionChecked.set(true);

    expect(await firstValueFrom(run())).toBe(true);
  });

  it('sends a regular user home', async () => {
    auth.currentUser.set({
      id: '1',
      email: 'user@x.com',
      username: 'Player',
      emailVerified: true,
      role: 'USER',
    });
    auth.sessionChecked.set(true);

    const result = (await firstValueFrom(run())) as UrlTree;
    expect(router.serializeUrl(result)).toBe('/');
  });

  it('sends a guest to /login, back to the page afterwards', async () => {
    auth.sessionChecked.set(true);

    const result = (await firstValueFrom(run())) as UrlTree;
    expect(router.serializeUrl(result)).toBe('/login?returnUrl=%2Fadmin%2Fcuration');
  });
});

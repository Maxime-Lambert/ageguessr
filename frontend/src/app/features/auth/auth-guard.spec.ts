import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CanActivateFn, provideRouter } from '@angular/router';
import { Auth } from './auth';
import { authGuard } from './auth-guard';

describe('authGuard', () => {
  const executeGuard: CanActivateFn = (...guardParameters) =>
    TestBed.runInInjectionContext(() => authGuard(...guardParameters));

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
  });

  it('allows navigation when authenticated', () => {
    TestBed.inject(Auth).currentUser.set({ id: '1', email: 'user@x.com', emailVerified: true });

    expect(executeGuard(null!, null!)).toBe(true);
  });

  it('redirects to /login when not authenticated', () => {
    const result = executeGuard(null!, null!);

    expect(result).not.toBe(true);
  });
});

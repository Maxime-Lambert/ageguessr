import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Auth } from './auth';

describe('Auth', () => {
  let service: Auth;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(Auth);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('login sets the access token and fetches the current user', () => {
    let result: unknown;
    service.login('user@x.com', 'password123').subscribe((user) => (result = user));

    const loginReq = httpMock.expectOne('/api/auth/login');
    expect(loginReq.request.withCredentials).toBe(true);
    loginReq.flush({ accessToken: 'access-token' });

    expect(service.accessToken()).toBe('access-token');

    const meReq = httpMock.expectOne('/api/auth/me');
    meReq.flush({ id: '1', email: 'user@x.com', emailVerified: true });

    expect(service.currentUser()).toEqual({ id: '1', email: 'user@x.com', emailVerified: true });
    expect(service.isAuthenticated()).toBe(true);
    expect(result).toEqual({ id: '1', email: 'user@x.com', emailVerified: true });
  });

  it('logout clears the session state', () => {
    service.accessToken.set('access-token');
    service.currentUser.set({ id: '1', email: 'user@x.com', emailVerified: true });

    service.logout().subscribe();

    const req = httpMock.expectOne('/api/auth/logout');
    expect(req.request.withCredentials).toBe(true);
    req.flush(null);

    expect(service.accessToken()).toBeNull();
    expect(service.currentUser()).toBeNull();
  });

  it('refreshOnce shares one in-flight request across concurrent callers', () => {
    let firstToken: string | undefined;
    let secondToken: string | undefined;
    service.refreshOnce().subscribe((token) => (firstToken = token));
    service.refreshOnce().subscribe((token) => (secondToken = token));

    const req = httpMock.expectOne('/api/auth/refresh');
    req.flush({ accessToken: 'rotated-token' });

    expect(firstToken).toBe('rotated-token');
    expect(secondToken).toBe('rotated-token');
    expect(service.accessToken()).toBe('rotated-token');
  });

  it('refreshOnce issues a new request once the previous one has completed', () => {
    service.refreshOnce().subscribe();
    httpMock.expectOne('/api/auth/refresh').flush({ accessToken: 'first-token' });

    service.refreshOnce().subscribe();
    httpMock.expectOne('/api/auth/refresh').flush({ accessToken: 'second-token' });

    expect(service.accessToken()).toBe('second-token');
  });

  it('tryRestoreSession resolves to false without throwing when there is no valid session', () => {
    let restored: boolean | undefined;
    service.tryRestoreSession().subscribe((value) => (restored = value));

    httpMock
      .expectOne('/api/auth/refresh')
      .flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(restored).toBe(false);
  });
});

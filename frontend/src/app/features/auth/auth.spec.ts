import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Auth } from './auth';

const USER = {
  id: '1',
  email: 'user@x.com',
  username: 'Player',
  emailVerified: true,
  role: 'USER' as const,
};

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
    service.login('user@x.com', 'tortoise-violin-42').subscribe((user) => (result = user));

    const loginReq = httpMock.expectOne('/api/auth/login');
    expect(loginReq.request.withCredentials).toBe(true);
    loginReq.flush({ accessToken: 'access-token' });

    expect(service.accessToken()).toBe('access-token');

    const meReq = httpMock.expectOne('/api/auth/me');
    meReq.flush({
      id: '1',
      email: 'user@x.com',
      username: 'Player',
      emailVerified: true,
      role: 'USER',
    });

    expect(service.currentUser()).toEqual({
      id: '1',
      email: 'user@x.com',
      username: 'Player',
      emailVerified: true,
      role: 'USER',
    });
    expect(service.isAuthenticated()).toBe(true);
    expect(result).toEqual({
      id: '1',
      email: 'user@x.com',
      username: 'Player',
      emailVerified: true,
      role: 'USER',
    });
  });

  it('logout clears the session state', () => {
    service.accessToken.set('access-token');
    service.currentUser.set({
      id: '1',
      email: 'user@x.com',
      username: 'Player',
      emailVerified: true,
      role: 'USER',
    });

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

  it('register sends the username and the Terms acceptance', () => {
    service.register('user@x.com', 'Player', 'tortoise-violin-42', true).subscribe();

    const req = httpMock.expectOne('/api/auth/register');
    expect(req.request.body).toEqual({
      email: 'user@x.com',
      username: 'Player',
      password: 'tortoise-violin-42',
      acceptTerms: true,
    });
    req.flush({ userId: '1', email: 'user@x.com', username: 'Player' });
  });

  it('tryRestoreSession marks the session as checked whatever the outcome', () => {
    expect(service.sessionChecked()).toBe(false);

    service.tryRestoreSession().subscribe();
    httpMock
      .expectOne('/api/auth/refresh')
      .flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(service.sessionChecked()).toBe(true);
  });

  it('logout clears the local session even when the call fails', () => {
    service.accessToken.set('access-token');
    service.currentUser.set(USER);

    service.logout().subscribe({ error: () => undefined });
    httpMock.expectOne('/api/auth/logout').flush(null, { status: 500, statusText: 'Error' });

    expect(service.currentUser()).toBeNull();
    expect(service.accessToken()).toBeNull();
  });

  it('changeUsername updates the current user', () => {
    service.currentUser.set(USER);

    service.changeUsername('NewName').subscribe();
    const req = httpMock.expectOne('/api/auth/me/username');
    expect(req.request.method).toBe('PATCH');
    req.flush({ ...USER, username: 'NewName' });

    expect(service.currentUser()?.username).toBe('NewName');
  });

  it('changePassword sends the refresh cookie so this session is kept', () => {
    service.changePassword('old-tortoise-violin', 'new-tortoise-violin').subscribe();

    const req = httpMock.expectOne('/api/auth/me/password');
    expect(req.request.withCredentials).toBe(true);
    expect(req.request.body).toEqual({
      currentPassword: 'old-tortoise-violin',
      newPassword: 'new-tortoise-violin',
    });
    req.flush(null);
  });

  it('requestAccountDeletion only asks for the confirmation email', () => {
    service.currentUser.set(USER);

    service.requestAccountDeletion('tortoise-violin-42').subscribe();
    httpMock
      .expectOne('/api/auth/me/deletion')
      .flush(null, { status: 202, statusText: 'Accepted' });

    expect(service.currentUser()).toEqual(USER);
  });

  it('confirmAccountDeletion clears the local session', () => {
    service.accessToken.set('access-token');
    service.currentUser.set(USER);

    service.confirmAccountDeletion('token').subscribe();
    httpMock.expectOne('/api/auth/confirm-account-deletion').flush(null);

    expect(service.currentUser()).toBeNull();
    expect(service.accessToken()).toBeNull();
  });

  it('resetPassword clears the local session, since every session was revoked', () => {
    service.accessToken.set('access-token');
    service.currentUser.set(USER);

    service.resetPassword('token', 'new-tortoise-violin').subscribe();
    httpMock.expectOne('/api/auth/reset-password').flush(null);

    expect(service.currentUser()).toBeNull();
  });
});

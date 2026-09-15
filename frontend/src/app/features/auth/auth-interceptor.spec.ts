import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Auth } from './auth';
import { authInterceptor } from './auth-interceptor';

describe('authInterceptor', () => {
  let http: HttpClient;
  let auth: Auth;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    auth = TestBed.inject(Auth);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('attaches the bearer token when one is set', () => {
    auth.accessToken.set('current-token');

    http.get('/api/protected').subscribe();

    const req = httpMock.expectOne('/api/protected');
    expect(req.request.headers.get('Authorization')).toBe('Bearer current-token');
    req.flush({});
  });

  it('refreshes and retries once on a 401, then succeeds with the new token', () => {
    auth.accessToken.set('expired-token');
    let result: unknown;
    http.get('/api/protected').subscribe((value) => (result = value));

    httpMock.expectOne('/api/protected').flush(null, { status: 401, statusText: 'Unauthorized' });
    httpMock.expectOne('/api/auth/refresh').flush({ accessToken: 'new-token' });

    const retried = httpMock.expectOne('/api/protected');
    expect(retried.request.headers.get('Authorization')).toBe('Bearer new-token');
    retried.flush({ ok: true });

    expect(result).toEqual({ ok: true });
  });

  it('two concurrent 401s trigger exactly one refresh call', () => {
    auth.accessToken.set('expired-token');
    http.get('/api/protected/a').subscribe();
    http.get('/api/protected/b').subscribe();

    httpMock.expectOne('/api/protected/a').flush(null, { status: 401, statusText: 'Unauthorized' });
    httpMock.expectOne('/api/protected/b').flush(null, { status: 401, statusText: 'Unauthorized' });

    httpMock.expectOne('/api/auth/refresh').flush({ accessToken: 'new-token' });

    httpMock.expectOne('/api/protected/a').flush({});
    httpMock.expectOne('/api/protected/b').flush({});
  });

  it('does not attempt a refresh when the login call itself returns 401', () => {
    let failed = false;
    http.post('/api/auth/login', {}).subscribe({ error: () => (failed = true) });

    httpMock.expectOne('/api/auth/login').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(failed).toBe(true);
    httpMock.expectNone('/api/auth/refresh');
  });
});

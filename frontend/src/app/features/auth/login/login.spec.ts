import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { Login } from './login';

describe('Login', () => {
  let fixture: ComponentFixture<Login>;
  let component: Login;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Login);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  function fillForm(email: string, password: string) {
    component.form.setValue({ email, password });
  }

  it('shows a generic error on invalid credentials', () => {
    fillForm('user@x.com', 'wrong-password');

    component.submit();

    httpMock.expectOne('/api/auth/login').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(component.errorMessage()).toBe('Invalid email or password.');
    expect(component.needsVerification()).toBe(false);
  });

  it('offers to resend the verification email on EMAIL_NOT_VERIFIED', () => {
    fillForm('unverified@x.com', 'password123');

    component.submit();

    httpMock
      .expectOne('/api/auth/login')
      .flush(
        { errorCode: 'EMAIL_NOT_VERIFIED', message: 'nope' },
        { status: 403, statusText: 'Forbidden' },
      );

    expect(component.needsVerification()).toBe(true);

    component.resendVerification();
    httpMock.expectOne('/api/auth/resend-verification').flush(null);

    expect(component.resendSent()).toBe(true);
  });
});

describe('Login return URL', () => {
  async function loginWith(returnUrl: string | null): Promise<string> {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { queryParamMap: convertToParamMap(returnUrl ? { returnUrl } : {}) },
          },
        },
      ],
    }).compileComponents();
    const navigateSpy = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const component = TestBed.createComponent(Login).componentInstance;
    const httpMock = TestBed.inject(HttpTestingController);

    component.form.setValue({ email: 'user@x.com', password: 'password123' });
    component.submit();
    httpMock.expectOne('/api/auth/login').flush({ accessToken: 'token' });
    httpMock
      .expectOne('/api/auth/me')
      .flush({ id: '1', email: 'user@x.com', username: 'Player', emailVerified: true });

    return navigateSpy.mock.calls[0][0] as string;
  }

  it('goes back to the page that required logging in', async () => {
    expect(await loginWith('/account')).toBe('/account');
  });

  it('goes home without a return URL', async () => {
    expect(await loginWith(null)).toBe('/');
  });

  it.each(['//evil.com', '/\\evil.com', 'https://evil.com'])(
    'never redirects off-site (%s)',
    async (returnUrl) => {
      expect(await loginWith(returnUrl)).toBe('/');
    },
  );
});

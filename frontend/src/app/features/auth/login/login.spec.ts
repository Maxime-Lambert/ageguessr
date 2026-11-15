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
    fillForm('unverified@x.com', 'tortoise-violin-42');

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

  describe('with two-factor authentication', () => {
    function toCodeStep() {
      fillForm('user@x.com', 'tortoise-violin-42');
      component.submit();
      httpMock
        .expectOne('/api/auth/login')
        .flush({ twoFactorRequired: true, challenge: 'the-challenge' });
      fixture.detectChanges();
    }

    it('asks for the code once the password is right', () => {
      toCodeStep();

      expect(component.challenge()).toBe('the-challenge');
      const input = fixture.nativeElement.querySelector('input[autocomplete="one-time-code"]');
      expect(input).not.toBeNull();
      httpMock.expectNone('/api/auth/me');
    });

    it('signs in with the code, sent with the challenge', () => {
      const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
      toCodeStep();
      component.codeForm.setValue({ code: '123456' });

      component.submitCode();
      const request = httpMock.expectOne('/api/auth/login/totp');
      expect(request.request.body).toEqual({ challenge: 'the-challenge', code: '123456' });
      request.flush({ accessToken: 'token' });
      httpMock.expectOne('/api/auth/me').flush({ id: '1' });
      httpMock.expectOne('/api/game/guest-plays/claim').flush({ claimed: 0 });

      expect(navigate).toHaveBeenCalledWith('/');
    });

    it('explains a wrong code and keeps the step', () => {
      toCodeStep();
      component.codeForm.setValue({ code: '000000' });

      component.submitCode();
      httpMock
        .expectOne('/api/auth/login/totp')
        .flush({ errorCode: 'INVALID_TOTP_CODE' }, { status: 403, statusText: 'Forbidden' });

      expect(component.challenge()).toBe('the-challenge');
      expect(component.errorMessage()).toContain('Incorrect or expired code');
    });

    it('goes back to the password once the challenge has expired', () => {
      toCodeStep();
      component.codeForm.setValue({ code: '123456' });

      component.submitCode();
      httpMock
        .expectOne('/api/auth/login/totp')
        .flush(null, { status: 404, statusText: 'Not Found' });

      expect(component.challenge()).toBeNull();
      expect(component.errorMessage()).toContain('expired');
    });

    it('refuses a code that is not 6 digits without asking the server', () => {
      toCodeStep();
      component.codeForm.setValue({ code: '12a' });

      component.submitCode();

      httpMock.expectNone('/api/auth/login/totp');
    });

    it('emails the recovery link when the app is lost', () => {
      toCodeStep();

      component.requestRecovery();
      const request = httpMock.expectOne('/api/auth/login/totp/recovery');
      expect(request.request.body).toEqual({ challenge: 'the-challenge' });
      request.flush(null, { status: 202, statusText: 'Accepted' });

      expect(component.recoverySent()).toBe(true);
    });
  });
});

describe('Login return URL', () => {
  async function loginWith(returnUrl: string | null, claimFails = false): Promise<string> {
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

    component.form.setValue({ email: 'user@x.com', password: 'tortoise-violin-42' });
    component.submit();
    httpMock.expectOne('/api/auth/login').flush({ accessToken: 'token' });
    httpMock.expectOne('/api/auth/me').flush({
      id: '1',
      email: 'user@x.com',
      username: 'Player',
      emailVerified: true,
      role: 'USER',
      language: 'EN',
    });
    const claim = httpMock.expectOne('/api/game/guest-plays/claim');
    expect(claim.request.method).toBe('POST');
    if (claimFails) {
      claim.flush(null, { status: 500, statusText: 'Server Error' });
    } else {
      claim.flush({ claimed: 2 });
    }

    return navigateSpy.mock.calls[0][0] as string;
  }

  it('goes back to the page that required logging in', async () => {
    expect(await loginWith('/account')).toBe('/account');
  });

  it('claims the guest games of the browser, and logs in even if that fails', async () => {
    expect(await loginWith('/account', true)).toBe('/account');
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

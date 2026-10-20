import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { Register } from './register';

describe('Register', () => {
  let fixture: ComponentFixture<Register>;
  let component: Register;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Register],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Register);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  function fillForm(email: string, password: string, confirmPassword: string, username = 'Player') {
    component.form.setValue({ email, username, password, confirmPassword });
  }

  it('shows an error when the passwords do not match', () => {
    fillForm('user@x.com', 'tortoise-violin-42', 'different-violin-42');

    component.submit();

    expect(component.errorMessage()).toBe("Passwords don't match.");
    httpMock.expectNone('/api/auth/register');
  });

  it('navigates to check-email on success', () => {
    const navigateSpy = vi.spyOn(router, 'navigate');
    fillForm('user@x.com', 'tortoise-violin-42', 'tortoise-violin-42');

    component.submit();

    const req = httpMock.expectOne('/api/auth/register');
    expect(req.request.body).toEqual({
      email: 'user@x.com',
      username: 'Player',
      password: 'tortoise-violin-42',
    });
    req.flush({ userId: '1', email: 'user@x.com', username: 'Player' });

    expect(navigateSpy).toHaveBeenCalledWith(['/check-email'], {
      queryParams: { email: 'user@x.com' },
    });
  });

  it('shows a duplicate-email message on 409', () => {
    fillForm('taken@x.com', 'tortoise-violin-42', 'tortoise-violin-42');

    component.submit();

    httpMock.expectOne('/api/auth/register').flush(null, { status: 409, statusText: 'Conflict' });

    expect(component.errorMessage()).toBe('An account with this email already exists.');
  });

  it('flags the username field on USERNAME_TAKEN instead of the generic message', () => {
    fillForm('user@x.com', 'tortoise-violin-42', 'tortoise-violin-42', 'Taken');

    component.submit();

    httpMock
      .expectOne('/api/auth/register')
      .flush(
        { errorCode: 'USERNAME_TAKEN', message: 'taken' },
        { status: 409, statusText: 'Conflict' },
      );

    expect(component.usernameTaken()).toBe(true);
    expect(component.errorMessage()).toBeNull();
  });

  it('does not submit a password shorter than 12 characters', () => {
    fillForm('user@x.com', 'elevenchars', 'elevenchars');

    component.submit();

    httpMock.expectNone('/api/auth/register');
  });

  it('shows a refused password under the password field', () => {
    fillForm('user@x.com', 'qwerty123456', 'qwerty123456');

    component.submit();
    httpMock
      .expectOne('/api/auth/register')
      .flush(
        { errorCode: 'PASSWORD_TOO_COMMON', message: 'too common' },
        { status: 400, statusText: 'Bad Request' },
      );

    expect(component.passwordRefusal()).toContain('too common');
    expect(component.errorMessage()).toBeNull();
  });
  it.each(['ab', 'with space', 'at@sign', 'a'.repeat(21)])(
    'does not submit an invalid username (%s)',
    (username) => {
      fillForm('user@x.com', 'tortoise-violin-42', 'tortoise-violin-42', username);

      component.submit();

      httpMock.expectNone('/api/auth/register');
    },
  );
});

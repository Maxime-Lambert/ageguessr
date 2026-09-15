import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
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

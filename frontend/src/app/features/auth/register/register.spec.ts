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

  function fillForm(email: string, password: string, confirmPassword: string) {
    component.form.setValue({ email, password, confirmPassword });
  }

  it('shows an error when the passwords do not match', () => {
    fillForm('user@x.com', 'password123', 'different123');

    component.submit();

    expect(component.errorMessage()).toBe("Passwords don't match.");
    httpMock.expectNone('/api/auth/register');
  });

  it('navigates to check-email on success', () => {
    const navigateSpy = vi.spyOn(router, 'navigate');
    fillForm('user@x.com', 'password123', 'password123');

    component.submit();

    httpMock.expectOne('/api/auth/register').flush({ userId: '1', email: 'user@x.com' });

    expect(navigateSpy).toHaveBeenCalledWith(['/check-email'], {
      queryParams: { email: 'user@x.com' },
    });
  });

  it('shows a duplicate-email message on 409', () => {
    fillForm('taken@x.com', 'password123', 'password123');

    component.submit();

    httpMock.expectOne('/api/auth/register').flush(null, { status: 409, statusText: 'Conflict' });

    expect(component.errorMessage()).toBe('An account with this email already exists.');
  });
});

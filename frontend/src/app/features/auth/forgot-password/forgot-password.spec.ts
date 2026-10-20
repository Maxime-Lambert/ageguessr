import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ForgotPassword } from './forgot-password';

describe('ForgotPassword', () => {
  let component: ForgotPassword;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ForgotPassword],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    component = TestBed.createComponent(ForgotPassword).componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  it('shows the neutral confirmation once the request is accepted', () => {
    component.form.setValue({ email: 'me@x.com' });

    component.submit();
    const req = httpMock.expectOne('/api/auth/forgot-password');
    expect(req.request.body).toEqual({ email: 'me@x.com' });
    req.flush(null);

    expect(component.sentTo()).toBe('me@x.com');
  });

  it('does not submit an invalid email', () => {
    component.form.setValue({ email: 'not-an-email' });

    component.submit();

    httpMock.expectNone('/api/auth/forgot-password');
  });

  it('explains a rate limit', () => {
    component.form.setValue({ email: 'me@x.com' });

    component.submit();
    httpMock
      .expectOne('/api/auth/forgot-password')
      .flush(null, { status: 429, statusText: 'Too Many Requests' });

    expect(component.errorMessage()).toContain('Too many attempts');
    expect(component.sentTo()).toBeNull();
  });
});

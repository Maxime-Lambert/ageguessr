import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { ResetPassword } from './reset-password';

describe('ResetPassword', () => {
  let httpMock: HttpTestingController;

  async function create(token: string | null): Promise<ResetPassword> {
    await TestBed.configureTestingModule({
      imports: [ResetPassword],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(token ? { token } : {}) } },
        },
      ],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    return TestBed.createComponent(ResetPassword).componentInstance;
  }

  it('sets the new password with the token from the link', async () => {
    const component = await create('the-token');
    component.form.setValue({
      newPassword: 'new-tortoise-violin',
      confirmPassword: 'new-tortoise-violin',
    });

    component.submit();
    const req = httpMock.expectOne('/api/auth/reset-password');
    expect(req.request.body).toEqual({ token: 'the-token', newPassword: 'new-tortoise-violin' });
    req.flush(null);

    expect(component.status()).toBe('success');
  });

  it('rejects mismatched passwords without calling the API', async () => {
    const component = await create('the-token');
    component.form.setValue({
      newPassword: 'new-tortoise-violin',
      confirmPassword: 'other-tortoise-violin',
    });

    component.submit();

    httpMock.expectNone('/api/auth/reset-password');
    expect(component.errorMessage()).toBe("Passwords don't match.");
  });

  it('shows the invalid-link state on 404', async () => {
    const component = await create('expired');
    component.form.setValue({
      newPassword: 'new-tortoise-violin',
      confirmPassword: 'new-tortoise-violin',
    });

    component.submit();
    httpMock
      .expectOne('/api/auth/reset-password')
      .flush(null, { status: 404, statusText: 'Not Found' });

    expect(component.status()).toBe('invalid-link');
  });

  it('shows a refused password under the field and stays on the form', async () => {
    const component = await create('the-token');
    component.form.setValue({ newPassword: 'qwerty123456', confirmPassword: 'qwerty123456' });

    component.submit();
    httpMock
      .expectOne('/api/auth/reset-password')
      .flush(
        { errorCode: 'PASSWORD_TOO_COMMON', message: 'too common' },
        { status: 400, statusText: 'Bad Request' },
      );

    expect(component.passwordRefusal()).toContain('too common');
    expect(component.status()).toBe('form');
    expect(component.errorMessage()).toBeNull();
  });
  it('shows the invalid-link state straight away without a token', async () => {
    const component = await create(null);

    expect(component.status()).toBe('invalid-link');
  });
});

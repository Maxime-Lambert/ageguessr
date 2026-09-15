import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { ConfirmEmail } from './confirm-email';

function setup(queryParams: Record<string, string>) {
  return TestBed.configureTestingModule({
    imports: [ConfirmEmail],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) } },
      },
    ],
  }).compileComponents();
}

describe('ConfirmEmail', () => {
  let fixture: ComponentFixture<ConfirmEmail>;
  let component: ConfirmEmail;
  let httpMock: HttpTestingController;

  it('shows success once the token is confirmed', async () => {
    await setup({ token: 'valid-token' });
    fixture = TestBed.createComponent(ConfirmEmail);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    httpMock.expectOne('/api/auth/confirm-email').flush(null);

    expect(component.status()).toBe('success');
  });

  it('shows an error when the token is rejected', async () => {
    await setup({ token: 'expired-token' });
    fixture = TestBed.createComponent(ConfirmEmail);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    httpMock
      .expectOne('/api/auth/confirm-email')
      .flush(null, { status: 404, statusText: 'Not Found' });

    expect(component.status()).toBe('error');
  });

  it('shows an error immediately when there is no token in the URL', async () => {
    await setup({});
    fixture = TestBed.createComponent(ConfirmEmail);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    expect(component.status()).toBe('error');
    httpMock.expectNone('/api/auth/confirm-email');
  });
});

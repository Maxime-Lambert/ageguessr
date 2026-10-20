import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { ConfirmAccountDeletion } from './confirm-account-deletion';

describe('ConfirmAccountDeletion', () => {
  let httpMock: HttpTestingController;

  async function create(token: string | null) {
    await TestBed.configureTestingModule({
      imports: [ConfirmAccountDeletion],
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
    const fixture = TestBed.createComponent(ConfirmAccountDeletion);
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture.componentInstance;
  }

  it('never calls the API just by opening the link', async () => {
    await create('the-token');

    httpMock.expectNone('/api/auth/confirm-account-deletion');
  });

  it('deletes only after the explicit click', async () => {
    const component = await create('the-token');

    component.confirm();
    const req = httpMock.expectOne('/api/auth/confirm-account-deletion');
    expect(req.request.body).toEqual({ token: 'the-token' });
    req.flush(null);

    expect(component.status()).toBe('deleted');
  });

  it('reports an invalid or expired link', async () => {
    const component = await create('expired');

    component.confirm();
    httpMock
      .expectOne('/api/auth/confirm-account-deletion')
      .flush(null, { status: 404, statusText: 'Not Found' });

    expect(component.status()).toBe('invalid-link');
  });

  it('shows the invalid-link state without a token', async () => {
    const component = await create(null);

    expect(component.status()).toBe('invalid-link');
  });
});

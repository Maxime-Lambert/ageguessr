import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { DisableTwoFactor } from './disable-two-factor';

describe('DisableTwoFactor', () => {
  let httpMock: HttpTestingController;

  async function create(token: string | null) {
    await TestBed.configureTestingModule({
      imports: [DisableTwoFactor],
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
    const fixture = TestBed.createComponent(DisableTwoFactor);
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture.componentInstance;
  }

  afterEach(() => httpMock.verify());

  it('never calls the API just by opening the link', async () => {
    await create('the-token');

    httpMock.expectNone('/api/auth/disable-two-factor');
  });

  it('turns two-factor off only after the explicit click', async () => {
    const component = await create('the-token');

    component.confirm();
    const request = httpMock.expectOne('/api/auth/disable-two-factor');
    expect(request.request.body).toEqual({ token: 'the-token' });
    request.flush(null, { status: 204, statusText: 'No Content' });

    expect(component.status()).toBe('disabled');
  });

  it('says the link no longer works when refused, or when it has no token', async () => {
    const component = await create('used');
    component.confirm();
    httpMock
      .expectOne('/api/auth/disable-two-factor')
      .flush(null, { status: 404, statusText: 'Not Found' });
    expect(component.status()).toBe('invalid-link');

    TestBed.resetTestingModule();
    expect((await create(null)).status()).toBe('invalid-link');
  });
});

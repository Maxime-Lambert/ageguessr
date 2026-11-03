import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';
import { BROWSER_PAGE } from './features/i18n/site-language';
import { fakePage } from './features/i18n/testing';

describe('App', () => {
  let httpMock: HttpTestingController;
  let page: ReturnType<typeof fakePage>;

  beforeEach(async () => {
    page = fakePage();
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: BROWSER_PAGE, useValue: page },
      ],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    // The constructor silently tries to restore a session via /api/auth/refresh -
    // respond so the pending request doesn't fail httpMock.verify() in other suites,
    // and it's irrelevant to what these tests assert.
    httpMock
      .match('/api/auth/refresh')
      .forEach((req) => req.flush(null, { status: 401, statusText: 'Unauthorized' }));
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render the wordmark', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('header a')?.textContent).toContain('Ageguessr');
  });

  it('loads the site in the language of the restored account', async () => {
    const fixture = TestBed.createComponent(App);
    httpMock.expectOne('/api/auth/refresh').flush({ accessToken: 'token' });
    httpMock.expectOne('/api/auth/me').flush({
      id: '1',
      email: 'me@x.com',
      username: 'Player',
      emailVerified: true,
      role: 'USER',
      language: 'FR',
    });
    await fixture.whenStable();

    // The specs run in the source language, English.
    expect(page.loaded).toEqual(['/fr/']);
  });
});

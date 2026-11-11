import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, LOCALE_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Auth } from '../auth/auth';
import { AuthUser } from '../auth/models';
import { BROWSER_PAGE, SiteLanguageService } from './site-language';
import { fakePage } from './testing';

@Component({ template: '' })
class Blank {}

const USER: AuthUser = {
  id: '1',
  email: 'me@x.com',
  username: 'Player',
  emailVerified: true,
  termsAccepted: true,
  role: 'USER',
  language: 'EN',
};

describe('SiteLanguageService', () => {
  let page: ReturnType<typeof fakePage>;
  let http: HttpTestingController;

  async function setUp(locale: string, url = '/account?tab=1#top'): Promise<SiteLanguageService> {
    page = fakePage(`/${locale}${url}`);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: '**', component: Blank }]),
        { provide: LOCALE_ID, useValue: locale },
        { provide: BROWSER_PAGE, useValue: page },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    await TestBed.inject(Router).navigateByUrl(url);
    return TestBed.inject(SiteLanguageService);
  }

  afterEach(() => http.verify());

  it('knows the language of the build and the other one', async () => {
    const french = await setUp('fr');

    expect(french.current).toBe('fr');
    expect(french.other).toBe('en');
  });

  it('points to the same page, query and fragment in another language', async () => {
    const language = await setUp('en');

    expect(language.urlIn('fr')).toBe('/fr/account?tab=1#top');
  });

  it('points to the home page of another language', async () => {
    const language = await setUp('en', '/');

    expect(language.urlIn('fr')).toBe('/fr/');
  });

  it('switches a guest at once, remembering the choice', async () => {
    const language = await setUp('en');

    language.switchTo('fr');

    expect(page.cookies).toEqual([expect.stringMatching(/^ageguessr_lang=fr;.*Secure$/)]);
    expect(page.loaded).toEqual(['/fr/account?tab=1#top']);
  });

  it('saves the language on the account before switching', async () => {
    const language = await setUp('en');
    TestBed.inject(Auth).currentUser.set(USER);

    language.switchTo('fr');
    expect(page.loaded).toEqual([]);
    const req = http.expectOne('/api/auth/me/language');
    expect(req.request.body).toEqual({ language: 'FR' });
    req.flush({ ...USER, language: 'FR' });

    expect(page.loaded).toEqual(['/fr/account?tab=1#top']);
  });

  it('stays on the page when the account could not be saved', async () => {
    const language = await setUp('en');
    TestBed.inject(Auth).currentUser.set(USER);

    language.switchTo('fr');
    http.expectOne('/api/auth/me/language').flush(null, { status: 500, statusText: 'Error' });

    expect(page.loaded).toEqual([]);
    expect(page.cookies).toEqual([]);
    expect(language.error()).toBe(true);
  });

  it("loads the page in the account's language when it differs", async () => {
    const language = await setUp('en');

    expect(language.alignWith({ ...USER, language: 'FR' })).toBeNull();

    expect(page.loaded).toEqual(['/fr/account?tab=1#top']);
    expect(page.cookies).toEqual([expect.stringContaining('ageguessr_lang=fr')]);
  });

  it('keeps the page the browser asked for while the first navigation waits on the session', async () => {
    const language = await setUp('en', '/');
    // A link from an email: the admin guard holds the navigation, Router.url is still "/".
    page = Object.assign(page, { location: () => '/en/admin/curation?x=1#top' });

    language.alignWith({ ...USER, language: 'FR' });

    expect(page.loaded).toEqual(['/fr/admin/curation?x=1#top']);
  });

  it("loads the home page of the account's language from a bare prefix", async () => {
    const language = await setUp('en', '/');
    page = Object.assign(page, { location: () => '/en' });

    language.alignWith({ ...USER, language: 'FR' });

    expect(page.loaded).toEqual(['/fr/']);
  });

  it("stays when the page is already in the account's language", async () => {
    const language = await setUp('fr');

    expect(language.alignWith({ ...USER, language: 'FR' })).toBeNull();

    expect(page.loaded).toEqual([]);
  });

  it('saves the language of the page on an account that never chose one', async () => {
    const language = await setUp('fr');

    language.alignWith({ ...USER, language: null })!.subscribe();

    const req = http.expectOne('/api/auth/me/language');
    expect(req.request.body).toEqual({ language: 'FR' });
    req.flush({ ...USER, language: 'FR' });
    expect(page.loaded).toEqual([]);
  });
});

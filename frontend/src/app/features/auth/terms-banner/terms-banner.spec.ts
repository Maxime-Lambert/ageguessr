import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TERMS_VERSION } from '../../legal/legal-dates';
import { Auth } from '../auth';
import { AuthUser } from '../models';
import { TermsBanner } from './terms-banner';

const USER: AuthUser = {
  id: '1',
  email: 'me@x.com',
  username: 'Player',
  emailVerified: true,
  termsAccepted: false,
  role: 'USER',
  language: 'EN',
};

describe('TermsBanner', () => {
  let fixture: ComponentFixture<TermsBanner>;
  let http: HttpTestingController;
  let auth: Auth;
  let page: HTMLElement;

  function create(user: AuthUser | null): void {
    auth.currentUser.set(user);
    fixture = TestBed.createComponent(TermsBanner);
    page = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  }

  function button(text: string): HTMLButtonElement {
    return Array.from(page.querySelectorAll('button')).find((b) => b.textContent?.trim() === text)!;
  }

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(Auth);
  });

  afterEach(() => http.verify());

  it('asks an account on older Terms to accept the new ones, with links to read them', () => {
    create(USER);

    expect(page.textContent).toContain('Our Terms of Use changed on 3 November 2026.');
    const links = Array.from(page.querySelectorAll('a')).map((a) => a.getAttribute('href'));
    expect(links).toEqual(['/terms', '/account']);
  });

  it('stays hidden for a guest and for an account up to date', () => {
    create(null);
    expect(page.querySelector('section')).toBeNull();

    auth.currentUser.set({ ...USER, termsAccepted: true });
    fixture.detectChanges();
    expect(page.querySelector('section')).toBeNull();
  });

  it('accepts the version shown and goes away', () => {
    create(USER);

    button('Accept').click();
    const req = http.expectOne('/api/auth/me/terms');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ version: TERMS_VERSION });
    req.flush({ ...USER, termsAccepted: true });
    fixture.detectChanges();

    expect(page.querySelector('section')).toBeNull();
  });

  it('reads the profile again when the Terms changed since the page was loaded', () => {
    create(USER);

    button('Accept').click();
    http
      .expectOne('/api/auth/me/terms')
      .flush(
        { errorCode: 'TERMS_VERSION_OUTDATED', message: 'x' },
        { status: 409, statusText: 'Conflict' },
      );
    http.expectOne('/api/auth/me').flush(USER);
    fixture.detectChanges();

    expect(page.querySelector('section')).not.toBeNull();
    expect(page.querySelector('[role=alert]')).toBeNull();
  });

  it('says so when the acceptance fails', () => {
    create(USER);

    button('Accept').click();
    http.expectOne('/api/auth/me/terms').flush(null, { status: 500, statusText: 'Error' });
    fixture.detectChanges();

    expect(page.querySelector('[role=alert]')?.textContent).toContain('Something went wrong');
    expect(button('Accept').disabled).toBe(false);
  });

  it('hides until the tab is closed on "Later", then asks again', () => {
    create(USER);

    button('Later').click();
    fixture.detectChanges();
    expect(page.querySelector('section')).toBeNull();

    // A new page in the same tab remembers it.
    create(USER);
    expect(page.querySelector('section')).toBeNull();

    // A new tab does not.
    sessionStorage.clear();
    create(USER);
    expect(page.querySelector('section')).not.toBeNull();
  });
});

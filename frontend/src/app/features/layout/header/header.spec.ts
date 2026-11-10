import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { Auth } from '../../auth/auth';
import { AuthUser } from '../../auth/models';
import { LIGHT_SCHEME_QUERY } from '../../theme/theme';
import { Header } from './header';

@Component({ template: '' })
class Blank {}

const PLAYER: AuthUser = {
  id: '1',
  email: 'me@x.com',
  username: 'Player',
  emailVerified: true,
  termsAccepted: true,
  role: 'USER',
  language: 'EN',
};

describe('Header', () => {
  let fixture: ComponentFixture<Header>;
  let auth: Auth;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Header],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: '**', component: Blank }]),
        { provide: LIGHT_SCHEME_QUERY, useValue: null },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Header);
    auth = TestBed.inject(Auth);
    httpMock = TestBed.inject(HttpTestingController);
    document.body.appendChild(fixture.nativeElement);
  });

  afterEach(() => document.body.replaceChildren());

  const header = () => {
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  };
  const named = (root: ParentNode, selector: string, name: string) =>
    Array.from(root.querySelectorAll<HTMLElement>(selector)).find(
      (el) => (el.getAttribute('aria-label') ?? el.textContent)?.trim() === name,
    );
  const texts = (root: ParentNode, selector: string) =>
    Array.from(root.querySelectorAll(selector)).map((el) => el.textContent?.trim());
  const signIn = (user: AuthUser) => {
    auth.sessionChecked.set(true);
    auth.currentUser.set(user);
  };

  it('links to both game modes, in the game vocabulary', () => {
    expect(texts(header(), 'nav a')).toEqual(["Today's file", 'Free investigation']);
  });

  it('shows nothing account-related until the session restore has settled', () => {
    const page = header();

    expect(named(page, 'a', 'Log in')).toBeUndefined();
    expect(named(page, 'button', 'Log out')).toBeUndefined();
    expect(named(page, 'button', 'Preferences · EN')).toBeDefined();
  });

  it('offers log in, sign up and the preferences to a guest', () => {
    auth.sessionChecked.set(true);
    const page = header();

    expect(named(page, 'a', 'Log in')?.getAttribute('href')).toBe('/login');
    expect(named(page, 'a', 'Sign up')?.getAttribute('href')).toBe('/register');
    expect(named(page, 'button', 'Preferences · EN')?.getAttribute('aria-expanded')).toBe('false');
  });

  it('gathers the account, the preferences and log out behind the username', () => {
    signIn(PLAYER);
    const page = header();
    const trigger = page.querySelector<HTMLButtonElement>('app-dropdown > button')!;

    expect(named(page, 'a', 'Log in')).toBeUndefined();
    expect(named(page, 'button', 'Preferences · EN')).toBeUndefined();
    expect(trigger.textContent).toContain('Player');

    trigger.click();
    header();

    expect(trigger.getAttribute('aria-expanded')).toBe('true');
    expect(named(page, 'a', 'My profile')?.getAttribute('href')).toBe('/profile');
    expect(named(page, 'a', 'My account')?.getAttribute('href')).toBe('/account');
    expect(named(page, 'button', 'Log out')).toBeDefined();
    expect(
      page.querySelector('app-dropdown [role="radiogroup"][aria-label="Theme"]'),
    ).not.toBeNull();
    // Never the email (docs/decisions/architecture.md, privacy policy).
    expect(page.textContent).not.toContain(PLAYER.email);
  });

  it('shows the admin pages to an admin only', () => {
    signIn(PLAYER);
    expect(texts(header(), 'app-dropdown a')).not.toContain('Curationadmin');

    signIn({ ...PLAYER, role: 'ADMIN' });
    const links = Array.from(header().querySelectorAll('app-dropdown a:not([hreflang])')).map((a) =>
      a.getAttribute('href'),
    );

    expect(links).toEqual(['/profile', '/friends', '/account', '/admin/curation', '/admin/hints']);
  });

  it('shows how many friend requests wait for an answer on the Friends link', () => {
    signIn(PLAYER);
    header();
    httpMock.expectOne('/api/friends').flush({
      friends: [],
      incoming: [{ id: 'r1', username: 'watson', since: '' }],
      outgoing: [],
    });
    const page = header();
    page.querySelector<HTMLButtonElement>('app-dropdown > button')!.click();
    header();

    const link = page.querySelector('app-dropdown a[href="/friends"]')!;
    expect(link.textContent).toContain('Friends');
    expect(link.textContent).toContain('1 requests waiting for an answer');
  });

  it('shows no count without requests, and asks nothing for a guest', () => {
    auth.sessionChecked.set(true);
    header();
    httpMock.expectNone('/api/friends');

    signIn(PLAYER);
    header();
    httpMock.expectOne('/api/friends').flush({ friends: [], incoming: [], outgoing: [] });
    const page = header();
    page.querySelector<HTMLButtonElement>('app-dropdown > button')!.click();
    header();

    expect(page.querySelector('app-dropdown a[href="/friends"]')!.textContent).not.toContain(
      'waiting',
    );
  });

  it('logging out returns to the home page as a guest', () => {
    const navigateSpy = vi.spyOn(TestBed.inject(Router), 'navigate');
    signIn(PLAYER);

    fixture.componentInstance.logout();
    httpMock.expectOne('/api/auth/logout').flush(null);

    expect(auth.currentUser()).toBeNull();
    expect(navigateSpy).toHaveBeenCalledWith(['/']);
  });

  describe('phone menu', () => {
    const dialog = () => document.querySelector<HTMLElement>('[role="dialog"]');

    function open() {
      named(header(), 'button', 'Menu')!.click();
      header();
      return dialog()!;
    }

    it('opens a named panel with the game modes and the preferences', () => {
      auth.sessionChecked.set(true);
      const panel = open();

      expect(panel.getAttribute('aria-label')).toBe('Menu');
      expect(texts(panel, 'nav a')).toEqual(["Today's file", 'Free investigation']);
      expect(named(panel, 'a', 'Sign up')).toBeDefined();
      expect(panel.querySelector('app-preferences')).not.toBeNull();
    });

    it('offers the account and log out to a signed-in player', () => {
      signIn(PLAYER);
      const panel = open();

      expect(named(panel, 'a', 'My account')).toBeDefined();
      expect(named(panel, 'button', 'Log out')).toBeDefined();
      expect(named(panel, 'a', 'Log in')).toBeUndefined();
    });

    it('closes with its button and on navigation', async () => {
      open();
      named(dialog()!, 'button', 'Close menu')!.click();
      await fixture.whenStable();
      expect(dialog()).toBeNull();

      open();
      await TestBed.inject(Router).navigateByUrl('/free');
      await fixture.whenStable();
      expect(dialog()).toBeNull();
    });
  });
});

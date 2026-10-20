import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { Auth } from '../../auth/auth';
import { Header } from './header';

describe('Header', () => {
  let fixture: ComponentFixture<Header>;
  let auth: Auth;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Header],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Header);
    auth = TestBed.inject(Auth);
    httpMock = TestBed.inject(HttpTestingController);
  });

  function links(): string[] {
    fixture.detectChanges();
    return Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('nav a, nav button'),
    ).map((el) => el.textContent?.trim() ?? '');
  }

  it('shows nothing account-related until the session restore has settled', () => {
    expect(links()).not.toContain('Log in');
    expect(links()).not.toContain('Log out');
  });

  it('offers log in and sign up to a guest', () => {
    auth.sessionChecked.set(true);

    expect(links()).toEqual(expect.arrayContaining(['Log in', 'Sign up']));
  });

  it('shows the username and a log out button once logged in', () => {
    auth.sessionChecked.set(true);
    auth.currentUser.set({ id: '1', email: 'me@x.com', username: 'Player', emailVerified: true });

    expect(links()).toEqual(expect.arrayContaining(['Player', 'Log out']));
    expect(links()).not.toContain('Log in');
  });

  it('logging out returns to the home page as a guest', () => {
    const navigateSpy = vi.spyOn(TestBed.inject(Router), 'navigate');
    auth.sessionChecked.set(true);
    auth.currentUser.set({ id: '1', email: 'me@x.com', username: 'Player', emailVerified: true });

    fixture.componentInstance.logout();
    httpMock.expectOne('/api/auth/logout').flush(null);

    expect(auth.currentUser()).toBeNull();
    expect(navigateSpy).toHaveBeenCalledWith(['/']);
  });
});

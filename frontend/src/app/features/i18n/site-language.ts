import { DOCUMENT } from '@angular/common';
import { InjectionToken, LOCALE_ID, Service, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { Observable, filter, map } from 'rxjs';
import { Auth } from '../auth/auth';
import { AuthUser } from '../auth/models';
import {
  SiteLanguage,
  fromApi,
  languageCookie,
  siteLanguageOf,
  toApi,
} from '../../shared/i18n/language';

/** The real page, behind a token so that the specs never leave theirs. */
export interface BrowserPage {
  secure(): boolean;
  setCookie(cookie: string): void;
  load(url: string): void;
  /** Path, query and fragment of the page as the browser shows it, the prefix included. */
  location(): string;
}

export const BROWSER_PAGE = new InjectionToken<BrowserPage>('BrowserPage', {
  factory: () => {
    const document = inject(DOCUMENT);
    return {
      secure: () => document.location.protocol === 'https:',
      setCookie: (cookie) => (document.cookie = cookie),
      load: (url) => document.location.assign(url),
      location: () =>
        document.location.pathname + document.location.search + document.location.hash,
    };
  },
});

/**
 * The language of the site. Each language is its own build under its own prefix, so
 * switching means loading the same page under the other prefix. A signed-in player's
 * language lives on the account, and the account wins (plans/active-plan.md).
 */
@Service()
export class SiteLanguageService {
  private readonly auth = inject(Auth);
  private readonly router = inject(Router);
  private readonly page = inject(BROWSER_PAGE);

  /** The language of this build. */
  readonly current: SiteLanguage = siteLanguageOf(inject(LOCALE_ID));
  readonly other: SiteLanguage = this.current === 'en' ? 'fr' : 'en';
  /** Set when the account could not be updated: the page then stays as it is. */
  readonly error = signal(false);

  /**
   * Router.url as a signal: Router.url alone is not one, so a template reading it (the
   * link of the switch) would keep the URL of its first render.
   */
  private readonly url = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map(() => this.router.url),
    ),
    { initialValue: this.router.url },
  );

  /**
   * The page being shown, in a language: same path, query and fragment. Router.url
   * leaves the prefix out (it is the base href of the build).
   */
  urlIn(language: SiteLanguage): string {
    return `/${language}${this.url()}`;
  }

  /**
   * Saves the choice on the account first when signed in: if that failed, the account's
   * language would send the player back on the next load, so the page does not change.
   */
  switchTo(language: SiteLanguage): void {
    this.error.set(false);
    if (!this.auth.isAuthenticated()) {
      this.load(language);
      return;
    }
    this.auth.changeLanguage(toApi(language)).subscribe({
      next: () => this.load(language),
      error: () => this.error.set(true),
    });
  }

  /**
   * Once the session is known: a language never chosen becomes the page's (it came from
   * the browser), and a different one reloads the page in the account's language.
   *
   * The page comes from the browser, not the router: the session is restored before the
   * first navigation ends (a guard such as the admin pages' waits for it), so Router.url
   * would still be "/" and a link from an email would land on the home page.
   */
  alignWith(user: AuthUser): Observable<void> | null {
    if (user.language === null) {
      return this.auth.changeLanguage(toApi(this.current)).pipe(map(() => undefined));
    }
    const accountLanguage = fromApi(user.language);
    if (accountLanguage !== this.current) {
      const prefix = new RegExp(`^/${this.current}(?=[/?#]|$)`);
      const rest = this.page.location().replace(prefix, '') || '/';
      this.page.setCookie(languageCookie(accountLanguage, this.page.secure()));
      this.page.load(`/${accountLanguage}${rest}`);
    }
    return null;
  }

  private load(language: SiteLanguage): void {
    // Before leaving: Caddy reads it for the next visit to "/".
    this.page.setCookie(languageCookie(language, this.page.secure()));
    this.page.load(this.urlIn(language));
  }
}

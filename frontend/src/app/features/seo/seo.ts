import { DOCUMENT } from '@angular/common';
import { LOCALE_ID, Service, inject } from '@angular/core';
import { Meta } from '@angular/platform-browser';
import { ActivatedRouteSnapshot, NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';
import { siteLanguageOf } from '../../shared/i18n/language';
import { SEO_TEXT } from './seo-texts';

/** What a route tells search engines, in its `data`. */
export interface SeoData {
  /** The page's own description; the site's otherwise. */
  description?: string;
  /** A private page (account, sign-in, a shared challenge): never indexed. */
  noindex?: boolean;
}

const LANGUAGES = ['en', 'fr'] as const;

/**
 * The tags search engines read on each page, set at every navigation: the canonical URL
 * (this language, no query), the same page in each language and the unprefixed one Caddy
 * sends by the visitor's language (x-default), the description, and noindex on private
 * pages. Only for the crawlers that run the JavaScript (Google): the link previews read
 * the tags scripts/seo-postbuild.mjs writes into index.html.
 */
@Service()
export class Seo {
  private readonly router = inject(Router);
  private readonly meta = inject(Meta);
  private readonly document = inject(DOCUMENT);
  private readonly language = siteLanguageOf(inject(LOCALE_ID));

  start(): void {
    this.router.events
      .pipe(filter((event) => event instanceof NavigationEnd))
      .subscribe(() => this.update());
  }

  private update(): void {
    const data = this.deepest(this.router.routerState.snapshot.root).data as SeoData;
    // Router.url leaves the language out (the base href) and keeps the query and fragment.
    const path = this.router.url.split(/[?#]/)[0];
    const origin = this.document.location.origin;

    this.link('canonical', `${origin}/${this.language}${path}`);
    for (const language of LANGUAGES) {
      this.link('alternate', `${origin}/${language}${path}`, language);
    }
    this.link('alternate', `${origin}${path}`, 'x-default');

    this.meta.updateTag({ name: 'description', content: data.description ?? SEO_TEXT.description });
    if (data.noindex) {
      this.meta.updateTag({ name: 'robots', content: 'noindex' });
    } else {
      this.meta.removeTag('name="robots"');
    }
  }

  private deepest(route: ActivatedRouteSnapshot): ActivatedRouteSnapshot {
    return route.firstChild ? this.deepest(route.firstChild) : route;
  }

  /** One link per rel (and hreflang): created once, then updated. */
  private link(rel: string, href: string, hreflang?: string): void {
    const selector = `link[rel="${rel}"]` + (hreflang ? `[hreflang="${hreflang}"]` : '');
    let link = this.document.head.querySelector<HTMLLinkElement>(selector);
    if (!link) {
      link = this.document.createElement('link');
      link.rel = rel;
      if (hreflang) {
        link.hreflang = hreflang;
      }
      this.document.head.appendChild(link);
    }
    link.href = href;
  }
}

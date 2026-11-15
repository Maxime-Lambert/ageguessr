import { Component, LOCALE_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { DOCUMENT } from '@angular/common';
import { Router, provideRouter } from '@angular/router';
import { Seo, SeoData } from './seo';
import { SEO_TEXT } from './seo-texts';

@Component({ template: '' })
class Blank {}

const page = (path: string, data: SeoData = {}) => ({ path, component: Blank, data });

describe('Seo', () => {
  let router: Router;
  let head: HTMLHeadElement;
  const origin = () => TestBed.inject(DOCUMENT).location.origin;

  function setUp(locale: string): void {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          page(''),
          page('how-to-play', { description: 'How it works.' }),
          page('account', { noindex: true }),
          { path: 'parent', children: [page('child', { description: 'Deep.' })] },
        ]),
        { provide: LOCALE_ID, useValue: locale },
      ],
    });
    head = TestBed.inject(DOCUMENT).head;
    TestBed.inject(Seo).start();
    router = TestBed.inject(Router);
  }

  const href = (selector: string) =>
    head.querySelector<HTMLLinkElement>(`link[${selector}]`)?.getAttribute('href');
  const meta = (name: string) =>
    head.querySelector<HTMLMetaElement>(`meta[name="${name}"]`)?.getAttribute('content');

  afterEach(() => head.querySelectorAll('link[rel], meta[name]').forEach((tag) => tag.remove()));

  it('points the canonical at this language, the query and fragment left out', async () => {
    setUp('fr');
    await router.navigateByUrl('/how-to-play?from=share#rules');

    expect(href('rel="canonical"')).toBe(`${origin()}/fr/how-to-play`);
  });

  it('lists the page in each language, and unprefixed for the others', async () => {
    setUp('en');
    await router.navigateByUrl('/how-to-play');

    expect(href('hreflang="en"')).toBe(`${origin()}/en/how-to-play`);
    expect(href('hreflang="fr"')).toBe(`${origin()}/fr/how-to-play`);
    expect(href('hreflang="x-default"')).toBe(`${origin()}/how-to-play`);
  });

  it("keeps the home page's trailing slash", async () => {
    setUp('en');
    await router.navigateByUrl('/');

    expect(href('rel="canonical"')).toBe(`${origin()}/en/`);
    expect(href('hreflang="x-default"')).toBe(`${origin()}/`);
  });

  it("describes a page by its own text, else by the site's", async () => {
    setUp('en');
    await router.navigateByUrl('/how-to-play');
    expect(meta('description')).toBe('How it works.');

    await router.navigateByUrl('/');
    expect(meta('description')).toBe(SEO_TEXT.description);

    await router.navigateByUrl('/parent/child');
    expect(meta('description')).toBe('Deep.');
  });

  it('keeps private pages out of the index, and only them', async () => {
    setUp('en');
    await router.navigateByUrl('/account');
    expect(meta('robots')).toBe('noindex');

    await router.navigateByUrl('/how-to-play');
    expect(meta('robots')).toBeUndefined();
  });

  it('updates its tags rather than piling them up', async () => {
    setUp('en');
    for (const url of ['/', '/how-to-play', '/account', '/']) {
      await router.navigateByUrl(url);
    }

    expect(head.querySelectorAll('link[rel="canonical"]')).toHaveLength(1);
    expect(head.querySelectorAll('link[rel="alternate"]')).toHaveLength(3);
    expect(head.querySelectorAll('meta[name="description"]')).toHaveLength(1);
    expect(href('rel="canonical"')).toBe(`${origin()}/en/`);
  });
});

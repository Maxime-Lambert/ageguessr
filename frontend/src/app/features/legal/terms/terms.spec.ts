import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { LOCALE_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Terms } from './terms';

describe('Terms', () => {
  /** "?lang=fr" stands for the French build: the language comes from LOCALE_ID. */
  async function render(url: string): Promise<HTMLElement> {
    const [path, query] = url.split('?');
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'terms', component: Terms }]),
        { provide: LOCALE_ID, useValue: query === 'lang=fr' ? 'fr' : 'en' },
      ],
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(path);
    return harness.routeNativeElement as HTMLElement;
  }

  it('is in English by default', async () => {
    const page = await render('/terms');

    expect(page.querySelector('h1')?.textContent).toBe('Terms of Use');
    expect(page.querySelector('article')?.hasAttribute('lang')).toBe(false);
  });

  it('is in French in the French build, marked as such for screen readers', async () => {
    const page = await render('/terms?lang=fr');

    expect(page.querySelector('h1')?.textContent).toBe("Conditions générales d'utilisation");
    expect(page.querySelector('article')?.getAttribute('lang')).toBe('fr');
  });

  it('falls back to English for an unknown language', async () => {
    const page = await render('/terms?lang=xx');

    expect(page.querySelector('h1')?.textContent).toBe('Terms of Use');
  });
});

describe('Terms version', () => {
  async function updatedLine(locale: string): Promise<string | undefined> {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'terms', component: Terms }]),
        { provide: LOCALE_ID, useValue: locale },
      ],
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/terms');
    return harness.routeNativeElement?.textContent ?? undefined;
  }

  it('shows TERMS_VERSION as the last update date, in English', async () => {
    expect(await updatedLine('en')).toContain('Last updated: 21 October 2026');
  });

  it('shows TERMS_VERSION as the last update date, in French', async () => {
    expect(await updatedLine('fr')).toContain('Dernière mise à jour : 21 octobre 2026');
  });
});

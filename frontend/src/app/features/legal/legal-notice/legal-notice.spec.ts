import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { LOCALE_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { LegalNotice } from './legal-notice';

describe('LegalNotice', () => {
  /** "?lang=fr" stands for the French build: the language comes from LOCALE_ID. */
  async function render(url: string): Promise<HTMLElement> {
    const [path, query] = url.split('?');
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'legal-notice', component: LegalNotice }]),
        { provide: LOCALE_ID, useValue: query === 'lang=fr' ? 'fr' : 'en' },
      ],
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(path);
    return harness.routeNativeElement as HTMLElement;
  }

  it('is in English by default', async () => {
    const page = await render('/legal-notice');

    expect(page.querySelector('h1')?.textContent).toBe('Legal notice');
    expect(page.querySelector('article')?.hasAttribute('lang')).toBe(false);
  });

  it('is in French in the French build, marked as such for screen readers', async () => {
    const page = await render('/legal-notice?lang=fr');

    expect(page.querySelector('h1')?.textContent).toBe('Mentions légales');
    expect(page.querySelector('article')?.getAttribute('lang')).toBe('fr');
  });

  it('falls back to English for an unknown language', async () => {
    const page = await render('/legal-notice?lang=xx');

    expect(page.querySelector('h1')?.textContent).toBe('Legal notice');
  });
});

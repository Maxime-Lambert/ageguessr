import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { LegalNotice } from './legal-notice';

describe('LegalNotice', () => {
  async function render(url: string): Promise<HTMLElement> {
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: 'legal-notice', component: LegalNotice }])],
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    return harness.routeNativeElement as HTMLElement;
  }

  it('is in English by default', async () => {
    const page = await render('/legal-notice');

    expect(page.querySelector('h1')?.textContent).toBe('Legal notice');
    expect(page.querySelector('article')?.hasAttribute('lang')).toBe(false);
  });

  it('is in French with ?lang=fr, marked as such for screen readers', async () => {
    const page = await render('/legal-notice?lang=fr');

    expect(page.querySelector('h1')?.textContent).toBe('Mentions légales');
    expect(page.querySelector('article')?.getAttribute('lang')).toBe('fr');
  });

  it('falls back to English for an unknown language', async () => {
    const page = await render('/legal-notice?lang=xx');

    expect(page.querySelector('h1')?.textContent).toBe('Legal notice');
  });
});

import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Terms } from './terms';

describe('Terms', () => {
  async function render(url: string): Promise<HTMLElement> {
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: 'terms', component: Terms }])],
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    return harness.routeNativeElement as HTMLElement;
  }

  it('is in English by default', async () => {
    const page = await render('/terms');

    expect(page.querySelector('h1')?.textContent).toBe('Terms of Use');
    expect(page.querySelector('article')?.hasAttribute('lang')).toBe(false);
  });

  it('is in French with ?lang=fr, marked as such for screen readers', async () => {
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
  it('shows TERMS_VERSION as the last update date, in both languages', async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: 'terms', component: Terms }])],
    });
    const harness = await RouterTestingHarness.create();

    await harness.navigateByUrl('/terms');
    expect(harness.routeNativeElement?.textContent).toContain('Last updated: 21 October 2026');

    await harness.navigateByUrl('/terms?lang=fr');
    expect(harness.routeNativeElement?.textContent).toContain(
      'Dernière mise à jour : 21 octobre 2026',
    );
  });
});

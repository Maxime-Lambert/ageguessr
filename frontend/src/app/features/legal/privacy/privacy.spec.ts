import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Privacy } from './privacy';

describe('Privacy', () => {
  async function render(url: string): Promise<HTMLElement> {
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: 'privacy', component: Privacy }])],
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    return harness.routeNativeElement as HTMLElement;
  }

  it('is in English by default', async () => {
    const page = await render('/privacy');

    expect(page.querySelector('h1')?.textContent).toBe('Privacy Policy');
    expect(page.querySelector('article')?.hasAttribute('lang')).toBe(false);
  });

  it('is in French with ?lang=fr, marked as such for screen readers', async () => {
    const page = await render('/privacy?lang=fr');

    expect(page.querySelector('h1')?.textContent).toBe('Politique de confidentialité');
    expect(page.querySelector('article')?.getAttribute('lang')).toBe('fr');
  });

  it('tells public figures shown in the game how to be removed, in both languages', async () => {
    const english = await render('/privacy');
    const englishSection = [...english.querySelectorAll('h2')].find(
      (h) => h.textContent === '8. People shown in the game',
    );
    expect(englishSection).toBeDefined();
    expect(englishSection?.nextElementSibling?.nextElementSibling?.textContent).toContain(
      'contact@ageguessr.app',
    );

    TestBed.resetTestingModule();
    const french = await render('/privacy?lang=fr');
    expect(
      [...french.querySelectorAll('h2')].some(
        (h) => h.textContent === '8. Personnes présentées dans le jeu',
      ),
    ).toBe(true);
  });

  it('falls back to English for an unknown language', async () => {
    const page = await render('/privacy?lang=xx');

    expect(page.querySelector('h1')?.textContent).toBe('Privacy Policy');
  });
});

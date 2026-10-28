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
    expect(sectionText(englishSection)).toContain('contact@ageguessr.app');

    TestBed.resetTestingModule();
    const french = await render('/privacy?lang=fr');
    expect(
      [...french.querySelectorAll('h2')].some(
        (h) => h.textContent === '8. Personnes présentées dans le jeu',
      ),
    ).toBe(true);
  });

  it('says that the editorial line leaves people out without keeping why, in both languages', async () => {
    const english = await render('/privacy');
    expect(english.textContent?.replace(/\s+/g, ' ')).toContain(
      'we only note that the person was left out under our editorial line, without the reason.',
    );

    TestBed.resetTestingModule();
    const french = await render('/privacy?lang=fr');
    expect(french.textContent?.replace(/\s+/g, ' ')).toContain(
      'nous notons seulement que la personne a été écartée au titre de notre ligne éditoriale, sans le motif.',
    );
  });

  it('promises no photo of a minor, in both languages', async () => {
    const english = await render('/privacy');
    expect(english.textContent?.replace(/\s+/g, ' ')).toContain(
      'We never show a photo taken before the person turned 18, even if they are an adult today.',
    );

    TestBed.resetTestingModule();
    const french = await render('/privacy?lang=fr');
    expect(french.textContent?.replace(/\s+/g, ' ')).toContain(
      'Nous ne montrons jamais une photo prise avant les 18 ans de la personne',
    );
  });

  it('says that gender and continent serve only to balance the selection, in both languages', async () => {
    const english = await render('/privacy');
    expect(english.textContent?.replace(/\s+/g, ' ')).toContain(
      'gender and continent serve only to balance the selection between women and men and between continents',
    );

    TestBed.resetTestingModule();
    const french = await render('/privacy?lang=fr');
    expect(french.textContent?.replace(/\s+/g, ' ')).toContain(
      'le genre et le continent servent uniquement à équilibrer la sélection',
    );
  });

  it('falls back to English for an unknown language', async () => {
    const page = await render('/privacy?lang=xx');

    expect(page.querySelector('h1')?.textContent).toBe('Privacy Policy');
  });
});

/** The text of a section: everything between its heading and the next one. */
function sectionText(heading: Element | undefined): string {
  let text = '';
  for (
    let node = heading?.nextElementSibling;
    node && node.tagName !== 'H2';
    node = node.nextElementSibling
  ) {
    text += node.textContent;
  }
  return text;
}

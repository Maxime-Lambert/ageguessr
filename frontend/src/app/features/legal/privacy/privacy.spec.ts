import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { LOCALE_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Privacy } from './privacy';

describe('Privacy', () => {
  /** "?lang=fr" stands for the French build: the language comes from LOCALE_ID. */
  async function render(url: string): Promise<HTMLElement> {
    const [path, query] = url.split('?');
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'privacy', component: Privacy }]),
        { provide: LOCALE_ID, useValue: query === 'lang=fr' ? 'fr' : 'en' },
      ],
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(path);
    return harness.routeNativeElement as HTMLElement;
  }

  it('is in English by default', async () => {
    const page = await render('/privacy');

    expect(page.querySelector('h1')?.textContent).toBe('Privacy Policy');
    expect(page.querySelector('article')?.hasAttribute('lang')).toBe(false);
  });

  it('is in French in the French build, marked as such for screen readers', async () => {
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

  it('says what friends see, and never the guesses, in both languages', async () => {
    const english = (await render('/privacy')).textContent?.replace(/\s+/g, ' ');
    expect(english).toContain(
      'Your friends see your username, your points of the day once they have finished their own daily game (never your guesses)',
    );
    expect(english).toContain('Friend requests and friendships: until they are declined');

    TestBed.resetTestingModule();
    const french = (await render('/privacy?lang=fr')).textContent?.replace(/\s+/g, ' ');
    expect(french).toContain(
      'Vos amis voient votre pseudo, vos points du jour une fois leur propre partie du jour terminée (jamais vos essais)',
    );
    expect(french).toContain("Demandes d'ami et amitiés : jusqu'à leur refus");
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

  it('says that backups keep deleted data for 30 days at most, in both languages', async () => {
    const english = await render('/privacy');
    expect(english.textContent?.replace(/\s+/g, ' ')).toContain(
      'Backups: a copy of the whole database is made every day, kept on our server at OVH for 30 days at most, then erased.',
    );

    TestBed.resetTestingModule();
    const french = await render('/privacy?lang=fr');
    expect(french.textContent?.replace(/\s+/g, ' ')).toContain(
      'Sauvegardes : une copie de toute la base de données est faite chaque jour, conservée sur notre serveur chez OVH 30 jours au plus, puis effacée.',
    );
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

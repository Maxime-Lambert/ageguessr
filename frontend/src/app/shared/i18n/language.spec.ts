import { fromApi, languageCookie, siteLanguageOf, toApi } from './language';

describe('site language', () => {
  it('reads the language of a build from its locale', () => {
    expect(siteLanguageOf('fr')).toBe('fr');
    expect(siteLanguageOf('fr-CA')).toBe('fr');
    expect(siteLanguageOf('en')).toBe('en');
    expect(siteLanguageOf('en-US')).toBe('en');
    expect(siteLanguageOf('de')).toBe('en');
  });

  it('names the languages as the API does', () => {
    expect(toApi('fr')).toBe('FR');
    expect(toApi('en')).toBe('EN');
    expect(fromApi('FR')).toBe('fr');
    expect(fromApi('EN')).toBe('en');
  });

  it('remembers the choice for a year on the whole site, Secure over HTTPS only', () => {
    expect(languageCookie('fr', true)).toBe(
      'ageguessr_lang=fr; Path=/; Max-Age=31536000; SameSite=Lax; Secure',
    );
    expect(languageCookie('en', false)).toBe(
      'ageguessr_lang=en; Path=/; Max-Age=31536000; SameSite=Lax',
    );
  });
});

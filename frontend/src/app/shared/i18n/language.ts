/**
 * The two languages of the site. The page's comes from its build (/en/ or /fr/, Angular
 * i18n, LOCALE_ID); the API names them EN and FR (account, game round).
 */
export type SiteLanguage = 'en' | 'fr';
export type ApiLanguage = 'EN' | 'FR';

export function siteLanguageOf(locale: string): SiteLanguage {
  return locale.toLowerCase().startsWith('fr') ? 'fr' : 'en';
}

export function toApi(language: SiteLanguage): ApiLanguage {
  return language === 'fr' ? 'FR' : 'EN';
}

export function fromApi(language: ApiLanguage): SiteLanguage {
  return language === 'FR' ? 'fr' : 'en';
}

/**
 * The language chosen on this device, read by Caddy to send "/" and the links without a
 * prefix to the right build. Not httpOnly: the page writes it. A choice the visitor made,
 * so it needs no consent (listed on /privacy).
 */
export const LANGUAGE_COOKIE = 'ageguessr_lang';
const ONE_YEAR_IN_SECONDS = 60 * 60 * 24 * 365;

export function languageCookie(language: SiteLanguage, secure: boolean): string {
  return `${LANGUAGE_COOKIE}=${language}; Path=/; Max-Age=${ONE_YEAR_IN_SECONDS}; SameSite=Lax${secure ? '; Secure' : ''}`;
}

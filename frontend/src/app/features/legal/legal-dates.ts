/**
 * Version of the Terms, shown as "Last updated" on /terms. Must equal
 * TermsVersion.CURRENT in the API (ageguessr-api/.../features/auth/TermsVersion.java),
 * which is the version recorded on each new account: bump both together.
 */
export const TERMS_VERSION = '2026-10-21';

export const PRIVACY_UPDATED = '2026-10-30';
export const LEGAL_NOTICE_UPDATED = '2026-10-21';

/** "21 October 2026" / "21 octobre 2026", from an ISO date (read as UTC, never shifted). */
export function displayDate(isoDate: string, language: 'en' | 'fr'): string {
  return new Intl.DateTimeFormat(language === 'fr' ? 'fr-FR' : 'en-GB', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
    timeZone: 'UTC',
  }).format(new Date(`${isoDate}T00:00:00Z`));
}

import { Service, inject, signal } from '@angular/core';
import { ApiLanguage, toApi } from '../../shared/i18n/language';
import { SiteLanguageService } from '../i18n/site-language';

const STORAGE_KEY = 'ageguessr-hint-language';

/**
 * The language of the hints and of the name revealed, chosen apart from the site's: a
 * French speaker may prefer English charts, or the reverse. The site's language until the
 * player picks one, then remembered on this device. It is sent with each first guess and
 * fixed for that round: changing it mid-round applies from the next round.
 */
@Service()
export class HintLanguage {
  private readonly site = inject(SiteLanguageService);

  readonly value = signal<ApiLanguage>(readStored() ?? toApi(this.site.current));

  set(language: ApiLanguage): void {
    try {
      window.localStorage.setItem(STORAGE_KEY, language);
    } catch {
      // Storage refused (private mode, full): the choice still holds for this visit.
    }
    this.value.set(language);
  }
}

function readStored(): ApiLanguage | null {
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY);
    return stored === 'EN' || stored === 'FR' ? stored : null;
  } catch {
    return null;
  }
}

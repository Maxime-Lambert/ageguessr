import { Signal, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';

export type LegalLanguage = 'en' | 'fr';

/**
 * English by default, French with ?lang=fr (any other value falls back to English).
 * Both versions live in the same template: French law requires a French version of the
 * Terms offered to consumers in France, English is the language of the site.
 */
export function legalLanguage(): Signal<LegalLanguage> {
  const params = toSignal(inject(ActivatedRoute).queryParamMap);
  return computed(() => (params()?.get('lang') === 'fr' ? 'fr' : 'en'));
}

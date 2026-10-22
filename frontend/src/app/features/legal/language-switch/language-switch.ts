import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LegalLanguage } from '../legal-language';

@Component({
  imports: [RouterLink],
  selector: 'app-language-switch',
  template: `
    @if (language() === 'en') {
      <a
        routerLink="."
        [queryParams]="{ lang: 'fr' }"
        lang="fr"
        hreflang="fr"
        class="text-accent underline"
        >Version française</a
      >
    } @else {
      <a routerLink="." [queryParams]="{}" lang="en" hreflang="en" class="text-accent underline"
        >English version</a
      >
    }
  `,
})
export class LanguageSwitch {
  readonly language = input.required<LegalLanguage>();
}

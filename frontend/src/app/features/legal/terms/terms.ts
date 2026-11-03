import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LanguageSwitch } from '../../i18n/language-switch/language-switch';
import { SiteLanguageService } from '../../i18n/site-language';
import { TERMS_VERSION, displayDate } from '../legal-dates';

@Component({
  imports: [LanguageSwitch, RouterLink],
  selector: 'app-terms',
  styleUrl: '../legal-page.css',
  templateUrl: './terms.html',
})
export class Terms {
  /** Both versions live in the template; the build (/en/ or /fr/) picks one. */
  protected readonly language = inject(SiteLanguageService).current;
  protected readonly updated = displayDate(TERMS_VERSION, this.language);
}

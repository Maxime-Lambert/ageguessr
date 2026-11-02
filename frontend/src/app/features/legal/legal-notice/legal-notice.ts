import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LanguageSwitch } from '../../i18n/language-switch/language-switch';
import { SiteLanguageService } from '../../i18n/site-language';
import { LEGAL_NOTICE_UPDATED, displayDate } from '../legal-dates';

@Component({
  imports: [LanguageSwitch, RouterLink],
  selector: 'app-legal-notice',
  styleUrl: '../legal-page.css',
  templateUrl: './legal-notice.html',
})
export class LegalNotice {
  /** Both versions live in the template; the build (/en/ or /fr/) picks one. */
  protected readonly language = inject(SiteLanguageService).current;
  protected readonly updated = displayDate(LEGAL_NOTICE_UPDATED, this.language);
}

import { Component, computed } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LanguageSwitch } from '../language-switch/language-switch';
import { LEGAL_NOTICE_UPDATED, displayDate } from '../legal-dates';
import { legalLanguage } from '../legal-language';

@Component({
  imports: [LanguageSwitch, RouterLink],
  selector: 'app-legal-notice',
  styleUrl: '../legal-page.css',
  templateUrl: './legal-notice.html',
})
export class LegalNotice {
  protected readonly language = legalLanguage();
  protected readonly updated = computed(() => displayDate(LEGAL_NOTICE_UPDATED, this.language()));
}

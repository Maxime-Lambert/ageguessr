import { Component, computed } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LanguageSwitch } from '../language-switch/language-switch';
import { TERMS_VERSION, displayDate } from '../legal-dates';
import { legalLanguage } from '../legal-language';

@Component({
  imports: [LanguageSwitch, RouterLink],
  selector: 'app-terms',
  styleUrl: '../legal-page.css',
  templateUrl: './terms.html',
})
export class Terms {
  protected readonly language = legalLanguage();
  protected readonly updated = computed(() => displayDate(TERMS_VERSION, this.language()));
}

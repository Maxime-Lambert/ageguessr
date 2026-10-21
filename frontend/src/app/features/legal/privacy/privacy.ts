import { Component, computed } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LanguageSwitch } from '../language-switch/language-switch';
import { PRIVACY_UPDATED, displayDate } from '../legal-dates';
import { legalLanguage } from '../legal-language';

@Component({
  imports: [LanguageSwitch, RouterLink],
  selector: 'app-privacy',
  styleUrl: '../legal-page.css',
  templateUrl: './privacy.html',
})
export class Privacy {
  protected readonly language = legalLanguage();
  protected readonly updated = computed(() => displayDate(PRIVACY_UPDATED, this.language()));
}

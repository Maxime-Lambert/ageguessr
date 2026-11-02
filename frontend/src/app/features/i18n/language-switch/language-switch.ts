import { Component, inject } from '@angular/core';
import { SiteLanguageService } from '../site-language';

/**
 * Each language named in itself, so that someone who cannot read the page still finds
 * theirs. A real link (with hreflang) that the click turns into a switch, so the account
 * is updated before leaving.
 */
@Component({
  selector: 'app-language-switch',
  templateUrl: './language-switch.html',
})
export class LanguageSwitch {
  protected readonly language = inject(SiteLanguageService);
  protected readonly names = { en: 'English', fr: 'Français' } as const;

  switch(event: Event): void {
    event.preventDefault();
    this.language.switchTo(this.language.other);
  }
}

import { Component, inject } from '@angular/core';
import { ApiLanguage } from '../../i18n/language';
import { GAME_TEXT } from '../game-vocabulary';
import { HintLanguage } from '../hint-language';

/** The two languages of the hints, as a pair of toggle buttons. */
@Component({
  selector: 'app-hint-language-picker',
  templateUrl: './hint-language-picker.html',
})
export class HintLanguagePicker {
  protected readonly language = inject(HintLanguage);
  protected readonly text = GAME_TEXT;
  protected readonly options: { value: ApiLanguage; name: string; lang: string }[] = [
    { value: 'EN', name: 'English', lang: 'en' },
    { value: 'FR', name: 'Français', lang: 'fr' },
  ];
}

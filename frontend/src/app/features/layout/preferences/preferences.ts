import { Component, inject } from '@angular/core';
import { ApiLanguage } from '../../../shared/i18n/language';
import { Segmented, SegmentedOption } from '../../../shared/ui/segmented/segmented';
import { GAME_TEXT } from '../../game/game-vocabulary';
import { HintLanguage } from '../../game/hint-language';
import { LanguageSwitch } from '../../i18n/language-switch/language-switch';
import { Theme, ThemePreference } from '../../theme/theme';

/**
 * The three display choices, gathered in one place (the account menu, the mobile panel)
 * instead of spread over the header and the game screens.
 */
@Component({
  selector: 'app-preferences',
  imports: [LanguageSwitch, Segmented],
  host: { class: 'grid gap-1' },
  template: `
    <div class="flex items-center justify-between gap-3 py-1.5">
      <span class="text-sm">{{ siteLanguage }}</span>
      <app-language-switch />
    </div>
    <div class="flex items-center justify-between gap-3 py-1.5">
      <span class="text-sm" aria-hidden="true">{{ hintLanguageLabel }}</span>
      <app-segmented
        [label]="hintLanguageLabel"
        [options]="hintLanguages"
        [value]="hintLanguage.value()"
        (valueChange)="hintLanguage.set($event)"
      />
    </div>
    <div class="flex items-center justify-between gap-3 py-1.5">
      <span class="text-sm" aria-hidden="true">{{ themeLabel }}</span>
      <app-segmented
        [label]="themeLabel"
        [options]="themes"
        [value]="theme.preference()"
        (valueChange)="theme.setPreference($event)"
      />
    </div>
  `,
})
export class Preferences {
  protected readonly hintLanguage = inject(HintLanguage);
  protected readonly theme = inject(Theme);

  protected readonly siteLanguage = $localize`:@@preferences.siteLanguage:Site language`;
  protected readonly hintLanguageLabel = GAME_TEXT.hintLanguage;
  protected readonly themeLabel = $localize`:@@preferences.theme:Theme`;

  protected readonly hintLanguages: SegmentedOption<ApiLanguage>[] = [
    { value: 'EN', label: 'English', lang: 'en' },
    { value: 'FR', label: 'Français', lang: 'fr' },
  ];
  protected readonly themes: SegmentedOption<ThemePreference>[] = [
    { value: 'dark', label: $localize`:@@preferences.dark:Dark` },
    { value: 'light', label: $localize`:@@preferences.light:Light` },
    { value: 'system', label: $localize`:@@preferences.system:Auto` },
  ];
}

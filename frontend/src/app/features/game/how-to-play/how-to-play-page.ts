import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button } from '../../../shared/ui/button/button';
import { Page } from '../../../shared/ui/page/page';
import { GAME_TEXT } from '../game-vocabulary';
import { HowToPlay } from './how-to-play';

/** The rules again, once the first-visit card is dismissed. Linked from the footer. */
@Component({
  imports: [RouterLink, Button, Page, HowToPlay],
  selector: 'app-how-to-play-page',
  template: `
    <main>
      <app-page width="narrow">
        <h1 class="font-display text-[32px] leading-tight sm:text-[36px]">
          {{ text.howToPlay }}
        </h1>
        <app-how-to-play class="mt-6 block" [heading]="false" />
        <a appButton routerLink="/" class="mt-8">{{ text.playToday }}</a>
      </app-page>
    </main>
  `,
})
export class HowToPlayPage {
  protected readonly text = GAME_TEXT;
}

import { Component, input } from '@angular/core';
import { GAME_TEXT } from '../game-vocabulary';

/**
 * The rules in three lines, for a first visit (RulesCard), the free page and the
 * « How to play » page. No round count: the daily one is a server setting.
 */
@Component({
  selector: 'app-how-to-play',
  template: `
    @if (heading()) {
      <h2 [id]="headingId()" class="font-display text-xl">{{ text.howToPlay }}</h2>
    }
    <ol [class.mt-3]="heading()" class="space-y-2.5 text-[15px] leading-relaxed">
      @for (rule of rules; track $index) {
        <li class="flex gap-3">
          <span
            class="bg-surface-2 text-score font-display grid size-6.5 shrink-0 place-items-center rounded-full text-sm font-semibold"
            aria-hidden="true"
            >{{ $index + 1 }}</span
          >
          <span>{{ rule }}</span>
        </li>
      }
    </ol>
  `,
})
export class HowToPlay {
  /** The heading's id, for the region that wraps it (aria-labelledby). */
  readonly headingId = input('how-to-play-title');
  /** False where the page's own h1 already says « How to play ». */
  readonly heading = input(true);

  protected readonly text = GAME_TEXT;
  protected readonly rules = [
    GAME_TEXT.ruleGuess,
    GAME_TEXT.ruleLead,
    GAME_TEXT.ruleHints,
    GAME_TEXT.rulePoints,
  ];
}

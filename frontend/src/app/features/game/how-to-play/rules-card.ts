import { Component, signal } from '@angular/core';
import { Button } from '../../../shared/ui/button/button';
import { GAME_TEXT } from '../game-vocabulary';
import { HowToPlay } from './how-to-play';

const STORAGE_KEY = 'ageguessr-rules-seen';

/**
 * The rules above the first round, until the player says « Got it ». Remembered on this
 * device only: a convenience, so a refused storage just shows the card again next visit.
 * The footer's « How to play » page keeps them at hand afterwards.
 */
@Component({
  imports: [Button, HowToPlay],
  selector: 'app-rules-card',
  template: `
    @if (!seen()) {
      <section aria-labelledby="rules-card-title" class="card mb-6 p-5">
        <app-how-to-play headingId="rules-card-title" />
        <button appButton size="sm" type="button" class="mt-4" (click)="dismiss()">
          {{ text.gotIt }}
        </button>
      </section>
    }
  `,
})
export class RulesCard {
  protected readonly text = GAME_TEXT;
  protected readonly seen = signal(readSeen());

  dismiss(): void {
    try {
      window.localStorage.setItem(STORAGE_KEY, '1');
    } catch {
      // Storage refused (private mode, full): hidden for this visit only.
    }
    this.seen.set(true);
  }
}

function readSeen(): boolean {
  try {
    return window.localStorage.getItem(STORAGE_KEY) === '1';
  } catch {
    return false;
  }
}

import { Component, DestroyRef, computed, inject, output, signal } from '@angular/core';
import { LucideClock } from '@lucide/angular';
import { GAME_TEXT, nextGameIn } from '../game-vocabulary';

/** The countdown moves on every minute: it never shows seconds. */
export const TICK_MS = 60_000;

/** The next midnight UTC after `now`, when the next daily game starts. */
export function nextMidnightUtc(now: number): number {
  const date = new Date(now);
  return Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate() + 1);
}

/**
 * Under today's summary: how long until the next daily game, counted for the player
 * rather than « midnight UTC » (testers, 2026-11-23). Once it has started, a button to
 * play it, never a reload under the player's feet: they may be reading their summary.
 */
@Component({
  selector: 'app-next-game-countdown',
  imports: [LucideClock],
  template: `
    <!-- No live region: it would read the time out every minute. -->
    <p class="text-muted flex flex-wrap items-center gap-2 text-sm">
      <svg lucideClock [size]="16" aria-hidden="true"></svg>
      @if (left() > 0) {
        {{ label() }}
      } @else {
        {{ text.nextGameReady }}
        <button type="button" class="text-accent font-semibold underline" (click)="playNext.emit()">
          {{ text.playNextGame }}
        </button>
      }
    </p>
  `,
})
export class NextGameCountdown {
  readonly playNext = output<void>();

  protected readonly text = GAME_TEXT;
  private readonly target = nextMidnightUtc(Date.now());
  private readonly now = signal(Date.now());

  protected readonly left = computed(() => this.target - this.now());
  protected readonly label = computed(() => nextGameIn(this.left()));

  constructor() {
    const timer = setInterval(() => this.now.set(Date.now()), TICK_MS);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }
}

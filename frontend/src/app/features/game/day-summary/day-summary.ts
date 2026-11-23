import { Component, ElementRef, afterNextRender, computed, input, viewChild } from '@angular/core';
import { GAME_TEXT, betterThanText, playersAverageText, summaryDetail } from '../game-vocabulary';
import { DayStats, PlayedRounds } from '../models';

/**
 * The end of a game, daily or of a series: the score, then one line per round, a withdrawn
 * one included. What follows (come back tomorrow, a new series) is projected by the screen.
 */
@Component({
  selector: 'app-day-summary',
  templateUrl: './day-summary.html',
})
export class DaySummary {
  readonly game = input.required<PlayedRounds>();
  /** How the others did, for the daily game; none for a free game, or under the minimum. */
  readonly stats = input<DayStats | null>(null);

  protected readonly betterThan = computed(() => {
    const stats = this.stats();
    return stats?.betterThanPercent != null
      ? betterThanText(stats.betterThanPercent, stats.players)
      : null;
  });

  protected readonly text = GAME_TEXT;

  private readonly heading = viewChild.required<ElementRef<HTMLElement>>('heading');

  constructor() {
    afterNextRender(() => this.heading().nativeElement.focus());
  }

  readonly lines = computed(() => {
    const averages = new Map(
      (this.stats()?.rounds ?? []).map((round) => [round.position, round.averageFirstGuess]),
    );
    return this.game().rounds.map((round) => ({
      position: round.position,
      removed: round.status === 'NEUTRALIZED',
      name: round.result?.person.name ?? null,
      detail: round.result
        ? summaryDetail(round.result.age, round.result.firstGuess, round.result.secondGuess)
        : null,
      average: averages.has(round.position)
        ? playersAverageText(averages.get(round.position)!)
        : null,
      points: round.result?.points ?? null,
      // A small copy of the photo in the report: nothing to hide any more once played.
      imageUrl: round.imageUrl,
    }));
  });
}

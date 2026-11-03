import { Component, ElementRef, afterNextRender, computed, input, viewChild } from '@angular/core';
import { GAME_TEXT } from '../game-vocabulary';
import { PlayedRounds } from '../models';

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

  protected readonly text = GAME_TEXT;

  private readonly heading = viewChild.required<ElementRef<HTMLElement>>('heading');

  constructor() {
    afterNextRender(() => this.heading().nativeElement.focus());
  }

  readonly lines = computed(() =>
    this.game().rounds.map((round) => ({
      position: round.position,
      removed: round.status === 'NEUTRALIZED',
      name: round.result?.person.name ?? null,
      points: round.result?.points ?? null,
    })),
  );
}

import { Component, input, output } from '@angular/core';
import { Button } from '../../../shared/ui/button/button';
import { GAME_TEXT } from '../game-vocabulary';
import { HintCategory, RoundView } from '../models';
import { Round } from '../round/round';
import { RoundResult } from '../round-result/round-result';
import { RoundReporter } from '../report-button/report-button';

/**
 * The round on screen, in every game (daily, free series, challenge): being played, or
 * its verdict, and the retry when its photo would not load. Only shows and emits; the
 * screen (RoundsScreen) talks to the API.
 */
@Component({
  imports: [Round, RoundResult, Button],
  selector: 'app-round-player',
  templateUrl: './round-player.html',
})
export class RoundPlayer {
  readonly round = input.required<RoundView>();
  readonly total = input.required<number>();
  readonly last = input(false);
  readonly busy = input(false);
  readonly error = input<string | null>(null);
  readonly imageFailed = input(false);
  readonly reporter = input.required<RoundReporter>();

  readonly firstGuess = output<number>();
  readonly secondGuess = output<number>();
  readonly hintPicked = output<HintCategory>();
  readonly imageError = output<void>();
  readonly next = output<void>();
  readonly retry = output<void>();

  protected readonly text = GAME_TEXT;

  guessed(guess: number): void {
    (this.round().step === 'NOT_STARTED' ? this.firstGuess : this.secondGuess).emit(guess);
  }
}

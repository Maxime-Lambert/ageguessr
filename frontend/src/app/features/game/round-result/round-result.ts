import {
  Component,
  ElementRef,
  afterNextRender,
  computed,
  input,
  output,
  viewChild,
} from '@angular/core';
import { CATEGORY_LABELS, GAME_TEXT, roundTitle } from '../game-vocabulary';
import { RoundReveal, RoundView } from '../models';

/**
 * The verdict of a finished round: the real age, both guesses with their points (the
 * first guess's points appear here for the first time), who it was, the photo's credit
 * and the hint with its year and source (CC BY-SA attribution).
 */
@Component({
  selector: 'app-round-result',
  templateUrl: './round-result.html',
})
export class RoundResult {
  readonly round = input.required<RoundView>();
  readonly total = input.required<number>();
  /** Whether another round follows, or the summary. */
  readonly last = input(false);

  readonly next = output<void>();

  protected readonly text = GAME_TEXT;
  protected readonly categoryLabels = CATEGORY_LABELS;

  readonly title = computed(() => roundTitle(this.round().position, this.total()));
  readonly result = computed(() => this.round().result as RoundReveal);
  readonly exact = computed(() => this.result().secondGuess === null);

  private readonly heading = viewChild.required<ElementRef<HTMLElement>>('heading');

  constructor() {
    // The verdict replaces the round: the focus follows, so it is read out at once.
    afterNextRender(() => this.heading().nativeElement.focus());
  }
}

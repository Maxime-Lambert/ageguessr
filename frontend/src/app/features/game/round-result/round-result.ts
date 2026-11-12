import {
  Component,
  ElementRef,
  afterNextRender,
  computed,
  input,
  output,
  viewChild,
} from '@angular/core';
import { LucideArrowRight, LucideStamp } from '@lucide/angular';
import { Button } from '../../../shared/ui/button/button';
import { CasePhoto } from '../case-photo/case-photo';
import { CaseSteps } from '../case-steps/case-steps';
import { CATEGORY_LABELS, GAME_TEXT, roundTitle, yearsAgo } from '../game-vocabulary';
import { LeadIcon } from '../lead-icon/lead-icon';
import { RoundReveal, RoundView } from '../models';
import { ReportButton, RoundReporter } from '../report-button/report-button';

/** Points of one guess at most; an exact first guess alone is worth twice that. */
const GUESS_MAX = 500;

/**
 * The verdict of a finished round: the real age, both guesses with their points (the
 * first guess's points appear here for the first time), who it was, the photo's credit
 * and the hint with its year and source (CC BY-SA attribution). The photo and the hint
 * can be reported from here, once the identity is known.
 */
@Component({
  imports: [Button, CasePhoto, CaseSteps, LeadIcon, LucideArrowRight, LucideStamp, ReportButton],
  selector: 'app-round-result',
  templateUrl: './round-result.html',
})
export class RoundResult {
  readonly round = input.required<RoundView>();
  readonly total = input.required<number>();
  /** Whether another round follows, or the summary. */
  readonly last = input(false);
  readonly reporter = input.required<RoundReporter>();

  readonly next = output<void>();

  protected readonly text = GAME_TEXT;
  protected readonly categoryLabels = CATEGORY_LABELS;

  readonly title = computed(() => roundTitle(this.round().position, this.total()));
  readonly result = computed(() => this.round().result as RoundReveal);
  /** The hint is from (game year − age): how far back it is, the key to read it. */
  /** The same for every hint of the round: they all date from the game's year minus the age. */
  protected readonly yearsAgo = computed(() => yearsAgo(this.result().age));
  readonly exact = computed(() => this.result().secondGuess === null);
  /** How full each guess's bar is, in percent of what that guess could earn. */
  protected readonly firstBar = computed(() =>
    this.exact() ? 100 : (100 * this.result().firstPoints) / GUESS_MAX,
  );
  protected readonly secondBar = computed(
    () => (100 * (this.result().secondPoints ?? 0)) / GUESS_MAX,
  );

  private readonly heading = viewChild.required<ElementRef<HTMLElement>>('heading');

  constructor() {
    // The verdict replaces the round: the focus follows, so it is read out at once.
    afterNextRender(() => this.heading().nativeElement.focus());
  }
}

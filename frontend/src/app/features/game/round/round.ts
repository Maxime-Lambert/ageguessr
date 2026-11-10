import {
  Component,
  ElementRef,
  computed,
  effect,
  input,
  output,
  untracked,
  viewChild,
  viewChildren,
} from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { LucideArrowDown, LucideArrowUp } from '@lucide/angular';
import { Button } from '../../../shared/ui/button/button';
import { AgeInput } from '../../../shared/ui/age-input/age-input';
import { Field } from '../../../shared/ui/field/field';
import { CasePhoto } from '../case-photo/case-photo';
import { CaseSteps } from '../case-steps/case-steps';
import { CATEGORY_LABELS, GAME_TEXT, directionText, roundTitle } from '../game-vocabulary';
import { LeadIcon } from '../lead-icon/lead-icon';
import { HintCategory, MAX_AGE, MIN_AGE, RoundView } from '../models';

/**
 * One round being played: the photo, then the guess, the side, the choice of a hint and
 * the final guess. It only shows what the API sent and emits what the player does; the
 * game page talks to the API.
 */
@Component({
  imports: [
    ReactiveFormsModule,
    AgeInput,
    Button,
    CasePhoto,
    CaseSteps,
    Field,
    LeadIcon,
    LucideArrowDown,
    LucideArrowUp,
  ],
  selector: 'app-round',
  templateUrl: './round.html',
})
export class Round {
  readonly round = input.required<RoundView>();
  readonly total = input.required<number>();
  readonly busy = input(false);
  readonly error = input<string | null>(null);

  readonly guessed = output<number>();
  readonly hintPicked = output<HintCategory>();
  readonly imageFailed = output<void>();

  protected readonly text = GAME_TEXT;
  protected readonly categoryLabels = CATEGORY_LABELS;
  protected readonly minAge = MIN_AGE;
  protected readonly maxAge = MAX_AGE;

  readonly guess = new FormControl<number | null>(null, [
    Validators.required,
    Validators.min(MIN_AGE),
    Validators.max(MAX_AGE),
    Validators.pattern(/^\d+$/),
  ]);

  readonly title = computed(() => roundTitle(this.round().position, this.total()));
  readonly step = computed(() => this.round().step);
  readonly direction = computed(() => {
    const { direction, firstGuess } = this.round();
    return direction && firstGuess !== null ? directionText(direction, firstGuess) : null;
  });

  /** Large while the player reads it and picks a lead, compact above the final guess. */
  protected readonly directionStyle = computed(() =>
    this.step() === 'AWAITING_HINT'
      ? { box: 'p-4', arrow: 'size-11', text: 'text-2xl' }
      : { box: 'px-3.5 py-2.5', arrow: 'size-8', text: 'text-lg' },
  );

  private readonly guessInput = viewChild(AgeInput);
  private readonly categoryButtons = viewChildren<ElementRef<HTMLButtonElement>>('category');

  constructor() {
    // Each step moves the focus to what is to be done next, so a keyboard or screen reader
    // user never has to look for it. The final guess starts one year past the first, on
    // the side given: − and + then adjust from there, and Enter alone never repeats a
    // guess already known to be wrong.
    effect(() => {
      const step = this.step();
      const target =
        step === 'AWAITING_HINT' ? this.categoryButtons().at(0)?.nativeElement : this.guessInput();
      untracked(() => {
        if (step === 'AWAITING_SECOND_GUESS') {
          this.guess.reset(this.secondGuessStart());
        }
        target?.focus();
      });
    });
  }

  private secondGuessStart(): number | null {
    const { firstGuess, direction } = this.round();
    if (firstGuess === null || direction === null) return null;
    const next = firstGuess + (direction === 'OLDER' ? 1 : -1);
    return Math.min(MAX_AGE, Math.max(MIN_AGE, next));
  }

  submitGuess(): void {
    if (this.guess.invalid || this.busy()) {
      this.guess.markAsTouched();
      return;
    }
    this.guessed.emit(Number(this.guess.value));
  }

  pick(category: HintCategory): void {
    if (!this.busy()) {
      this.hintPicked.emit(category);
    }
  }
}

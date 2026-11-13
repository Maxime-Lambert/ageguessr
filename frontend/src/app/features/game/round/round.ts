import {
  Component,
  ElementRef,
  computed,
  effect,
  input,
  output,
  signal,
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
import {
  CATEGORY_LABELS,
  GAME_TEXT,
  directionText,
  gapText,
  pickHintsText,
  pickedText,
  ageOutOfRange,
  rangeText,
  roundTitle,
} from '../game-vocabulary';
import { LeadCard } from '../lead-card/lead-card';
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
    LeadCard,
    LeadIcon,
    LucideArrowDown,
    LucideArrowUp,
  ],
  selector: 'app-round',
  templateUrl: './round.html',
  host: {
    '(document:pointerdown)': 'pointedAt($event)',
    '(document:keydown.escape)': 'openHelp.set(null)',
  },
})
export class Round {
  readonly round = input.required<RoundView>();
  readonly total = input.required<number>();
  readonly busy = input(false);
  readonly error = input<string | null>(null);

  readonly guessed = output<number>();
  readonly hintsPicked = output<HintCategory[]>();
  readonly imageFailed = output<void>();

  protected readonly text = GAME_TEXT;
  protected readonly categoryLabels = CATEGORY_LABELS;

  readonly guess = new FormControl<number | null>(null, ageValidators(MIN_AGE, MAX_AGE));

  readonly title = computed(() => roundTitle(this.round().position, this.total()));
  readonly step = computed(() => this.round().step);
  readonly direction = computed(() => {
    const { direction, gap, firstGuess } = this.round();
    if (!direction || firstGuess === null) return null;
    const side = directionText(direction, firstGuess);
    return gap ? `${side}, ${gapText(gap)}` : side;
  });
  readonly rangeLabel = computed(() => {
    const range = this.round().range;
    return range ? rangeText(range) : null;
  });

  protected readonly invalidText = computed(() => {
    const range = this.round().range;
    return range ? ageOutOfRange(range) : this.text.invalidAge;
  });

  /** The final guess stays within the ages the side and the band leave. */
  protected readonly minAge = computed(() => this.round().range?.min ?? MIN_AGE);
  protected readonly maxAge = computed(() => this.round().range?.max ?? MAX_AGE);

  /** The categories picked so far, in the order they are offered. */
  protected readonly picked = signal<readonly HintCategory[]>([]);
  protected readonly toPick = computed(() => this.round().hintsToPick ?? 1);
  protected readonly pickTitle = computed(() => pickHintsText(this.toPick()));
  protected readonly pickedLabel = computed(() => pickedText(this.picked().length, this.toPick()));
  protected readonly pickComplete = computed(() => this.picked().length === this.toPick());

  /** The lead card whose help is open: one at a time (LeadCard). */
  protected readonly openHelp = signal<HintCategory | null>(null);
  /**
   * The help's examples count from this year. The API picks the hints from the game's
   * date (today for the daily game, the start for a series), which the round does not
   * carry: the current UTC year, wrong only for a game started before New Year's night,
   * and then only in an example.
   */
  protected readonly gameYear = new Date().getUTCFullYear();

  /** Large while the player reads it and picks a lead, compact above the final guess. */
  protected readonly directionStyle = computed(() =>
    this.step() === 'AWAITING_HINT'
      ? { box: 'p-4', arrow: 'size-11', text: 'text-2xl' }
      : { box: 'px-3.5 py-2.5', arrow: 'size-8', text: 'text-lg' },
  );

  private readonly guessInput = viewChild(AgeInput);
  private readonly leadCards = viewChildren(LeadCard, { read: ElementRef<HTMLElement> });

  constructor() {
    // Each step moves the focus to what is to be done next, so a keyboard or screen reader
    // user never has to look for it. The final guess starts one year past the first, on
    // the side given: − and + then adjust from there, and Enter alone never repeats a
    // guess already known to be wrong.
    effect(() => {
      const step = this.step();
      const target =
        step === 'AWAITING_HINT'
          ? this.leadCards().at(0)?.nativeElement.querySelector('button')
          : this.guessInput();
      untracked(() => {
        if (step === 'AWAITING_HINT') {
          this.picked.set([]);
        }
        this.openHelp.set(null);
        // The same component plays the next round: back to 18-100 until a first guess.
        this.guess.setValidators(ageValidators(this.minAge(), this.maxAge()));
        if (step === 'AWAITING_SECOND_GUESS') {
          this.guess.reset(this.secondGuessStart());
        } else {
          this.guess.updateValueAndValidity();
        }
        target?.focus();
      });
    });
  }

  /**
   * The edge of the range nearest the first guess: − and + adjust from there, and Enter
   * alone never sends an age already ruled out.
   */
  private secondGuessStart(): number | null {
    const { firstGuess, direction, range } = this.round();
    if (firstGuess === null || direction === null) return null;
    if (range) return direction === 'OLDER' ? range.min : range.max;
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

  isPicked(category: HintCategory): boolean {
    return this.picked().includes(category);
  }

  /** Picks or unpicks a lead; once enough are picked, the others wait for one to be unpicked. */
  toggle(category: HintCategory): void {
    if (this.busy()) return;
    if (this.isPicked(category)) {
      this.picked.update((picked) => picked.filter((one) => one !== category));
    } else if (!this.pickComplete()) {
      const offered = this.round().categories ?? [];
      this.picked.update((picked) =>
        offered.filter((one) => one === category || picked.includes(one)),
      );
    }
  }

  protected closeHelp(category: HintCategory): void {
    if (this.openHelp() === category) this.openHelp.set(null);
  }

  /** A tap anywhere but on the open card or its help closes it. */
  protected pointedAt(event: PointerEvent): void {
    const open = this.openHelp();
    if (open === null) return;
    const card = (event.target as Element | null)?.closest('app-lead-card');
    if (card?.getAttribute('data-lead') !== open) this.openHelp.set(null);
  }

  confirmHints(): void {
    if (this.pickComplete() && !this.busy()) {
      this.hintsPicked.emit([...this.picked()]);
    }
  }
}

function ageValidators(min: number, max: number) {
  return [
    Validators.required,
    Validators.min(min),
    Validators.max(max),
    Validators.pattern(/^\d+$/),
  ];
}

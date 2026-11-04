import { HttpErrorResponse } from '@angular/common/http';
import { Directive, computed, inject, signal } from '@angular/core';
import { EMPTY, Observable, tap } from 'rxjs';
import { ApiErrorResponse } from '../../shared/api-error';
import { ApiLanguage } from '../../shared/i18n/language';
import { GAME_TEXT } from './game-vocabulary';
import { HintLanguage } from './hint-language';
import { HintCategory, PlayedRounds, RoundReport, RoundView } from './models';
import { RoundReporter } from './report-button/report-button';

/**
 * What every game screen does with its rounds, daily or of a series: load them, play the
 * round on screen step by step, move to the next one, and reload when the server knows
 * better (409). The state lives on the server, so a reload picks up where the player was.
 * Each screen says only how to fetch its rounds and where to send a step.
 */
@Directive()
export abstract class RoundsScreen<T extends PlayedRounds> {
  private readonly hintLanguage = inject(HintLanguage);
  /** A photo that failed to load is retried once with a fresh state, then reported. */
  private readonly imageRetried = new Set<number>();

  protected readonly text = GAME_TEXT;

  readonly game = signal<T | null>(null);
  readonly status = signal<'loading' | 'ready' | 'error'>('loading');
  /** The round on screen, its result included; null shows the summary. */
  readonly activePosition = signal<number | null>(null);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  /** Why the rounds were reloaded under the player's feet (midnight, a photo withdrawn). */
  readonly notice = signal<string | null>(null);
  readonly imageFailed = signal(false);

  readonly total = computed(() => this.game()?.rounds.length ?? 0);
  readonly active = computed(
    () => this.game()?.rounds.find((round) => round.position === this.activePosition()) ?? null,
  );
  readonly isLast = computed(() => {
    const active = this.active();
    return active !== null && this.nextAfter(active.position) === null;
  });

  protected abstract fetch(): Observable<T>;
  protected abstract sendFirstGuess(
    game: T,
    round: RoundView,
    guess: number,
    language: ApiLanguage,
  ): Observable<RoundView>;
  protected abstract sendHint(
    game: T,
    round: RoundView,
    category: HintCategory,
  ): Observable<RoundView>;
  protected abstract sendSecondGuess(
    game: T,
    round: RoundView,
    guess: number,
  ): Observable<RoundView>;
  protected abstract sendReport(game: T, round: RoundView, report: RoundReport): Observable<void>;

  /**
   * Reports the round on screen, its result being what the player reports from. Once
   * sent, the round says so, so that coming back to it does not offer it again.
   */
  readonly reporter: RoundReporter = (report) => {
    const game = this.game();
    const round = this.active();
    if (!game || !round?.result) {
      return EMPTY;
    }
    return this.sendReport(game, round, report).pipe(
      tap(() =>
        this.replace({
          ...round,
          result: {
            ...round.result!,
            ...(report.target === 'PHOTO' ? { photoReported: true } : { hintReported: true }),
          },
        }),
      ),
    );
  };

  load(): void {
    this.status.set('loading');
    this.fetch().subscribe({
      next: (game) => this.show(game),
      error: () => this.status.set('error'),
    });
  }

  /** Puts rounds on screen, from the first one left to play. */
  protected show(game: T): void {
    this.game.set(game);
    this.activePosition.set(this.nextAfter(0));
    this.imageFailed.set(false);
    this.status.set('ready');
  }

  firstGuess(guess: number): void {
    this.play((game, round) => this.sendFirstGuess(game, round, guess, this.hintLanguage.value()));
  }

  pickHint(category: HintCategory): void {
    this.play((game, round) => this.sendHint(game, round, category));
  }

  secondGuess(guess: number): void {
    this.play((game, round) => this.sendSecondGuess(game, round, guess));
  }

  /** After a verdict: the next round left to play, or the summary with the score. */
  next(): void {
    const active = this.active();
    const next = active ? this.nextAfter(active.position) : null;
    this.notice.set(null);
    this.imageFailed.set(false);
    if (next === null) {
      this.load();
      return;
    }
    this.activePosition.set(next);
  }

  onImageFailed(): void {
    const position = this.activePosition();
    if (position === null || this.imageRetried.has(position)) {
      this.imageFailed.set(true);
      return;
    }
    // Most likely withdrawn since the page was loaded: the fresh state says so.
    this.imageRetried.add(position);
    this.load();
  }

  private play(call: (game: T, round: RoundView) => Observable<RoundView>): void {
    const game = this.game();
    const round = this.active();
    if (!game || !round || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    call(game, round).subscribe({
      next: (updated) => {
        this.replace(updated);
        this.busy.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.busy.set(false);
        this.handle(error);
      },
    });
  }

  private handle(error: HttpErrorResponse): void {
    const code = error.status === 409 ? (error.error as ApiErrorResponse | null)?.errorCode : null;
    if (code === 'GAME_CLOSED' || code === 'ROUND_NEUTRALIZED' || code === 'WRONG_STEP') {
      // The server knows better (midnight passed, photo withdrawn, played in another tab).
      this.notice.set(
        code === 'GAME_CLOSED'
          ? GAME_TEXT.closed
          : code === 'ROUND_NEUTRALIZED'
            ? GAME_TEXT.removedExplained
            : null,
      );
      this.load();
      return;
    }
    this.error.set(GAME_TEXT.genericError);
  }

  private replace(updated: RoundView): void {
    this.game.update(
      (game) =>
        game && {
          ...game,
          rounds: game.rounds.map((round) =>
            round.position === updated.position ? updated : round,
          ),
        },
    );
  }

  /** The first playable round after this position not finished yet, or null. */
  private nextAfter(position: number): number | null {
    const round = this.game()?.rounds.find(
      (candidate) =>
        candidate.position > position &&
        candidate.status === 'PLAYABLE' &&
        candidate.step !== 'FINISHED',
    );
    return round?.position ?? null;
  }
}

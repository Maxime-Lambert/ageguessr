import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiErrorResponse } from '../../../shared/api-error';
import { GameApi } from '../game-api';
import { HintLanguage } from '../hint-language';
import { HintLanguagePicker } from '../hint-language-picker/hint-language-picker';
import { GAME_TEXT } from '../game-vocabulary';
import { DaySummary } from '../day-summary/day-summary';
import { HintCategory, RoundView, TodayGame } from '../models';
import { Round } from '../round/round';
import { RoundResult } from '../round-result/round-result';

/**
 * Today's game, at /: the rounds one after the other, then the summary. The state lives
 * on the server (game_plays), so a reload picks up where the player was.
 */
@Component({
  imports: [Round, RoundResult, DaySummary, HintLanguagePicker],
  selector: 'app-game',
  templateUrl: './game.html',
})
export class Game {
  private readonly api = inject(GameApi);
  private readonly hintLanguage = inject(HintLanguage);
  /** A photo that failed to load is retried once with a fresh state, then reported. */
  private readonly imageRetried = new Set<number>();

  protected readonly text = GAME_TEXT;

  readonly game = signal<TodayGame | null>(null);
  readonly status = signal<'loading' | 'ready' | 'error'>('loading');
  /** The round on screen, its result included; null shows the summary. */
  readonly activePosition = signal<number | null>(null);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  /** Why the game was reloaded under the player's feet (midnight, a photo withdrawn). */
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

  constructor() {
    this.load();
  }

  load(): void {
    this.status.set('loading');
    this.api.today().subscribe({
      next: (game) => {
        this.game.set(game);
        this.activePosition.set(this.nextAfter(0));
        this.imageFailed.set(false);
        this.status.set('ready');
      },
      error: () => this.status.set('error'),
    });
  }

  firstGuess(guess: number): void {
    this.play((game, round) =>
      this.api.firstGuess(game.date, round.position, guess, this.hintLanguage.value()),
    );
  }

  pickHint(category: HintCategory): void {
    this.play((game, round) => this.api.hint(game.date, round.position, category));
  }

  secondGuess(guess: number): void {
    this.play((game, round) => this.api.secondGuess(game.date, round.position, guess));
  }

  /** After a verdict: the next round left to play, or the summary with the day's score. */
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

  private play(call: (game: TodayGame, round: RoundView) => Observable<RoundView>): void {
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

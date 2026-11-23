import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiErrorResponse } from '../../../shared/api-error';
import { ApiLanguage } from '../../../shared/i18n/language';
import { DaySummary } from '../day-summary/day-summary';
import { seriesLength, shorterSeries } from '../game-vocabulary';
import { HowToPlay } from '../how-to-play/how-to-play';
import { RulesCard } from '../how-to-play/rules-card';
import { Button } from '../../../shared/ui/button/button';
import { Page } from '../../../shared/ui/page/page';
import { HintCategory, RoundReport, RoundView, SERIES_SIZES, Series, SeriesSize } from '../models';
import { RoundPlayer } from '../round-player/round-player';
import { RoundsScreen } from '../rounds-screen';
import { SeriesApi } from '../series-api';
import { ShareChallenge } from '../share-challenge/share-challenge';

/**
 * Free play, at /free: the player picks a length, plays the series with the
 * rules of the daily game (RoundsScreen), then starts another one. An unfinished series
 * is picked up again, from the server, on any device of an account.
 */
@Component({
  imports: [RoundPlayer, DaySummary, ShareChallenge, Button, Page, HowToPlay, RulesCard],
  selector: 'app-free-game',
  templateUrl: './free-game.html',
})
export class FreeGame extends RoundsScreen<Series> {
  private readonly api = inject(SeriesApi);

  protected readonly sizes = SERIES_SIZES;
  protected readonly seriesLength = seriesLength;

  /** No series on screen: the player picks a length. */
  readonly choosing = signal(false);
  readonly starting = signal(false);
  readonly startError = signal<string | null>(null);
  /** The player asked to give the series up: the screen asks them to confirm first. */
  readonly confirmingAbandon = signal(false);
  readonly abandoning = signal(false);
  readonly abandonError = signal<string | null>(null);

  readonly shorter = computed(() => {
    const series = this.game();
    return series && series.rounds.length < series.size
      ? shorterSeries(series.rounds.length)
      : null;
  });

  constructor() {
    super();
    this.api.current().subscribe({
      next: (series) => {
        if (series) {
          this.show(series);
        } else {
          this.choose();
        }
      },
      error: () => this.status.set('error'),
    });
  }

  choose(): void {
    this.confirmingAbandon.set(false);
    this.abandonError.set(null);
    this.game.set(null);
    this.startError.set(null);
    this.choosing.set(true);
    this.status.set('ready');
  }

  start(size: SeriesSize): void {
    if (this.starting()) {
      return;
    }
    this.starting.set(true);
    this.startError.set(null);
    this.api.start(size).subscribe({
      next: (series) => {
        this.starting.set(false);
        this.choosing.set(false);
        this.show(series);
      },
      error: (error: HttpErrorResponse) => {
        this.starting.set(false);
        const code = (error.error as ApiErrorResponse | null)?.errorCode;
        this.startError.set(
          code === 'NO_PHOTOS'
            ? this.text.noPhotos
            : error.status === 429
              ? this.text.tooManySeries
              : this.text.genericError,
        );
      },
    });
  }

  /**
   * Gives up the series on screen (testers, 2026-11-23): it was picked up again on every
   * visit with no way out. Deleted by the API, then back to the choice of a length.
   */
  abandon(): void {
    const series = this.game();
    if (!series || this.abandoning()) {
      return;
    }
    this.abandoning.set(true);
    this.abandonError.set(null);
    this.api.abandon(series.id).subscribe({
      next: () => {
        this.abandoning.set(false);
        this.choose();
      },
      error: () => {
        this.abandoning.set(false);
        this.abandonError.set(this.text.genericError);
      },
    });
  }

  /** The series itself, once on screen: a reload asks for it again by its id. */
  protected fetch(): Observable<Series> {
    return this.api.get(this.game()!.id);
  }

  protected sendFirstGuess(
    series: Series,
    round: RoundView,
    guess: number,
    language: ApiLanguage,
  ): Observable<RoundView> {
    return this.api.firstGuess(series.id, round.position, guess, language);
  }

  protected sendHint(
    series: Series,
    round: RoundView,
    categories: HintCategory[],
  ): Observable<RoundView> {
    return this.api.hint(series.id, round.position, categories);
  }

  protected sendReport(series: Series, round: RoundView, report: RoundReport): Observable<void> {
    return this.api.report(series.id, round.position, report);
  }

  protected sendSecondGuess(
    series: Series,
    round: RoundView,
    guess: number,
  ): Observable<RoundView> {
    return this.api.secondGuess(series.id, round.position, guess);
  }
}

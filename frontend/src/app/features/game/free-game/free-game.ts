import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { ApiErrorResponse } from '../../../shared/api-error';
import { ApiLanguage } from '../../../shared/i18n/language';
import { DaySummary } from '../day-summary/day-summary';
import { seriesLength, shorterSeries } from '../game-vocabulary';
import { HintLanguagePicker } from '../hint-language-picker/hint-language-picker';
import { HintCategory, RoundView, SERIES_SIZES, Series, SeriesSize } from '../models';
import { Round } from '../round/round';
import { RoundResult } from '../round-result/round-result';
import { RoundsScreen } from '../rounds-screen';
import { SeriesApi } from '../series-api';

/**
 * The free investigation, at /free: the player picks a length, plays the series with the
 * rules of the daily game (RoundsScreen), then starts another one. An unfinished series
 * is picked up again, from the server, on any device of an account.
 */
@Component({
  imports: [Round, RoundResult, DaySummary, HintLanguagePicker, RouterLink],
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
        this.startError.set(code === 'NO_PHOTOS' ? this.text.noPhotos : this.text.genericError);
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
    category: HintCategory,
  ): Observable<RoundView> {
    return this.api.hint(series.id, round.position, category);
  }

  protected sendSecondGuess(
    series: Series,
    round: RoundView,
    guess: number,
  ): Observable<RoundView> {
    return this.api.secondGuess(series.id, round.position, guess);
  }
}

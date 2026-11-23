import { Component, DestroyRef, LOCALE_ID, effect, inject, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { ApiLanguage } from '../../../shared/i18n/language';
import { DaySummary } from '../day-summary/day-summary';
import { GameApi } from '../game-api';
import { LucideArrowLeft, LucideShare2 } from '@lucide/angular';
import { Button } from '../../../shared/ui/button/button';
import { Page } from '../../../shared/ui/page/page';
import { DayStats, HintCategory, RoundReport, RoundView, TodayGame } from '../models';
import { SHARE_TARGET } from '../../../shared/share-target';
import { calendarDate, dailyShareText, pastDayTitle } from '../game-vocabulary';
import { dailyShareUrl, shareOrCopy, scoreLines } from '../share';
import { RulesCard } from '../how-to-play/rules-card';
import { NextGameCountdown } from '../next-game-countdown/next-game-countdown';
import { RoundPlayer } from '../round-player/round-player';
import { RoundsScreen } from '../rounds-screen';

/**
 * Today's game, at /, or a past day's, at /day/:date from the calendar: the rounds one
 * after the other, then the summary (RoundsScreen), from the day's rounds (game_plays).
 * A past day has no share (the text speaks of the daily game) and leads back to the
 * calendar instead.
 */
@Component({
  imports: [
    RoundPlayer,
    DaySummary,
    RouterLink,
    Button,
    Page,
    LucideShare2,
    LucideArrowLeft,
    RulesCard,
    NextGameCountdown,
  ],
  selector: 'app-game',
  templateUrl: './game.html',
})
export class Game extends RoundsScreen<TodayGame> {
  private readonly api = inject(GameApi);
  private readonly target = inject(SHARE_TARGET);
  private readonly locale = inject(LOCALE_ID);

  /** The day played from the calendar ("2026-11-12"), null for today's game. */
  protected readonly pastDay = inject(ActivatedRoute).snapshot.paramMap.get('date');
  protected readonly pastDayTitle =
    this.pastDay && pastDayTitle(calendarDate(this.pastDay, this.locale));

  /** What became of the last share: copied says so, a refused copy says it failed. */
  protected readonly shared = signal<'shared' | 'copied' | 'failed' | null>(null);

  /** How the others did, once the summary is on screen; null until then, or on a failure. */
  protected readonly stats = signal<DayStats | null>(null);

  constructor() {
    super();
    this.load();
    // The summary is on screen: the stats of the day, once per day. A failure only leaves
    // them out, the summary stands without them. Cancelled with the page, not on a rerun of
    // the effect: `asked` would keep it from being asked again.
    const destroyRef = inject(DestroyRef);
    let asked: string | null = null;
    effect(() => {
      const game = this.game();
      if (!game || !game.score.complete || this.active() || game.rounds.length === 0) return;
      if (asked === game.date) return;
      asked = game.date;
      untracked(() =>
        this.api
          .stats(game.date)
          .pipe(takeUntilDestroyed(destroyRef))
          .subscribe({
            next: (stats) => this.stats.set(stats),
            error: () => this.stats.set(null),
          }),
      );
    });
  }

  /** The day's result, without the answers (« Partage façon Wordle »), to a friend. */
  shareResult(game: TodayGame): void {
    const text = dailyShareText(
      game.date,
      game.score.points,
      game.score.max,
      scoreLines(game.rounds, { exact: true }),
      game.rounds.filter((round) => round.status !== 'NEUTRALIZED').length,
      dailyShareUrl(this.target.origin()),
      this.locale,
    );
    this.shared.set(null);
    shareOrCopy(this.target, text).then(
      (how) => this.shared.set(how),
      () => this.shared.set('failed'),
    );
  }

  protected fetch(): Observable<TodayGame> {
    return this.pastDay ? this.api.day(this.pastDay) : this.api.today();
  }

  protected sendFirstGuess(
    game: TodayGame,
    round: RoundView,
    guess: number,
    language: ApiLanguage,
  ): Observable<RoundView> {
    return this.api.firstGuess(game.date, round.position, guess, language);
  }

  protected sendHint(
    game: TodayGame,
    round: RoundView,
    categories: HintCategory[],
  ): Observable<RoundView> {
    return this.api.hint(game.date, round.position, categories);
  }

  protected sendReport(game: TodayGame, round: RoundView, report: RoundReport): Observable<void> {
    return this.api.report(game.date, round.position, report);
  }

  protected sendSecondGuess(
    game: TodayGame,
    round: RoundView,
    guess: number,
  ): Observable<RoundView> {
    return this.api.secondGuess(game.date, round.position, guess);
  }
}

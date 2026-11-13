import { Component, LOCALE_ID, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { ApiLanguage } from '../../../shared/i18n/language';
import { Auth } from '../../auth/auth';
import { DaySummary } from '../day-summary/day-summary';
import { GameApi } from '../game-api';
import { LucideClock, LucideShare2 } from '@lucide/angular';
import { Button } from '../../../shared/ui/button/button';
import { Page } from '../../../shared/ui/page/page';
import { HintCategory, RoundReport, RoundView, TodayGame } from '../models';
import { SHARE_TARGET } from '../../../shared/share-target';
import { dailyShareText } from '../game-vocabulary';
import { dailyShareUrl, shareOrCopy, squares } from '../share';
import { RulesCard } from '../how-to-play/rules-card';
import { RoundPlayer } from '../round-player/round-player';
import { RoundsScreen } from '../rounds-screen';

/**
 * Today's game, at /: the rounds one after the other, then the summary (RoundsScreen),
 * from today's rounds (game_plays).
 */
@Component({
  imports: [
    RoundPlayer,
    DaySummary,
    RouterLink,
    Button,
    Page,
    LucideClock,
    LucideShare2,
    RulesCard,
  ],
  selector: 'app-game',
  templateUrl: './game.html',
})
export class Game extends RoundsScreen<TodayGame> {
  private readonly api = inject(GameApi);
  /** A signed-in player can compare the day with their friends (exception front game → auth). */
  protected readonly auth = inject(Auth);
  private readonly target = inject(SHARE_TARGET);
  private readonly locale = inject(LOCALE_ID);

  /** What became of the last share: copied says so, a refused copy says it failed. */
  protected readonly shared = signal<'shared' | 'copied' | 'failed' | null>(null);

  constructor() {
    super();
    this.load();
  }

  /** The day's result, without the answers (« Partage façon Wordle »), to a friend. */
  shareResult(game: TodayGame): void {
    const text = dailyShareText(
      game.date,
      game.score.points,
      game.score.max,
      squares(game.rounds, { exact: true }),
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
    return this.api.today();
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

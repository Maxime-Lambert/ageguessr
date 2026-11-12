import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { ApiLanguage } from '../../../shared/i18n/language';
import { Auth } from '../../auth/auth';
import { DaySummary } from '../day-summary/day-summary';
import { GameApi } from '../game-api';
import { LucideClock } from '@lucide/angular';
import { Button } from '../../../shared/ui/button/button';
import { Page } from '../../../shared/ui/page/page';
import { HintCategory, RoundReport, RoundView, TodayGame } from '../models';
import { RulesCard } from '../how-to-play/rules-card';
import { RoundPlayer } from '../round-player/round-player';
import { RoundsScreen } from '../rounds-screen';

/**
 * Today's game, at /: the rounds one after the other, then the summary (RoundsScreen),
 * from today's rounds (game_plays).
 */
@Component({
  imports: [RoundPlayer, DaySummary, RouterLink, Button, Page, LucideClock, RulesCard],
  selector: 'app-game',
  templateUrl: './game.html',
})
export class Game extends RoundsScreen<TodayGame> {
  private readonly api = inject(GameApi);
  /** A signed-in player can compare the day with their friends (exception front game → auth). */
  protected readonly auth = inject(Auth);

  constructor() {
    super();
    this.load();
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

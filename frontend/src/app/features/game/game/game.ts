import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { ApiLanguage } from '../../../shared/i18n/language';
import { DaySummary } from '../day-summary/day-summary';
import { GameApi } from '../game-api';
import { HintLanguagePicker } from '../hint-language-picker/hint-language-picker';
import { HintCategory, RoundReport, RoundView, TodayGame } from '../models';
import { RoundPlayer } from '../round-player/round-player';
import { RoundsScreen } from '../rounds-screen';

/**
 * Today's game, at /: the rounds one after the other, then the summary (RoundsScreen),
 * from today's rounds (game_plays).
 */
@Component({
  imports: [RoundPlayer, DaySummary, HintLanguagePicker, RouterLink],
  selector: 'app-game',
  templateUrl: './game.html',
})
export class Game extends RoundsScreen<TodayGame> {
  private readonly api = inject(GameApi);

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
    category: HintCategory,
  ): Observable<RoundView> {
    return this.api.hint(game.date, round.position, category);
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

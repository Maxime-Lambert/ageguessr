import { Component, input } from '@angular/core';
import { GAME_TEXT } from '../game-vocabulary';
import { LeaderboardEntry } from '../models';

/** The leaderboard of a challenge: rank, name, points; a guest's name is marked as such. */
@Component({
  selector: 'app-leaderboard',
  templateUrl: './leaderboard.html',
})
export class Leaderboard {
  readonly entries = input.required<LeaderboardEntry[]>();

  protected readonly text = GAME_TEXT;
}

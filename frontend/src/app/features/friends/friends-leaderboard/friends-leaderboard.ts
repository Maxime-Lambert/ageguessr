import { DecimalPipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { FRIENDS_TEXT } from '../friends-text';
import { FriendsLeaderboard as Board } from '../models';

/** The player and their friends by average score per daily file (no minimum of files). */
@Component({
  imports: [DecimalPipe],
  selector: 'app-friends-leaderboard',
  templateUrl: './friends-leaderboard.html',
})
export class FriendsLeaderboard {
  readonly board = input.required<Board>();

  protected readonly text = FRIENDS_TEXT;
  protected readonly alone = computed(() => this.board().players.length <= 1);
}

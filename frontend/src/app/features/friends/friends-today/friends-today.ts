import { DecimalPipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button } from '../../../shared/ui/button/button';
import { FRIENDS_TEXT } from '../friends-text';
import { FriendsToday as Today, TodayPlayer } from '../models';

/** A round's square, stronger with its points out of 1 000, like the profile's calendar. */
function squareClass(points: number): string {
  return points >= 900 ? 'bg-score' : points >= 500 ? 'bg-score/60' : 'bg-score/25';
}

/**
 * Today's scores of the player and their friends: a message until the player's own file is
 * finished (the API sends nothing before), then points only, never an answer.
 */
@Component({
  imports: [DecimalPipe, RouterLink, Button],
  selector: 'app-friends-today',
  templateUrl: './friends-today.html',
})
export class FriendsToday {
  readonly today = input.required<Today>();

  protected readonly text = FRIENDS_TEXT;
  protected readonly squareClass = squareClass;

  protected statusText(player: TodayPlayer): string {
    return player.status === 'IN_PROGRESS' ? this.text.inProgress : this.text.notPlayed;
  }
}

import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Button } from '../../../shared/ui/button/button';
import { Field, TextInput } from '../../../shared/ui/field/field';
import { Page } from '../../../shared/ui/page/page';
import { FriendsApi } from '../friends-api';
import { FriendsLeaderboard } from '../friends-leaderboard/friends-leaderboard';
import { FRIENDS_TEXT, NO_SUCH_PLAYER, friendsError } from '../friends-text';
import { FriendsToday } from '../friends-today/friends-today';
import { Friends, FriendsLeaderboard as Board, FriendsToday as Today, Relation } from '../models';

/**
 * The signed-in player's friends: requests received, today's comparison, the leaderboard,
 * adding a friend (exact username), the friends and the requests sent.
 * Every change reloads the list, today's scores and the leaderboard, which depend on it.
 */
@Component({
  imports: [
    DatePipe,
    ReactiveFormsModule,
    Page,
    Button,
    Field,
    TextInput,
    FriendsToday,
    FriendsLeaderboard,
  ],
  selector: 'app-friends-page',
  templateUrl: './friends-page.html',
})
export class FriendsPage {
  private readonly api = inject(FriendsApi);

  protected readonly text = FRIENDS_TEXT;

  protected readonly status = signal<'loading' | 'ready' | 'error'>('loading');
  protected readonly friends = signal<Friends | null>(null);
  protected readonly today = signal<Today | null>(null);
  protected readonly board = signal<Board | null>(null);

  /** The relation whose action is being sent: its buttons are disabled meanwhile. */
  protected readonly busyId = signal<string | null>(null);
  protected readonly actionError = signal<string | null>(null);
  protected readonly confirmingRemoval = signal<string | null>(null);

  protected readonly username = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required],
  });
  protected readonly addForm = new FormGroup({ username: this.username });
  protected readonly sending = signal(false);
  protected readonly sendError = signal<string | null>(null);
  protected readonly sentTo = signal<string | null>(null);

  constructor() {
    this.load();
  }

  protected load(): void {
    this.status.set('loading');
    this.api.list().subscribe({
      next: (friends) => {
        this.friends.set(friends);
        this.status.set('ready');
      },
      error: () => this.status.set('error'),
    });
    this.loadScores();
  }

  /** Today's scores and the leaderboard: a failure hides their card, the list stays. */
  private loadScores(): void {
    this.api.today().subscribe({
      next: (today) => this.today.set(today),
      error: () => this.today.set(null),
    });
    this.api.leaderboard().subscribe({
      next: (board) => this.board.set(board),
      error: () => this.board.set(null),
    });
  }

  private refresh(): void {
    this.api
      .list()
      .subscribe({ next: (friends) => this.friends.set(friends), error: () => undefined });
    this.loadScores();
  }

  protected send(): void {
    const username = this.username.value.trim();
    if (!username) {
      this.username.markAsTouched();
      this.sendError.set(NO_SUCH_PLAYER);
      return;
    }
    this.sending.set(true);
    this.sendError.set(null);
    this.sentTo.set(null);
    this.api.sendRequest(username).subscribe({
      next: () => {
        this.sending.set(false);
        this.sentTo.set(username);
        this.username.reset();
        this.refresh();
      },
      error: (error: HttpErrorResponse) => {
        this.sending.set(false);
        this.sendError.set(friendsError(error, NO_SUCH_PLAYER));
      },
    });
  }

  protected accept(request: Relation): void {
    this.act(request, this.api.accept(request.id));
  }

  protected deleteRequest(request: Relation): void {
    this.act(request, this.api.deleteRequest(request.id));
  }

  protected remove(friend: Relation): void {
    this.confirmingRemoval.set(null);
    this.act(friend, this.api.remove(friend.id));
  }

  private act(relation: Relation, action: ReturnType<FriendsApi['accept']>): void {
    this.busyId.set(relation.id);
    this.actionError.set(null);
    action.subscribe({
      next: () => {
        this.busyId.set(null);
        this.refresh();
      },
      error: (error: HttpErrorResponse) => {
        this.busyId.set(null);
        this.actionError.set(friendsError(error));
        this.refresh();
      },
    });
  }
}

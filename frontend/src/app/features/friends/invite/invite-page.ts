import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Button } from '../../../shared/ui/button/button';
import { Page } from '../../../shared/ui/page/page';
import { FriendsApi } from '../friends-api';
import { FRIENDS_TEXT, friendsError } from '../friends-text';
import { InvitePreview } from '../models';

/**
 * A friend's invite link (`/friends/invite/:code`): who sent it, then « Add as a friend »,
 * which makes the two friends at once. Account only (authGuard): a guest logs in first and
 * comes back here.
 */
@Component({
  imports: [RouterLink, Page, Button],
  selector: 'app-invite-page',
  templateUrl: './invite-page.html',
})
export class InvitePage {
  private readonly api = inject(FriendsApi);
  private readonly code = inject(ActivatedRoute).snapshot.paramMap.get('code') ?? '';

  protected readonly text = FRIENDS_TEXT;
  protected readonly status = signal<'loading' | 'ready' | 'gone' | 'error' | 'added'>('loading');
  protected readonly preview = signal<InvitePreview | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.load();
  }

  protected load(): void {
    this.status.set('loading');
    this.api.previewInvite(this.code).subscribe({
      next: (preview) => {
        this.preview.set(preview);
        this.status.set('ready');
      },
      error: (error: HttpErrorResponse) => this.status.set(error.status === 404 ? 'gone' : 'error'),
    });
  }

  protected accept(): void {
    this.busy.set(true);
    this.error.set(null);
    this.api.acceptInvite(this.code).subscribe({
      next: () => {
        this.busy.set(false);
        this.status.set('added');
      },
      error: (error: HttpErrorResponse) => {
        this.busy.set(false);
        if (error.status === 404) {
          this.status.set('gone');
          return;
        }
        this.error.set(friendsError(error));
      },
    });
  }
}

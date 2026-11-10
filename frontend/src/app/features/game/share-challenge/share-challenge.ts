import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiErrorResponse } from '../../../shared/api-error';
import { Auth } from '../../auth/auth';
import { GAME_TEXT, nicknameError, shareText } from '../game-vocabulary';
import { LucideUsers } from '@lucide/angular';
import { Button } from '../../../shared/ui/button/button';
import { Field, TextInput } from '../../../shared/ui/field/field';
import { Leaderboard } from '../leaderboard/leaderboard';
import { Challenge, Series } from '../models';
import { SeriesApi } from '../series-api';
import { SHARE_TARGET } from '../../../shared/share-target';
import { NICKNAME_PATTERN, challengeUrl, squares } from '../share';

/**
 * « Challenge friends », at the end of a series its creator played: a guest names
 * themselves first (an account plays under its username), then the link is shared or
 * copied, with the score. Sharing again gives the same link (ShareSeriesHandler).
 */
@Component({
  imports: [ReactiveFormsModule, Leaderboard, Button, Field, TextInput, LucideUsers],
  selector: 'app-share-challenge',
  templateUrl: './share-challenge.html',
})
export class ShareChallenge {
  private readonly api = inject(SeriesApi);
  private readonly auth = inject(Auth);
  private readonly target = inject(SHARE_TARGET);

  readonly series = input.required<Series>();

  protected readonly text = GAME_TEXT;

  readonly open = signal(false);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly copied = signal(false);
  readonly challenge = signal<Challenge | null>(null);

  readonly nickname = new FormControl('', [
    Validators.required,
    Validators.pattern(NICKNAME_PATTERN),
  ]);

  /** A guest names themselves; an account does not, the API ignores it. */
  readonly needsNickname = computed(() => !this.auth.isAuthenticated());
  readonly url = computed(() => {
    const challenge = this.challenge();
    return challenge ? challengeUrl(this.target.origin(), challenge.code) : null;
  });
  readonly canShare = this.target.canShare();

  start(): void {
    this.open.set(true);
    // Already shared: the same code comes back, nothing to type.
    if (this.series().shareCode || !this.needsNickname()) {
      this.create();
    }
  }

  create(): void {
    if (this.busy()) {
      return;
    }
    if (this.needsNickname() && !this.series().shareCode && this.nickname.invalid) {
      this.nickname.markAsTouched();
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    const nickname = this.needsNickname() ? this.nickname.value : null;
    this.api.share(this.series().id, nickname).subscribe({
      next: (challenge) => {
        this.busy.set(false);
        this.challenge.set(challenge);
      },
      error: (error: HttpErrorResponse) => {
        this.busy.set(false);
        // 400: a nickname missing, refused or taken by an account.
        this.error.set(
          error.status === 400
            ? nicknameError((error.error as ApiErrorResponse | null)?.errorCode)
            : this.text.genericError,
        );
      },
    });
  }

  share(): void {
    void this.target.share(this.message()).catch(() => undefined);
  }

  copy(): void {
    this.target.copy(this.message()).then(
      () => this.copied.set(true),
      () => this.error.set(this.text.genericError),
    );
  }

  private message(): string {
    const series = this.series();
    return shareText(series.score.points, series.score.max, squares(series.rounds), this.url()!);
  }
}

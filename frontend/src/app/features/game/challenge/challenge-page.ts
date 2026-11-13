import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { LucideShare2 } from '@lucide/angular';
import { ApiErrorResponse } from '../../../shared/api-error';
import { Button } from '../../../shared/ui/button/button';
import { Field, TextInput } from '../../../shared/ui/field/field';
import { Page } from '../../../shared/ui/page/page';
import { ApiLanguage } from '../../../shared/i18n/language';
import { Auth } from '../../auth/auth';
import { DaySummary } from '../day-summary/day-summary';
import { challengeIntro, nicknameError, shareText } from '../game-vocabulary';
import { Leaderboard } from '../leaderboard/leaderboard';
import { Challenge, HintCategory, RoundReport, RoundView, Series } from '../models';
import { RoundPlayer } from '../round-player/round-player';
import { RoundsScreen } from '../rounds-screen';
import { SeriesApi } from '../series-api';
import { SHARE_TARGET } from '../../../shared/share-target';
import { NICKNAME_PATTERN, challengeUrl, shareOrCopy, squares } from '../share';

/**
 * A challenge, at /challenge/:code: who sent it, then, once joined, its series played with
 * the rules of every game (RoundsScreen), then the leaderboard. One try each: joining
 * again shows the same series where the player left it.
 */
@Component({
  imports: [
    RoundPlayer,
    DaySummary,
    Leaderboard,
    ReactiveFormsModule,
    RouterLink,
    Button,
    Field,
    Page,
    TextInput,
    LucideShare2,
  ],
  selector: 'app-challenge-page',
  templateUrl: './challenge-page.html',
})
export class ChallengePage extends RoundsScreen<Series> {
  private readonly api = inject(SeriesApi);
  private readonly target = inject(SHARE_TARGET);
  protected readonly auth = inject(Auth);

  private readonly code = inject(ActivatedRoute).snapshot.paramMap.get('code') ?? '';

  readonly challenge = signal<Challenge | null>(null);
  readonly lookup = signal<'loading' | 'ready' | 'notFound' | 'error'>('loading');
  readonly joining = signal(false);
  readonly joinError = signal<string | null>(null);
  /** What became of the last share: copied says so, a refused copy says it failed. */
  readonly shared = signal<'shared' | 'copied' | 'failed' | null>(null);

  readonly nickname = new FormControl('', [
    Validators.required,
    Validators.pattern(NICKNAME_PATTERN),
  ]);

  readonly intro = computed(() => {
    const challenge = this.challenge();
    return challenge ? challengeIntro(challenge.creatorName, challenge.rounds) : '';
  });

  constructor() {
    super();
    this.refresh();
  }

  /** The challenge as this visitor sees it, then its series once joined. */
  refresh(): void {
    this.lookup.set('loading');
    this.api.challenge(this.code).subscribe({
      next: (challenge) => this.joined(challenge),
      error: (error: HttpErrorResponse) =>
        this.lookup.set(error.status === 404 ? 'notFound' : 'error'),
    });
  }

  join(): void {
    const guest = !this.auth.isAuthenticated();
    if (this.joining()) {
      return;
    }
    if (guest && this.nickname.invalid) {
      this.nickname.markAsTouched();
      return;
    }
    this.joining.set(true);
    this.joinError.set(null);
    this.api.join(this.code, guest ? this.nickname.value : null).subscribe({
      next: (challenge) => {
        this.joining.set(false);
        this.joined(challenge);
      },
      error: (error: HttpErrorResponse) => {
        this.joining.set(false);
        this.joinError.set(
          error.status === 400
            ? nicknameError((error.error as ApiErrorResponse | null)?.errorCode)
            : error.status === 429
              ? this.text.tooManySeries
              : error.status === 404
                ? this.text.challengeNotFound
                : this.text.genericError,
        );
      },
    });
  }

  shareResult(): void {
    const series = this.game();
    const challenge = this.challenge();
    if (!series || !challenge) {
      return;
    }
    const text = shareText(
      series.score.points,
      series.score.max,
      squares(series.rounds),
      challengeUrl(this.target.origin(), challenge.code),
    );
    this.shared.set(null);
    // Not the round's error: the round player is gone once the summary is on screen.
    shareOrCopy(this.target, text).then(
      (how) => this.shared.set(how),
      () => this.shared.set('failed'),
    );
  }

  /** The series finished: the challenge again, for the leaderboard. */
  protected override show(series: Series): void {
    super.show(series);
    if (series.score.complete && this.challenge()?.status !== 'FINISHED') {
      this.refresh();
    }
  }

  protected fetch(): Observable<Series> {
    return this.api.get(this.challenge()!.seriesId!);
  }

  protected sendFirstGuess(
    series: Series,
    round: RoundView,
    guess: number,
    language: ApiLanguage,
  ): Observable<RoundView> {
    return this.api.firstGuess(series.id, round.position, guess, language);
  }

  protected sendHint(
    series: Series,
    round: RoundView,
    categories: HintCategory[],
  ): Observable<RoundView> {
    return this.api.hint(series.id, round.position, categories);
  }

  protected sendReport(series: Series, round: RoundView, report: RoundReport): Observable<void> {
    return this.api.report(series.id, round.position, report);
  }

  protected sendSecondGuess(
    series: Series,
    round: RoundView,
    guess: number,
  ): Observable<RoundView> {
    return this.api.secondGuess(series.id, round.position, guess);
  }

  private joined(challenge: Challenge): void {
    this.challenge.set(challenge);
    this.lookup.set('ready');
    const series = this.game();
    // Joined: its series, unless it is already on screen and finished (the leaderboard
    // was what changed).
    if (challenge.seriesId && !(series?.score.complete && series.id === challenge.seriesId)) {
      this.load();
    }
  }
}

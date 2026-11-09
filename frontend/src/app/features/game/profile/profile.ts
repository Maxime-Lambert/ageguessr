import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LucideFlame } from '@lucide/angular';
import { Button } from '../../../shared/ui/button/button';
import { Page } from '../../../shared/ui/page/page';
import { Auth } from '../../auth/auth';
import { GameApi } from '../game-api';
import { GAME_TEXT } from '../game-vocabulary';
import { HistoryDay, HistoryStats } from '../models';

/**
 * The signed-in player's own profile: statistics, then the daily games, most recent
 * first. Only for its owner (authGuard, and the API answers 401 to anyone else). No photo:
 * an old image served by today's id would tie it to a known answer.
 */
@Component({
  imports: [RouterLink, DatePipe, DecimalPipe, Page, Button, LucideFlame],
  selector: 'app-profile',
  templateUrl: './profile.html',
})
export class Profile {
  private readonly api = inject(GameApi);
  protected readonly auth = inject(Auth);

  protected readonly status = signal<'loading' | 'ready' | 'error'>('loading');
  protected readonly stats = signal<HistoryStats | null>(null);
  protected readonly days = signal<HistoryDay[]>([]);
  protected readonly hasMore = signal(false);
  protected readonly loadingMore = signal(false);
  protected readonly moreFailed = signal(false);
  private page = 0;

  protected readonly game = GAME_TEXT;
  protected readonly text = {
    title: $localize`:@@profile.title:My profile`,
    currentStreak: $localize`:@@profile.currentStreak:Current streak`,
    bestStreak: $localize`:@@profile.bestStreak:Best streak`,
    days: $localize`:@@profile.days:days in a row`,
    gamesPlayed: $localize`:@@profile.gamesPlayed:Files closed`,
    average: $localize`:@@profile.average:Average score`,
    best: $localize`:@@profile.best:Best score`,
    history: $localize`:@@profile.history:Past files`,
    streakExplained: $localize`:@@profile.streakExplained:A streak counts the days in a row on which you finished the day's file.`,
    empty: $localize`:@@profile.empty:No file closed yet. Your games will show up here.`,
    play: $localize`:@@profile.play:Open today's file`,
    incomplete: $localize`:@@profile.incomplete:Not finished`,
    roundNotFinished: $localize`:@@profile.roundNotFinished:Not finished`,
    years: $localize`:@@profile.years:years old`,
    // The colon is part of the text: French puts a space before it.
    guesses: $localize`:@@profile.guesses:Your guesses:`,
    older: $localize`:@@profile.older:Older files`,
    loadError: $localize`:@@profile.loadError:Your history could not be loaded.`,
  };

  constructor() {
    this.load();
  }

  protected load(): void {
    this.status.set('loading');
    this.api.history(0).subscribe({
      next: (history) => {
        this.page = 0;
        this.stats.set(history.stats);
        this.days.set(history.days);
        this.hasMore.set(history.hasMore);
        this.status.set('ready');
      },
      error: () => this.status.set('error'),
    });
  }

  protected loadOlder(): void {
    this.loadingMore.set(true);
    this.moreFailed.set(false);
    this.api.history(this.page + 1).subscribe({
      next: (history) => {
        this.page = history.page;
        this.days.update((days) => [...days, ...history.days]);
        this.hasMore.set(history.hasMore);
        this.loadingMore.set(false);
      },
      error: () => {
        this.loadingMore.set(false);
        this.moreFailed.set(true);
      },
    });
  }
}

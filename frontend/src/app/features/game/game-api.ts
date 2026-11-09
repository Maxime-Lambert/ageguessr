import { HttpClient } from '@angular/common/http';
import { Service, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiLanguage } from '../../shared/i18n/language';
import { HintCategory, History, RoundReport, RoundView, TodayGame } from './models';

/**
 * The daily game. Public: the auth interceptor adds the bearer token when signed in, and a
 * guest is told apart by the httpOnly ageguessr_guest cookie, which the browser sends on its
 * own (same origin, through the dev proxy and Caddy).
 */
@Service()
export class GameApi {
  private readonly http = inject(HttpClient);

  /**
   * Right after a login: the daily games played as a guest on this browser become the
   * account's. Under /api/game because the guest cookie is only sent there.
   */
  claimGuestPlays(): Observable<{ claimed: number }> {
    return this.http.post<{ claimed: number }>('/api/game/guest-plays/claim', {});
  }

  /** The signed-in account's statistics and one page of its days (401 for a guest). */
  history(page = 0): Observable<History> {
    return this.http.get<History>('/api/game/history', { params: { page } });
  }

  today(): Observable<TodayGame> {
    return this.http.get<TodayGame>('/api/game/today');
  }

  firstGuess(
    date: string,
    position: number,
    guess: number,
    language: ApiLanguage,
  ): Observable<RoundView> {
    return this.http.post<RoundView>(roundUrl(date, position, 'first-guess'), { guess, language });
  }

  hint(date: string, position: number, category: HintCategory): Observable<RoundView> {
    return this.http.post<RoundView>(roundUrl(date, position, 'hint'), { category });
  }

  /** 204, whether new or already sent from this round: nothing to read back. */
  report(date: string, position: number, report: RoundReport): Observable<void> {
    return this.http.post<void>(roundUrl(date, position, 'report'), report);
  }

  secondGuess(date: string, position: number, guess: number): Observable<RoundView> {
    return this.http.post<RoundView>(roundUrl(date, position, 'second-guess'), { guess });
  }
}

function roundUrl(date: string, position: number, step: string): string {
  return `/api/game/${encodeURIComponent(date)}/rounds/${position}/${step}`;
}

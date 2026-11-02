import { HttpClient } from '@angular/common/http';
import { Service, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiLanguage } from '../i18n/language';
import { HintCategory, RoundView, TodayGame } from './models';

/**
 * The daily game. Public: the auth interceptor adds the bearer token when signed in, and a
 * guest is told apart by the httpOnly ageguessr_guest cookie, which the browser sends on its
 * own (same origin, through the dev proxy and Caddy).
 */
@Service()
export class GameApi {
  private readonly http = inject(HttpClient);

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

  secondGuess(date: string, position: number, guess: number): Observable<RoundView> {
    return this.http.post<RoundView>(roundUrl(date, position, 'second-guess'), { guess });
  }
}

function roundUrl(date: string, position: number, step: string): string {
  return `/api/game/${encodeURIComponent(date)}/rounds/${position}/${step}`;
}

import { HttpClient } from '@angular/common/http';
import { Service, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ApiLanguage } from '../../shared/i18n/language';
import { Challenge, HintCategory, RoundView, Series, SeriesSize } from './models';

/**
 * The free series and the challenges. Public, as the daily game (GameApi): a guest is told
 * apart by the httpOnly ageguessr_guest cookie, which the API sets when they start a
 * series or join a challenge.
 */
@Service()
export class SeriesApi {
  private readonly http = inject(HttpClient);

  start(size: SeriesSize): Observable<Series> {
    return this.http.post<Series>('/api/game/series', { size });
  }

  get(id: string): Observable<Series> {
    return this.http.get<Series>(`/api/game/series/${encodeURIComponent(id)}`);
  }

  /** The series left unfinished, or null (204). */
  current(): Observable<Series | null> {
    return this.http
      .get<Series>('/api/game/series/current', { observe: 'response' })
      .pipe(map((response) => (response.status === 204 ? null : response.body)));
  }

  firstGuess(
    id: string,
    position: number,
    guess: number,
    language: ApiLanguage,
  ): Observable<RoundView> {
    return this.http.post<RoundView>(roundUrl(id, position, 'first-guess'), { guess, language });
  }

  hint(id: string, position: number, category: HintCategory): Observable<RoundView> {
    return this.http.post<RoundView>(roundUrl(id, position, 'hint'), { category });
  }

  secondGuess(id: string, position: number, guess: number): Observable<RoundView> {
    return this.http.post<RoundView>(roundUrl(id, position, 'second-guess'), { guess });
  }

  /** @param nickname required for a guest, ignored for an account */
  share(id: string, nickname: string | null): Observable<Challenge> {
    return this.http.post<Challenge>(`/api/game/series/${encodeURIComponent(id)}/share`, {
      nickname,
    });
  }

  challenge(code: string): Observable<Challenge> {
    return this.http.get<Challenge>(`/api/game/challenges/${encodeURIComponent(code)}`);
  }

  /** @param nickname required for a guest, ignored for an account */
  join(code: string, nickname: string | null): Observable<Challenge> {
    return this.http.post<Challenge>(`/api/game/challenges/${encodeURIComponent(code)}/join`, {
      nickname,
    });
  }
}

function roundUrl(id: string, position: number, step: string): string {
  return `/api/game/series/${encodeURIComponent(id)}/rounds/${position}/${step}`;
}

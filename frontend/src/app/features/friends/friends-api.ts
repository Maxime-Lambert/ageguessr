import { HttpClient } from '@angular/common/http';
import { Service, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { Friends, FriendsLeaderboard, FriendsToday } from './models';

/**
 * The friends of the signed-in account (every endpoint answers 401 to a guest). Also holds
 * the number of requests waiting for an answer, which the header shows: refreshed when the
 * header sees a session, and each time the list is read.
 */
@Service()
export class FriendsApi {
  private readonly http = inject(HttpClient);

  readonly incomingCount = signal(0);

  list(): Observable<Friends> {
    return this.http
      .get<Friends>('/api/friends')
      .pipe(tap((friends) => this.incomingCount.set(friends.incoming.length)));
  }

  /** Reads the list for its count only; a failure leaves the badge as it was. */
  refreshCount(): void {
    this.list().subscribe({ error: () => undefined });
  }

  sendRequest(username: string): Observable<void> {
    return this.http.post<void>('/api/friends/requests', { username });
  }

  accept(requestId: string): Observable<void> {
    return this.http.post<void>(
      `/api/friends/requests/${encodeURIComponent(requestId)}/accept`,
      {},
    );
  }

  /** Declines a request received, or cancels one sent. */
  deleteRequest(requestId: string): Observable<void> {
    return this.http.delete<void>(`/api/friends/requests/${encodeURIComponent(requestId)}`);
  }

  remove(friendshipId: string): Observable<void> {
    return this.http.delete<void>(`/api/friends/${encodeURIComponent(friendshipId)}`);
  }

  today(): Observable<FriendsToday> {
    return this.http.get<FriendsToday>('/api/friends/today');
  }

  leaderboard(): Observable<FriendsLeaderboard> {
    return this.http.get<FriendsLeaderboard>('/api/friends/leaderboard');
  }
}

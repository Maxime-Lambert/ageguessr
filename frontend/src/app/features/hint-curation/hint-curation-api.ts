import { HttpClient } from '@angular/common/http';
import { Service, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { HintCoverage, HintReview, HintReviewResult, PendingHints } from './models';

/** Admin API. Every call carries the bearer token (auth interceptor); the API checks the role. */
@Service()
export class HintCurationApi {
  private readonly http = inject(HttpClient);

  pending(limit: number): Observable<PendingHints> {
    return this.http.get<PendingHints>('/api/admin/hints/pending', { params: { limit } });
  }

  review(hintId: string, review: HintReview): Observable<HintReviewResult> {
    return this.http.put<HintReviewResult>(
      `/api/admin/hints/${encodeURIComponent(hintId)}/review`,
      review,
    );
  }

  coverage(): Observable<HintCoverage> {
    return this.http.get<HintCoverage>('/api/admin/hints/coverage');
  }
}

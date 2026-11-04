import { HttpClient } from '@angular/common/http';
import { Service, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { HintCoverage, HintReview, HintReviewResult, PendingHints, ReportedHint } from './models';

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

  reported(): Observable<ReportedHint[]> {
    return this.http
      .get<{ items: ReportedHint[] }>('/api/admin/hints/reported')
      .pipe(map((response) => response.items));
  }

  /** Keep the hint as it is: its open reports are closed. */
  dismissReports(hintId: string): Observable<void> {
    return this.http.post<void>(
      `/api/admin/hints/${encodeURIComponent(hintId)}/reports/dismiss`,
      null,
    );
  }

  coverage(): Observable<HintCoverage> {
    return this.http.get<HintCoverage>('/api/admin/hints/coverage');
  }
}

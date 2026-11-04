import { HttpClient } from '@angular/common/http';
import { Service, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import {
  ExclusionResult,
  PendingPhotos,
  PersonSearchResult,
  ReportedPhoto,
  ReviewDecision,
  ReviewResult,
} from './models';

/** Admin API. Every call carries the bearer token (auth interceptor); the API checks the role. */
@Service()
export class CurationApi {
  private readonly http = inject(HttpClient);

  pending(limit: number): Observable<PendingPhotos> {
    return this.http.get<PendingPhotos>('/api/admin/photos/pending', { params: { limit } });
  }

  /**
   * As a Blob, through HttpClient, rather than an <img src>: a browser never sends the
   * Authorization header for an image tag, and these photos are admin-only.
   */
  image(photoId: string): Observable<Blob> {
    return this.http.get(`/api/admin/photos/${encodeURIComponent(photoId)}/image`, {
      responseType: 'blob',
    });
  }

  review(photoId: string, decision: ReviewDecision): Observable<ReviewResult> {
    return this.http.put<ReviewResult>(`/api/admin/photos/${encodeURIComponent(photoId)}/review`, {
      decision,
    });
  }

  reported(): Observable<ReportedPhoto[]> {
    return this.http
      .get<{ items: ReportedPhoto[] }>('/api/admin/photos/reported')
      .pipe(map((response) => response.items));
  }

  /** Keep the photo as it is: its open reports are closed. */
  dismissReports(photoId: string): Observable<void> {
    return this.http.post<void>(
      `/api/admin/photos/${encodeURIComponent(photoId)}/reports/dismiss`,
      null,
    );
  }

  searchPeople(query: string): Observable<PersonSearchResult[]> {
    return this.http
      .get<{ people: PersonSearchResult[] }>('/api/admin/people', { params: { query } })
      .pipe(map((response) => response.people));
  }

  exclude(personId: string): Observable<ExclusionResult> {
    return this.http.post<ExclusionResult>(
      `/api/admin/people/${encodeURIComponent(personId)}/exclusion`,
      null,
    );
  }
}

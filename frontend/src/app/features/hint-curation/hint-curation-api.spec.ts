import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { HintCurationApi } from './hint-curation-api';
import { coverage } from './testing';

describe('HintCurationApi', () => {
  let api: HintCurationApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(HintCurationApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('asks for a batch of pending hints', () => {
    api.pending(20).subscribe();

    const req = http.expectOne('/api/admin/hints/pending?limit=20');
    expect(req.request.method).toBe('GET');
    req.flush({ totalPending: 0, items: [] });
  });

  it('sends a review with the corrected text', () => {
    api.review('h 1', { decision: 'APPROVED', textFr: 'Corrigé.' }).subscribe();

    const req = http.expectOne('/api/admin/hints/h%201/review');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ decision: 'APPROVED', textFr: 'Corrigé.' });
    req.flush({ hintId: 'h 1', reviewStatus: 'APPROVED', textEn: null, textFr: 'Corrigé.' });
  });

  it('reads the coverage', () => {
    let gaps = -1;
    api.coverage().subscribe((result) => (gaps = result.gaps));

    http.expectOne('/api/admin/hints/coverage').flush(coverage({ gaps: 3 }));

    expect(gaps).toBe(3);
  });
});

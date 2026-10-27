import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CurationApi } from './curation-api';

describe('CurationApi', () => {
  let api: CurationApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(CurationApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('asks for a batch of pending photos', () => {
    api.pending(20).subscribe();

    const req = http.expectOne('/api/admin/photos/pending?limit=20');
    expect(req.request.method).toBe('GET');
    req.flush({ totalPending: 0, items: [] });
  });

  it('fetches an image as a blob, so that the bearer token goes with it', () => {
    api.image('p 1').subscribe();

    const req = http.expectOne('/api/admin/photos/p%201/image');
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob());
  });

  it('sends a review decision', () => {
    api.review('p1', 'REJECTED').subscribe();

    const req = http.expectOne('/api/admin/photos/p1/review');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ decision: 'REJECTED' });
    req.flush({ photoId: 'p1', reviewStatus: 'REJECTED' });
  });

  it('searches people and returns the list', () => {
    let names: string[] = [];
    api.searchPeople('Jane').subscribe((people) => (names = people.map((p) => p.nameEn ?? '')));

    http.expectOne('/api/admin/people?query=Jane').flush({
      people: [
        {
          id: '1',
          wikidataId: 'Q1',
          nameEn: 'Jane',
          nameFr: null,
          birthDate: '1950-01-01',
          deathDate: null,
          excluded: false,
          photoCount: 2,
        },
      ],
    });
    expect(names).toEqual(['Jane']);
  });

  it('excludes a person', () => {
    api.exclude('person-1').subscribe();

    const req = http.expectOne('/api/admin/people/person-1/exclusion');
    expect(req.request.method).toBe('POST');
    req.flush({ personId: 'person-1', deletedPhotos: 2 });
  });
});

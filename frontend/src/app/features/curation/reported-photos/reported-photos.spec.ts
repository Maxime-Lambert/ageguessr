import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReportedPhoto } from '../models';
import { ReportedPhotos } from './reported-photos';

function reported(photoId: string, overrides: Partial<ReportedPhoto> = {}): ReportedPhoto {
  return {
    photoId,
    reviewStatus: 'APPROVED',
    ageAtPhoto: 40,
    sourceUrl: `https://commons.wikimedia.org/wiki/File:${photoId}.jpg`,
    person: {
      id: `person-${photoId}`,
      wikidataId: 'Q42',
      nameEn: `Person ${photoId}`,
      nameFr: null,
    },
    reasons: { WRONG_PERSON: 2, LOOKS_MINOR: 1 },
    total: 3,
    firstReportedAt: '2026-11-03T08:00:00Z',
    inTodaysGame: false,
    ...overrides,
  };
}

describe('ReportedPhotos', () => {
  let fixture: ComponentFixture<ReportedPhotos>;
  let http: HttpTestingController;
  let page: HTMLElement;

  beforeEach(() => {
    URL.createObjectURL = vi.fn(() => 'blob:test');
    URL.revokeObjectURL = vi.fn();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ReportedPhotos);
    page = fixture.nativeElement as HTMLElement;
  });

  afterEach(() => http.verify());

  function start(items: ReportedPhoto[]): void {
    http.expectOne('/api/admin/photos/reported').flush({ items });
    items.forEach((item) =>
      http.expectOne(`/api/admin/photos/${item.photoId}/image`).flush(new Blob(['x'])),
    );
    fixture.detectChanges();
  }

  function button(text: string): HTMLButtonElement {
    return Array.from(page.querySelectorAll('button')).find((b) =>
      b.textContent?.trim().startsWith(text),
    )!;
  }

  it('lists the reported photos with their reasons, today first in sight', () => {
    start([reported('p1', { inTodaysGame: true }), reported('p2', { reasons: { OTHER: 1 } })]);

    const items = page.querySelectorAll('li');
    expect(items).toHaveLength(2);
    expect(items[0].textContent).toContain('Person p1');
    expect(items[0].textContent).toContain('Wrong person (2), Looks under 18 (1)');
    expect(items[0].textContent).toContain("In today's game");
    expect(items[1].textContent).not.toContain("In today's game");
    expect(items[0].querySelector('img')?.getAttribute('src')).toBe('blob:test');
  });

  it('says so when nothing is reported', () => {
    start([]);
    expect(page.textContent).toContain('No photo reported.');
  });

  it('rejects a photo and takes it off the list', () => {
    start([reported('p1')]);

    button('Reject').click();
    const req = http.expectOne('/api/admin/photos/p1/review');
    expect(req.request.body).toEqual({ decision: 'REJECTED' });
    req.flush({ photoId: 'p1', reviewStatus: 'REJECTED' });
    fixture.detectChanges();

    expect(page.querySelectorAll('li')).toHaveLength(0);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:test');
  });

  it('keeps a photo by closing its reports', () => {
    start([reported('p1')]);

    button('Keep').click();
    const req = http.expectOne('/api/admin/photos/p1/reports/dismiss');
    expect(req.request.method).toBe('POST');
    req.flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();

    expect(page.querySelectorAll('li')).toHaveLength(0);
  });

  it('excludes the person on the second click only, and says so', () => {
    let excluded = 0;
    fixture.componentInstance.excluded.subscribe(() => excluded++);
    start([reported('p1')]);

    button('Exclude the person').click();
    fixture.detectChanges();
    expect(button('Confirm: exclude Person p1')).toBeDefined();
    http.expectNone('/api/admin/people/person-p1/exclusion');

    button('Confirm').click();
    http
      .expectOne('/api/admin/people/person-p1/exclusion')
      .flush({ personId: 'person-p1', deletedPhotos: 2 });
    fixture.detectChanges();

    expect(page.querySelectorAll('li')).toHaveLength(0);
    expect(excluded).toBe(1);
  });

  it('keeps the photo on the list when an action fails', () => {
    start([reported('p1')]);

    button('Keep').click();
    http
      .expectOne('/api/admin/photos/p1/reports/dismiss')
      .flush(null, { status: 500, statusText: 'Error' });
    fixture.detectChanges();

    expect(page.querySelectorAll('li')).toHaveLength(1);
    expect(page.querySelector('[role=alert]')?.textContent).toContain('Something went wrong');
  });

  it('offers a retry when the list cannot be loaded', () => {
    http.expectOne('/api/admin/photos/reported').flush(null, { status: 500, statusText: 'Error' });
    fixture.detectChanges();

    button('Retry').click();
    http.expectOne('/api/admin/photos/reported').flush({ items: [] });
    fixture.detectChanges();
    expect(page.textContent).toContain('No photo reported.');
  });
});

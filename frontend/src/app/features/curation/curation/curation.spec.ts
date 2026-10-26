import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PendingPhoto } from '../models';
import { pendingPhoto } from '../testing';
import { Curation } from './curation';

describe('Curation', () => {
  let fixture: ComponentFixture<Curation>;
  let http: HttpTestingController;
  let page: HTMLElement;
  let createdUrls: string[];
  let revokedUrls: string[];

  beforeEach(() => {
    createdUrls = [];
    revokedUrls = [];
    let counter = 0;
    // jsdom implements neither: record what the component creates and frees.
    URL.createObjectURL = vi.fn(() => {
      const url = `blob:test-${++counter}`;
      createdUrls.push(url);
      return url;
    });
    URL.revokeObjectURL = vi.fn((url: string) => revokedUrls.push(url));

    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Curation);
    page = fixture.nativeElement as HTMLElement;
  });

  afterEach(() => http.verify());

  function start(items: PendingPhoto[], total = items.length): void {
    http.expectOne('/api/admin/photos/pending?limit=20').flush({ totalPending: total, items });
    items.slice(0, 2).forEach((item) => {
      http.expectOne(`/api/admin/photos/${item.photoId}/image`).flush(new Blob(['x']));
    });
    fixture.detectChanges();
  }

  function press(key: string, target: EventTarget = document.body): void {
    target.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true }));
    fixture.detectChanges();
  }

  function title(): string | null | undefined {
    return page.querySelector('h2#photo-title')?.textContent?.trim();
  }

  function expectReview(photoId: string, decision: string): void {
    const req = http.expectOne(`/api/admin/photos/${photoId}/review`);
    expect(req.request.body).toEqual({ decision });
    req.flush({ photoId, reviewStatus: decision });
  }

  /** Swallows the refill a short queue triggers, with nothing new in it. */
  function refillWithNothingNew(total: number): void {
    http.expectOne('/api/admin/photos/pending?limit=20').flush({ totalPending: total, items: [] });
  }

  it('shows the first photo with what is needed to judge it', () => {
    start([
      pendingPhoto('p1', {
        otherPhotos: [
          { photoId: 'p9', takenFrom: '1990-01-01', ageAtPhoto: 39, reviewStatus: 'APPROVED' },
        ],
      }),
      pendingPhoto('p2'),
    ]);

    expect(title()).toBe('Person p1');
    expect(page.textContent).toContain('2 left to review');
    expect(page.textContent).toContain('14 July 1976');
    expect(page.textContent).toContain('10 March 1950');
    expect(page.textContent).toContain('1990 · age 39 · approved');
    expect(page.querySelector('img')?.getAttribute('src')).toBe('blob:test-1');
    expect(page.querySelector('a[href*="commons.wikimedia.org"]')?.textContent).toContain(
      'Photo p1.jpg',
    );
  });

  it('approves with A and moves to the next photo', () => {
    start([pendingPhoto('p1'), pendingPhoto('p2')]);

    press('a');
    expectReview('p1', 'APPROVED');
    refillWithNothingNew(1);
    fixture.detectChanges();

    // p2's image was preloaded with p1's: no new request, no wait.
    expect(title()).toBe('Person p2');
    expect(page.textContent).toContain('1 left to review');
  });

  it('rejects with R', () => {
    start([pendingPhoto('p1')]);

    press('R');
    expectReview('p1', 'REJECTED');
    refillWithNothingNew(0);
    fixture.detectChanges();

    expect(page.textContent).toContain('Nothing left to review.');
  });

  it('skips with the right arrow without deciding anything', () => {
    start([pendingPhoto('p1'), pendingPhoto('p2')]);

    press('ArrowRight');
    refillWithNothingNew(2);
    fixture.detectChanges();

    expect(title()).toBe('Person p2');
    expect(page.textContent).toContain('2 left to review');
  });

  it('cannot approve a living person while it is disabled, but can reject them', () => {
    start([
      pendingPhoto('p1', {
        approvable: false,
        person: { ...pendingPhoto('p1').person, deathDate: null },
      }),
    ]);

    const approve = [...page.querySelectorAll('button')].find((b) =>
      b.textContent?.includes('Approve'),
    );
    expect(approve?.disabled).toBe(true);
    expect(page.textContent).toContain('Living person: approving is disabled');
    expect(page.textContent).toContain('Alive');

    press('a');
    http.expectNone('/api/admin/photos/p1/review');

    press('r');
    expectReview('p1', 'REJECTED');
    refillWithNothingNew(0);
  });

  it('undoes the last decision with U, putting the photo back on top', () => {
    start([pendingPhoto('p1'), pendingPhoto('p2')]);
    press('a');
    expectReview('p1', 'APPROVED');
    refillWithNothingNew(1);

    press('u');
    expectReview('p1', 'PENDING');
    http.expectOne('/api/admin/photos/p1/image').flush(new Blob(['x']));
    fixture.detectChanges();

    expect(title()).toBe('Person p1');
    expect(page.textContent).toContain('2 left to review');
  });

  it('ignores shortcuts typed in the search field', () => {
    start([pendingPhoto('p1')]);
    const input = page.querySelector('input#exclusion-query') as HTMLInputElement;

    press('a', input);
    press('r', input);

    http.expectNone('/api/admin/photos/p1/review');
    expect(title()).toBe('Person p1');
  });

  it('ignores shortcuts with a modifier key, such as Ctrl+R to reload', () => {
    start([pendingPhoto('p1')]);

    document.body.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'r', ctrlKey: true, bubbles: true }),
    );

    http.expectNone('/api/admin/photos/p1/review');
  });

  it('explains why a decision was refused and keeps the photo', () => {
    start([pendingPhoto('p1')]);

    press('a');
    http
      .expectOne('/api/admin/photos/p1/review')
      .flush(
        { errorCode: 'PHOTO_SLOT_TAKEN', message: 'taken' },
        { status: 409, statusText: 'Conflict' },
      );
    fixture.detectChanges();

    expect(page.querySelector('[role="alert"]')?.textContent).toContain(
      'Another photo of this person already has this age or year.',
    );
    expect(title()).toBe('Person p1');
  });

  it('frees the image of a photo once it has left the screen', () => {
    start([pendingPhoto('p1'), pendingPhoto('p2')]);

    press('r');
    expectReview('p1', 'REJECTED');
    refillWithNothingNew(1);

    expect(revokedUrls).toContain('blob:test-1');
  });

  it('asks for more photos when few are left, without duplicates', () => {
    start([pendingPhoto('p1'), pendingPhoto('p2')], 4);

    press('a');
    expectReview('p1', 'APPROVED');
    http.expectOne('/api/admin/photos/pending?limit=20').flush({
      totalPending: 3,
      items: [pendingPhoto('p2'), pendingPhoto('p3'), pendingPhoto('p4')],
    });
    http.expectOne('/api/admin/photos/p3/image').flush(new Blob(['x']));
    fixture.detectChanges();

    press('ArrowRight');
    fixture.detectChanges();
    expect(title()).toBe('Person p3');
    http.expectOne('/api/admin/photos/p4/image').flush(new Blob(['x']));
    http.expectOne('/api/admin/photos/pending?limit=20').flush({ totalPending: 3, items: [] });
  });

  it('says so when there is nothing to review', () => {
    start([]);

    expect(page.textContent).toContain('Nothing left to review.');
  });

  it('offers a retry when the queue cannot be loaded', () => {
    http
      .expectOne('/api/admin/photos/pending?limit=20')
      .flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(page.textContent).toContain('The queue could not be loaded.');
  });
});

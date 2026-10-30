import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PendingHint } from '../models';
import { coverage, pendingHint } from '../testing';
import { HintCuration } from './hint-curation';

describe('HintCuration', () => {
  let fixture: ComponentFixture<HintCuration>;
  let http: HttpTestingController;
  let page: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(HintCuration);
    page = fixture.nativeElement as HTMLElement;
  });

  afterEach(() => http.verify());

  function start(items: PendingHint[], total = items.length): void {
    http.expectOne('/api/admin/hints/pending?limit=20').flush({ totalPending: total, items });
    flushCoverage();
  }

  function flushCoverage(gaps = 2): void {
    http.expectOne('/api/admin/hints/coverage').flush(coverage({ gaps }));
    fixture.detectChanges();
  }

  function press(key: string, target: EventTarget = document.body): void {
    target.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true }));
    fixture.detectChanges();
  }

  function frenchText(): HTMLTextAreaElement {
    return page.querySelector('textarea') as HTMLTextAreaElement;
  }

  function expectReview(hintId: string, body: object, textFr = 'Découverte.'): void {
    const req = http.expectOne(`/api/admin/hints/${hintId}/review`);
    expect(req.request.body).toEqual(body);
    req.flush({ hintId, reviewStatus: 'APPROVED', textEn: null, textFr });
  }

  function refillWithNothingNew(total: number): void {
    http.expectOne('/api/admin/hints/pending?limit=20').flush({ totalPending: total, items: [] });
  }

  it('shows a hint with what is needed to judge it', () => {
    start([pendingHint('h1')], 12);

    expect(page.querySelector('h2#hint-title')?.textContent).toContain('Science · 1976');
    expect(page.textContent).toContain('12 left to review');
    expect(frenchText().value).toBe('Découverte h1.');
    expect(page.querySelector('a')?.getAttribute('href')).toBe('https://en.wikipedia.org/wiki/h1');
    expect(page.querySelectorAll('textarea')).toHaveLength(1);
  });

  it('shows both texts of a hint for everyone', () => {
    start([pendingHint('h1', { audience: 'ALL', textEn: 'A discovery.' })]);

    expect(page.querySelectorAll('textarea')).toHaveLength(2);
  });

  it('approves from the keyboard, then shows the next hint and the new coverage', () => {
    start([pendingHint('h1'), pendingHint('h2')]);

    press('a');
    expectReview('h1', { decision: 'APPROVED' });
    refillWithNothingNew(1);
    flushCoverage(1);

    expect(page.querySelector('h2#hint-title')).not.toBeNull();
    expect(frenchText().value).toBe('Découverte h2.');
    expect(page.textContent).toContain('1 left to review');
    expect(page.textContent).toContain('1 gaps');
  });

  it('rejects with R', () => {
    start([pendingHint('h1')]);

    press('r');
    expectReview('h1', { decision: 'REJECTED' });
    refillWithNothingNew(0);
    flushCoverage();

    expect(page.textContent).toContain('Nothing left to review.');
  });

  it('sends a corrected text, and only that one', () => {
    start([pendingHint('h1', { audience: 'ALL', textEn: 'A discovery.' })]);
    const [fr] = Array.from(page.querySelectorAll('textarea'));

    fr.value = 'Corrigé.';
    fr.dispatchEvent(new Event('input'));
    press('a');

    expectReview('h1', { decision: 'APPROVED', textFr: 'Corrigé.' }, 'Corrigé.');
    refillWithNothingNew(0);
    flushCoverage();
  });

  it('never decides while the admin types in a text', () => {
    start([pendingHint('h1')]);

    press('a', frenchText());
    press('r', frenchText());

    http.expectNone('/api/admin/hints/h1/review');
    expect(frenchText().value).toBe('Découverte h1.');
  });

  it('explains a text refused by the API and keeps the hint', () => {
    start([pendingHint('h1')]);

    press('a');
    http
      .expectOne('/api/admin/hints/h1/review')
      .flush(
        { errorCode: 'HINT_TEXT_CONTAINS_YEAR', message: 'x' },
        { status: 400, statusText: 'Bad Request' },
      );
    fixture.detectChanges();

    expect(page.querySelector('[role="alert"]')?.textContent).toContain('cannot contain a year');
    expect(frenchText().value).toBe('Découverte h1.');
  });

  it('says so when a hint no longer exists', () => {
    start([pendingHint('h1')]);

    press('a');
    http
      .expectOne('/api/admin/hints/h1/review')
      .flush({ errorCode: 'NOT_FOUND', message: 'x' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(page.querySelector('[role="alert"]')?.textContent).toContain('no longer exists');
  });

  it('skips a hint without deciding, for the session', () => {
    start([pendingHint('h1'), pendingHint('h2')]);

    press('ArrowRight');
    http
      .expectOne('/api/admin/hints/pending?limit=20')
      .flush({ totalPending: 2, items: [pendingHint('h1'), pendingHint('h2')] });
    fixture.detectChanges();

    expect(frenchText().value).toBe('Découverte h2.');
    http.expectNone('/api/admin/hints/h1/review');
  });

  it('undoes the last decision, with the corrected text', () => {
    start([pendingHint('h1')]);
    frenchText().value = 'Corrigé.';
    frenchText().dispatchEvent(new Event('input'));
    press('a');
    expectReview('h1', { decision: 'APPROVED', textFr: 'Corrigé.' }, 'Corrigé.');
    refillWithNothingNew(0);
    flushCoverage();

    press('u');
    const req = http.expectOne('/api/admin/hints/h1/review');
    expect(req.request.body).toEqual({ decision: 'PENDING' });
    req.flush({ hintId: 'h1', reviewStatus: 'PENDING', textEn: null, textFr: 'Corrigé.' });
    flushCoverage();

    expect(frenchText().value).toBe('Corrigé.');
    expect(page.textContent).toContain('1 left to review');
  });

  it('marks the cells under the minimum', () => {
    start([]);

    const cells = Array.from(page.querySelectorAll('td'));
    const music = cells[0];
    const science = cells[3];
    const film = cells[1];

    expect(music.textContent?.trim()).toBe('10 / 10');
    expect(music.classList.contains('text-danger')).toBe(false);
    expect(science.textContent?.trim()).toBe('4 / 5');
    expect(science.classList.contains('text-danger')).toBe(true);
    expect(film.textContent?.trim()).toBe('0 / 0');
    expect(film.classList.contains('text-danger')).toBe(true);
    expect(page.textContent).toContain('2 gaps');
  });

  it('offers to retry when the queue fails', () => {
    http
      .expectOne('/api/admin/hints/pending?limit=20')
      .flush(null, { status: 500, statusText: 'Error' });
    flushCoverage();

    (page.querySelector('button') as HTMLButtonElement).click();

    http.expectOne('/api/admin/hints/pending?limit=20').flush({ totalPending: 0, items: [] });
    fixture.detectChanges();
    expect(page.textContent).toContain('Nothing left to review.');
  });

  it('offers to retry when the coverage fails', () => {
    http.expectOne('/api/admin/hints/pending?limit=20').flush({ totalPending: 0, items: [] });
    http.expectOne('/api/admin/hints/coverage').flush(null, { status: 500, statusText: 'Error' });
    fixture.detectChanges();

    expect(page.textContent).toContain('The coverage could not be loaded.');
    (Array.from(page.querySelectorAll('button')).at(-1) as HTMLButtonElement).click();

    flushCoverage();
    expect(page.textContent).toContain('Coverage');
    expect(page.textContent).not.toContain('could not be loaded');
  });
});

import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReportedHint } from '../models';
import { ReportedHints } from './reported-hints';

function reported(hintId: string, overrides: Partial<ReportedHint> = {}): ReportedHint {
  return {
    hintId,
    reviewStatus: 'APPROVED',
    category: 'MUSIC',
    year: 1986,
    audience: 'ALL',
    textEn: 'A hit topped the charts.',
    textFr: 'Un tube a dominé les classements.',
    sourceUrl: 'https://en.wikipedia.org/wiki/Hit',
    reasons: { WRONG_YEAR: 2, UNCLEAR: 1 },
    total: 3,
    firstReportedAt: '2026-11-03T08:00:00Z',
    ...overrides,
  };
}

describe('ReportedHints', () => {
  let fixture: ComponentFixture<ReportedHints>;
  let http: HttpTestingController;
  let page: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ReportedHints);
    page = fixture.nativeElement as HTMLElement;
  });

  afterEach(() => http.verify());

  function start(items: ReportedHint[]): void {
    http.expectOne('/api/admin/hints/reported').flush({ items });
    fixture.detectChanges();
  }

  function button(text: string): HTMLButtonElement {
    return Array.from(page.querySelectorAll('button')).find((b) => b.textContent?.trim() === text)!;
  }

  function type(index: number, text: string): void {
    const area = page.querySelectorAll<HTMLTextAreaElement>('textarea')[index];
    area.value = text;
    area.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  it('lists the reported hints with their reasons and the texts of their audience', () => {
    start([reported('h1'), reported('h2', { audience: 'FR', textEn: null })]);

    const items = page.querySelectorAll('li');
    expect(items).toHaveLength(2);
    expect(items[0].textContent).toContain('MUSIC · 1986');
    expect(items[0].textContent).toContain('Wrong year (2), Unclear (1)');
    expect(items[0].querySelectorAll('textarea')).toHaveLength(2);
    expect(items[1].querySelectorAll('textarea')).toHaveLength(1);
  });

  it('saves a correction of the texts the audience reads, and approves', () => {
    start([reported('h1', { audience: 'FR', textEn: null })]);

    type(0, 'Un tube corrigé.');
    button('Save and approve').click();
    const req = http.expectOne('/api/admin/hints/h1/review');
    expect(req.request.body).toEqual({ decision: 'APPROVED', textFr: 'Un tube corrigé.' });
    req.flush({ hintId: 'h1', reviewStatus: 'APPROVED', textEn: null, textFr: 'Un tube corrigé.' });
    fixture.detectChanges();

    expect(page.querySelectorAll('li')).toHaveLength(0);
  });

  it('rejects a hint, or keeps it by closing its reports', () => {
    start([reported('h1'), reported('h2')]);

    button('Reject').click();
    const reject = http.expectOne('/api/admin/hints/h1/review');
    expect(reject.request.body).toEqual({ decision: 'REJECTED' });
    reject.flush({ hintId: 'h1', reviewStatus: 'REJECTED', textEn: null, textFr: null });
    fixture.detectChanges();

    button('Keep').click();
    http
      .expectOne('/api/admin/hints/h2/reports/dismiss')
      .flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();

    expect(page.textContent).toContain('No hint reported.');
  });

  it('explains a correction refused for containing a year', () => {
    start([reported('h1')]);

    type(0, 'A hit of 1986.');
    button('Save and approve').click();
    http
      .expectOne('/api/admin/hints/h1/review')
      .flush(
        { errorCode: 'HINT_TEXT_CONTAINS_YEAR', message: 'x' },
        { status: 400, statusText: 'Bad Request' },
      );
    fixture.detectChanges();

    expect(page.querySelector('[role=alert]')?.textContent).toContain('cannot contain a year');
    expect(page.querySelectorAll('li')).toHaveLength(1);
  });
});

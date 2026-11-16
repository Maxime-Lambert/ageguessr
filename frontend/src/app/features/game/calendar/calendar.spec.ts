import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { CalendarMonth, DayState } from '../models';
import { Calendar } from './calendar';

/** November 2026 starts on a Sunday: six empty squares before it, weeks from Monday. */
function november(states: Partial<Record<number, DayState>> = {}): CalendarMonth {
  return {
    today: '2026-11-16',
    month: '2026-11',
    firstMonth: '2026-10',
    days: Array.from({ length: 30 }, (_, index) => ({
      date: `2026-11-${String(index + 1).padStart(2, '0')}`,
      state: states[index + 1] ?? (index + 1 <= 16 ? 'AVAILABLE' : 'UNAVAILABLE'),
    })),
  };
}

describe('Calendar', () => {
  let fixture: ComponentFixture<Calendar>;
  let http: HttpTestingController;
  let page: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Calendar);
    page = fixture.nativeElement as HTMLElement;
  });

  afterEach(() => http.verify());

  function load(month: CalendarMonth, url = '/api/game/calendar'): void {
    http.expectOne(url).flush(month);
    fixture.detectChanges();
  }

  function day(dayOfMonth: number): HTMLElement {
    return page.querySelectorAll<HTMLElement>('[data-day]')[dayOfMonth - 1];
  }

  function arrow(label: string): HTMLButtonElement {
    return page.querySelector<HTMLButtonElement>(`button[aria-label="${label}"]`)!;
  }

  it('puts the 1st under its weekday, weeks from Monday', () => {
    load(november());

    expect(page.querySelector('#calendar-month')?.textContent).toContain('November 2026');
    expect(page.querySelectorAll('ol > li[aria-hidden="true"]')).toHaveLength(6);
    expect(page.querySelectorAll('[data-day]')).toHaveLength(30);
  });

  it('links each day by its state: today to /, a past day to its page, none when unavailable', () => {
    load(november({ 3: 'STARTED', 4: 'FINISHED', 5: 'UNAVAILABLE' }));

    expect(day(2).querySelector('a')?.getAttribute('href')).toBe('/day/2026-11-02');
    expect(day(3).querySelector('a')?.getAttribute('href')).toBe('/day/2026-11-03');
    expect(day(4).querySelector('a')?.getAttribute('href')).toBe('/day/2026-11-04');
    expect(day(5).querySelector('a')).toBeNull();
    expect(day(16).querySelector('a')?.getAttribute('href')).toBe('/');
    expect(day(17).querySelector('a')).toBeNull();
  });

  it('names each day with its state, never by colour alone', () => {
    load(november({ 3: 'STARTED', 4: 'FINISHED' }));

    expect(day(3).querySelector('a')?.getAttribute('aria-label')).toBe(
      'Tuesday, November 3, 2026, Started',
    );
    expect(day(4).querySelector('a svg')).not.toBeNull();
    expect(day(16).querySelector('a')?.getAttribute('aria-label')).toContain('To play, today');
    expect(day(16).querySelector('a')?.getAttribute('aria-current')).toBe('date');
    expect(day(20).querySelector('[role="img"]')?.getAttribute('aria-label')).toContain('No game');
  });

  it('moves between months, from the first one to the current one', () => {
    load(november());
    expect(arrow('Next month').disabled).toBe(true);
    expect(arrow('Previous month').disabled).toBe(false);

    arrow('Previous month').click();
    load(
      { ...november(), month: '2026-10', days: [{ date: '2026-10-31', state: 'FINISHED' }] },
      '/api/game/calendar?month=2026-10',
    );

    expect(page.querySelector('#calendar-month')?.textContent).toContain('October 2026');
    expect(arrow('Previous month').disabled).toBe(true);
    expect(arrow('Next month').disabled).toBe(false);
  });

  it('crosses the new year backwards', () => {
    load({ ...november(), month: '2027-01', today: '2027-01-05', firstMonth: '2026-01', days: [] });

    arrow('Previous month').click();
    http.expectOne('/api/game/calendar?month=2026-12').flush(november());
  });

  it('offers to try again when the month cannot be read', () => {
    http.expectOne('/api/game/calendar').flush({}, { status: 500, statusText: 'Error' });
    fixture.detectChanges();

    expect(page.querySelector('[role="alert"]')).not.toBeNull();
  });
});

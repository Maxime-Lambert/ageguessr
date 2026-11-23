import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { NextGameCountdown, TICK_MS, nextMidnightUtc } from './next-game-countdown';

describe('NextGameCountdown', () => {
  afterEach(() => vi.useRealTimers());

  function render(now: string) {
    vi.useFakeTimers({ toFake: ['Date', 'setInterval', 'clearInterval'] });
    vi.setSystemTime(new Date(now));
    const fixture = TestBed.createComponent(NextGameCountdown);
    fixture.detectChanges();
    return fixture;
  }

  const text = (fixture: { nativeElement: HTMLElement }) =>
    fixture.nativeElement.textContent?.replace(/\s+/g, ' ').trim();

  it('finds the next midnight UTC', () => {
    expect(nextMidnightUtc(Date.parse('2026-11-23T18:47:59Z'))).toBe(
      Date.parse('2026-11-24T00:00:00Z'),
    );
    expect(nextMidnightUtc(Date.parse('2026-12-31T23:59:00Z'))).toBe(
      Date.parse('2027-01-01T00:00:00Z'),
    );
  });

  it('counts the hours and minutes left, then the minutes alone', () => {
    const fixture = render('2026-11-23T18:48:00Z');
    expect(text(fixture)).toBe('Next game in 5 h 12 min');

    vi.advanceTimersByTime(5 * 60 * TICK_MS + 9 * TICK_MS);
    fixture.detectChanges();
    expect(text(fixture)).toBe('Next game in 3 min');
  });

  it('offers to play the new game once midnight has passed, without reloading', () => {
    const fixture = render('2026-11-23T23:59:30Z');
    expect(text(fixture)).toBe('Next game in 1 min');
    let played = 0;
    fixture.componentInstance.playNext.subscribe(() => played++);

    vi.advanceTimersByTime(TICK_MS);
    fixture.detectChanges();

    expect(text(fixture)).toBe('A new game is ready. Play it');
    (fixture.nativeElement as HTMLElement).querySelector('button')!.click();
    expect(played).toBe(1);
  });
});

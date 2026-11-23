import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { CONFETTI_MS, CONFETTI_PIECES, Confetti } from './confetti';

describe('Confetti', () => {
  function motion(reduce: boolean): void {
    vi.stubGlobal('matchMedia', () => ({ matches: reduce }) as MediaQueryList);
  }

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('drops its pieces once, hidden from screen readers, then clears them', async () => {
    motion(false);
    vi.useFakeTimers();
    const fixture = TestBed.createComponent(Confetti);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.getAttribute('aria-hidden')).toBe('true');
    expect(host.classList).toContain('pointer-events-none');
    expect(host.querySelectorAll('span')).toHaveLength(CONFETTI_PIECES);

    vi.advanceTimersByTime(CONFETTI_MS);
    fixture.detectChanges();
    expect(host.querySelectorAll('span')).toHaveLength(0);
  });

  it('drops nothing for a player who asked for less motion', async () => {
    motion(true);
    const fixture = TestBed.createComponent(Confetti);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelectorAll('span')).toHaveLength(0);
  });
});

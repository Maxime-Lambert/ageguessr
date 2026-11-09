import { TestBed } from '@angular/core/testing';
import { CaseSteps } from './case-steps';

describe('CaseSteps', () => {
  function bars(position: number, total: number) {
    const fixture = TestBed.createComponent(CaseSteps);
    fixture.componentRef.setInput('position', position);
    fixture.componentRef.setInput('total', total);
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;
    return { host, bars: Array.from(host.querySelectorAll('i')) };
  }

  it('marks the cases done, the current one and those to come, hidden from screen readers', () => {
    const { host, bars: steps } = bars(2, 3);

    expect(host.getAttribute('aria-hidden')).toBe('true');
    expect(steps.map((bar) => bar.className)).toEqual([
      expect.stringContaining('bg-foreground'),
      expect.stringContaining('bg-accent'),
      expect.stringContaining('bg-border'),
    ]);
  });

  it('draws shorter bars for a long series', () => {
    expect(bars(1, 3).bars[0].classList).toContain('w-7');
    expect(bars(1, 20).bars[0].classList).toContain('w-3');
  });
});

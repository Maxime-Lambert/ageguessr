import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { LeadCard } from './lead-card';

describe('LeadCard', () => {
  let fixture: ComponentFixture<LeadCard>;
  let host: HTMLElement;
  let events: string[];

  const card = () => host.querySelector<HTMLButtonElement>('button[aria-pressed]')!;
  const info = () => host.querySelector<HTMLButtonElement>('button[aria-expanded]')!;
  const help = () => host.querySelector<HTMLElement>('#lead-help-film')!;

  function set(inputs: Record<string, unknown>): void {
    Object.entries(inputs).forEach(([name, value]) => fixture.componentRef.setInput(name, value));
    fixture.detectChanges();
  }

  function pointer(type: string, pointerType: string, target: Element = host): void {
    target.dispatchEvent(new PointerEvent(type, { pointerType }));
  }

  beforeEach(() => {
    fixture = TestBed.createComponent(LeadCard);
    host = fixture.nativeElement as HTMLElement;
    events = [];
    fixture.componentInstance.toggled.subscribe(() => events.push('toggled'));
    fixture.componentInstance.helpOpened.subscribe(() => events.push('opened'));
    fixture.componentInstance.helpClosed.subscribe(() => events.push('closed'));
    set({ category: 'FILM', gameYear: 2026 });
  });

  afterEach(() => vi.useRealTimers());

  it('explains the category in a help the card points to, hidden until opened', () => {
    expect(card().textContent?.trim()).toBe('Film');
    expect(card().getAttribute('aria-describedby')).toBe('lead-help-film');
    expect(help().hidden).toBe(true);
    expect(help().textContent).toContain('A film released');
    expect(help().textContent).toContain('29 years ago → 29 on the photo.');
    expect(info().getAttribute('aria-label')).toBe('How the Film hint works');
    expect(info().getAttribute('aria-controls')).toBe('lead-help-film');
    expect(info().getAttribute('aria-expanded')).toBe('false');

    set({ open: true });

    expect(help().hidden).toBe(false);
    expect(info().getAttribute('aria-expanded')).toBe('true');
  });

  it('never opens on the mouse passing over ⓘ or the card (testers, 2026-11-23)', () => {
    vi.useFakeTimers();
    pointer('pointerenter', 'mouse', info());
    pointer('pointerover', 'mouse', info());
    pointer('pointerenter', 'mouse', card());
    vi.advanceTimersByTime(5000);

    expect(events).toEqual([]);
  });

  it('toggles on a mouse click of ⓘ', () => {
    info().dispatchEvent(new PointerEvent('click', { pointerType: 'mouse', bubbles: true }));
    expect(events).toEqual(['opened']);

    set({ open: true });
    info().dispatchEvent(new PointerEvent('click', { pointerType: 'mouse', bubbles: true }));

    expect(events).toEqual(['opened', 'closed']);
  });

  it('stays open when the mouse leaves the card: only a click elsewhere closes it', () => {
    set({ open: true });

    pointer('pointerleave', 'mouse');

    expect(events).toEqual([]);
  });

  it('toggles on a tap of ⓘ', () => {
    info().dispatchEvent(new PointerEvent('click', { pointerType: 'touch', bubbles: true }));
    expect(events).toEqual(['opened']);
    set({ open: true });
    info().dispatchEvent(new PointerEvent('click', { pointerType: 'touch', bubbles: true }));

    expect(events).toEqual(['opened', 'closed']);
  });

  it('toggles on Enter once Tab reached ⓘ (a click that no pointer made)', () => {
    info().dispatchEvent(new MouseEvent('click', { bubbles: true }));
    expect(events).toEqual(['opened']);

    set({ open: true });
    info().dispatchEvent(new MouseEvent('click', { bubbles: true }));

    expect(events).toEqual(['opened', 'closed']);
  });

  it('toggles the lead on a click of the card, without opening its help', () => {
    card().click();

    expect(events).toEqual(['toggled']);
  });

  it('keeps ⓘ usable on a card that cannot be picked', () => {
    set({ disabled: true });

    info().click();

    expect(card().disabled).toBe(true);
    expect(events).toEqual(['opened']);
  });

  it('does not open when the card is reached with Tab, and closes when the focus leaves it', () => {
    card().dispatchEvent(new KeyboardEvent('keyup', { key: 'Tab', bubbles: true }));
    expect(events).toEqual([]);

    set({ open: true });
    card().dispatchEvent(new FocusEvent('focusout', { relatedTarget: info(), bubbles: true }));
    expect(events).toEqual([]);
    card().dispatchEvent(
      new FocusEvent('focusout', { relatedTarget: document.body, bubbles: true }),
    );

    expect(events).toEqual(['closed']);
  });

  it('aligns its help on the right edge in the last column only', () => {
    expect(help().classList).toContain('max-sm:left-0');
    expect(help().classList).toContain('sm:left-0');

    set({ rightOnMobile: true, rightOnWide: true });

    expect(help().classList).toContain('max-sm:right-0');
    expect(help().classList).toContain('sm:right-0');
    expect(help().classList).toContain('absolute');
  });

  it('counts the example from the year of the game', () => {
    set({ gameYear: 2030 });

    expect(help().textContent).toContain('33 years ago → 33 on the photo.');
  });
});

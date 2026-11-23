import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LeadHelpButton } from './lead-help-button';

describe('LeadHelpButton', () => {
  let fixture: ComponentFixture<LeadHelpButton>;
  let host: HTMLElement;

  const button = () => host.querySelector<HTMLButtonElement>('button')!;
  const help = () => host.querySelector<HTMLElement>('#revealed-help-music')!;

  function render(): void {
    fixture.detectChanges();
  }

  beforeEach(() => {
    fixture = TestBed.createComponent(LeadHelpButton);
    host = fixture.nativeElement as HTMLElement;
    document.body.appendChild(host);
    fixture.componentRef.setInput('category', 'MUSIC');
    fixture.componentRef.setInput('gameYear', 2026);
    render();
  });

  afterEach(() => host.remove());

  it('names the category it explains, its help hidden until clicked', () => {
    expect(button().getAttribute('aria-label')).toBe('How the Music hint works');
    expect(button().getAttribute('aria-controls')).toBe('revealed-help-music');
    expect(button().getAttribute('aria-expanded')).toBe('false');
    expect(help().hidden).toBe(true);
    expect(help().textContent).toContain('The fact dates from as many years ago');
  });

  it('toggles on a click, never on the mouse passing over', () => {
    button().dispatchEvent(new PointerEvent('pointerenter', { pointerType: 'mouse' }));
    button().dispatchEvent(
      new PointerEvent('pointerover', { pointerType: 'mouse', bubbles: true }),
    );
    render();
    expect(help().hidden).toBe(true);

    button().click();
    render();
    expect(help().hidden).toBe(false);
    expect(button().getAttribute('aria-expanded')).toBe('true');

    button().click();
    render();
    expect(help().hidden).toBe(true);
  });

  it('closes on a click elsewhere, not on a click in its help', () => {
    button().click();
    render();

    help().dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
    render();
    expect(help().hidden).toBe(false);

    document.body.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
    render();
    expect(help().hidden).toBe(true);
  });

  it('closes on Escape, and when the focus leaves it', () => {
    button().click();
    render();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    render();
    expect(help().hidden).toBe(true);

    button().click();
    render();
    button().dispatchEvent(
      new FocusEvent('focusout', { relatedTarget: document.body, bubbles: true }),
    );
    render();
    expect(help().hidden).toBe(true);
  });
});

import { TestBed } from '@angular/core/testing';
import { GAME_TEXT } from '../game-vocabulary';
import { HowToPlay } from './how-to-play';

describe('HowToPlay', () => {
  function render(inputs: Record<string, unknown> = {}): HTMLElement {
    const fixture = TestBed.createComponent(HowToPlay);
    for (const [name, value] of Object.entries(inputs)) {
      fixture.componentRef.setInput(name, value);
    }
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('lists the three rules, in order, under a heading with the id given', () => {
    const page = render({ headingId: 'rules-x' });

    expect(page.querySelector('h2#rules-x')?.textContent).toContain(GAME_TEXT.howToPlay);
    const rules = Array.from(page.querySelectorAll('li')).map((li) => li.textContent);
    expect(rules).toHaveLength(4);
    expect(rules[0]).toContain(GAME_TEXT.ruleGuess);
    expect(rules[1]).toContain(GAME_TEXT.ruleLead);
    expect(rules[2]).toContain(GAME_TEXT.ruleHints);
    expect(rules[3]).toContain(GAME_TEXT.rulePoints);
  });

  it('leaves the heading to the page when told to', () => {
    const page = render({ heading: false });

    expect(page.querySelector('h2')).toBeNull();
    expect(page.querySelectorAll('li')).toHaveLength(4);
  });
});

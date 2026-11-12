import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GAME_TEXT } from '../game-vocabulary';
import { RulesCard } from './rules-card';

describe('RulesCard', () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => vi.restoreAllMocks());

  let fixture: ComponentFixture<RulesCard>;

  function render(): HTMLElement {
    fixture = TestBed.createComponent(RulesCard);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function gotIt(page: HTMLElement): HTMLButtonElement {
    return Array.from(page.querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === GAME_TEXT.gotIt,
    )!;
  }

  it('shows the four rules on a first visit, as a named region', () => {
    const page = render();

    const region = page.querySelector('section')!;
    expect(region.getAttribute('aria-labelledby')).toBe('rules-card-title');
    expect(page.querySelector('#rules-card-title')?.textContent).toContain(GAME_TEXT.howToPlay);
    expect(page.querySelectorAll('li')).toHaveLength(4);
    expect(page.textContent).toContain(GAME_TEXT.ruleLead);
  });

  it('hides for good once dismissed', () => {
    const page = render();

    gotIt(page).click();
    fixture.detectChanges();

    expect(page.querySelector('section')).toBeNull();
    expect(render().querySelector('section')).toBeNull();
  });

  it('still shows, and still hides on click, when the storage is refused', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('denied');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('denied');
    });
    const page = render();

    gotIt(page).click();
    fixture.detectChanges();

    expect(page.querySelector('section')).toBeNull();
  });
});

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GAME_TEXT } from '../game-vocabulary';
import { RoundView } from '../models';
import { round } from '../testing';
import { Round } from './round';

describe('Round', () => {
  let fixture: ComponentFixture<Round>;
  let page: HTMLElement;
  let guesses: number[];
  let picks: string[][];

  function show(view: RoundView): void {
    fixture.componentRef.setInput('round', view);
    fixture.componentRef.setInput('total', 3);
    fixture.detectChanges();
  }

  function type(value: string): void {
    const input = page.querySelector<HTMLInputElement>('app-age-input input')!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  beforeEach(() => {
    fixture = TestBed.createComponent(Round);
    page = fixture.nativeElement as HTMLElement;
    guesses = [];
    picks = [];
    fixture.componentInstance.guessed.subscribe((guess) => guesses.push(guess));
    fixture.componentInstance.hintsPicked.subscribe((categories) => picks.push(categories));
  });

  it('asks for a first guess under the photo, aged like an archive', () => {
    show(round(1));

    const image = page.querySelector('img')!;
    expect(image.getAttribute('src')).toBe('/api/images/public-1');
    expect(image.classList).toContain('archive-photo');
    expect(image.getAttribute('alt')).toBe(GAME_TEXT.photoAlt);
    expect(page.textContent).toContain(`${GAME_TEXT.round} 1 of 3`);
    expect(page.textContent).toContain(GAME_TEXT.question);
    expect(document.activeElement).toBe(page.querySelector('app-age-input input'));
  });

  it('sends a valid guess', () => {
    show(round(1));

    type('42');

    expect(guesses).toEqual([42]);
  });

  it.each(['17', '101', '', '40.5'])('refuses %j without sending it', (value) => {
    show(round(1));

    type(value);

    expect(guesses).toEqual([]);
    expect(page.querySelector('app-field [aria-live="polite"]')?.textContent).toContain(
      GAME_TEXT.invalidAge,
    );
  });

  it('sends nothing while a request is on its way', () => {
    show(round(1));
    fixture.componentRef.setInput('busy', true);
    fixture.detectChanges();

    type('42');

    expect(guesses).toEqual([]);
  });

  function leadButtons(): HTMLButtonElement[] {
    return Array.from(
      page.querySelectorAll<HTMLButtonElement>('[role=group] button[aria-pressed]'),
    );
  }

  function confirmButton(): HTMLButtonElement {
    return Array.from(page.querySelectorAll<HTMLButtonElement>('button')).find(
      (button) => button.textContent?.trim() === GAME_TEXT.showHints,
    )!;
  }

  const awaitingHints = (overrides: Partial<RoundView> = {}) =>
    round(1, {
      step: 'AWAITING_HINT',
      firstGuess: 30,
      direction: 'OLDER',
      gap: 'CLEARLY',
      range: { min: 36, max: 45 },
      categories: ['MUSIC', 'FILM', 'SPORT', 'WORLD'],
      hintsToPick: 3,
      ...overrides,
    });

  it('tells the side, the band and the ages left after the first guess, then offers the leads', () => {
    show(awaitingHints());

    expect(page.textContent).toContain('Older than 30, by 6 to 15 years');
    expect(page.textContent).toContain('Between 36 and 45');
    expect(page.textContent).toContain('Pick 3 hints');
    expect(page.textContent).toContain('0 of 3 picked');
    expect(page.querySelector('app-age-input input')).toBeNull();
    expect(leadButtons().map((button) => button.textContent?.trim())).toEqual([
      'Music',
      'Film',
      'Sport',
      'World events',
    ]);
    expect(document.activeElement).toBe(leadButtons()[0]);
    expect(confirmButton().disabled).toBe(true);
  });

  it('offers the seven categories, Literature included, and still asks for three', () => {
    show(
      awaitingHints({
        categories: ['MUSIC', 'FILM', 'SPORT', 'SCIENCE', 'WORLD', 'CELEBRITIES', 'LITERATURE'],
      }),
    );

    expect(leadButtons()).toHaveLength(7);
    expect(leadButtons()[6].textContent?.trim()).toBe('Literature');
    expect(page.textContent).toContain('Pick 3 hints');
  });

  it('sends exactly the number of leads asked, in the order offered', () => {
    show(awaitingHints());

    leadButtons()[3].click();
    leadButtons()[0].click();
    fixture.detectChanges();
    expect(confirmButton().disabled).toBe(true);
    leadButtons()[2].click();
    fixture.detectChanges();

    expect(page.textContent).toContain('3 of 3 picked');
    expect(leadButtons().map((button) => button.getAttribute('aria-pressed'))).toEqual([
      'true',
      'false',
      'true',
      'true',
    ]);
    // Enough picked: the last one waits for another to be unpicked.
    expect(leadButtons()[1].disabled).toBe(true);
    confirmButton().click();

    expect(picks).toEqual([['MUSIC', 'SPORT', 'WORLD']]);
  });

  it('lets a lead be unpicked to pick another', () => {
    show(awaitingHints());
    for (const index of [0, 1, 2]) leadButtons()[index].click();
    fixture.detectChanges();

    leadButtons()[1].click();
    fixture.detectChanges();
    leadButtons()[3].click();
    fixture.detectChanges();
    confirmButton().click();

    expect(picks).toEqual([['MUSIC', 'SPORT', 'WORLD']]);
  });

  it('asks for a single lead in the singular when only one is offered', () => {
    show(awaitingHints({ categories: ['SPORT'], hintsToPick: 1 }));

    expect(page.textContent).toContain(GAME_TEXT.pickHint);
    expect(page.textContent).not.toContain('picked');
    leadButtons()[0].click();
    fixture.detectChanges();
    confirmButton().click();

    expect(picks).toEqual([['SPORT']]);
  });

  it('sends no leads while a request is on its way', () => {
    show(awaitingHints({ categories: ['SPORT'], hintsToPick: 1 }));
    leadButtons()[0].click();
    fixture.componentRef.setInput('busy', true);
    fixture.detectChanges();

    fixture.componentInstance.confirmHints();

    expect(picks).toEqual([]);
  });

  function help(category: string): HTMLElement {
    return page.querySelector<HTMLElement>(`#lead-help-${category}`)!;
  }

  function infoButton(index: number): HTMLButtonElement {
    return page.querySelectorAll<HTMLButtonElement>('[role=group] button[aria-expanded]')[index];
  }

  it('opens one lead help at a time', () => {
    show(awaitingHints());

    infoButton(0).click();
    fixture.detectChanges();
    expect(help('music').hidden).toBe(false);
    infoButton(1).click();
    fixture.detectChanges();

    expect(help('music').hidden).toBe(true);
    expect(help('film').hidden).toBe(false);
  });

  it('closes the lead help on a tap elsewhere, or on Escape', () => {
    show(awaitingHints());
    infoButton(0).click();
    fixture.detectChanges();

    help('music').dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
    fixture.detectChanges();
    expect(help('music').hidden).toBe(false);
    page
      .querySelector('#pick-title')!
      .dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
    fixture.detectChanges();
    expect(help('music').hidden).toBe(true);

    infoButton(0).click();
    fixture.detectChanges();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(help('music').hidden).toBe(true);
  });

  it('picks the leads the same with a help open, and keeps ⓘ on the cards left', () => {
    show(awaitingHints());
    infoButton(3).click();
    for (const index of [0, 1, 2]) leadButtons()[index].click();
    fixture.detectChanges();

    expect(leadButtons()[3].disabled).toBe(true);
    expect(infoButton(3).disabled).toBe(false);
    confirmButton().click();
    expect(picks).toEqual([['MUSIC', 'FILM', 'SPORT']]);
  });

  it('puts the help of the last column on the right edge, the lone last card on the left', () => {
    show(awaitingHints({ categories: ['MUSIC', 'FILM', 'SPORT', 'SCIENCE', 'WORLD'] }));

    // 2 columns on a phone, 3 beyond: the 5th card is alone on a phone row, left-aligned.
    expect(help('film').classList).toContain('max-sm:right-0');
    expect(help('world').classList).toContain('max-sm:left-0');
    expect(help('sport').classList).toContain('sm:right-0');
    expect(help('world').classList).toContain('sm:left-0');
  });

  it('shows the leads and starts the final guess at the edge of the range nearest the first', () => {
    show(round(1));
    type('30');
    show(
      round(1, {
        step: 'AWAITING_SECOND_GUESS',
        firstGuess: 30,
        direction: 'YOUNGER',
        gap: 'SLIGHTLY',
        range: { min: 25, max: 29 },
        hints: [
          { category: 'MUSIC', text: 'A hit.' },
          { category: 'FILM', text: 'A film.' },
        ],
      }),
    );

    expect(page.textContent).toContain('Younger than 30, by 1 to 5 years');
    expect(page.textContent).toContain('A hit.');
    expect(page.textContent).toContain('A film.');
    const input = page.querySelector<HTMLInputElement>('app-age-input input')!;
    expect(input.value).toBe('29');
    expect(input.getAttribute('aria-valuemin')).toBe('25');
    expect(input.getAttribute('aria-valuemax')).toBe('29');
    expect(page.querySelector('button[type=submit]')?.textContent?.trim()).toBe(
      GAME_TEXT.secondGuessButton,
    );
    expect(document.activeElement).toBe(input);
  });

  it.each([
    ['OLDER', 30, { min: 36, max: 45 }, '36'],
    ['OLDER', 98, { min: 99, max: 100 }, '99'],
    ['YOUNGER', 40, { min: 18, max: 24 }, '24'],
  ] as const)(
    'starts a final guess %s than %i at its range %o, at %s',
    (direction, first, range, expected) => {
      show(round(1));
      show(
        round(1, { step: 'AWAITING_SECOND_GUESS', firstGuess: first, direction, range, hints: [] }),
      );

      expect(page.querySelector<HTMLInputElement>('app-age-input input')!.value).toBe(expected);
    },
  );

  it.each(['35', '46'])('refuses %j, out of the range left, without sending it', (value) => {
    show(round(1));
    show(
      round(1, {
        step: 'AWAITING_SECOND_GUESS',
        firstGuess: 30,
        direction: 'OLDER',
        gap: 'CLEARLY',
        range: { min: 36, max: 45 },
        hints: [],
      }),
    );

    type(value);

    expect(guesses).toEqual([]);
    expect(page.querySelector('app-field [aria-live="polite"]')?.textContent).toContain(
      'Enter a whole age from 36 to 45.',
    );
  });

  it('goes back to every age for the first guess of the next round', () => {
    show(
      round(1, {
        step: 'AWAITING_SECOND_GUESS',
        firstGuess: 30,
        direction: 'OLDER',
        range: { min: 36, max: 45 },
        hints: [],
      }),
    );
    show(round(2));

    type('20');

    expect(guesses).toEqual([20]);
  });

  it('sends the prefilled final guess as is', () => {
    show(round(1));
    show(
      round(1, {
        step: 'AWAITING_SECOND_GUESS',
        firstGuess: 40,
        direction: 'OLDER',
        gap: 'SLIGHTLY',
        range: { min: 41, max: 45 },
        hints: [],
      }),
    );

    page.querySelector('form')!.dispatchEvent(new Event('submit'));

    expect(guesses).toEqual([41]);
  });

  it('says so when no hint could be offered', () => {
    show(
      round(1, {
        step: 'AWAITING_SECOND_GUESS',
        firstGuess: 30,
        direction: 'OLDER',
        hints: [],
      }),
    );

    expect(page.textContent).toContain(GAME_TEXT.noHint);
  });

  it('reports a photo that does not load', () => {
    let failed = 0;
    fixture.componentInstance.imageFailed.subscribe(() => failed++);
    show(round(1));

    page.querySelector('img')!.dispatchEvent(new Event('error'));

    expect(failed).toBe(1);
  });
});

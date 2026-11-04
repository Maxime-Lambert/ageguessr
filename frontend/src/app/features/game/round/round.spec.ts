import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GAME_TEXT } from '../game-vocabulary';
import { RoundView } from '../models';
import { round } from '../testing';
import { Round } from './round';

describe('Round', () => {
  let fixture: ComponentFixture<Round>;
  let page: HTMLElement;
  let guesses: number[];
  let hints: string[];

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
    hints = [];
    fixture.componentInstance.guessed.subscribe((guess) => guesses.push(guess));
    fixture.componentInstance.hintPicked.subscribe((category) => hints.push(category));
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
    expect(page.querySelector('[aria-live="polite"]')?.textContent).toContain(GAME_TEXT.invalidAge);
  });

  it('sends nothing while a request is on its way', () => {
    show(round(1));
    fixture.componentRef.setInput('busy', true);
    fixture.detectChanges();

    type('42');

    expect(guesses).toEqual([]);
  });

  it('tells the side after the first guess and offers the categories, focused', () => {
    show(
      round(1, {
        step: 'AWAITING_HINT',
        firstGuess: 30,
        direction: 'OLDER',
        categories: ['MUSIC', 'SPORT'],
      }),
    );

    expect(page.textContent).toContain('Older than 30');
    expect(page.querySelector('app-age-input input')).toBeNull();
    const buttons = Array.from(page.querySelectorAll<HTMLButtonElement>('button'));
    expect(buttons.map((button) => button.textContent?.trim())).toEqual(['Music', 'Sport']);
    expect(document.activeElement).toBe(buttons[0]);

    buttons[1].click();
    expect(hints).toEqual(['SPORT']);
  });

  it('shows the hint and asks for the final guess in an empty field', () => {
    show(round(1));
    type('30');
    show(
      round(1, {
        step: 'AWAITING_SECOND_GUESS',
        firstGuess: 30,
        direction: 'YOUNGER',
        hint: { category: 'MUSIC', text: 'A hit.' },
      }),
    );

    expect(page.textContent).toContain('Younger than 30');
    expect(page.textContent).toContain('A hit.');
    expect(page.querySelector<HTMLInputElement>('app-age-input input')!.value).toBe('');
    expect(page.querySelector('button[type=submit]')?.textContent?.trim()).toBe(
      GAME_TEXT.secondGuessButton,
    );
    expect(document.activeElement).toBe(page.querySelector('app-age-input input'));
  });

  it('says so when no hint could be offered', () => {
    show(
      round(1, {
        step: 'AWAITING_SECOND_GUESS',
        firstGuess: 30,
        direction: 'OLDER',
        categories: [],
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

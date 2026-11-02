import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GAME_TEXT } from '../game-vocabulary';
import { TodayGame } from '../models';
import { finished, round, today } from '../testing';
import { Game } from './game';

describe('Game', () => {
  let fixture: ComponentFixture<Game>;
  let http: HttpTestingController;
  let page: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Game);
    page = fixture.nativeElement as HTMLElement;
  });

  afterEach(() => http.verify());

  function load(game: TodayGame): void {
    http.expectOne('/api/game/today').flush(game);
    fixture.detectChanges();
  }

  function guess(value: string): void {
    const input = page.querySelector<HTMLInputElement>('#guess')!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  function button(text: string): HTMLButtonElement {
    return Array.from(page.querySelectorAll('button')).find((b) => b.textContent?.trim() === text)!;
  }

  function conflict(url: string, errorCode: string): void {
    http.expectOne(url).flush({ errorCode, message: 'x' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
  }

  it('plays a round from the first guess to the verdict, then goes to the next one', () => {
    load(today([round(1), round(2), round(3)]));
    expect(page.querySelector('h2')?.textContent).toContain(`${GAME_TEXT.round} 1 of 3`);

    guess('30');
    const first = http.expectOne('/api/game/2026-11-02/rounds/1/first-guess');
    expect(first.request.body).toEqual({ guess: 30, language: expect.stringMatching(/^(EN|FR)$/) });
    first.flush(
      round(1, {
        step: 'AWAITING_HINT',
        firstGuess: 30,
        direction: 'OLDER',
        categories: ['MUSIC'],
      }),
    );
    fixture.detectChanges();
    expect(page.textContent).toContain('Older than 30');

    button('Music').click();
    http.expectOne('/api/game/2026-11-02/rounds/1/hint').flush(
      round(1, {
        step: 'AWAITING_SECOND_GUESS',
        firstGuess: 30,
        direction: 'OLDER',
        hint: { category: 'MUSIC', text: 'A hit.' },
      }),
    );
    fixture.detectChanges();

    guess('38');
    http.expectOne('/api/game/2026-11-02/rounds/1/second-guess').flush(finished(1));
    fixture.detectChanges();
    expect(page.textContent).toContain('Jane Doe');

    button(GAME_TEXT.next).click();
    fixture.detectChanges();
    expect(page.querySelector('h2')?.textContent).toContain(`${GAME_TEXT.round} 2 of 3`);
  });

  it('picks up where the player was, past the rounds finished or withdrawn', () => {
    load(
      today([
        finished(1),
        round(2, { status: 'NEUTRALIZED', imageUrl: null }),
        round(3, {
          step: 'AWAITING_HINT',
          firstGuess: 50,
          direction: 'YOUNGER',
          categories: ['FILM'],
        }),
      ]),
    );

    expect(page.querySelector('h2')?.textContent).toContain(`${GAME_TEXT.round} 3 of 3`);
    expect(page.textContent).toContain('Younger than 50');
  });

  it('shows the summary with the fresh score after the last verdict', () => {
    load(today([finished(1), round(2)]));
    guess('40');
    http
      .expectOne('/api/game/2026-11-02/rounds/2/first-guess')
      .flush(finished(2, { points: 1000 }));
    fixture.detectChanges();

    button(GAME_TEXT.seeSummary).click();
    load(today([finished(1), finished(2, { points: 1000 })]));

    expect(page.textContent).toContain(GAME_TEXT.summary);
    expect(page.textContent).toContain(`1593 ${GAME_TEXT.outOf} 2000`);
  });

  it('shows the summary at once when the day is already played', () => {
    load(today([finished(1)]));

    expect(page.textContent).toContain(GAME_TEXT.summary);
  });

  it('says when there is no game today', () => {
    load(today([]));

    expect(page.textContent).toContain(GAME_TEXT.noGame);
  });

  it('loads the new day when the game closed at midnight', () => {
    load(today([round(1)]));
    guess('30');

    conflict('/api/game/2026-11-02/rounds/1/first-guess', 'GAME_CLOSED');
    load(today([round(1)], { date: '2026-11-03' }));

    expect(page.querySelector('[role=status]')?.textContent).toContain(GAME_TEXT.closed);
  });

  it('moves on when the photo was withdrawn during the round', () => {
    load(
      today([
        round(1, {
          step: 'AWAITING_HINT',
          firstGuess: 30,
          direction: 'OLDER',
          categories: ['MUSIC'],
        }),
        round(2),
      ]),
    );
    button('Music').click();

    conflict('/api/game/2026-11-02/rounds/1/hint', 'ROUND_NEUTRALIZED');
    load(today([round(1, { status: 'NEUTRALIZED', imageUrl: null }), round(2)]));

    expect(page.querySelector('[role=status]')?.textContent).toContain(GAME_TEXT.removedExplained);
    expect(page.querySelector('h2')?.textContent).toContain(`${GAME_TEXT.round} 2 of 2`);
  });

  it('reloads the state when the step was already played elsewhere', () => {
    load(today([round(1)]));
    guess('30');

    conflict('/api/game/2026-11-02/rounds/1/first-guess', 'WRONG_STEP');
    load(today([finished(1)]));

    expect(page.textContent).toContain(GAME_TEXT.summary);
  });

  it('keeps the round on screen after any other error', () => {
    load(today([round(1)]));
    guess('30');

    http
      .expectOne('/api/game/2026-11-02/rounds/1/first-guess')
      .flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(page.querySelector('[role=alert]')?.textContent).toContain(GAME_TEXT.genericError);
    expect(page.querySelector('#guess')).not.toBeNull();
  });

  it('reloads once when the photo fails to load, then reports it', () => {
    load(today([round(1)]));

    page.querySelector('img')!.dispatchEvent(new Event('error'));
    load(today([round(1)]));
    page.querySelector('img')!.dispatchEvent(new Event('error'));
    fixture.detectChanges();

    expect(page.textContent).toContain(GAME_TEXT.imageFailed);
  });

  it('offers to retry when the game cannot be loaded', () => {
    http.expectOne('/api/game/today').flush(null, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();

    button(GAME_TEXT.retry).click();
    load(today([round(1)]));

    expect(page.querySelector('#guess')).not.toBeNull();
  });
});

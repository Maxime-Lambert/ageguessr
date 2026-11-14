import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Auth } from '../../auth/auth';
import { GAME_TEXT } from '../game-vocabulary';
import { TodayGame } from '../models';
import { SHARE_TARGET } from '../../../shared/share-target';
import { fakeShareTarget, finished, round, today } from '../testing';
import { HintLanguage } from '../hint-language';
import { Game } from './game';

describe('Game', () => {
  let fixture: ComponentFixture<Game>;
  let http: HttpTestingController;
  let page: HTMLElement;
  let share: ReturnType<typeof fakeShareTarget>;

  beforeEach(() => {
    share = fakeShareTarget();
    localStorage.clear();
    // The first-visit rules card is covered on its own (rules-card.spec.ts).
    localStorage.setItem('ageguessr-rules-seen', '1');
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: SHARE_TARGET, useValue: share.target },
      ],
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
    const input = page.querySelector<HTMLInputElement>('app-age-input input')!;
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
    expect(first.request.body).toEqual({ guess: 30, language: 'EN' });
    first.flush(
      round(1, {
        step: 'AWAITING_HINT',
        firstGuess: 30,
        direction: 'OLDER',
        gap: 'CLEARLY',
        range: { min: 36, max: 45 },
        categories: ['MUSIC'],
        hintsToPick: 1,
      }),
    );
    fixture.detectChanges();
    expect(page.textContent).toContain('Older than 30, by 6 to 15 years');

    button('Music').click();
    fixture.detectChanges();
    button(GAME_TEXT.showHints).click();
    const hint = http.expectOne('/api/game/2026-11-02/rounds/1/hint');
    expect(hint.request.body).toEqual({ categories: ['MUSIC'] });
    hint.flush(
      round(1, {
        step: 'AWAITING_SECOND_GUESS',
        firstGuess: 30,
        direction: 'OLDER',
        gap: 'CLEARLY',
        range: { min: 36, max: 45 },
        hints: [{ category: 'MUSIC', text: 'A hit.' }],
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

  it('sends the language of the hints the player chose with the first guess', () => {
    TestBed.inject(HintLanguage).set('FR');
    load(today([round(1)]));

    guess('30');

    const first = http.expectOne('/api/game/2026-11-02/rounds/1/first-guess');
    expect(first.request.body).toEqual({ guess: 30, language: 'FR' });
    first.flush(finished(1));
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

  it('leads from the summary of the day to the free investigation', () => {
    load(today([finished(1)]));

    const link = Array.from(page.querySelectorAll('a')).find(
      (a) => a.textContent?.trim() === GAME_TEXT.tryFree,
    );
    expect(link?.getAttribute('href')).toBe('/free');
  });

  const playedDay = () =>
    today(
      [
        finished(1, { points: 1000, secondGuess: null, secondPoints: null }),
        finished(2, { points: 920 }),
        round(3, { status: 'NEUTRALIZED', imageUrl: null }),
        finished(4, { points: 420 }),
      ],
      { date: '2026-11-13' },
    );
  const expectedText =
    "AgeGuessr · Nov 13, 2026: well played!\n2340 / 3000 points\n🎯🟩⬛🟥\n🎯 first try · 🟩 very close · 🟥 far off · ⬛ photo withdrawn\nPlay today's game at https://ageguessr.app";

  it('offers to share the result only once the day is played', () => {
    load(today([finished(1), round(2)]));
    expect(button(GAME_TEXT.shareResult)).toBeUndefined();
  });

  it('copies the result of the day where there is no share sheet, and says so', async () => {
    load(playedDay());

    button(GAME_TEXT.shareResult).click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(share.handed.copied).toEqual([expectedText]);
    expect(page.querySelector('[role="status"]')?.textContent).toContain(GAME_TEXT.copied);
  });

  it('hands the result of the day to the share sheet when there is one', async () => {
    share.target.canShare = () => true;
    load(playedDay());

    button(GAME_TEXT.shareResult).click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(share.handed.shared).toEqual([expectedText]);
    expect(page.textContent).not.toContain(GAME_TEXT.copied);
  });

  it('says so when the result cannot be copied', async () => {
    share.target.copy = () => Promise.reject(new DOMException('', 'NotAllowedError'));
    load(playedDay());

    button(GAME_TEXT.shareResult).click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(page.querySelector('[role="alert"]')?.textContent).toContain(GAME_TEXT.genericError);
  });

  it('offers a signed-in player to compare the day with their friends', () => {
    const link = () =>
      Array.from(page.querySelectorAll('a')).find(
        (a) => a.textContent?.trim() === GAME_TEXT.compareFriends,
      );
    load(today([finished(1), finished(2), finished(3)]));
    expect(link()).toBeUndefined();

    TestBed.inject(Auth).currentUser.set({
      id: '1',
      email: 'me@x.com',
      username: 'Jane_D',
      emailVerified: true,
      termsAccepted: true,
      role: 'USER',
      language: 'EN',
    });
    fixture.detectChanges();
    expect(link()?.getAttribute('href')).toBe('/friends');
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
          hintsToPick: 1,
        }),
        round(2),
      ]),
    );
    button('Music').click();
    fixture.detectChanges();
    button(GAME_TEXT.showHints).click();

    conflict('/api/game/2026-11-02/rounds/1/hint', 'ROUND_NEUTRALIZED');
    load(today([round(1, { status: 'NEUTRALIZED', imageUrl: null }), round(2)]));

    expect(page.querySelector('[role=status]')?.textContent).toContain(GAME_TEXT.removedExplained);
    expect(page.querySelector('h2')?.textContent).toContain(`${GAME_TEXT.round} 2 of 2`);
  });

  it('reloads the round when the leads offered changed while picking', () => {
    load(
      today([
        round(1, {
          step: 'AWAITING_HINT',
          firstGuess: 30,
          direction: 'OLDER',
          categories: ['MUSIC'],
          hintsToPick: 1,
        }),
      ]),
    );
    button('Music').click();
    fixture.detectChanges();
    button(GAME_TEXT.showHints).click();

    conflict('/api/game/2026-11-02/rounds/1/hint', 'HINT_COUNT');
    load(
      today([
        round(1, {
          step: 'AWAITING_HINT',
          firstGuess: 30,
          direction: 'OLDER',
          categories: ['MUSIC', 'FILM'],
          hintsToPick: 2,
        }),
      ]),
    );

    expect(page.textContent).toContain('Pick 2 hints');
    expect(page.querySelector('[role=alert]')).toBeNull();
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
    expect(page.querySelector('app-age-input input')).not.toBeNull();
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

    expect(page.querySelector('app-age-input input')).not.toBeNull();
  });

  it('reports the photo of the round on screen and keeps it reported', () => {
    load(today([round(1), round(2)]));
    guess('40');
    http.expectOne('/api/game/2026-11-02/rounds/1/first-guess').flush(finished(1));
    fixture.detectChanges();

    button(GAME_TEXT.reportPhoto).click();
    fixture.detectChanges();
    const radio = page.querySelector<HTMLInputElement>('input[value=WRONG_PERSON]')!;
    radio.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    const report = http.expectOne('/api/game/2026-11-02/rounds/1/report');
    expect(report.request.method).toBe('POST');
    expect(report.request.body).toEqual({ target: 'PHOTO', reason: 'WRONG_PERSON' });
    report.flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();

    expect(page.textContent).toContain(GAME_TEXT.reportSent);
    expect(fixture.componentInstance.active()?.result?.photoReported).toBe(true);
    expect(fixture.componentInstance.active()?.result?.reportedHints).toEqual([]);
  });
});

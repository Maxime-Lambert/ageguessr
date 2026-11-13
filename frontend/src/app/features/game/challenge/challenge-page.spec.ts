import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { Auth } from '../../auth/auth';
import { GAME_TEXT } from '../game-vocabulary';
import { Challenge } from '../models';
import { SHARE_TARGET } from '../../../shared/share-target';
import { challenge, fakeShareTarget, finished, round, series } from '../testing';
import { ChallengePage } from './challenge-page';

describe('ChallengePage', () => {
  let fixture: ComponentFixture<ChallengePage>;
  let http: HttpTestingController;
  let page: HTMLElement;
  let share: ReturnType<typeof fakeShareTarget>;

  beforeEach(() => {
    localStorage.clear();
    share = fakeShareTarget();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: SHARE_TARGET, useValue: share.target },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ code: 'K7F2QX9M' }) } },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    // The session is restored: a guest.
    TestBed.inject(Auth).sessionChecked.set(true);
    fixture = TestBed.createComponent(ChallengePage);
    page = fixture.nativeElement as HTMLElement;
  });

  afterEach(() => http.verify());

  function opened(found: Challenge): void {
    http.expectOne('/api/game/challenges/K7F2QX9M').flush(found);
    fixture.detectChanges();
  }

  function button(text: string): HTMLButtonElement {
    return Array.from(page.querySelectorAll('button')).find((b) => b.textContent?.trim() === text)!;
  }

  it('says who sent it, then lets a guest join under a name and play', () => {
    opened(challenge());
    expect(page.textContent).toContain('Sherlock challenges you on 5 cases.');
    expect(page.textContent).toContain(GAME_TEXT.onceOnly);

    const input = page.querySelector<HTMLInputElement>('input[autocomplete="nickname"]')!;
    input.value = 'Watson';
    input.dispatchEvent(new Event('input'));
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    const join = http.expectOne('/api/game/challenges/K7F2QX9M/join');
    expect(join.request.body).toEqual({ nickname: 'Watson' });
    join.flush(challenge({ status: 'PLAYING', seriesId: 'series-1' }));
    http
      .expectOne('/api/game/series/series-1')
      .flush(series([round(1), round(2)], { creator: false }));
    fixture.detectChanges();

    expect(page.querySelector('h2')?.textContent).toContain(`${GAME_TEXT.round} 1 of 2`);
  });

  it('refuses a name the API would refuse, before sending it', () => {
    opened(challenge());

    const input = page.querySelector<HTMLInputElement>('input[autocomplete="nickname"]')!;
    input.value = 'a b';
    input.dispatchEvent(new Event('input'));
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    http.expectNone('/api/game/challenges/K7F2QX9M/join');
    expect(page.textContent).toContain(GAME_TEXT.nicknameInvalid);
  });

  it('says when the name belongs to an account', () => {
    opened(challenge());

    const input = page.querySelector<HTMLInputElement>('input[autocomplete="nickname"]')!;
    input.value = 'holmes';
    input.dispatchEvent(new Event('input'));
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    http
      .expectOne('/api/game/challenges/K7F2QX9M/join')
      .flush(
        { errorCode: 'NICKNAME_TAKEN', message: 'x' },
        { status: 400, statusText: 'Bad Request' },
      );
    fixture.detectChanges();

    expect(page.textContent).toContain(GAME_TEXT.nicknameTaken);
  });

  it('shows the leaderboard once the last round is played', () => {
    opened(challenge({ status: 'PLAYING', seriesId: 'series-1' }));
    http.expectOne('/api/game/series/series-1').flush(series([round(1)], { creator: false }));
    fixture.detectChanges();

    const guess = page.querySelector<HTMLInputElement>('app-age-input input')!;
    guess.value = '40';
    guess.dispatchEvent(new Event('input'));
    page.querySelector('form')!.dispatchEvent(new Event('submit'));
    http
      .expectOne('/api/game/series/series-1/rounds/1/first-guess')
      .flush(finished(1, { points: 1000, secondGuess: null }));
    fixture.detectChanges();
    button(GAME_TEXT.seeSummary).click();
    http
      .expectOne('/api/game/series/series-1')
      .flush(series([finished(1, { points: 1000 })], { creator: false }));
    http.expectOne('/api/game/challenges/K7F2QX9M').flush(
      challenge({
        status: 'FINISHED',
        seriesId: 'series-1',
        leaderboard: [
          { rank: 1, name: 'Watson', guest: true, points: 1000, max: 1000, you: true },
          { rank: 2, name: 'Sherlock', guest: true, points: 600, max: 1000, you: false },
        ],
      }),
    );
    fixture.detectChanges();

    const rows = Array.from(page.querySelectorAll('tbody tr')).map((row) =>
      row.textContent?.replace(/\s+/g, ' ').trim(),
    );
    expect(rows).toEqual([
      `1 Watson (${GAME_TEXT.guestMark})· ${GAME_TEXT.youMark} 1000 / 1000`,
      `2 Sherlock (${GAME_TEXT.guestMark}) 600 / 1000`,
    ]);
  });

  it('shares the result with the link of the challenge', async () => {
    opened(challenge({ status: 'FINISHED', seriesId: 'series-1' }));
    http
      .expectOne('/api/game/series/series-1')
      .flush(series([finished(1, { points: 1000 })], { creator: false }));
    fixture.detectChanges();

    button(GAME_TEXT.shareResult).click();
    await fixture.whenStable();

    expect(share.handed.copied).toEqual([
      'AgeGuessr · Challenge · 1000 / 1000\n🟩\nhttps://ageguessr.app/challenge/K7F2QX9M',
    ]);
  });

  it('says so on the summary when the result cannot be copied', async () => {
    share.target.copy = () => Promise.reject(new DOMException('', 'NotAllowedError'));
    opened(challenge({ status: 'FINISHED', seriesId: 'series-1' }));
    http
      .expectOne('/api/game/series/series-1')
      .flush(series([finished(1, { points: 1000 })], { creator: false }));
    fixture.detectChanges();

    button(GAME_TEXT.shareResult).click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(page.querySelector('[role="alert"]')?.textContent).toContain(GAME_TEXT.genericError);
  });

  it('says when the challenge does not exist or has expired', () => {
    http
      .expectOne('/api/game/challenges/K7F2QX9M')
      .flush({ errorCode: 'NOT_FOUND', message: 'x' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(page.textContent).toContain(GAME_TEXT.challengeNotFound);
  });
});

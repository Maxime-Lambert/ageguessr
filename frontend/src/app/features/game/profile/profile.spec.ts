import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Auth } from '../../auth/auth';
import { History, HistoryDay } from '../models';
import { Profile } from './profile';

const DAY: HistoryDay = {
  date: '2026-11-08',
  points: 1734,
  max: 3000,
  complete: true,
  rounds: [
    {
      position: 1,
      finished: true,
      neutralized: false,
      name: 'Jane Doe',
      ageAtPhoto: 52,
      firstGuess: 45,
      secondGuess: 50,
      points: 734,
    },
    {
      position: 2,
      finished: true,
      neutralized: true,
      name: null,
      ageAtPhoto: null,
      firstGuess: 30,
      secondGuess: null,
      points: 1000,
    },
  ],
};

function history(overrides: Partial<History> = {}): History {
  return {
    stats: {
      gamesPlayed: 12,
      averagePoints: 1820,
      bestPoints: 2750,
      currentStreak: 4,
      bestStreak: 9,
    },
    days: [DAY],
    page: 0,
    hasMore: false,
    ...overrides,
  };
}

describe('Profile', () => {
  let fixture: ComponentFixture<Profile>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Profile],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    TestBed.inject(Auth).currentUser.set({
      id: '1',
      email: 'me@x.com',
      username: 'Jane_D',
      emailVerified: true,
      termsAccepted: true,
      role: 'USER',
      language: 'EN',
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Profile);
  });

  afterEach(() => http.verify());

  const page = () => {
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  };
  const respond = (body: History, pageNumber = 0) =>
    http.expectOne(`/api/game/history?page=${pageNumber}`).flush(body);

  it('shows the statistics, the streak first', () => {
    page();
    respond(history());
    const root = page();

    expect(root.querySelector('h1')?.textContent).toContain('Jane_D');
    expect(root.querySelector('#streak-title')?.textContent).toContain('Current streak');
    expect(root.textContent).toContain('4');
    const tiles = Array.from(root.querySelectorAll('dl > div')).map((tile) =>
      tile.textContent?.replace(/\s+/g, ' ').trim(),
    );
    expect(tiles).toEqual([
      'Best streak 9',
      'Files closed 12',
      'Average score 1,820',
      'Best score 2,750',
    ]);
  });

  describe('the last 30 days', () => {
    beforeEach(() => vi.useFakeTimers({ now: new Date('2026-11-09T23:30:00Z'), toFake: ['Date'] }));
    afterEach(() => vi.useRealTimers());

    it('draws one square a day up to today in UTC, by score, unfinished days outlined', () => {
      page();
      respond(
        history({
          days: [
            { ...DAY, date: '2026-11-09', points: 3000, complete: true },
            { ...DAY, date: '2026-11-08', points: 400, complete: false },
            { ...DAY, date: '2026-10-11', points: 1600, complete: true },
            { ...DAY, date: '2026-10-10', points: 3000, complete: true },
          ],
        }),
      );
      const root = page();
      const squares = Array.from(root.querySelectorAll<HTMLElement>('[data-day]'));

      expect(squares).toHaveLength(30);
      expect(squares[0].className).toContain('bg-score/75');
      expect(squares[1].className).toContain('bg-surface-2');
      expect(squares[28].className).toContain('border-dashed');
      expect(squares[29].className).toMatch(/bg-score(?!\/)/);
      expect(root.querySelector('.sr-only')?.textContent).toContain(
        'Files finished in the last 30 days: 2',
      );
    });
  });

  it('lists a day with its rounds, and no name for a withdrawn photo', () => {
    page();
    respond(history());
    const day = page().querySelector('ol > li')!;
    const text = day.textContent!.replace(/\s+/g, ' ');

    expect(day.querySelector('h3')?.textContent).toContain('Sunday, November 8, 2026');
    expect(text).toContain('1,734 out of 3,000');
    expect(text).toContain('Jane Doe · 52 years old');
    expect(text).toContain('Your guesses: 45 → 50');
    expect(text).toContain('Withdrawn from the file');
    expect(text).not.toContain('Not finished');
    expect(day.querySelector('img')).toBeNull();
  });

  it('marks a day and a round not finished yet', () => {
    page();
    respond(
      history({
        days: [
          {
            ...DAY,
            complete: false,
            rounds: [
              { ...DAY.rounds[0], finished: false, name: null, ageAtPhoto: null, points: null },
            ],
          },
        ],
      }),
    );
    const text = page().querySelector('ol > li')!.textContent!;

    expect(text.match(/Not finished/g)).toHaveLength(2);
    expect(text).not.toContain('Jane Doe');
  });

  it('invites to play when no day was played yet', () => {
    page();
    respond(
      history({
        days: [],
        stats: { gamesPlayed: 0, averagePoints: 0, bestPoints: 0, currentStreak: 0, bestStreak: 0 },
      }),
    );
    const root = page();

    expect(root.textContent).toContain('No file closed yet.');
    expect(root.querySelector('a[href="/"]')?.textContent).toContain("Open today's file");
  });

  it('offers to try again after an error', () => {
    page();
    http.expectOne('/api/game/history?page=0').flush(null, { status: 500, statusText: 'Error' });
    const root = page();

    expect(root.querySelector('[role="alert"]')?.textContent).toContain(
      'Your history could not be loaded.',
    );
    root.querySelector<HTMLButtonElement>('main button')!.click();
    respond(history());

    expect(page().querySelectorAll('ol > li')).toHaveLength(1);
  });

  it('adds the older days below on demand', () => {
    page();
    respond(history({ hasMore: true }));
    const older = Array.from(page().querySelectorAll<HTMLButtonElement>('button')).find((button) =>
      button.textContent?.includes('Older files'),
    )!;

    older.click();
    respond(history({ days: [{ ...DAY, date: '2026-10-01' }], page: 1, hasMore: false }), 1);
    const root = page();

    expect(root.querySelectorAll('ol > li')).toHaveLength(2);
    expect(
      Array.from(root.querySelectorAll('button')).some((b) =>
        b.textContent?.includes('Older files'),
      ),
    ).toBe(false);
  });
});

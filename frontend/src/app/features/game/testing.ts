import { Challenge, GameScore, RoundReveal, RoundView, Series, TodayGame } from './models';

/** A playable round of today's game, not started. */
export function round(position: number, overrides: Partial<RoundView> = {}): RoundView {
  return {
    position,
    status: 'PLAYABLE',
    imageUrl: `/api/images/public-${position}`,
    step: 'NOT_STARTED',
    firstGuess: null,
    direction: null,
    categories: null,
    hint: null,
    result: null,
    ...overrides,
  };
}

export function reveal(overrides: Partial<RoundReveal> = {}): RoundReveal {
  return {
    age: 40,
    firstGuess: 30,
    firstPoints: 184,
    secondGuess: 38,
    secondPoints: 409,
    points: 593,
    person: {
      name: 'Jane Doe',
      wikipediaUrl: 'https://www.wikidata.org/wiki/Special:GoToLinkedPage/enwiki/Q42',
    },
    credit: {
      author: 'Jane Photographer',
      license: 'CC BY-SA 4.0',
      licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0',
      sourceUrl: 'https://commons.wikimedia.org/wiki/File:Jane.jpg',
    },
    hint: {
      category: 'MUSIC',
      text: 'A hit.',
      year: 1986,
      sourceUrl: 'https://en.wikipedia.org/wiki/Hit',
    },
    ...overrides,
  };
}

export function finished(position: number, overrides: Partial<RoundReveal> = {}): RoundView {
  return round(position, { step: 'FINISHED', firstGuess: 30, result: reveal(overrides) });
}

export function today(rounds: RoundView[], overrides: Partial<TodayGame> = {}): TodayGame {
  return { date: '2026-11-02', rounds, score: scoreOf(rounds), ...overrides };
}

function scoreOf(rounds: RoundView[]): GameScore {
  const playable = rounds.filter((r) => r.status === 'PLAYABLE');
  return {
    points: playable.reduce((sum, r) => sum + (r.result?.points ?? 0), 0),
    max: playable.length * 1000,
    complete: playable.every((r) => r.step === 'FINISHED'),
  };
}

/** A free series of the given rounds, created by the player. */
export function series(rounds: RoundView[], overrides: Partial<Series> = {}): Series {
  return {
    id: 'series-1',
    size: rounds.length,
    rounds,
    score: scoreOf(rounds),
    shareCode: null,
    creator: true,
    ...overrides,
  };
}

/** A challenge the visitor has not joined yet. */
export function challenge(overrides: Partial<Challenge> = {}): Challenge {
  return {
    code: 'K7F2QX9M',
    size: 5,
    rounds: 5,
    creatorName: 'Sherlock',
    creatorGuest: true,
    status: 'NOT_JOINED',
    seriesId: null,
    score: null,
    leaderboard: [],
    ...overrides,
  };
}

/** The share sheet and clipboard of a spec: records what the page handed over. */
export function fakeShareTarget(canShare = false) {
  const handed = { shared: [] as string[], copied: [] as string[] };
  return {
    handed,
    target: {
      canShare: () => canShare,
      share: (text: string) => {
        handed.shared.push(text);
        return Promise.resolve();
      },
      copy: (text: string) => {
        handed.copied.push(text);
        return Promise.resolve();
      },
      origin: () => 'https://ageguessr.app',
    },
  };
}

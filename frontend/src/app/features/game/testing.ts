import { RoundReveal, RoundView, TodayGame } from './models';

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
  const playable = rounds.filter((r) => r.status === 'PLAYABLE');
  return {
    date: '2026-11-02',
    rounds,
    score: {
      points: playable.reduce((sum, r) => sum + (r.result?.points ?? 0), 0),
      max: playable.length * 1000,
      complete: playable.every((r) => r.step === 'FINISHED'),
    },
    ...overrides,
  };
}

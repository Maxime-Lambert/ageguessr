/** Network stand-ins for the game screen, shared by the English and French tests. */
import { Page } from '@playwright/test';
import { asGuest } from './session';

/** 1x1 PNG: the browser must really decode and paint the photo. */
const PIXEL = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=',
  'base64',
);

const DATE = '2026-11-02';

export function round(position: number, overrides: Record<string, unknown> = {}) {
  return {
    position,
    status: 'PLAYABLE',
    imageUrl: `/api/images/public-${position}`,
    step: 'NOT_STARTED',
    firstGuess: null,
    direction: null,
    gap: null,
    range: null,
    categories: null,
    hintsToPick: null,
    hints: null,
    result: null,
    ...overrides,
  };
}

export const RESULT = {
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
  hints: [
    {
      category: 'MUSIC',
      text: 'A hit topped the charts.',
      year: 1986,
      sourceUrl: 'https://en.wikipedia.org/wiki/Hit',
    },
  ],
  photoReported: false,
  reportedHints: [],
};

function json(body: unknown) {
  return { status: 200, contentType: 'application/json', body: JSON.stringify(body) };
}

/**
 * Marks the first-visit rules card as seen, so a test sees the round straight away;
 * the card has its own test (game.spec.ts).
 */
export async function skipRules(page: Page): Promise<void> {
  await page.addInitScript(() => localStorage.setItem('ageguessr-rules-seen', '1'));
}

/** Picks the three leads of the mocked round, then opens them. */
export async function pickLeads(
  page: Page,
  names = ['Music', 'Film', 'Sport'],
  open = 'Open these leads',
) {
  for (const name of names) {
    await page.getByRole('button', { name, exact: true }).click();
  }
  await page.getByRole('button', { name: open }).click();
}

/** Mocks the game API for one round played to the end; returns the bodies sent, in order. */
export async function mockGame(
  page: Page,
  rounds = [round(1), round(2), round(3)],
): Promise<unknown[]> {
  const sent: unknown[] = [];
  await skipRules(page);
  await asGuest(page);
  await page.route('**/api/game/today', (route) =>
    route.fulfill(json({ date: DATE, rounds, score: { points: 0, max: 3000, complete: false } })),
  );
  await page.route('**/api/images/*', (route) =>
    route.fulfill({ status: 200, contentType: 'image/png', body: PIXEL }),
  );
  await page.route(`**/api/game/${DATE}/rounds/*/*`, (route) => {
    const request = route.request();
    const [position, step] = request.url().split('/').slice(-2);
    sent.push(request.postDataJSON());
    const n = Number(position);
    if (step === 'report') {
      return route.fulfill({ status: 204 });
    }
    if (step === 'first-guess') {
      return route.fulfill(
        json(
          round(n, {
            step: 'AWAITING_HINT',
            firstGuess: 30,
            direction: 'OLDER',
            gap: 'CLEARLY',
            range: { min: 36, max: 45 },
            categories: ['MUSIC', 'FILM', 'SPORT', 'WORLD'],
            hintsToPick: 3,
          }),
        ),
      );
    }
    if (step === 'hint') {
      return route.fulfill(
        json(
          round(n, {
            step: 'AWAITING_SECOND_GUESS',
            firstGuess: 30,
            direction: 'OLDER',
            gap: 'CLEARLY',
            range: { min: 36, max: 45 },
            hints: [
              { category: 'MUSIC', text: 'A hit topped the charts.' },
              { category: 'FILM', text: 'A film filled the cinemas.' },
              { category: 'SPORT', text: 'A team won the cup.' },
            ],
          }),
        ),
      );
    }
    return route.fulfill(json(round(n, { step: 'FINISHED', firstGuess: 30, result: RESULT })));
  });
  return sent;
}

const SERIES_ID = 'series-1';

function finishedRound(position: number) {
  return round(position, {
    step: 'FINISHED',
    firstGuess: 40,
    result: {
      ...RESULT,
      firstGuess: 40,
      firstPoints: 500,
      secondGuess: null,
      secondPoints: null,
      points: 1000,
    },
  });
}

function seriesOf(rounds: ReturnType<typeof round>[], size = rounds.length) {
  const playable = rounds.filter((r) => r.status === 'PLAYABLE');
  return {
    id: SERIES_ID,
    size,
    rounds,
    score: {
      points: playable.filter((r) => r.step === 'FINISHED').length * 1000,
      max: playable.length * 1000,
      complete: playable.every((r) => r.step === 'FINISHED'),
    },
    shareCode: null,
    creator: true,
  };
}

/**
 * Mocks a free series of `count` rounds, none in progress: every guess is exact (40), so
 * a round ends at its first guess. Returns the bodies sent to start a series.
 */
export async function mockSeries(page: Page, count = 5): Promise<unknown[]> {
  const started: unknown[] = [];
  let rounds = Array.from({ length: count }, (_, i) => round(i + 1));
  await skipRules(page);
  await asGuest(page);
  await page.route('**/api/images/*', (route) =>
    route.fulfill({ status: 200, contentType: 'image/png', body: PIXEL }),
  );
  await page.route('**/api/game/series/current', (route) => route.fulfill({ status: 204 }));
  await page.route('**/api/game/series', (route) => {
    started.push(route.request().postDataJSON());
    return route.fulfill(json(seriesOf(rounds, count)));
  });
  await page.route(`**/api/game/series/${SERIES_ID}`, (route) =>
    route.fulfill(json(seriesOf(rounds))),
  );
  await page.route(`**/api/game/series/${SERIES_ID}/rounds/*/first-guess`, (route) => {
    const position = Number(route.request().url().split('/').slice(-2)[0]);
    rounds = rounds.map((r) => (r.position === position ? finishedRound(position) : r));
    return route.fulfill(json(finishedRound(position)));
  });
  return started;
}

/**
 * Mocks a challenge from "Sherlock" on a series of `count` rounds, for a guest who has not
 * joined yet. Returns the bodies sent to join.
 */
export async function mockChallenge(page: Page, count = 5): Promise<unknown[]> {
  const joined: unknown[] = [];
  let status = 'NOT_JOINED';
  await mockSeries(page, count);
  const challenge = () => ({
    code: 'K7F2QX9M',
    size: count,
    rounds: count,
    creatorName: 'Sherlock',
    creatorGuest: true,
    status,
    seriesId: status === 'NOT_JOINED' ? null : SERIES_ID,
    score: null,
    leaderboard:
      status === 'FINISHED'
        ? [
            {
              rank: 1,
              name: 'Watson',
              guest: true,
              points: count * 1000,
              max: count * 1000,
              you: true,
            },
            { rank: 2, name: 'Sherlock', guest: true, points: 1200, max: count * 1000, you: false },
          ]
        : [],
  });
  await page.route('**/api/game/challenges/K7F2QX9M', (route) => {
    // Asked again once the series is finished: the leaderboard comes with it.
    if (status === 'PLAYING') {
      status = 'FINISHED';
    }
    return route.fulfill(json(challenge()));
  });
  await page.route('**/api/game/challenges/K7F2QX9M/join', (route) => {
    joined.push(route.request().postDataJSON());
    status = 'PLAYING';
    return route.fulfill(json(challenge()));
  });
  return joined;
}

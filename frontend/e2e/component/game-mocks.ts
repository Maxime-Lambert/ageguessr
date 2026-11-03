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
    categories: null,
    hint: null,
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
  hint: {
    category: 'MUSIC',
    text: 'A hit topped the charts.',
    year: 1986,
    sourceUrl: 'https://en.wikipedia.org/wiki/Hit',
  },
};

function json(body: unknown) {
  return { status: 200, contentType: 'application/json', body: JSON.stringify(body) };
}

/** Mocks the game API for one round played to the end; returns the bodies sent, in order. */
export async function mockGame(
  page: Page,
  rounds = [round(1), round(2), round(3)],
): Promise<unknown[]> {
  const sent: unknown[] = [];
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
    if (step === 'first-guess') {
      return route.fulfill(
        json(
          round(n, {
            step: 'AWAITING_HINT',
            firstGuess: 30,
            direction: 'OLDER',
            categories: ['MUSIC', 'FILM'],
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
            hint: { category: 'MUSIC', text: 'A hit topped the charts.' },
          }),
        ),
      );
    }
    return route.fulfill(json(round(n, { step: 'FINISHED', firstGuess: 30, result: RESULT })));
  });
  return sent;
}
